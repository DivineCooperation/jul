package org.openbase.jul.extension.rsb.com;

/*
 * #%L
 * JUL Extension RSB Communication
 * $Id:$
 * $HeadURL:$
 * %%
 * Copyright (C) 2015 - 2016 openbase.org
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Lesser Public License for more details.
 * 
 * You should have received a copy of the GNU General Lesser Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/lgpl-3.0.html>.
 * #L%
 */
import com.google.protobuf.Descriptors;
import com.google.protobuf.GeneratedMessage;
import java.util.Random;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.openbase.jps.core.JPService;
import org.openbase.jps.exception.JPServiceException;
import org.openbase.jul.exception.CouldNotPerformException;
import org.openbase.jul.exception.CouldNotTransformException;
import org.openbase.jul.exception.InitializationException;
import org.openbase.jul.exception.InstantiationException;
import org.openbase.jul.exception.InvalidStateException;
import org.openbase.jul.exception.NotAvailableException;
import org.openbase.jul.exception.TimeoutException;
import org.openbase.jul.exception.printer.ExceptionPrinter;
import org.openbase.jul.exception.printer.LogLevel;
import static org.openbase.jul.extension.rsb.com.RSBCommunicationService.RPC_REQUEST_STATUS;
import org.openbase.jul.extension.rsb.com.jp.JPRSBTransport;
import org.openbase.jul.extension.rsb.iface.RSBListener;
import org.openbase.jul.extension.rsb.iface.RSBRemoteServer;
import org.openbase.jul.extension.rsb.scope.ScopeGenerator;
import org.openbase.jul.extension.rsb.scope.ScopeTransformer;
import static org.openbase.jul.iface.Shutdownable.registerShutdownHook;
import org.openbase.jul.pattern.Observable;
import org.openbase.jul.pattern.ObservableImpl;
import org.openbase.jul.pattern.Observer;
import org.openbase.jul.pattern.Remote;
import static org.openbase.jul.pattern.Remote.ConnectionState.CONNECTED;
import static org.openbase.jul.pattern.Remote.ConnectionState.CONNECTING;
import static org.openbase.jul.pattern.Remote.ConnectionState.DISCONNECTED;
import org.openbase.jul.schedule.GlobalExecutionService;
import org.openbase.jul.schedule.SyncObject;
import org.openbase.jul.schedule.WatchDog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rsb.Event;
import rsb.Handler;
import rsb.config.ParticipantConfig;
import rsb.config.TransportConfig;
import rst.rsb.ScopeType.Scope;

/**
 *
 * @author <a href="mailto:divine@openbase.org">Divine Threepwood</a>
 * @param <M>
 */
public abstract class RSBRemoteService<M extends GeneratedMessage> implements RSBRemote<M> {

    protected final Logger logger = LoggerFactory.getLogger(getClass());

    public static final long REQUEST_TIMEOUT = 15000;
    public static final long PING_TIMEOUT = 5000;
    public static final long CONNECTION_TIMEOUT = 60000;
    public static final long DATA_WAIT_TIMEOUT = 1000;
    public static final long METHOD_CALL_START_TIMEOUT = 500;
    public static final double METHOD_CALL_TIMEOUT_MULTIPLIER = 1.2;
    public static final long METHOD_CALL_MAX_TIMEOUT = 30000;

    private static final Random JITTER_RANDOM = new Random();

    static {
        RSBSharedConnectionConfig.load();
    }

    private RSBListener listener;
    private WatchDog listenerWatchDog, remoteServerWatchDog;
    private RSBRemoteServer remoteServer;
    private Remote.ConnectionState connectionState;
    private long connectionPing;
    private long lastPingReceived;
    private final Handler mainHandler;

    private final SyncObject syncMonitor = new SyncObject("SyncMonitor");
    private final SyncObject connectionMonitor = new SyncObject("ConnectionMonitor");

    private CompletableFuture<M> syncFuture;
    private Future<M> syncTask;

    protected Scope scope;
    private M data;
    private boolean initialized;
    private final Class<M> dataClass;

    private final ObservableImpl<ConnectionState> connectionStateObservable = new ObservableImpl<>();
    private final ObservableImpl<M> dataObservable = new ObservableImpl<>();

    public RSBRemoteService(final Class<M> dataClass) {
        this.dataClass = dataClass;
        this.mainHandler = new InternalUpdateHandler();
        this.initialized = false;
        this.remoteServer = new NotInitializedRSBRemoteServer();
        this.listener = new NotInitializedRSBListener();
        this.connectionState = DISCONNECTED;
        this.connectionPing = -1;
        this.lastPingReceived = -1;
        registerShutdownHook(this);
    }

    /**
     * {@inheritDoc}
     *
     * @param scope {@inheritDoc}
     * @throws org.openbase.jul.exception.InitializationException {@inheritDoc}
     * @throws java.lang.InterruptedException {@inheritDoc}
     */
    @Override
    public void init(final Scope scope) throws InitializationException, InterruptedException {
        init(scope, RSBSharedConnectionConfig.getParticipantConfig());
    }

    /**
     * {@inheritDoc}
     *
     * @param scope {@inheritDoc}
     * @throws org.openbase.jul.exception.InitializationException {@inheritDoc}
     * @throws java.lang.InterruptedException {@inheritDoc}
     */
    @Override
    public void init(final rsb.Scope scope) throws InitializationException, InterruptedException {
        init(scope, RSBSharedConnectionConfig.getParticipantConfig());
    }

    /**
     * {@inheritDoc}
     *
     * @throws org.openbase.jul.exception.InitializationException {@inheritDoc}
     * @throws java.lang.InterruptedException {@inheritDoc}
     */
    @Override
    public void init(final String scope) throws InitializationException, InterruptedException {
        try {
            init(new rsb.Scope(scope));
        } catch (CouldNotPerformException | NullPointerException ex) {
            throw new InitializationException(this, ex);
        }
    }

    /**
     * {@inheritDoc}
     *
     * @param scope {@inheritDoc}
     * @param participantConfig {@inheritDoc}
     * @throws InitializationException {@inheritDoc}
     * @throws InterruptedException {@inheritDoc}
     */
    @Override
    public void init(final rsb.Scope scope, final ParticipantConfig participantConfig) throws InitializationException, InterruptedException {
        try {
            init(ScopeTransformer.transform(scope), participantConfig);
        } catch (CouldNotTransformException ex) {
            throw new InitializationException(this, ex);
        }
    }

    /**
     * Method is called after communication initialization. You can overwrite
     * this method to trigger any component specific initialization.
     *
     * @throws InitializationException
     * @throws InterruptedException
     */
    protected void postInit() throws InitializationException, InterruptedException {
        // overwrite for specific post initialization tasks.
    }

    private void enableTransport(final ParticipantConfig participantConfig, final JPRSBTransport.TransportType type) {
        if (type == JPRSBTransport.TransportType.DEFAULT) {
            return;
        }

        for (TransportConfig transport : participantConfig.getEnabledTransports()) {
            logger.debug("Disable " + transport.getName() + " communication.");
            transport.setEnabled(false);
        }
        logger.debug("Enable [" + type.name().toLowerCase() + "] communication.");
        participantConfig.getOrCreateTransport(type.name().toLowerCase()).setEnabled(true);
    }

    /**
     * {@inheritDoc}
     *
     * @param scope {@inheritDoc}
     * @param participantConfig {@inheritDoc}
     * @throws org.openbase.jul.exception.InitializationException {@inheritDoc}
     * @throws java.lang.InterruptedException {@inheritDoc}
     */
    @Override
    public synchronized void init(final Scope scope, final ParticipantConfig participantConfig) throws InitializationException, InterruptedException {
        try {
            final boolean alreadyActivated = isActive();
            ParticipantConfig internalParticipantConfig = participantConfig;
            try {
                // activate transport communication set by the JPRSBTransport porperty.
                enableTransport(internalParticipantConfig, JPService.getProperty(JPRSBTransport.class).getValue());
            } catch (JPServiceException ex) {
                ExceptionPrinter.printHistory(new CouldNotPerformException("Could not access java property!", ex), logger);
            }

            if (scope == null) {
                throw new NotAvailableException("scope");
            }

            // check if this instance was partly or fully initialized before.
            if (initialized | listenerWatchDog != null | remoteServerWatchDog != null) {
                deactivate();
                reset();
            }

            this.scope = scope;

            rsb.Scope internalScope = new rsb.Scope(ScopeGenerator.generateStringRep(scope).toLowerCase());
            logger.debug("Init RSBCommunicationService for component " + getClass().getSimpleName() + " on " + this.scope + ".");

            initListener(internalScope, internalParticipantConfig);
            initRemoteServer(internalScope, internalParticipantConfig);

            addHandler(mainHandler, true);

            postInit();
            initialized = true;

            // check if remote service was already activated before and recover state.
            if (alreadyActivated) {
                activate();
            }
        } catch (CouldNotPerformException ex) {
            throw new InitializationException(this, ex);
        }
    }

    private void initListener(final rsb.Scope scope, final ParticipantConfig participantConfig) throws CouldNotPerformException {
        try {
            this.listener = RSBFactoryImpl.getInstance().createSynchronizedListener(scope.concat(RSBCommunicationService.SCOPE_SUFFIX_STATUS), participantConfig);
            this.listenerWatchDog = new WatchDog(listener, "RSBListener[" + scope.concat(RSBCommunicationService.SCOPE_SUFFIX_STATUS) + "]");
        } catch (InstantiationException ex) {
            throw new CouldNotPerformException("Could not create Listener on scope [" + scope + "]!", ex);
        }
    }

    private void initRemoteServer(final rsb.Scope scope, final ParticipantConfig participantConfig) throws CouldNotPerformException {
        try {
            this.remoteServer = RSBFactoryImpl.getInstance().createSynchronizedRemoteServer(scope.concat(RSBCommunicationService.SCOPE_SUFFIX_CONTROL), participantConfig);
            this.remoteServerWatchDog = new WatchDog(remoteServer, "RSBRemoteServer[" + scope.concat(RSBCommunicationService.SCOPE_SUFFIX_CONTROL) + "]");
            this.listenerWatchDog.addObserver((final Observable<WatchDog.ServiceState> source, WatchDog.ServiceState data1) -> {
                logger.debug("listener state update: " + data1.name());
                // Sync data after service start.
                if (data1 == WatchDog.ServiceState.RUNNING) {
                    remoteServerWatchDog.waitForActivation();
                    requestData();
                }
            });
        } catch (Exception ex) {
            throw new CouldNotPerformException("Could not create RemoteServer on scope [" + scope + "]!", ex);
        }
    }

    /**
     * {@inheritDoc}
     *
     * @return {@inheritDoc}
     */
    @Override
    public Class<M> getDataClass() {
        return dataClass;
    }

    /**
     * Method adds an handler to the internal rsb listener.
     *
     * @param handler
     * @param wait
     * @throws InterruptedException
     * @throws CouldNotPerformException
     */
    public void addHandler(final Handler handler, final boolean wait) throws InterruptedException, CouldNotPerformException {
        try {
            listener.addHandler(handler, wait);
        } catch (InterruptedException ex) {
            throw new CouldNotPerformException("Could not register Handler!", ex);
        }
    }

    /**
     * {@inheritDoc}
     *
     * @throws InterruptedException {@inheritDoc}
     * @throws CouldNotPerformException {@inheritDoc}
     */
    @Override
    public void activate() throws InterruptedException, CouldNotPerformException {
        try {
            validateInitialization();
            setConnectionState(CONNECTING);
            remoteServerWatchDog.activate();
            listenerWatchDog.activate();
        } catch (CouldNotPerformException ex) {
            throw new InvalidStateException("Could not activate remote service!", ex);
        }
    }

    /**
     * {@inheritDoc}
     *
     * @throws InterruptedException {@inheritDoc}
     * @throws CouldNotPerformException {@inheritDoc}
     */
    @Override
    public void activate(boolean waitForData) throws InterruptedException, CouldNotPerformException {
        activate();

        if (waitForData) {
            // make sure the remote is fully synchronized with main controller before continue.
            waitForData();
        }
    }

    /**
     * {@inheritDoc}
     *
     * @throws InterruptedException {@inheritDoc}
     * @throws CouldNotPerformException {@inheritDoc}
     */
    @Override
    public void deactivate() throws InterruptedException, CouldNotPerformException {
        try {
            validateInitialization();
        } catch (InvalidStateException ex) {
            // was never initialized!
            return;
        }
        setConnectionState(DISCONNECTED);
        skipSyncTasks();
        if (listenerWatchDog != null) {
            listenerWatchDog.deactivate();
        }

        if (remoteServerWatchDog != null) {
            remoteServerWatchDog.deactivate();
        }
    }

    public void reset() {
        // clear existing instances.
        if (listenerWatchDog != null) {
            listenerWatchDog.shutdown();
            listenerWatchDog = null;
            listener = new NotInitializedRSBListener();
        }
        if (remoteServerWatchDog != null) {
            remoteServerWatchDog.shutdown();
            remoteServerWatchDog = null;
            remoteServer = new NotInitializedRSBRemoteServer();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isConnected() {
        return connectionState == CONNECTED;
    }

    private void setConnectionState(final ConnectionState connectionState) {
        synchronized (connectionMonitor) {

            // filter unchanged events
            if (this.connectionState.equals(connectionState)) {
                return;
            }

            // update state and notify
            this.connectionState = connectionState;
            if (connectionState == CONNECTED) {
                logger.info("Connection established " + this);
            }

            // init ping
            if (connectionState.equals(CONNECTED)) {
                ping();
            }

            this.connectionMonitor.notifyAll();
            try {
                this.connectionStateObservable.notifyObservers(connectionState);
            } catch (CouldNotPerformException ex) {
                ExceptionPrinter.printHistory(new CouldNotPerformException("Could not notify ConnectionState[" + connectionState + "] change to all observers!", ex), logger);
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ConnectionState getConnectionState() {
        return connectionState;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isActive() {
        try {
            return listenerWatchDog.isActive() && remoteServerWatchDog.isActive();
        } catch (NullPointerException ex) {
            return false;
        }
    }

    /**
     * {@inheritDoc}
     *
     * @throws org.openbase.jul.exception.CouldNotPerformException {@inheritDoc}
     */
    @Override
    public <R> Future<R> callMethodAsync(final String methodName) throws CouldNotPerformException {
        return callMethodAsync(methodName, null);
    }

    /**
     * {@inheritDoc}
     *
     * @throws org.openbase.jul.exception.CouldNotPerformException {@inheritDoc}
     * @throws java.lang.InterruptedException {@inheritDoc}
     */
    @Override
    public <R> R callMethod(final String methodName) throws CouldNotPerformException, InterruptedException {
        return callMethod(methodName, null);
    }

    /**
     * {@inheritDoc}
     *
     * @throws org.openbase.jul.exception.CouldNotPerformException {@inheritDoc}
     * @throws java.lang.InterruptedException {@inheritDoc}
     */
    @Override
    public <R, T extends Object> R callMethod(final String methodName, final T argument) throws CouldNotPerformException, InterruptedException {
        return callMethod(methodName, argument, -1);
    }

    /**
     * {@inheritDoc}
     *
     * @throws org.openbase.jul.exception.CouldNotPerformException {@inheritDoc}
     * @throws java.lang.InterruptedException {@inheritDoc}
     */
    @Override
    public <R> R callMethod(String methodName, long timeout) throws CouldNotPerformException, TimeoutException, InterruptedException {
        return callMethod(methodName, null, timeout);
    }

    /**
     * {@inheritDoc}
     *
     * @throws org.openbase.jul.exception.CouldNotPerformException {@inheritDoc}
     * @throws java.lang.InterruptedException {@inheritDoc}
     */
    @Override
    public <R, T extends Object> R callMethod(final String methodName, final T argument, final long timeout) throws CouldNotPerformException, TimeoutException, InterruptedException {

        validateActivation();
        long retryTimeout = METHOD_CALL_START_TIMEOUT;
        long validTimeout = timeout;
        try {
            logger.info("Calling method [" + methodName + "(" + argument + ")] on scope: " + remoteServer.getScope().toString());
            if (!isConnected()) {
                waitForConnectionState(CONNECTED);
            }

            if (timeout > -1) {
                retryTimeout = Math.min(METHOD_CALL_START_TIMEOUT, validTimeout);
            }

            while (true) {

                if (!isActive()) {
                    throw new InvalidStateException("Remote service is not active!");
                }

                try {
                    logger.info("Calling method [" + methodName + "(" + argument + ")] on scope: " + remoteServer.getScope().toString());
                    return remoteServer.call(methodName, argument, retryTimeout);
                } catch (TimeoutException ex) {
                    ExceptionPrinter.printHistory(ex, logger, LogLevel.WARN);

                    // check if timeout is set and handle
                    if (timeout != -1) {
                        validTimeout -= retryTimeout;
                        if (validTimeout <= 0) {
                            throw new TimeoutException("Could not call remote Methode[" + methodName + "(" + argument + ")] on Scope[" + remoteServer.getScope() + "] in Time[" + timeout + "ms].");
                        }
                        retryTimeout = Math.min(generateTimeout(retryTimeout), validTimeout);
                    } else {
                        retryTimeout = generateTimeout(retryTimeout);
                    }
                    logger.warn("Waiting for RPCServer[" + remoteServer.getScope() + "] to call method [" + methodName + "(" + argument + ")]. Next retry timeout in " + (int) (Math.floor(retryTimeout / 1000)) + " sec.");
                    Thread.yield();
                }
            }
        } catch (TimeoutException ex) {
            throw ex;
        } catch (CouldNotPerformException ex) {
            throw new CouldNotPerformException("Could not call remote Methode[" + methodName + "(" + argument + ")] on Scope[" + remoteServer.getScope() + "].", ex);
        }
    }

    /**
     * {@inheritDoc}
     *
     * @param <R> {@inheritDoc}
     * @param <T> {@inheritDoc}
     * @param methodName {@inheritDoc}
     * @param argument {@inheritDoc}
     * @return {@inheritDoc}
     * @throws CouldNotPerformException {@inheritDoc}
     */
    @Override
    public <R, T extends Object> Future<R> callMethodAsync(final String methodName, final T argument) throws CouldNotPerformException {

        validateActivation();
        return GlobalExecutionService.submit(new Callable<R>() {

            private Future<R> internalCallFuture;

            @Override
            public R call() throws Exception {
                try {
                    logger.debug("Calling method [" + methodName + "(" + (argument != null ? argument.toString() : "") + ")] on scope: " + remoteServer.getScope().toString());

                    if (!isConnected()) {
                        waitForConnectionState(CONNECTED);
                    }

                    internalCallFuture = remoteServer.callAsync(methodName, argument);
                    try {
                        return internalCallFuture.get(REQUEST_TIMEOUT, TimeUnit.MILLISECONDS);
                    } catch (java.util.concurrent.TimeoutException ex) {
                        // validate connection
                        try {
                            ping().get(REQUEST_TIMEOUT, TimeUnit.MILLISECONDS);
                        } catch (ExecutionException | java.util.concurrent.TimeoutException exx) {
                            // connection broken
                            internalCallFuture.cancel(true);
                        }
                        return internalCallFuture.get();
                    }
                } catch (InterruptedException ex) {
                    if (internalCallFuture != null) {
                        internalCallFuture.cancel(true);
                    }
                    throw ex;
                } catch (CouldNotPerformException ex) {
                    throw new CouldNotPerformException("Could not call remote Methode[" + methodName + "(" + argument + ")] on Scope[" + remoteServer.getScope() + "].", ex);
                }
            }
        });
    }

    /**
     * {@inheritDoc}
     *
     * @return {@inheritDoc}
     * @throws CouldNotPerformException {@inheritDoc}
     */
    @Override
    public CompletableFuture<M> requestData() throws CouldNotPerformException {
        logger.debug(this + " requestData...");
        validateInitialization();
        try {
            synchronized (syncMonitor) {

                // Check if sync is in process.
                if (syncFuture != null) {
                    return syncFuture;
                }

                // Create new sync process
                syncFuture = new CompletableFuture();
                syncTask = sync();
                return syncFuture;
            }
        } catch (CouldNotPerformException ex) {
            throw new CouldNotPerformException("Could not request data!", ex);
        }
    }

    /**
     * Method forces a server - remote data sync and returns the new acquired
     * data. Can be useful for initial data sync or data sync after
     * reconnection.
     *
     * @return fresh synchronized data object.
     * @throws CouldNotPerformException
     */
    private Future<M> sync() throws CouldNotPerformException {
        logger.debug("Synchronization of Remote[" + this + "] triggered...");
        validateInitialization();
        try {
            SyncTaskCallable syncCallable = new SyncTaskCallable();

            final Future<M> currentSyncTask = GlobalExecutionService.submit(syncCallable);
            syncCallable.setRelatedFuture(currentSyncTask);
            return currentSyncTask;
        } catch (java.util.concurrent.RejectedExecutionException | NullPointerException ex) {
            throw new CouldNotPerformException("Could not request the current status.", ex);
        }
    }

    private class SyncTaskCallable implements Callable<M> {

        Future<M> relatedFuture;

        public void setRelatedFuture(Future<M> relatedFuture) {
            this.relatedFuture = relatedFuture;
        }

        @Override
        public M call() throws InterruptedException, CouldNotPerformException {

            Future<Event> internalFuture = null;
            M dataUpdate;
            try {
                logger.debug("call request");

                long timeout = METHOD_CALL_START_TIMEOUT;
                while (true) {

                    if (!isActive()) {
                        throw new InvalidStateException("Remote service is not active!");
                    }

                    try {
                        internalFuture = remoteServer.callAsync(RPC_REQUEST_STATUS);
                        dataUpdate = (M) internalFuture.get(timeout, TimeUnit.MILLISECONDS).getData();
                        break;
                    } catch (java.util.concurrent.ExecutionException | java.util.concurrent.TimeoutException ex) {
                        ExceptionPrinter.printHistory(ex, logger, LogLevel.WARN);
                        timeout = generateTimeout(timeout);
                        logger.warn("Remote Controller[" + ScopeTransformer.transform(getScope()) + "] does not respond!  Next retry timeout in " + (int) (Math.floor(timeout / 1000)) + " sec.");
                    }
                }

                if (dataUpdate == null) {
                    logger.info("Remote connection to Controller[" + ScopeTransformer.transform(getScope()) + "] was detached because the controller shutdown was initiated.");
                    setConnectionState(CONNECTING);
                    return data;
                }

                // skip if sync was already performed by global data update.
                if (relatedFuture == null || !relatedFuture.isCancelled()) {
                    applyDataUpdate(dataUpdate);
                }
                return dataUpdate;
            } catch (InterruptedException ex) {
                if (internalFuture != null) {
                    internalFuture.cancel(true);
                }
                throw ex;
            } catch (CouldNotPerformException ex) {
                throw ExceptionPrinter.printHistoryAndReturnThrowable(new CouldNotPerformException("Sync aborted!", ex), logger);
            }
        }
    }

    /**
     * This method deactivates the remote and cleans all resources.
     */
    @Override
    public void shutdown() {
        try {
            dataObservable.shutdown();
        } finally {
            try {
                deactivate();
            } catch (CouldNotPerformException | InterruptedException ex) {
                ExceptionPrinter.printHistory("Could not shutdown " + this + "!", ex, logger);
            }
        }
    }

    /**
     * {@inheritDoc}
     *
     * @return {@inheritDoc}
     * @throws CouldNotPerformException {@inheritDoc}
     */
    @Override
    public CompletableFuture<M> getDataFuture() throws CouldNotPerformException {
        try {
            if (data == null) {
                return requestData();
            }
            return CompletableFuture.completedFuture(data);
        } catch (CouldNotPerformException ex) {
            throw new NotAvailableException("data", ex);
        }
    }

    /**
     * {@inheritDoc}
     *
     * @return {@inheritDoc}
     * @throws NotAvailableException {@inheritDoc}
     */
    @Override
    public M getData() throws NotAvailableException {
        if (data == null) {
            throw new NotAvailableException("data");
        }
        return data;
    }

    /**
     * {@inheritDoc}
     *
     * @return {@inheritDoc}
     */
    @Override
    public boolean isDataAvailable() {
        return data != null;
    }

    /**
     * {@inheritDoc}
     *
     * @throws CouldNotPerformException {@inheritDoc}
     * @throws InterruptedException {@inheritDoc}
     */
    @Override
    public void waitForData() throws CouldNotPerformException, InterruptedException {
        try {
            if (isDataAvailable()) {
                return;
            }
            logger.info("Wait for " + this.toString() + " data...");
            getDataFuture().get();
            dataObservable.waitForValue();
        } catch (ExecutionException ex) {
            throw new TimeoutException("Could not wait for data!", ex);
        }
    }

    /**
     * {@inheritDoc}
     *
     * @param timeout {@inheritDoc}
     * @param timeUnit {@inheritDoc}
     * @throws NotAvailableException {@inheritDoc}
     * @throws java.lang.InterruptedException {@inheritDoc}
     */
    @Override
    public void waitForData(long timeout, TimeUnit timeUnit) throws NotAvailableException, InterruptedException {
        try {
            if (isDataAvailable()) {
                return;
            }
            long startTime = System.currentTimeMillis();
            getDataFuture().get(timeout, timeUnit);
            long partialTimeout = timeUnit.toMillis(timeout) - (System.currentTimeMillis() - startTime);
            if (partialTimeout <= 0) {
                throw new java.util.concurrent.TimeoutException("Data timeout is reached!");
            }
            dataObservable.waitForValue(partialTimeout, TimeUnit.MILLISECONDS);
        } catch (java.util.concurrent.TimeoutException | CouldNotPerformException | ExecutionException ex) {
            throw new NotAvailableException("Data is not yet available!", ex);
        }
    }

    protected final Object getDataField(String name) throws CouldNotPerformException {
        try {
            Descriptors.FieldDescriptor findFieldByName = getData().getDescriptorForType().findFieldByName(name);
            if (findFieldByName == null) {
                throw new NotAvailableException("Field[" + name + "] does not exist for type " + getData().getClass().getName());
            }
            return getData().getField(findFieldByName);
        } catch (Exception ex) {
            throw new CouldNotPerformException("Could not return value of field [" + name + "] for " + this, ex);
        }
    }

    protected final boolean hasDataField(final String name) throws CouldNotPerformException {
        try {
            Descriptors.FieldDescriptor findFieldByName = getData().getDescriptorForType().findFieldByName(name);
            if (findFieldByName == null) {
                return false;
            }
            return getData().hasField(findFieldByName);
        } catch (Exception ex) {
            return false;
        }
    }

    public void validateInitialization() throws InvalidStateException {
        if (!initialized) {
            throw new InvalidStateException(this + " not initialized!");
        }
    }

    public void validateActivation() throws InvalidStateException {
        if (!isActive()) {
            throw new InvalidStateException(this + " not activated!");
        }
    }

    public void validateData() throws InvalidStateException {
        if (!isDataAvailable()) {
            throw new InvalidStateException(this + " not synchronized yet!", new NotAvailableException("data"));
        }
    }

    /**
     * Method blocks until the remote reaches the desired connection state. In
     * case the timeout is expired an TimeoutException will be thrown.
     *
     * @param connectionState the desired connection state
     * @param timeout the timeout in milliseconds until the method throw a
     * TimeoutException in case the connection state was not reached.
     * @throws InterruptedException is thrown in case the thread is externally
     * interrupted.
     * @throws org.openbase.jul.exception.TimeoutException is thrown in case the
     * timeout is expired without reaching the connection state.
     */
    public void waitForConnectionState(final ConnectionState connectionState, long timeout) throws InterruptedException, TimeoutException {
        synchronized (connectionMonitor) {
            while (!Thread.currentThread().isInterrupted()) {
                if (this.connectionState.equals(connectionState)) {
                    return;
                }
                logger.info("Wait for " + getClass().getSimpleName().replace("Remote", "") + "[scope:" + scope + "] connection...");
                connectionMonitor.wait(timeout);
                if (timeout != 0 && !this.connectionState.equals(connectionState)) {
                    throw new TimeoutException("Timeout expired!");
                }
            }
        }
    }

    /**
     * Method blocks until the remote reaches the desired connection state.
     *
     * @param connectionState the desired connection state
     * @throws InterruptedException is thrown in case the thread is externally
     * interrupted.
     */
    public void waitForConnectionState(final ConnectionState connectionState) throws InterruptedException {
        try {
            waitForConnectionState(connectionState, 0);
        } catch (TimeoutException ex) {
            assert false;
        }
    }

    /**
     * {@inheritDoc}
     *
     * @return {@inheritDoc}
     * @throws NotAvailableException {@inheritDoc}
     */
    @Override
    public Scope getScope() throws NotAvailableException {
        if (scope == null) {
            throw new NotAvailableException("scope", new InvalidStateException("remote service not initialized yet!"));
        }
        return scope;
    }

    private class InternalUpdateHandler implements Handler {

        @Override
        public void internalNotify(Event event) {
            try {
                logger.debug("Internal notification: " + event.toString());
                Object dataUpdate = event.getData();

                if (dataUpdate == null) {
                    logger.info("Remote connection to Controller[" + ScopeTransformer.transform(getScope()) + "] was detached because the controller shutdown was initiated.");
                    setConnectionState(CONNECTING);
                    return;
                }

                try {
                    applyDataUpdate((M) dataUpdate);
                } catch (ClassCastException ex) {
                    // Thats not the right internal data type. Skip update...
                }
            } catch (RuntimeException ex) {
                throw ex;
            } catch (Exception ex) {
                ExceptionPrinter.printHistory(new CouldNotPerformException("Internal notification failed!", ex), logger);
            }
        }
    }

    /**
     * Method is used to internally update the data object.
     *
     * @param data
     */
    private void applyDataUpdate(final M data) {
        this.data = data;
        CompletableFuture<M> currentSyncFuture = null;
        Future<M> currentSyncTask = null;

        // Check if sync is in process.
        synchronized (syncMonitor) {
            if (syncFuture != null) {
                currentSyncFuture = syncFuture;
                currentSyncTask = syncTask;
                syncFuture = null;
                syncTask = null;
            }
        }

        // Notify data update
        try {
            notifyDataUpdate(data);
        } catch (CouldNotPerformException ex) {
            ExceptionPrinter.printHistory(new CouldNotPerformException("Could not notify data update!", ex), logger);
        }

        if (currentSyncFuture != null) {
            currentSyncFuture.complete(data);
        }

        if (currentSyncTask != null && !currentSyncTask.isDone()) {
            currentSyncTask.cancel(true);
        }
        setConnectionState(CONNECTED);

        try {
            dataObservable.notifyObservers(data);
        } catch (CouldNotPerformException ex) {
            ExceptionPrinter.printHistory(new CouldNotPerformException("Could not notify data update to all observer!", ex), logger);
        }
    }

    /**
     * Overwrite this method to get informed about data updates.
     *
     * @param data new arrived data messages.
     * @throws CouldNotPerformException
     */
    protected void notifyDataUpdate(M data) throws CouldNotPerformException {
        // dummy method, please overwrite if needed.
    }

    /**
     *
     * @param observer
     * @deprecated use addDataObserver(observer); instead!
     */
    @Deprecated
    public void addObserver(Observer<M> observer) {
        addDataObserver(observer);
    }

    /**
     *
     * @param observer
     * @deprecated use removeDataObserver(observer); instead
     */
    @Deprecated
    public void removeObserver(Observer<M> observer) {
        removeDataObserver(observer);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void addDataObserver(final Observer<M> observer) {
        dataObservable.addObserver(observer);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void removeDataObserver(final Observer<M> observer) {
        dataObservable.removeObserver(observer);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void addConnectionStateObserver(final Observer<ConnectionState> observer) {
        connectionStateObservable.addObserver(observer);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void removeConnectionStateObserver(final Observer<ConnectionState> observer) {
        connectionStateObservable.removeObserver(observer);
    }

    /**
     * Method triggers a ping between this remote and its main controller and
     * returns the calculated connection delay. This method is triggered
     * automatically in background to check if the main controller is still
     * available.
     *
     * @return the connection delay in milliseconds.
     */
    public Future<Long> ping() {
        return GlobalExecutionService.submit(new Callable<Long>() {

            @Override
            public Long call() throws Exception {
                try {
                    Long requestTime = (Long) callMethodAsync("ping", System.currentTimeMillis()).get(PING_TIMEOUT, TimeUnit.MILLISECONDS);
                    lastPingReceived = System.currentTimeMillis();
                    connectionPing = lastPingReceived - requestTime;
                    return connectionPing;
                } catch (java.util.concurrent.TimeoutException ex) {
                    synchronized (connectionMonitor) {
                        if (connectionState == CONNECTED) {
                            logger.warn("Remote connection to Controller[" + ScopeTransformer.transform(getScope()) + "] lost!");

                            // init reconnection
                            setConnectionState(CONNECTING);
                            requestData();
                        }
                    }
                    throw ex;
                } catch (CouldNotPerformException | ExecutionException ex) {
                    throw new CouldNotPerformException("Could not compute ping!", ex);
                }
            }
        });
    }

    /**
     * Method returns the result of the latest connection ping between this
     * remote and its main controller.
     *
     * @return the latest connection delay in milliseconds.
     */
    public long getPing() {
        return connectionPing;
    }

    private void skipSyncTasks() {
        CompletableFuture<M> currentSyncFuture = null;
        Future<M> currentSyncTask = null;

        // Check if sync is in process.
        synchronized (syncMonitor) {
            if (syncFuture != null) {
                currentSyncFuture = syncFuture;
                currentSyncTask = syncTask;
                syncFuture = null;
                syncTask = null;
            }
        }

        // Notify sync cancelation
        try {
            if (currentSyncFuture != null) {
                currentSyncFuture.cancel(true);
            }
        } catch (CancellationException ex) {
            ExceptionPrinter.printHistory(new CouldNotPerformException("Could not cancel synchronization because the cancelation was canceled!", ex), logger, LogLevel.WARN);
        }
        try {
            if (currentSyncTask != null) {
                currentSyncTask.cancel(true);
            }
        } catch (CancellationException ex) {
            ExceptionPrinter.printHistory(new CouldNotPerformException("Could not cancel synchronization because the cancelation was canceled!", ex), logger, LogLevel.WARN);
        }
    }

    /**
     * Method prints a class instance representation.
     *
     * @return the class string representation.
     */
    @Override
    public String toString() {
        return getClass().getSimpleName() + "[scope:" + scope + "]";
    }

    private static long generateTimeout(long currentTimeout) {
        return Math.min(METHOD_CALL_MAX_TIMEOUT, (long) (currentTimeout * METHOD_CALL_TIMEOUT_MULTIPLIER + (JITTER_RANDOM.nextDouble() * 1000)));
    }
}
