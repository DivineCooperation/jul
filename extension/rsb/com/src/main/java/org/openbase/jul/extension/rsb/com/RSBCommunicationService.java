package org.openbase.jul.extension.rsb.com;

/*
 * #%L
 * JUL Extension RSB Communication
 * %%
 * Copyright (C) 2015 - 2018 openbase.org
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
import org.openbase.jul.exception.*;
import org.openbase.jul.exception.InstantiationException;
import org.openbase.jul.exception.printer.ExceptionPrinter;
import org.openbase.jul.exception.printer.LogLevel;
import org.openbase.jul.extension.protobuf.BuilderSyncSetup;
import org.openbase.jul.extension.protobuf.ClosableDataBuilder;
import org.openbase.jul.extension.protobuf.MessageController;
import org.openbase.jul.extension.protobuf.MessageObservable;
import org.openbase.jul.extension.rsb.iface.RSBInformer;
import org.openbase.jul.extension.rsb.iface.RSBLocalServer;
import org.openbase.jul.extension.rsb.scope.ScopeGenerator;
import org.openbase.jul.extension.rsb.scope.ScopeTransformer;
import org.openbase.jul.extension.rst.iface.ScopeProvider;
import org.openbase.jul.extension.rst.iface.TransactionIdProvider;
import org.openbase.jul.iface.Pingable;
import org.openbase.jul.iface.Readyable;
import org.openbase.jul.iface.Requestable;
import org.openbase.jul.pattern.Observable;
import org.openbase.jul.pattern.Observer;
import org.openbase.jul.pattern.provider.DataProvider;
import org.openbase.jul.schedule.GlobalCachedExecutorService;
import org.openbase.jul.schedule.SyncObject;
import org.openbase.jul.schedule.WatchDog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rsb.Event;
import rsb.config.ParticipantConfig;
import rst.rsb.ScopeType.Scope;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock.ReadLock;
import java.util.concurrent.locks.ReentrantReadWriteLock.WriteLock;

import static org.openbase.jul.iface.Shutdownable.registerShutdownHook;

/**
 * @param <M>  the message type of the communication service
 * @param <MB> the builder for message M
 * @author <a href="mailto:divine@openbase.org">Divine Threepwood</a>
 */

public abstract class RSBCommunicationService<M extends GeneratedMessage, MB extends M.Builder<MB>> implements MessageController<M, MB>, ScopeProvider, DataProvider<M>, Readyable, TransactionIdProvider {

    // todo release: refactor class name into AbstractControllerService and document in jul changelog

    public final static rsb.Scope SCOPE_SUFFIX_CONTROL = new rsb.Scope("/ctrl");
    public final static rsb.Scope SCOPE_SUFFIX_STATUS = new rsb.Scope("/status");

    private static final long NOTIFICATILONG_TIMEOUT = TimeUnit.SECONDS.toMillis(15);

    public final static String RPC_REQUEST_STATUS = "requestStatus";

    protected final Logger logger = LoggerFactory.getLogger(getClass());

    private final ShutdownDaemon shutdownDaemon;

    protected RSBInformer<Object> informer;
    protected RSBLocalServer server;
    protected WatchDog informerWatchDog;
    protected WatchDog serverWatchDog;

    private final MB dataBuilder;
    private final Class<M> messageClass;

    protected final SyncObject manageableLock = new SyncObject(getClass());
    private final SyncObject transactionIdLock = new SyncObject(getClass());

    private final ReentrantReadWriteLock dataLock;
    private final ReadLock dataBuilderReadLock;
    private final WriteLock dataBuilderWriteLock;

    protected Scope scope;

    private final SyncObject controllerAvailabilityMonitor = new SyncObject("ControllerAvailabilityMonitor");
    private ControllerAvailabilityState controllerAvailabilityState;
    private boolean initialized, destroyed;

    private final MessageObservable dataObserver;
    private Future initialDataSyncFuture;


    long transaction_id = 0;

    /**
     * Create a communication service.
     *
     * @param builder the initial data builder
     * @throws InstantiationException if the creation fails
     */
    public RSBCommunicationService(final MB builder) throws InstantiationException {
        logger.debug("Create RSBCommunicationService for component " + getClass().getSimpleName() + ".");
        this.dataBuilder = builder;

        try {
            if (builder == null) {
                throw new NotAvailableException("builder");
            }

            this.controllerAvailabilityState = ControllerAvailabilityState.OFFLINE;
            this.dataLock = new ReentrantReadWriteLock();
            this.dataBuilderReadLock = dataLock.readLock();
            this.dataBuilderWriteLock = dataLock.writeLock();
            this.messageClass = detectDataClass();
            this.server = new NotInitializedRSBLocalServer();
            this.informer = new NotInitializedRSBInformer<>();
            this.dataObserver = new MessageObservable(this);
            this.dataObserver.setExecutorService(GlobalCachedExecutorService.getInstance().getExecutorService());
            this.initialized = false;
            this.destroyed = false;
            this.shutdownDaemon = registerShutdownHook(this);
        } catch (CouldNotPerformException ex) {
            throw new InstantiationException(this, ex);
        }
    }


    /**
     * @param scope
     * @throws InitializationException
     * @throws InterruptedException
     */
    public void init(final Scope scope) throws InitializationException, InterruptedException {
        init(scope, RSBSharedConnectionConfig.getParticipantConfig());
    }

    /**
     * @param scope
     * @throws InitializationException
     * @throws InterruptedException
     */
    public void init(final rsb.Scope scope) throws InitializationException, InterruptedException {
        init(scope, RSBSharedConnectionConfig.getParticipantConfig());
    }

    /**
     * @param scope
     * @throws InitializationException
     * @throws InterruptedException
     */
    public void init(final String scope) throws InitializationException, InterruptedException {
        try {
            init(new rsb.Scope(scope));
        } catch (CouldNotPerformException | NullPointerException ex) {
            throw new InitializationException(this, ex);
        }
    }

    /**
     * @param label
     * @param type
     * @param location
     * @throws InitializationException
     * @throws InterruptedException
     */
    public void init(final String label, final String type, final ScopeProvider location) throws InitializationException, InterruptedException {
        try {
            init(ScopeGenerator.generateScope(label, type, location.getScope()));
        } catch (CouldNotPerformException | NullPointerException ex) {
            throw new InitializationException(this, ex);
        }
    }

    /**
     * @param scope
     * @param participantConfig
     * @throws InitializationException
     * @throws InterruptedException
     */
    public void init(final rsb.Scope scope, final ParticipantConfig participantConfig) throws InitializationException, InterruptedException {
        try {
            init(ScopeTransformer.transform(scope), participantConfig);
        } catch (CouldNotTransformException ex) {
            throw new InitializationException(this, ex);
        }
    }

    /**
     * @param scope
     * @param participantConfig
     * @throws InitializationException
     * @throws InterruptedException
     */
    public void init(final Scope scope, final ParticipantConfig participantConfig) throws InitializationException, InterruptedException {
        synchronized (manageableLock) {
            try {
                final boolean alreadyActivated = isActive();
                ParticipantConfig internalParticipantConfig = participantConfig;

                if (scope == null) {
                    throw new NotAvailableException("scope");
                }

                // check if this instance was partly or fully initialized before.
                if (initialized | informerWatchDog != null | serverWatchDog != null) {
                    deactivate();
                    reset();
                }

                this.scope = scope;
                rsb.Scope internalScope = new rsb.Scope(ScopeGenerator.generateStringRep(scope).toLowerCase());

                // init new instances.
                logger.debug("Init RSBCommunicationService for component " + getClass().getSimpleName() + " on " + internalScope + ".");
                informer = new RSBSynchronizedInformer<>(internalScope.concat(new rsb.Scope(rsb.Scope.COMPONENT_SEPARATOR).concat(SCOPE_SUFFIX_STATUS)), Object.class, internalParticipantConfig);
                informerWatchDog = new WatchDog(informer, "RSBInformer[" + internalScope.concat(new rsb.Scope(rsb.Scope.COMPONENT_SEPARATOR).concat(SCOPE_SUFFIX_STATUS)) + "]");

                // get local server object which allows to expose remotely callable methods.
                server = RSBFactoryImpl.getInstance().createSynchronizedLocalServer(internalScope.concat(new rsb.Scope(rsb.Scope.COMPONENT_SEPARATOR).concat(SCOPE_SUFFIX_CONTROL)), internalParticipantConfig);

                // register rpc methods.
                RPCHelper.registerInterface(Pingable.class, this, server);
                RPCHelper.registerInterface(Requestable.class, this, server);
                registerMethods(server);

                serverWatchDog = new WatchDog(server, "RSBLocalServer[" + internalScope.concat(new rsb.Scope(rsb.Scope.COMPONENT_SEPARATOR).concat(SCOPE_SUFFIX_CONTROL)) + "]");

                this.informerWatchDog.addObserver((final Observable<WatchDog.ServiceState> source, WatchDog.ServiceState data) -> {
                    if (data == WatchDog.ServiceState.RUNNING) {

                        // Sync data after service start.
                        initialDataSyncFuture = GlobalCachedExecutorService.submit(() -> {
                            try {
                                // skip if shutdown was already initiated
                                if (informerWatchDog.isServiceDone() || serverWatchDog.isServiceDone()) {
                                    return;
                                }

                                informerWatchDog.waitForServiceActivation();
                                serverWatchDog.waitForServiceActivation();

                                // mark controller as online.
                                setControllerAvailabilityState(ControllerAvailabilityState.ONLINE);

                                logger.debug("trigger initial sync");
                                notifyChange();
                            } catch (InterruptedException ex) {
                                logger.debug("Initial sync was skipped because of controller shutdown.");
                            } catch (CouldNotPerformException ex) {
                                ExceptionPrinter.printHistory(new CouldNotPerformException("Could not trigger data sync!", ex), logger, LogLevel.ERROR);
                            }
                        });
                    }
                });

                postInit();

                initialized = true;

                // check if communication service was already activated before and recover state.
                if (alreadyActivated) {
                    activate();
                }
            } catch (CouldNotPerformException | NullPointerException ex) {
                throw new InitializationException(this, ex);
            }
        }
    }

    /**
     * Method is called after communication initialization.
     * You can overwrite this method to trigger any component specific initialization.
     *
     * @throws InitializationException
     * @throws InterruptedException
     */
    protected void postInit() throws InitializationException, InterruptedException {
        // overwrite for specific post initialization tasks.
    }

    private Class<M> detectDataClass() throws CouldNotPerformException {
        try {
            @SuppressWarnings("unchecked")
            Class<M> clazz = (Class<M>) dataBuilder.getClass().getEnclosingClass();
            if (clazz == null) {
                throw new NotAvailableException("message class");
            }
            return clazz;
        } catch (SecurityException | NotAvailableException | NullPointerException ex) {
            throw new CouldNotPerformException("Could not detect message class of builder " + dataBuilder.getClass().getName() + "!", ex);
        }
    }

    /**
     * {@inheritDoc}
     *
     * @return {@inheritDoc}
     */
    @Override
    public Class<M> getDataClass() {
        return messageClass;
    }

    /**
     * {@inheritDoc}
     *
     * @throws InterruptedException     {@inheritDoc}
     * @throws CouldNotPerformException {@inheritDoc}
     */
    @Override
    public void activate() throws InterruptedException, CouldNotPerformException {
        synchronized (manageableLock) {
            validateInitialization();
            logger.debug("Activate RSBCommunicationService for: " + this);
            setControllerAvailabilityState(ControllerAvailabilityState.ACTIVATING);
            assert serverWatchDog != null;
            assert informerWatchDog != null;
            serverWatchDog.activate();
            informerWatchDog.activate();
        }
    }

    /**
     * {@inheritDoc}
     *
     * @throws InterruptedException     {@inheritDoc}
     * @throws CouldNotPerformException {@inheritDoc}
     */
    @Override
    public synchronized void deactivate() throws InterruptedException, CouldNotPerformException {
        synchronized (manageableLock) {
            try {
                validateInitialization();
            } catch (InvalidStateException ex) {
                // was never initialized!
                return;
            }

            // skip initial data sync if still running
            if (initialDataSyncFuture != null && !initialDataSyncFuture.isDone()) {
                initialDataSyncFuture.cancel(true);
            }

            logger.debug("Deactivate RSBCommunicationService for: " + this);
            // The order is important: The informer publishes a zero event when the controllerAvailabilityState is set to deactivating which leads remotes to disconnect
            // The remotes try to reconnect again and start a requestData. If the server is still active it will respond
            // and the remotes will think that the server is still there..
            if (serverWatchDog != null) {
                serverWatchDog.deactivate();
            }
            // inform remotes about deactivation
            setControllerAvailabilityState(ControllerAvailabilityState.DEACTIVATING);
            if (informerWatchDog != null) {
                informerWatchDog.deactivate();
            }
            setControllerAvailabilityState(ControllerAvailabilityState.OFFLINE);
        }
    }

    private void reset() {
        // disabled related to #44
        synchronized (manageableLock) {
            // clear init flag
            initialized = false;

            // clear existing instances.        
            if (informerWatchDog != null) {
                informerWatchDog.shutdown();
                informerWatchDog = null;
                informer = new NotInitializedRSBInformer<>();
            }
            if (serverWatchDog != null) {
                serverWatchDog.shutdown();
                serverWatchDog = null;
                server = new NotInitializedRSBLocalServer();
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void shutdown() {
        try {
            deactivate();
        } catch (CouldNotPerformException | InterruptedException ex) {
            ExceptionPrinter.printHistory("Could not deactivate " + this + " during shutdown!", ex, logger);
        }
        reset();
        destroyed = true;

        if (shutdownDaemon != null) {
            shutdownDaemon.cancel();
        }
    }

    /**
     * {@inheritDoc}
     *
     * @return {@inheritDoc}
     */
    @Override
    public boolean isActive() {
        try {
            validateInitialization();
        } catch (InvalidStateException ex) {
            return false;
        }
        return informerWatchDog.isActive() && serverWatchDog.isActive();
    }

    /**
     * {@inheritDoc}
     *
     * @return {@inheritDoc}
     * @throws NotAvailableException {@inheritDoc}
     */
    @SuppressWarnings("unchecked")
    @Override
    public M getData() throws NotAvailableException {
        try {
            return (M) cloneDataBuilder().build();
        } catch (Exception ex) {
            throw new NotAvailableException("Data", new CouldNotPerformException("Could not build message!", ex));
        }
    }

    /**
     * {@inheritDoc}
     *
     * @return {@inheritDoc}
     */
    @Override
    public CompletableFuture<M> getDataFuture() {
        try {
            return CompletableFuture.completedFuture(getData());
        } catch (NotAvailableException ex) {
            CompletableFuture future = new CompletableFuture();
            future.completeExceptionally(ex);
            return future;
        }
    }

    /**
     * @param controllerAvailability
     * @throws InterruptedException
     */
    private void setControllerAvailabilityState(final ControllerAvailabilityState controllerAvailability) throws InterruptedException {
        synchronized (controllerAvailabilityMonitor) {

            // filter unchanged events
            if (this.controllerAvailabilityState.equals(controllerAvailability)) {
                return;
            }

            // update state and notify
            this.controllerAvailabilityState = controllerAvailability;
            logger.debug(this + " is now " + controllerAvailability.name());

            try {
                // notify remotes about controller shutdown
                if (controllerAvailabilityState.equals(ControllerAvailabilityState.DEACTIVATING)) {
                    try {
                        logger.debug("Notify data change of " + this);
                        validateInitialization();
                        if (!informer.isActive()) {
                            logger.debug("Skip update notification because connection not established.");
                            return;
                        }
                        try {
                            informer.publish(new Event(informer.getScope(), Void.class, null));
                        } catch (CouldNotPerformException ex) {
                            throw new CouldNotPerformException("Could not notify change of " + this + "!", ex);
                        }
                    } catch (final NotInitializedException ex) {
                        logger.debug("Skip update notification because instance is not initialized.");
                    } catch (CouldNotPerformException ex) {
                        ExceptionPrinter.printHistory(new CouldNotPerformException("Could not update communication service state in internal data object!", ex), logger);
                    }
                }
            } finally {
                // wakeup listener.
                this.controllerAvailabilityMonitor.notifyAll();
            }
        }
    }

    /**
     * {@inheritDoc}
     *
     * @throws InterruptedException {@inheritDoc}
     */
    @Override
    public void waitForAvailabilityState(final ControllerAvailabilityState controllerAvailabilityState) throws InterruptedException {
        synchronized (controllerAvailabilityMonitor) {
            while (!Thread.currentThread().isInterrupted()) {
                if (this.controllerAvailabilityState.equals(controllerAvailabilityState)) {
                    return;
                }
                controllerAvailabilityMonitor.wait();
            }
        }
    }

    /**
     * {@inheritDoc}
     *
     * @return {@inheritDoc}
     */
    @Override
    public MB cloneDataBuilder() {
        dataBuilderReadLock.lock();
        try {
            return dataBuilder.clone();
        } finally {
            dataBuilderReadLock.unlock();
        }
    }

    /**
     * {@inheritDoc}
     *
     * @return {@inheritDoc}
     */
    @Override
    public BuilderSyncSetup<MB> getBuilderSetup() {
        return new BuilderSyncSetup<>(dataBuilder, dataBuilderReadLock, dataBuilderWriteLock, this);
    }

    /**
     * {@inheritDoc}
     *
     * @param consumer {@inheritDoc}
     * @return {@inheritDoc}
     */
    @Override
    public ClosableDataBuilder<MB> getDataBuilder(final Object consumer) {
        return new ClosableDataBuilder<>(getBuilderSetup(), consumer);
    }

    /**
     * {@inheritDoc}
     *
     * @param consumer {@inheritDoc}
     * @return {@inheritDoc}
     */
    @Override
    public ClosableDataBuilder<MB> getDataBuilder(final Object consumer, final boolean notifyChange) {
        return new ClosableDataBuilder<>(getBuilderSetup(), consumer, notifyChange);
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
            throw new NotAvailableException("scope", new InvalidStateException("communication service not initialized yet!"));
        }
        return scope;
    }

    /**
     * Synchronize all registered remote instances about a data change.
     *
     * @throws CouldNotPerformException
     * @throws java.lang.InterruptedException
     */
    @Override
    public void notifyChange() throws CouldNotPerformException, InterruptedException {
        logger.debug("Notify data change of " + this);
        // synchronized by manageable lock to prevent reinit between validateInitialization and publish
        M newData;
        synchronized (manageableLock) {
            try {
                validateInitialization();
            } catch (final NotInitializedException ex) {
                // only forward if instance was not destroyed before otherwise skip notification.
                if (destroyed) {
                    return;
                }
                throw ex;
            }

            // update the current data builder before updating to allow implementations to change data beforehand
            newData = updateDataToPublish(cloneDataBuilder());
            Event event = new Event(informer.getScope(), newData.getClass(), newData);
            event.getMetaData().setUserTime(RPCHelper.USER_TIME_KEY, System.nanoTime());

            if (isActive()) {
                try {
                    waitForMiddleware(NOTIFICATILONG_TIMEOUT, TimeUnit.MILLISECONDS);
                    informer.publish(event);
                } catch (CouldNotPerformException ex) {
                    ExceptionPrinter.printHistory(new CouldNotPerformException("Could not inform about data change of " + this + "!", ex), logger);
                }
            }
        }

        // Notify data update
        try {
            notifyDataUpdate(newData);
        } catch (CouldNotPerformException ex) {
            ExceptionPrinter.printHistory(new CouldNotPerformException("Could not notify data update!", ex), logger);
        }

        dataObserver.notifyObservers(newData);
    }

    /**
     * Called before publishing data via the informer. Can be implemented by
     * sub classes to update data which can be received by everyone.
     *
     * @param dataBuilder a clone of the current data builder.
     * @return a message build from the data builder
     * @throws CouldNotPerformException if the update fails
     */
    protected M updateDataToPublish(MB dataBuilder) throws CouldNotPerformException {
        return (M) dataBuilder.build();
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
     * @param fieldNumber
     * @param value
     * @throws CouldNotPerformException
     */
    protected final void setDataField(int fieldNumber, Object value) throws CouldNotPerformException {
        try {
            try {
                dataBuilderWriteLock.lock();
                Descriptors.FieldDescriptor findFieldByName = dataBuilder.getDescriptorForType().findFieldByNumber(fieldNumber);
                if (findFieldByName == null) {
                    throw new NotAvailableException("Field[" + fieldNumber + "] does not exist for type " + dataBuilder.getClass().getName());
                }
                dataBuilder.setField(findFieldByName, value);
            } finally {
                dataBuilderWriteLock.unlock();
            }
        } catch (CouldNotPerformException | NullPointerException ex) {
            throw new CouldNotPerformException("Could not set field [" + fieldNumber + "=" + value + "] for " + this, ex);
        }
    }

    /**
     * @param fieldName
     * @param value
     * @throws CouldNotPerformException
     */
    protected final void setDataField(String fieldName, Object value) throws CouldNotPerformException {
        try {
            try {
                dataBuilderWriteLock.lock();
                Descriptors.FieldDescriptor findFieldByName = dataBuilder.getDescriptorForType().findFieldByName(fieldName);
                if (findFieldByName == null) {
                    throw new NotAvailableException("Field[" + fieldName + "] does not exist for type " + dataBuilder.getClass().getName());
                }

                dataBuilder.setField(findFieldByName, value);
            } finally {
                dataBuilderWriteLock.unlock();
            }
        } catch (CouldNotPerformException | NullPointerException ex) {
            throw new CouldNotPerformException("Could not set field [" + fieldName + "=" + value + "] for " + this, ex);
        }
    }

    /**
     * @param name
     * @return
     * @throws NotAvailableException
     */
    protected final Object getDataField(String name) throws NotAvailableException {
        try {
            MB dataClone = cloneDataBuilder();
            Descriptors.FieldDescriptor findFieldByName = dataClone.getDescriptorForType().findFieldByName(name);
            if (findFieldByName == null) {
                throw new NotAvailableException("Field[" + name + "] does not exist for type " + dataClone.getClass().getName());
            }
            return dataClone.getField(findFieldByName);
        } catch (Exception ex) {
            throw new NotAvailableException(name, this, ex);
        }
    }

    /**
     * @param name
     * @return
     * @throws CouldNotPerformException
     */
    protected final boolean hasDataField(final String name) throws CouldNotPerformException {
        try {
            MB dataClone = cloneDataBuilder();
            Descriptors.FieldDescriptor findFieldByName = dataClone.getDescriptorForType().findFieldByName(name);
            if (findFieldByName == null) {
                return false;
            }
            return dataClone.hasField(findFieldByName);
        } catch (Exception ex) {
            return false;
        }
    }

    /**
     * @param name
     * @return
     * @throws CouldNotPerformException
     */
    protected final boolean supportsDataField(final String name) throws CouldNotPerformException {
        try {
            Descriptors.FieldDescriptor findFieldByName = dataBuilder.getDescriptorForType().findFieldByName(name);
            return findFieldByName != null;
        } catch (NullPointerException ex) {
            return false;
        }
    }

    /**
     * @param fieldId
     * @return
     */
    protected final Descriptors.FieldDescriptor getDataFieldDescriptor(int fieldId) {
        return cloneDataBuilder().getDescriptorForType().findFieldByNumber(fieldId);
    }

    /**
     * {@inheritDoc}
     *
     * @return {@inheritDoc}
     */
    @Override
    public ControllerAvailabilityState getControllerAvailabilityState() {
        return controllerAvailabilityState;
    }


    /**
     * This method validates the controller initialisation.
     *
     * @throws NotInitializedException is thrown if the controller is not initialized.
     */
    public void validateInitialization() throws NotInitializedException {
        synchronized (manageableLock) {
            if (!initialized) {
                throw new NotInitializedException("communication service");
            }
        }
    }

    /**
     * This method validates the controller activation.
     *
     * @throws InvalidStateException is thrown if the controller is not active.
     */
    public void validateActivation() throws InvalidStateException {
        if (!isActive()) {
            throw new InvalidStateException(this + " not activated!");
        }
    }

    public void validateMiddleware() throws InvalidStateException {
        validateActivation();
        if (informer == null || !informer.isActive() || !informerWatchDog.isServiceRunning()) {
            throw new InvalidStateException("Informer of " + this + " not connected to middleware!");
        }

        if (server == null || !server.isActive() || !serverWatchDog.isServiceRunning()) {
            throw new InvalidStateException("Server of " + this + " not connected to middleware!");
        }
    }

    public void waitForMiddleware(final long timeout, final TimeUnit timeUnit) throws InterruptedException, CouldNotPerformException {
        validateActivation();
        informerWatchDog.waitForServiceActivation(timeout, timeUnit);
        serverWatchDog.waitForServiceActivation(timeout, timeUnit);
    }

    /**
     * {@inheritDoc }
     *
     * @param timestamp {@inheritDoc }
     * @return {@inheritDoc }
     */
    @Override
    public Future<Long> ping(final Long timestamp) {
        return CompletableFuture.completedFuture(timestamp);
    }

    /**
     * {@inheritDoc}
     *
     * @return {@inheritDoc}
     * @throws org.openbase.jul.exception.CouldNotPerformException {@inheritDoc}
     */
    @Override
    public M requestStatus() throws CouldNotPerformException {
        logger.debug("requestStatus of " + this);
        try {
            return getData();
        } catch (RuntimeException ex) {
            throw ex;
        } catch (Exception ex) {
            throw ExceptionPrinter.printHistoryAndReturnThrowable(new CouldNotPerformException("Could not request status update.", ex), logger, LogLevel.ERROR);
        }
    }

    /**
     * Register methods for RPCs on the internal RSB local server.
     * <p>
     * Note:
     * Methods should not be registered outside this method because the internal server can already be active.
     * When extending from a class already implementing this method always remember to call the super method
     * so that all methods are properly registered.
     *
     * @param server the local server on which the methods should be registered.
     * @throws CouldNotPerformException if registering methods fails
     */
    public abstract void registerMethods(final RSBLocalServer server) throws CouldNotPerformException;

    /**
     * {@inheritDoc}
     *
     * @return {@inheritDoc}
     */
    @Override
    public boolean isDataAvailable() {
        try {
            return getData().isInitialized();
        } catch (NotAvailableException ex) {
            return false;
        }
    }

    /**
     * Method returns true if this instance was initialized, activated and is successfully connected to the middleware.
     *
     * @return returns true if this instance is ready otherwise false.
     */
    @Override
    public Boolean isReady() {
        try {
            validateInitialization();
            validateActivation();
            validateMiddleware();
            return true;
        } catch (InvalidStateException e) {
            return false;
        }
    }

    /**
     * {@inheritDoc}
     *
     * @throws CouldNotPerformException {@inheritDoc}
     * @throws InterruptedException     {@inheritDoc}
     */
    @Override
    public void waitForData() throws CouldNotPerformException, InterruptedException {
        // because this is the controller, the data is already available.
    }

    /**
     * {@inheritDoc}
     *
     * @param timeout  {@inheritDoc}
     * @param timeUnit {@inheritDoc}.
     * @throws CouldNotPerformException {@inheritDoc}
     * @throws InterruptedException     {@inheritDoc}
     */
    @Override
    public void waitForData(long timeout, TimeUnit timeUnit) throws CouldNotPerformException, InterruptedException {
        // because this is the controller, the data is already available.
    }

    /**
     * {@inheritDoc}
     *
     * @param observer {@inheritDoc}
     */
    @Override
    public void addDataObserver(Observer<M> observer) {
        dataObserver.addObserver(observer);
    }

    /**
     * {@inheritDoc}
     *
     * @param observer {@inheritDoc}
     */
    @Override
    public void removeDataObserver(Observer<M> observer) {
        dataObserver.removeObserver(observer);
    }

    /**
     * {@inheritDoc}
     *
     * @return {@inheritDoc}
     */
    @Override
    public long getTransactionId() {
        synchronized (transactionIdLock) {
            return transaction_id;
        }
    }

    /**
     * Method generates a new id which can be used for the next transaction (e.g. state transition).
     *
     * @return the next transaction identifier.
     */
    public long generateTransactionId() {
        synchronized (transactionIdLock) {
            // Transaction id should never be 0 because thats the builder default value.
            return ++transaction_id;
        }
    }

    /**
     * Methde sets a new generated transaction id in the global data field of this controller.
     *
     * @throws CouldNotPerformException
     */
    public void updateTransactionId() throws CouldNotPerformException {
        synchronized (transactionIdLock) {
            setDataField(TransactionIdProvider.TRANSACTION_ID_FIELD_NAME, generateTransactionId());
        }
    }

    /**
     * {@inheritDoc}
     *
     * @return {@inheritDoc}
     */
    @Override
    public String toString() {
        try {
            return getClass().getSimpleName() + "[" + informer.getScope().toString() + "]";
        } catch (NotAvailableException ex) {
            return getClass().getSimpleName() + "[]";
        }
    }
}
