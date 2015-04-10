/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package de.citec.jul.storage.registry;

import de.citec.jps.core.JPService;
import de.citec.jps.preset.JPReadOnly;
import de.citec.jul.exception.CouldNotPerformException;
import de.citec.jul.exception.ExceptionPrinter;
import de.citec.jul.exception.InvalidStateException;
import de.citec.jul.exception.MultiException;
import de.citec.jul.exception.NotAvailableException;
import de.citec.jul.exception.VerificationFailedException;
import de.citec.jul.iface.Identifiable;
import de.citec.jul.pattern.Observable;
import de.citec.jul.schedule.SyncObject;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Divine Threepwood
 * @param <KEY>
 * @param <ENTRY>
 * @param <MAP>
 * @param <R>
 */
public class AbstractRegistry<KEY, ENTRY extends Identifiable<KEY>, MAP extends Map<KEY, ENTRY>, R extends RegistryInterface<KEY, ENTRY, MAP, R>> extends Observable<Map<KEY, ENTRY>> implements RegistryInterface<KEY, ENTRY, MAP, R> {

    protected final Logger logger = LoggerFactory.getLogger(getClass());

    private final SyncObject SYNC = new SyncObject(AbstractRegistry.class);
    protected final MAP entryMap;
    private final List<ConsistencyHandler<KEY, ENTRY, MAP, R>> consistencyHandlerList;

    public AbstractRegistry(final MAP entryMap) {
        this.entryMap = entryMap;
        this.consistencyHandlerList = new ArrayList<>();
        this.notifyConsistencyHandler();
        this.notifyObservers();
    }

    @Override
    public ENTRY register(final ENTRY entry) throws CouldNotPerformException {
        logger.info("Register " + entry + "...");
        try {
            checkAccess();
            synchronized (SYNC) {
                if (entryMap.containsKey(entry.getId())) {
                    throw new CouldNotPerformException("Could not register " + entry + "! Entry with same id already registered!");
                }
                entryMap.put(entry.getId(), entry);
            }
        } catch (CouldNotPerformException ex) {
            throw new CouldNotPerformException("Could not register " + entry + "!", ex);
        }
        notifyConsistencyHandler();
        notifyObservers();
        return entry;
    }

    @Override
    public ENTRY update(final ENTRY entry) throws CouldNotPerformException {
        logger.info("Update " + entry + "...");
        try {
            checkAccess();
            synchronized (SYNC) {
                if (!entryMap.containsKey(entry.getId())) {
                    throw new InvalidStateException("Entry not registered!");
                }
                // replace
                entryMap.put(entry.getId(), entry);
            }
        } catch (CouldNotPerformException ex) {
            throw new CouldNotPerformException("Could not update " + entry + "!", ex);
        }
        notifyConsistencyHandler();
        notifyObservers();
        return entry;
    }

    @Override
    public ENTRY remove(final ENTRY entry) throws CouldNotPerformException {
        logger.info("Remove " + entry + "...");
        try {
            checkAccess();
            synchronized (SYNC) {
                if (!entryMap.containsKey(entry.getId())) {
                    throw new InvalidStateException("Entry not registered!");
                }
                return entryMap.remove(entry.getId());
            }
        } catch (CouldNotPerformException ex) {
            throw new CouldNotPerformException("Could not remove " + entry + "!", ex);
        } finally {
            notifyConsistencyHandler();
            notifyObservers();
        }
    }

    @Override
    public ENTRY get(final KEY key) throws CouldNotPerformException {
        verifyID(key);
        synchronized (SYNC) {
            if (!entryMap.containsKey(key)) {
                TreeMap<KEY, ENTRY> sortedMap = new TreeMap<>(entryMap);
                throw new NotAvailableException("Entry[" + key + "]", "Nearest neighbor is [" + sortedMap.floorKey(key) + "] or [" + sortedMap.ceilingKey(key) + "].");
            }
            return entryMap.get(key);
        }
    }

    @Override
    public List<ENTRY> getEntries() {
        synchronized (SYNC) {
            return new ArrayList<>(entryMap.values());
        }
    }

    @Override
    public boolean contains(final ENTRY entry) throws CouldNotPerformException {
        return contains(entry.getId());
    }

    @Override
    public boolean contains(final KEY key) throws CouldNotPerformException {
        return entryMap.containsKey(verifyID(key));
    }

    @Override
    public void clean() {
        synchronized (SYNC) {
            entryMap.clear();
        }
        notifyObservers();
    }

    protected void replaceInternalMap(final Map<KEY, ENTRY> map) {
        synchronized (SYNC) {
            entryMap.clear();
            entryMap.putAll(map);
        }
    }

    @Override
    public void checkAccess() throws InvalidStateException {
        if (JPService.getProperty(JPReadOnly.class).getValue()) {
            throw new InvalidStateException("ReadOnlyMode is detected!");
        }
    }

    private void notifyObservers() {
        try {
            super.notifyObservers(entryMap);
        } catch (MultiException ex) {
            ExceptionPrinter.printHistory(logger, new CouldNotPerformException("Could not notify all observer!", ex));
        }
    }

    protected KEY verifyID(final ENTRY entry) throws VerificationFailedException {
        try {
            return verifyID(entry.getId());
        } catch (CouldNotPerformException ex) {
            throw new VerificationFailedException("Could not verify message!", ex);
        }
    }

    protected KEY verifyID(final KEY id) throws VerificationFailedException {
        if (id == null) {
            throw new VerificationFailedException("Invalid id!", new NotAvailableException("id"));
        }
        return id;
    }

    @Override
    public void registerConsistencyHandler(final ConsistencyHandler<KEY, ENTRY, MAP, R> consistencyHandler) {
        consistencyHandlerList.add(consistencyHandler);
    }

    boolean consistencyCheckRunning = false;

    private synchronized void notifyConsistencyHandler() {
        
        // avoid dublicated consistency check
        if (consistencyCheckRunning) {
            return;
        }
        consistencyCheckRunning = true;

        int interationCounter = 0;
        MultiException.ExceptionStack exceptionStack = null;

        synchronized (SYNC) {
            while(true) {
                
                // handle handler interfereience
                if (interationCounter > consistencyHandlerList.size() * entryMap.size() * 2) {
                    try {
                        MultiException.checkAndThrow("To many errors occoured during processing!", exceptionStack);
                        throw new InvalidStateException("ConsistencyHandler interference detected!");
                    } catch (CouldNotPerformException ex) {
                        ExceptionPrinter.printHistory(logger, new CouldNotPerformException("Consistency process aborted!", ex));
                    }
                }
                
                interationCounter++;
                try {
                    for (ConsistencyHandler<KEY, ENTRY, MAP, R> consistencyHandler : consistencyHandlerList) {
                        for (ENTRY entry : entryMap.values()) {
                            try {
                                consistencyHandler.processData(entry.getId(), entry, entryMap, (R) this);
                            } catch (CouldNotPerformException | NullPointerException ex) {
                                exceptionStack = MultiException.push(consistencyHandler, new VerificationFailedException("Could not verify registry data consistency!", ex), exceptionStack);
                            }
                        }
                    }
                } catch (EntryModification ex) {
                    logger.info("Consistency modification applied: "+ex.getMessage());
                    continue;
                }
                logger.info("Registry consistend.");
                break;
            }
        }
        consistencyCheckRunning = false;
    }

    @Override
    public void shutdown() {
        clean();
        super.shutdown();
    }
}