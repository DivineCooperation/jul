package org.openbase.jul.storage.registry;

/*
 * #%L
 * JUL Storage
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
import com.google.protobuf.GeneratedMessage;
import java.util.Map;
import org.openbase.jps.core.JPService;
import org.openbase.jul.exception.CouldNotPerformException;
import org.openbase.jul.exception.MultiException;
import org.openbase.jul.exception.VerificationFailedException;
import org.openbase.jul.exception.printer.ExceptionPrinter;
import org.openbase.jul.exception.printer.LogLevel;
import org.openbase.jul.extension.protobuf.IdentifiableMessage;
import org.openbase.jul.extension.protobuf.IdentifiableMessageMap;
import org.openbase.jul.extension.protobuf.ProtobufListDiff;
import org.openbase.jul.iface.Activatable;
import org.openbase.jul.iface.Configurable;
import static org.openbase.jul.iface.Identifiable.TYPE_FIELD_ID;
import org.openbase.jul.iface.Shutdownable;
import org.openbase.jul.pattern.Factory;
import org.openbase.jul.pattern.Observable;
import org.openbase.jul.pattern.Observer;
import org.openbase.jul.schedule.RecurrenceEventFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author <a href="mailto:divine@openbase.org">Divine Threepwood</a>
 * @param <KEY>
 * @param <ENTRY>
 * @param <CONFIG_M>
 * @param <CONFIG_MB>
 */
public class RegistrySynchronizer<KEY, ENTRY extends Configurable<KEY, CONFIG_M>, CONFIG_M extends GeneratedMessage, CONFIG_MB extends CONFIG_M.Builder<CONFIG_MB>> implements Activatable, Shutdownable {

    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final SynchronizableRegistry<KEY, ENTRY> localRegistry;
    private final Observer<Map<KEY, IdentifiableMessage<KEY, CONFIG_M, CONFIG_MB>>> remoteRegistryChangeObserver;
    private final RecurrenceEventFilter recurrenceSyncFilter;
    private final ProtobufListDiff<KEY, CONFIG_M, CONFIG_MB> entryConfigDiff;
    private final Factory<ENTRY, CONFIG_M> factory;
    protected final RemoteRegistry<KEY, CONFIG_M, CONFIG_MB> remoteRegistry;
    private boolean active;

    public RegistrySynchronizer(final SynchronizableRegistry<KEY, ENTRY> registry, final RemoteRegistry<KEY, CONFIG_M, CONFIG_MB> remoteRegistry, final Factory<ENTRY, CONFIG_M> factory) throws org.openbase.jul.exception.InstantiationException {
        try {
            this.localRegistry = registry;
            this.remoteRegistry = remoteRegistry;
            this.entryConfigDiff = new ProtobufListDiff<>();
            this.factory = factory;
            this.recurrenceSyncFilter = new RecurrenceEventFilter(15000) {

                @Override
                public void relay() throws Exception {
                    logger.debug("Incomming updates passed filter...");
                    try {
                        internalSync();
                    } finally {
                        registry.notifySynchronization();
                    }
                }
            };

            this.remoteRegistryChangeObserver = new Observer<Map<KEY, IdentifiableMessage<KEY, CONFIG_M, CONFIG_MB>>>() {
                @Override
                public void update(Observable<Map<KEY, IdentifiableMessage<KEY, CONFIG_M, CONFIG_MB>>> source, Map<KEY, IdentifiableMessage<KEY, CONFIG_M, CONFIG_MB>> data) throws Exception {
                    logger.debug("Incomming updates...");
                    recurrenceSyncFilter.trigger();
                }
            };

        } catch (Exception ex) {
            throw new org.openbase.jul.exception.InstantiationException(this, ex);
        }
    }

    @Override
    public void activate() throws CouldNotPerformException, InterruptedException {
        remoteRegistry.waitForValue();
        remoteRegistry.addObserver(remoteRegistryChangeObserver);

        try {
            remoteRegistry.waitForValue();
            internalSync();
        } catch (CouldNotPerformException ex) {
            ExceptionPrinter.printHistory(new CouldNotPerformException("Initial sync failed!", ex), logger, LogLevel.ERROR);
        }
        active = true;
    }

    @Override
    public void deactivate() throws CouldNotPerformException, InterruptedException {
        logger.debug("deactivate " + this);
        active = false;
        remoteRegistry.removeObserver(remoteRegistryChangeObserver);
        recurrenceSyncFilter.cancel();
    }

    @Override
    public boolean isActive() {
        return active;
    }

    @Override
    public void shutdown() {
        try {
            deactivate();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        } catch (CouldNotPerformException ex) {
            ExceptionPrinter.printHistory("Could not shutdown " + this, ex, logger);
        }
    }

    private synchronized void internalSync() throws CouldNotPerformException, InterruptedException {
        logger.debug("Perform registry sync...");

        try {
            entryConfigDiff.diff(remoteRegistry.getMessages());
            int skippedChanges = 0;

            MultiException.ExceptionStack removeExceptionStack = null;
            for (CONFIG_M config : entryConfigDiff.getRemovedMessageMap().getMessages()) {
                try {
                    remove(config);
                } catch (CouldNotPerformException ex) {
                    removeExceptionStack = MultiException.push(this, ex, removeExceptionStack);
                }
            }

            MultiException.ExceptionStack updateExceptionStack = null;
            for (CONFIG_M config : entryConfigDiff.getUpdatedMessageMap().getMessages()) {
                try {
                    if (verifyConfig(config)) {
                        update(config);
                    } else {
                        remove(config);
                        entryConfigDiff.getOriginMessages().removeMessage(config);
                    }
                } catch (CouldNotPerformException ex) {
                    updateExceptionStack = MultiException.push(this, ex, updateExceptionStack);
                }
            }

            MultiException.ExceptionStack registerExceptionStack = null;
            for (CONFIG_M config : entryConfigDiff.getNewMessageMap().getMessages()) {
                try {
                    if (verifyConfig(config)) {
                        register(config);
                    } else {
                        skippedChanges++;
                    }
                } catch (CouldNotPerformException ex) {
                    registerExceptionStack = MultiException.push(this, ex, registerExceptionStack);
                }
            }

            // print changes
            final int errorCounter = MultiException.size(removeExceptionStack) + MultiException.size(updateExceptionStack) + MultiException.size(registerExceptionStack);
            final int changeCounter = (entryConfigDiff.getChangeCounter() - skippedChanges);
            if (changeCounter != 0 || errorCounter != 0) {
                logger.info(changeCounter + " registry changes applied. " + errorCounter + " are skipped.");
            }

            // sync origin list.
            IdentifiableMessageMap<KEY, CONFIG_M, CONFIG_MB> newOriginEntryMap = new IdentifiableMessageMap<>();
            for (ENTRY entry : localRegistry.getEntries()) {
                newOriginEntryMap.put(remoteRegistry.get(entry.getId()));
            }
            entryConfigDiff.replaceOriginMap(newOriginEntryMap);

            // build exception cause chain.
            MultiException.ExceptionStack exceptionStack = null;
            int counter;
            try {
                if (removeExceptionStack != null) {
                    counter = removeExceptionStack.size();
                } else {
                    counter = 0;
                }
                MultiException.checkAndThrow("Could not remove " + counter + " entries!", removeExceptionStack);
            } catch (CouldNotPerformException ex) {
                exceptionStack = MultiException.push(this, ex, exceptionStack);
            }
            try {
                if (updateExceptionStack != null) {
                    counter = updateExceptionStack.size();
                } else {
                    counter = 0;
                }
                MultiException.checkAndThrow("Could not update " + counter + " entries!", updateExceptionStack);
            } catch (CouldNotPerformException ex) {
                exceptionStack = MultiException.push(this, ex, exceptionStack);
            }
            try {
                if (registerExceptionStack != null) {
                    counter = registerExceptionStack.size();
                } else {
                    counter = 0;
                }
                MultiException.checkAndThrow("Could not register " + counter + " entries!", registerExceptionStack);
            } catch (CouldNotPerformException ex) {
                exceptionStack = MultiException.push(this, ex, exceptionStack);
            }
            MultiException.checkAndThrow("Could not sync all entries!", exceptionStack);
        } catch (CouldNotPerformException ex) {
            CouldNotPerformException exx = new CouldNotPerformException("Entry registry sync failed!", ex);
            if (JPService.testMode()) {
                ExceptionPrinter.printHistory(exx, logger);
                assert false; // exit if errors occurs during unit tests.
            }
            throw exx;
        }
    }

    public ENTRY register(final CONFIG_M config) throws CouldNotPerformException, InterruptedException {
        return localRegistry.register(factory.newInstance(config));
    }

    public ENTRY update(final CONFIG_M config) throws CouldNotPerformException, InterruptedException {
        ENTRY entry = localRegistry.get(remoteRegistry.getId(config));
        entry.applyConfigUpdate(config);
        return entry;
    }

    public ENTRY remove(final CONFIG_M config) throws CouldNotPerformException, InterruptedException {
        return localRegistry.remove(getId(config));
    }

    public KEY getId(final CONFIG_M entry) throws CouldNotPerformException {
        KEY key = (KEY) entry.getField(entry.getDescriptorForType().findFieldByName(TYPE_FIELD_ID));
        if (!localRegistry.contains(key)) {
            throw new CouldNotPerformException("Entry for given Key[" + key + "] is not available for local registry!");
        }
        return key;
    }

    /**
     * Method should return true if the given configurations is valid, otherwise false.
     * This default implementation accepts all configurations. To implement a custom verification just overwrite this
     * method.
     *
     * @param config
     * @return
     * @throws org.openbase.jul.exception.VerificationFailedException
     */
    public boolean verifyConfig(final CONFIG_M config) throws VerificationFailedException {
        return true;
    }
}
