package org.dc.jul.extension.protobuf;

/*
 * #%L
 * JUL Extension Protobuf
 * $Id:$
 * $HeadURL:$
 * %%
 * Copyright (C) 2015 - 2016 DivineCooperation
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
import com.google.protobuf.GeneratedMessage.Builder;
import org.dc.jps.core.JPService;
import org.dc.jps.exception.JPServiceException;
import org.dc.jps.preset.JPTestMode;
import org.dc.jul.exception.CouldNotPerformException;
import org.dc.jul.exception.NotInitializedException;
import org.dc.jul.exception.printer.ExceptionPrinter;
import org.dc.jul.exception.printer.LogLevel;
import org.dc.jul.iface.Changeable;
import org.dc.jul.schedule.Timeout;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author mpohling
 * @param <MB>
 */
public class BuilderSyncSetup<MB extends Builder<MB>> {

    public static final long LOCK_TIMEOUT = 10000;

    protected final Logger logger = LoggerFactory.getLogger(BuilderSyncSetup.class);

    private final Changeable holder;
    private final MB builder;
    private final ReentrantReadWriteLock.ReadLock readLock;
    private final ReentrantReadWriteLock.WriteLock writeLock;
    private final Timeout readLockTimeout;
    private final Timeout writeLockTimeout;
    private Object readLockConsumer;
    private Object writeLockConsumer;

    public BuilderSyncSetup(final MB builder, final ReentrantReadWriteLock.ReadLock readLock, final ReentrantReadWriteLock.WriteLock writeLock, final Changeable holder) {
        this.builder = builder;
        this.readLock = readLock;
        this.writeLock = writeLock;
        this.holder = holder;
        this.readLockTimeout = new Timeout(LOCK_TIMEOUT) {

            @Override
            public void expired() {
                try {
                    if (JPService.getProperty(JPTestMode.class).getValue()) {
                        return;
                    }
                } catch (JPServiceException ex) {
                    ExceptionPrinter.printHistory(new CouldNotPerformException("Could not access java property!", ex), logger);
                }
                logger.error("Fatal implementation error!", new TimeoutException("ReadLock of " + builder.buildPartial().getClass().getSimpleName() + " was locked for more than " + LOCK_TIMEOUT / 1000 + " sec! Last access by Consumer[" + readLockConsumer + "]!"));
                unlockRead("TimeoutHandler");
            }
        };
        this.writeLockTimeout = new Timeout(LOCK_TIMEOUT) {

            @Override
            public void expired() {
                try {
                    if (JPService.getProperty(JPTestMode.class).getValue()) {
                        return;
                    }
                } catch (JPServiceException ex) {
                    ExceptionPrinter.printHistory(new CouldNotPerformException("Could not access java property!", ex), logger);
                }
                logger.error("Fatal implementation error!", new TimeoutException("WriteLock of " + builder.buildPartial().getClass().getSimpleName() + " was locked for more than " + LOCK_TIMEOUT / 1000 + " sec by Consumer[" + writeLockConsumer + "]!"));
                unlockWrite();
            }
        };
    }

    /**
     * Returns the internal builder instance. Use builder with care of read and
     * write locks.
     *
     * @return
     */
    public MB getBuilder() {
        return builder;
    }

    public void lockRead(final Object consumer) {
        logger.debug("order lockRead by " + consumer);
        readLock.lock();
        readLockConsumer = consumer;
        readLockTimeout.restart();
        logger.debug("lockRead by " + consumer);
    }

    public boolean tryLockRead(final Object consumer) {
        boolean success = readLock.tryLock();
        if (success) {
            readLockConsumer = consumer;
            readLockTimeout.restart();
        }
        return success;
    }

    public boolean tryLockRead(final long time, final TimeUnit unit, final Object consumer) throws InterruptedException {
        boolean success = readLock.tryLock(time, unit);
        if (success) {
            readLockConsumer = consumer;
            readLockTimeout.restart();
        }
        return success;
    }

    public void unlockRead(final Object consumer) {
        logger.debug("order unlockRead by " + consumer);
        if (readLockConsumer == consumer) {
            readLockConsumer = "Unknown";
        }
        readLockTimeout.cancel();
        readLock.unlock();
        logger.debug("unlockRead by " + consumer);
    }

    public void lockWrite(final Object consumer) {
        logger.debug("order lockWrite by " + consumer);
        writeLock.lock();
        writeLockConsumer = consumer;
        writeLockTimeout.start();
        logger.debug("lockWrite by " + consumer);
    }

    public boolean tryLockWrite(final Object consumer) {
        boolean success = writeLock.tryLock();
        if (success) {
            writeLockConsumer = consumer;
            writeLockTimeout.start();
        }
        return success;
    }

    public boolean tryLockWrite(final long time, final TimeUnit unit, final Object consumer) throws InterruptedException {
        boolean success = writeLock.tryLock(time, unit);
        if (success) {
            writeLockConsumer = consumer;
            writeLockTimeout.start();
        }
        return success;
    }

    public void unlockWrite() {
        unlockWrite(true);
    }

    public void unlockWrite(boolean notifyChange) {
        logger.debug("order write unlock");
        writeLockConsumer = "Unknown";
        writeLockTimeout.cancel();
        writeLock.unlock();
        logger.debug("write unlocked");
        if (notifyChange) {
            try {
                holder.notifyChange();
            } catch (NotInitializedException ex) {
                ExceptionPrinter.printHistory(new CouldNotPerformException("Could not inform builder holder about data update!", ex), logger, LogLevel.DEBUG);
            } catch (CouldNotPerformException ex) {
                ExceptionPrinter.printHistory(new CouldNotPerformException("Could not inform builder holder about data update!", ex), logger, LogLevel.ERROR);
            }
        }
    }
}
