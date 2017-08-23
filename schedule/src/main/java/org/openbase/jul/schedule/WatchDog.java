package org.openbase.jul.schedule;

/*
 * #%L
 * JUL Schedule
 * %%
 * Copyright (C) 2015 - 2017 openbase.org
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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import org.openbase.jps.core.JPService;
import org.openbase.jul.exception.CouldNotPerformException;
import org.openbase.jul.exception.FatalImplementationErrorException;
import org.openbase.jul.exception.InstantiationException;
import org.openbase.jul.exception.NotAvailableException;
import org.openbase.jul.exception.printer.ExceptionPrinter;
import org.openbase.jul.exception.printer.LogLevel;
import org.openbase.jul.iface.Activatable;
import org.openbase.jul.iface.Shutdownable;
import org.openbase.jul.pattern.ObservableImpl;
import org.openbase.jul.pattern.Observer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * //
 *
 *
 * @author <a href="mailto:divine@openbase.org">Divine Threepwood</a>
 */
public class WatchDog implements Activatable, Shutdownable {

    protected static final Logger logger = LoggerFactory.getLogger(WatchDog.class);

    public static final List<WatchDog> globalWatchDogList = Collections.synchronizedList(new ArrayList<WatchDog>());

    static {
        try {
            Runtime.getRuntime().addShutdownHook(new Thread("WatchDogShutdownHook") {
                @Override
                public void run() {
                    assert globalWatchDogList != null;
                    globalWatchDogList.forEach((watchDog) -> {
                        try {
                            watchDog.shutdown();
                        } catch (Exception ex) {
                            ExceptionPrinter.printHistory("Could not shutdown watchdog!", ex, logger);
                        }
                    });
                }
            });
        } catch (Exception ex) {
            ExceptionPrinter.printHistory("Could not register shutdown watchdog hook!", ex, logger);
        }
    }

    private final Object EXECUTION_LOCK;
    private final SyncObject STATE_LOCK;

    private static final long RUNNING_DELAY = 60000;
    private static final long DEFAULT_DELAY = 10000;

    public enum ServiceState {

        UNKNWON, CONSTRUCTED, INITIALIZING, RUNNING, TERMINATING, FINISHED, FAILED, INTERRUPTED
    };

    private final Activatable service;
    private final String serviceName;
    private Minder minder;
    private ServiceState serviceState = ServiceState.UNKNWON;

    private final ObservableImpl<ServiceState> serviceStateObserable;

    public WatchDog(final Activatable task, final String serviceName) throws InstantiationException {
        try {
            this.service = task;
            this.serviceName = serviceName;
            this.serviceStateObserable = new ObservableImpl<>();
            this.EXECUTION_LOCK = new SyncObject(serviceName + " EXECUTION_LOCK");
            this.STATE_LOCK = new SyncObject(serviceName + " STATE_LOCK");

            if (task == null) {
                throw new NotAvailableException("task");
            }

            setServiceState(ServiceState.CONSTRUCTED);
            globalWatchDogList.add(this);
        } catch (CouldNotPerformException ex) {
            throw new InstantiationException(this, ex);
        }
    }

    @Override
    public void activate() throws InterruptedException, CouldNotPerformException {
        logger.trace("Try to activate service: " + serviceName);
        synchronized (EXECUTION_LOCK) {
            logger.trace("Init activation of service: " + serviceName);
            if (minder != null) {
                logger.debug("Skip activation, Service[" + serviceName + "] already running!");
                return;
            }
            synchronized (STATE_LOCK) {
                minder = new Minder(serviceName + "WatchDog");
                logger.trace("Start activation of service: " + serviceName);
                minder.setFuture(GlobalScheduledExecutorService.scheduleAtFixedRate(minder, 0, getRate(), TimeUnit.MILLISECONDS));
            }
        }

//        try {
//            waitForServiceActivation();
//        } catch (CouldNotPerformException | InterruptedException ex) {
//            ExceptionPrinter.printHistory(new CouldNotPerformException("Could not wait for service activation!", ex), logger, LogLevel.WARN);
//            throw ex;
//        }
    }

    @Override
    public void deactivate() throws InterruptedException {
        logger.trace("Try to deactivate service: " + serviceName);
        synchronized (EXECUTION_LOCK) {
            logger.trace("Init deactivation of service: " + serviceName);
            if (minder == null) {
                logger.debug("Skip deactivation, Service[" + serviceName + "] not running!");
                return;
            }

            logger.trace("Init service interruption...");
            minder.shutdown();
            logger.trace("Wait for service interruption...");

            synchronized (STATE_LOCK) {
                minder = null;
            }

            logger.trace("Service interrupted!");
            skipActivation();
        }
    }

    @Override
    public boolean isActive() {
        return minder != null;
    }

    public boolean isServiceDone() {
        return minder == null || minder.getFuture().isDone();
    }

    public String getServiceName() {
        return serviceName;
    }

    public void waitForServiceActivation() throws InterruptedException, CouldNotPerformException {
        waitForServiceState(ServiceState.RUNNING);
    }

    public void waitForServiceActivation(final long timeout, final TimeUnit timeUnit) throws InterruptedException, CouldNotPerformException {
        waitForServiceState(ServiceState.RUNNING, timeout, timeUnit);
    }

    public void waitForServiceState(final ServiceState serviceSatet) throws InterruptedException, CouldNotPerformException {
        waitForServiceState(serviceSatet, 0, TimeUnit.MILLISECONDS);
    }

    public void waitForServiceState(final ServiceState serviceState, final long timeout, final TimeUnit timeUnit) throws InterruptedException, CouldNotPerformException {
        synchronized (STATE_LOCK) {
            while (true) {
                if (Thread.interrupted()) {
                    throw new InterruptedException();
                }

                if (this.serviceState.equals(serviceState)) {
                    return;
                }

                // skip if watchdog is not active
                if (!isActive()) {
                    throw new CouldNotPerformException("Could not wait for ServiceState[" + serviceState.name() + "] because watchdog is not active!");
                }

                // skip if state is already passed.
                if (minder.getFuture().isDone() && (serviceState == ServiceState.RUNNING || serviceState == ServiceState.INITIALIZING)) {
                    throw new CouldNotPerformException("Could not wait for ServiceState[" + serviceState.name() + "] because service is already done!");
                }

                if (timeout <= 0) {
                    STATE_LOCK.wait();
                } else {
                    STATE_LOCK.wait(timeUnit.toMillis(timeout));
                }
            }
        }
    }

    public void skipActivation() {
        synchronized (STATE_LOCK) {
            STATE_LOCK.notifyAll();
        }
    }

    private class Minder implements Runnable, Shutdownable {

        private final String name;
        private boolean processing;
        private ScheduledFuture future;
        private final Object FUTURE_LOCK = new SyncObject("FUTURE_LOCK");

        private Minder(String name) {
            this.name = name;
            setServiceState(ServiceState.INITIALIZING);
        }

        public ScheduledFuture getFuture() {
            return future;
        }

        public void setFuture(ScheduledFuture future) {
            synchronized (FUTURE_LOCK) {
                this.future = future;
                FUTURE_LOCK.notifyAll();
            }
        }

        public void waitForInit() throws InterruptedException {
            synchronized (FUTURE_LOCK) {
                if (future == null) {
                    FUTURE_LOCK.wait();
                }
            }
        }

        @Override
        public void run() {

            // avoid parallel execution by simple process filter.
            if (processing) {
                return;
            }
            processing = true;

            try {
                try {
                    try {
                        waitForInit();
                        if (!future.isCancelled()) {
                            if (!service.isActive()) {
                                setServiceState(ServiceState.INITIALIZING);
                                try {
                                    logger.debug("Service activate: " + service.hashCode() + " : " + serviceName);
                                    service.activate();
                                    setServiceState(ServiceState.RUNNING);
                                } catch (CouldNotPerformException | NullPointerException ex) {
                                    ExceptionPrinter.printHistory(new CouldNotPerformException("Could not start Service[" + serviceName + " " + service.hashCode() + "]!", ex), logger);
                                    setServiceState(ServiceState.FAILED);
                                    logger.info("Try again in " + (getRate() / 1000) + " seconds...");
                                }
                            }
                            return; // check finished because service is still running.
                        }
                    } catch (InterruptedException ex) {
                        /**
                         * An interrupted exception was caught triggered by the deactivate() or cancel() method of the watchdog.
                         * The minder shutdown will be initiated now.
                         *
                         * !!! Do not recover the interrupted state to grantee a proper shutdown !!!
                         */
                        logger.debug("Minder shutdown initiated of Service[" + serviceName + "]...");
                    }
                } catch (Throwable tr) {
                    ExceptionPrinter.printHistory(new FatalImplementationErrorException(this, tr), logger);
                    skipActivation();
                }
            } finally {
                processing = false;
            }
        }

        public String getName() {
            return name;
        }

        @Override
        public void shutdown() {
            future.cancel(true);
            if (service.isActive()) {
                setServiceState(ServiceState.TERMINATING);
                try {
                    try {
                        logger.debug("Minder deactivation initiated of Service[" + serviceName + "]...");
                        System.out.println("watchdog shutdown thread name: "+Thread.currentThread().getName());
                        service.deactivate();
                    } catch (IllegalStateException | CouldNotPerformException ex) {
                        ExceptionPrinter.printHistory(new CouldNotPerformException("Could not deactivate Service[" + serviceName + "]!", ex), logger);
                    }
                } catch (InterruptedException ex) {
                    ExceptionPrinter.printHistory(new CouldNotPerformException("Could not terminate Service[" + serviceName + "] because termination was externaly interrupted.", ex), logger, LogLevel.WARN);
                    setServiceState(ServiceState.INTERRUPTED);
                }
            }
            setServiceState(ServiceState.FINISHED);
        }
    }

    private long getRate() throws InterruptedException {
        if (JPService.testMode()) {
            return 10;
        }
        if (serviceState == ServiceState.RUNNING) {
            return RUNNING_DELAY;
        }
        return DEFAULT_DELAY;
    }

    public Activatable getService() {
        return service;
    }

    private void setServiceState(final ServiceState serviceState) {
        try {
            synchronized (STATE_LOCK) {
                if (this.serviceState == serviceState) {
                    return;
                }
                this.serviceState = serviceState;
                STATE_LOCK.notifyAll();
            }
            logger.debug(this + " is now " + serviceState.name().toLowerCase() + ".");
            serviceStateObserable.notifyObservers(serviceState);
        } catch (CouldNotPerformException ex) {
            ExceptionPrinter.printHistory(new CouldNotPerformException("Could not notify state change to all instances!", ex), logger);
        }
    }

    public ServiceState getServiceState() {
        return serviceState;
    }

    public void addObserver(Observer<ServiceState> observer) {
        serviceStateObserable.addObserver(observer);
    }

    public void removeObserver(Observer<ServiceState> observer) {
        serviceStateObserable.removeObserver(observer);
    }

    @Override
    public void shutdown() {
        try {
            if (serviceStateObserable != null) {
                serviceStateObserable.shutdown();
            }
            deactivate();
        } catch (InterruptedException ex) {
            ExceptionPrinter.printHistory(this + "was interruped during shutdown!", ex, logger);
            Thread.currentThread().interrupt();
        } catch (Exception ex) {
            ExceptionPrinter.printHistory("Could not shutdown " + this, ex, logger);
        }
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "[" + serviceName + "]";
    }
}
