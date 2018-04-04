package org.openbase.jul.storage.registry;

/*-
 * #%L
 * JUL Storage
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

import org.openbase.jps.core.JPService;
import org.openbase.jul.exception.CouldNotPerformException;
import org.openbase.jul.exception.MultiException;
import org.openbase.jul.exception.VerificationFailedException;
import org.openbase.jul.exception.printer.ExceptionPrinter;
import org.openbase.jul.extension.protobuf.IdentifiableValueMap;
import org.openbase.jul.extension.protobuf.ListDiff;
import org.openbase.jul.iface.Activatable;
import org.openbase.jul.iface.Identifiable;
import org.openbase.jul.iface.Shutdownable;
import org.openbase.jul.pattern.Observable;
import org.openbase.jul.pattern.Observer;
import org.openbase.jul.schedule.SyncObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public abstract class AbstractSynchronizer<KEY, ENTRY extends Identifiable<KEY>> implements Activatable, Shutdownable {

    protected final Logger logger = LoggerFactory.getLogger(getClass());

    private final IdentifiableValueMap<KEY, ENTRY> currentEntryMap;
    private final ListDiff<KEY, ENTRY> listDiff;
    private final Observable observable;
    private boolean isActive = false;
    private final Observer observer;

    private final SyncObject synchronizationLock = new SyncObject("SynchronizationLock");

    public AbstractSynchronizer(final Observable observable) {
        this.listDiff = new ListDiff<>();
        this.observable = observable;
        this.currentEntryMap = new IdentifiableValueMap<>();
        this.observer = (source, data) -> internalSync();
    }

    @Override
    public void activate() throws CouldNotPerformException, InterruptedException {
        // add data observer
        observable.addObserver(observer);

        if (observable.isValueAvailable()) {
            internalSync();
        }
        isActive = true;
    }

    @Override
    public void deactivate() throws CouldNotPerformException, InterruptedException {
        isActive = false;

        observable.removeObserver(observer);
    }

    @Override
    public boolean isActive() {
        return isActive;
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

    private void internalSync() throws CouldNotPerformException, InterruptedException {
        synchronized (synchronizationLock) {
            logger.debug("Perform sync...");

            try {
                listDiff.diff(getEntries());
                int skippedChanges = 0;

                MultiException.ExceptionStack removeExceptionStack = null;
                for (ENTRY entry : listDiff.getRemovedValueMap().values()) {
                    try {
                        removeInternal(entry);
                    } catch (CouldNotPerformException ex) {
                        removeExceptionStack = MultiException.push(this, ex, removeExceptionStack);
                    }
                }

                MultiException.ExceptionStack updateExceptionStack = null;
                for (ENTRY entry : listDiff.getUpdatedValueMap().values()) {
                    try {
                        if (verifyEntry(entry)) {
                            updateInternal(entry);
                        } else {
                            removeInternal(entry);
                            listDiff.getOriginalValueMap().removeValue(entry);
                        }
                    } catch (CouldNotPerformException ex) {
                        updateExceptionStack = MultiException.push(this, ex, updateExceptionStack);
                    }
                }

                MultiException.ExceptionStack registerExceptionStack = null;
                for (ENTRY entry : listDiff.getNewValueMap().values()) {
                    try {
                        if (verifyEntry(entry)) {
                            registerInternal(entry);
                        } else {
                            skippedChanges++;
                        }
                    } catch (CouldNotPerformException ex) {
                        registerExceptionStack = MultiException.push(this, ex, registerExceptionStack);
                    }
                }

                // print changes
                final int errorCounter = MultiException.size(removeExceptionStack) + MultiException.size(updateExceptionStack) + MultiException.size(registerExceptionStack);
                final int changeCounter = (listDiff.getChangeCounter() - skippedChanges);
                if (changeCounter != 0 || errorCounter != 0) {
                    logger.info(changeCounter + " changes synchronized." + (errorCounter == 0 ? "" : " " + errorCounter + (errorCounter == 1 ? " is" : " are") + " skipped."));
                }

                // sync list diff to what actually happened
                listDiff.replaceOriginalMap(currentEntryMap);

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
    }

    private void updateInternal(final ENTRY entry) throws CouldNotPerformException {
        update(entry);
        this.currentEntryMap.put(entry);
    }

    private void registerInternal(final ENTRY entry) throws CouldNotPerformException {
        register(entry);
        this.currentEntryMap.put(entry);
    }

    private void removeInternal(final ENTRY entry) throws CouldNotPerformException {
        remove(entry);
        this.currentEntryMap.removeValue(entry);
    }

    public abstract void update(final ENTRY entry) throws CouldNotPerformException;

    public abstract void register(final ENTRY entry) throws CouldNotPerformException;

    public abstract void remove(final ENTRY entry) throws CouldNotPerformException;

    public abstract List<ENTRY> getEntries();

    /**
     * Method should return true if the given entry is valid, otherwise
     * false. This default implementation accepts all entries. To
     * implement a custom verification just overwrite this method.
     *
     * @param entry the entry which is tested
     * @return if the entry should be synchronized
     * @throws org.openbase.jul.exception.VerificationFailedException if verifying the entry fails
     */
    public boolean verifyEntry(final ENTRY entry) throws VerificationFailedException {
        return true;
    }
}
