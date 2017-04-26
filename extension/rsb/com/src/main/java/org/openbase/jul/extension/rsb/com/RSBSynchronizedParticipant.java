package org.openbase.jul.extension.rsb.com;

/*
 * #%L
 * JUL Extension RSB Communication
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
import java.util.concurrent.*;

import org.openbase.jul.exception.CouldNotPerformException;
import org.openbase.jul.exception.InstantiationException;
import org.openbase.jul.exception.NotAvailableException;
import org.openbase.jul.exception.printer.ExceptionPrinter;
import org.openbase.jul.exception.printer.LogLevel;
import org.openbase.jul.schedule.SyncObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rsb.Factory;
import rsb.InitializeException;
import rsb.Participant;
import rsb.ParticipantId;
import rsb.Scope;
import rsb.config.ParticipantConfig;
import org.openbase.jul.extension.rsb.iface.RSBParticipant;
import org.openbase.jul.schedule.GlobalCachedExecutorService;

/**
 *
 * * @author Divine <a href="mailto:DivineThreepwood@gmail.com">Divine</a>
 * @param <P>
 */
public abstract class RSBSynchronizedParticipant<P extends Participant> implements RSBParticipant {

    private final Logger logger = LoggerFactory.getLogger(RSBSynchronizedParticipant.class);

    private static final long DEACTIVATION_TIMEOUT = 3000;

    private P participant;
    protected final SyncObject participantLock = new SyncObject("participant");
    protected final Scope scope;
    protected final ParticipantConfig config;

    protected RSBSynchronizedParticipant(final Scope scope, final ParticipantConfig config) throws InstantiationException {
        try {
            if (scope == null) {
                throw new NotAvailableException("scope");
            }
            this.scope = scope;
            this.config = config;
        } catch (CouldNotPerformException ex) {
            throw new InstantiationException(this, ex);
        }
    }

    abstract P init() throws InitializeException, InterruptedException;

    protected P getParticipant() throws NotAvailableException {
        if (participant == null) {
            throw new NotAvailableException("participant");
        }
        return participant;
    }

    @Override
    public ParticipantId getId() throws NotAvailableException {
        try {
            synchronized (participantLock) {
                return getParticipant().getId();
            }
        } catch (IllegalStateException | NullPointerException ex) {
            throw new NotAvailableException("id", ex);
        }
    }

    @Override
    public Scope getScope() throws NotAvailableException {
        return scope;
    }

    @Override
    public ParticipantConfig getConfig() throws NotAvailableException {
        if (config != null) {
            return config;
        }

        try {
            synchronized (participantLock) {
                return getParticipant().getConfig();
            }
        } catch (IllegalStateException | NullPointerException ex) {
            throw new NotAvailableException("config", ex);
        }
    }

    @Override
    public void setObserverManager(final Factory.ParticipantObserverManager observerManager) throws CouldNotPerformException {
        try {
            synchronized (participantLock) {
                getParticipant().setObserverManager(observerManager);
            }
        } catch (IllegalStateException | NullPointerException ex) {
            throw new CouldNotPerformException("Could not regitser observer manager " + observerManager + "!", ex);
        }
    }

    @Override
    public String getKind() throws NotAvailableException {
        try {
            synchronized (participantLock) {
                return getParticipant().getKind();
            }
        } catch (IllegalStateException | NullPointerException ex) {
            throw new NotAvailableException("kind", ex);
        }
    }

    @Override
    public Class<?> getDataType() throws NotAvailableException {
        try {
            synchronized (participantLock) {
                return getParticipant().getDataType();
            }
        } catch (IllegalStateException | NullPointerException ex) {
            throw new NotAvailableException("data type", ex);
        }
    }

    @Override
    public void activate() throws CouldNotPerformException, InterruptedException {
        try {
            synchronized (participantLock) {

                // ignore request if participant is already active.
                if (isActive()) {
                    return;
                }

                if (participant == null) {
                    participant = init();
                }
                logger.debug("Participant[" + this + "] will be activated.");
                if (participant.isActive()) {
                    logger.warn("Skip activation because Participant[" + this + "] is already active!");
                    return;
                }
                getParticipant().activate();
                logger.debug("Participant[" + this + "] is now activate.");
            }
        } catch (InterruptedException ex) {
            throw ex;
        } catch (Exception ex) {
            try {
                deactivate();
            } catch (InterruptedException exx) {
                throw exx;
            } catch (Exception exx) {
                ExceptionPrinter.printHistory("Could not deactivate listener which was triggert because activation has been failed.", exx, logger, LogLevel.WARN);
            }
            /**
             * Catching specific exceptions here is not possible because not all thrown exceptions are predictable *
             */
            throw new CouldNotPerformException("Could not activate listener!", ex);
        }
    }

    @Override
    public void deactivate() throws CouldNotPerformException, InterruptedException {
        try {
            synchronized (participantLock) {

                // ignore request if participant is already or still inactive.
                if (!isActive()) {
                    return;
                }

                // deactivate
                logger.debug("Participant[" + this + "] will be deactivated.");
                final Future<Void> deactivationFuture = GlobalCachedExecutorService.submit(new Callable<Void>() {
                    @Override
                    public Void call() throws Exception {
                        if (participant.isActive()) {
                            participant.deactivate();
                        }
                        return null;
                    }
                });

                // wait for deactivation and handle error case
                try {
                    deactivationFuture.get(DEACTIVATION_TIMEOUT, TimeUnit.MILLISECONDS);
                    participant = null;
                } catch (TimeoutException ex) {
                    throw new CouldNotPerformException(this + " does not response in time! Deactivation stall detected!");
                } catch (ExecutionException ex) {
                    if (ex.getCause() == null) {
                        throw ex;
                    }
                    throw ex.getCause();
                } catch (InterruptedException ex) {
                    deactivationFuture.cancel(true);
                    throw ex;
                }
                logger.debug("Participant[" + this + "] deactivated.");
            }
        } catch (InterruptedException ex) {
            throw ex;
        } catch (Throwable ex) {
            /**
             * Catching specific exceptions here is not possible because not all thrown exceptions are predictable *
             */
            throw new CouldNotPerformException("Could not deactivate listener!", ex);
        }
    }

    @Override
    public boolean isActive() {
        synchronized (participantLock) {
            return participant != null;
        }
    }
}
