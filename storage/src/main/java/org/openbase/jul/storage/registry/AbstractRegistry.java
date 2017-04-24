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
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import org.openbase.jps.core.JPService;
import org.openbase.jps.exception.JPNotAvailableException;
import org.openbase.jps.exception.JPServiceException;
import org.openbase.jps.preset.JPForce;
import org.openbase.jps.preset.JPReadOnly;
import org.openbase.jps.preset.JPVerbose;
import org.openbase.jul.exception.CouldNotPerformException;
import org.openbase.jul.exception.FatalImplementationErrorException;
import org.openbase.jul.exception.InstantiationException;
import org.openbase.jul.exception.InvalidStateException;
import org.openbase.jul.exception.MultiException;
import org.openbase.jul.exception.NotAvailableException;
import org.openbase.jul.exception.RejectedException;
import org.openbase.jul.exception.VerificationFailedException;
import org.openbase.jul.exception.printer.ExceptionPrinter;
import org.openbase.jul.exception.printer.LogLevel;
import org.openbase.jul.iface.Identifiable;
import org.openbase.jul.iface.Shutdownable;
import org.openbase.jul.iface.Shutdownable$;
import org.openbase.jul.pattern.Observable;
import org.openbase.jul.pattern.ObservableImpl;
import org.openbase.jul.pattern.Observer;
import org.openbase.jul.schedule.RecurrenceEventFilter;
import org.openbase.jul.storage.registry.plugin.RegistryPlugin;
import org.openbase.jul.storage.registry.plugin.RegistryPluginPool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author <a href="mailto:divine@openbase.org">Divine Threepwood</a>
 *
 * @param <KEY> EntryKey
 * @param <ENTRY> EntryType
 * @param <MAP> RegistryEntryMap
 * @param <R> Registry
 * @param <P> RegistryPluginType
 */
public class AbstractRegistry<KEY, ENTRY extends Identifiable<KEY>, MAP extends Map<KEY, ENTRY>, R extends Registry<KEY, ENTRY>, P extends RegistryPlugin<KEY, ENTRY>> extends ObservableImpl<Map<KEY, ENTRY>> implements Registry<KEY, ENTRY> {

    protected final Logger logger = LoggerFactory.getLogger(getClass());

    private String name;

    private final MAP entryMap;

    private final Random randomJitter;
    protected final RegistryPluginPool<KEY, ENTRY, P> pluginPool;
    protected RegistrySandbox<KEY, ENTRY, MAP, R> sandbox;

    protected boolean consistent;
    private final ReentrantReadWriteLock registryLock, consistencyCheckLock;

    private final List<ConsistencyHandler<KEY, ENTRY, MAP, R>> consistencyHandlerList;
    private final Map<Registry, DependencyConsistencyCheckTrigger> dependingRegistryMap;

    private RecurrenceEventFilter<String> consistencyFeedbackEventFilter;

    private boolean notificationSkiped;

    public AbstractRegistry(final MAP entryMap) throws InstantiationException {
        this(entryMap, new RegistryPluginPool<>());
    }

    public AbstractRegistry(final MAP entryMap, final RegistryPluginPool<KEY, ENTRY, P> pluginPool) throws InstantiationException {
        try {
            this.randomJitter = new Random(System.currentTimeMillis());
            this.registryLock = new ReentrantReadWriteLock();
            this.consistencyCheckLock = new ReentrantReadWriteLock();
            this.consistent = true;
            this.notificationSkiped = false;
            this.entryMap = entryMap;
            this.pluginPool = pluginPool;
            this.pluginPool.init(this);
            this.sandbox = new MockRegistrySandbox<>(this);
            this.consistencyHandlerList = new ArrayList<>();
            this.dependingRegistryMap = new HashMap<>();
            this.consistencyFeedbackEventFilter = new RecurrenceEventFilter<String>(10000) {
                @Override
                public void relay() throws Exception {
                    info(getLastValue());
                }
            };

            Shutdownable$.registerShutdownHook(this);
            finishTransaction();
            notifyObservers();
        } catch (CouldNotPerformException ex) {
            throw new InstantiationException(this, ex);
        }
    }

    protected <S extends AbstractRegistry<KEY, ENTRY, MAP, R, P> & RegistrySandbox<KEY, ENTRY, MAP, R>> void setupSandbox(final S sandbox) throws CouldNotPerformException {
        final RegistrySandbox<KEY, ENTRY, MAP, R> oldSandbox = sandbox;
        try {
            this.sandbox = sandbox;
            this.sandbox.sync(entryMap);

            for (ConsistencyHandler<KEY, ENTRY, MAP, R> consistencyHandler : consistencyHandlerList) {
                this.sandbox.registerConsistencyHandler(consistencyHandler);
            }
        } catch (CouldNotPerformException ex) {
            this.sandbox = oldSandbox;
            throw new CouldNotPerformException("Could not setup sandbox!", ex);
        }
    }

    /**
     * {@inheritDoc}
     *
     * @param entry {@inheritDoc}
     * @return {@inheritDoc}
     * @throws CouldNotPerformException {@inheritDoc}
     */
    @Override
    public ENTRY register(final ENTRY entry) throws CouldNotPerformException {
        info("Register " + entry + "...");
        try {
            checkWriteAccess();
            lock();
            try {
                if (entryMap.containsKey(entry.getId())) {
                    throw new CouldNotPerformException("Could not register " + entry + "! Entry with same Id[" + entry.getId() + "] already registered!");
                }
                sandbox.register(entry);
                pluginPool.beforeRegister(entry);
                entryMap.put(entry.getId(), entry);
                finishTransaction();
                pluginPool.afterRegister(entry);
            } finally {
                unlock();
            }
        } catch (CouldNotPerformException ex) {
            throw new CouldNotPerformException("Could not register " + entry + " in " + this + "!", ex);
        } finally {
            syncSandbox();
        }
        notifyObservers();
        return get(entry);
    }

    public ENTRY load(final ENTRY entry) throws CouldNotPerformException {
        logger.debug("Load " + entry + "...");
        try {
            lock();
            try {
                if (entryMap.containsKey(entry.getId())) {
                    throw new CouldNotPerformException("Could not register " + entry + "! Entry with same Id[" + entry.getId() + "] already registered!");
                }
                sandbox.load(entry);
                pluginPool.beforeRegister(entry);
                entryMap.put(entry.getId(), entry);
                pluginPool.afterRegister(entry);

            } finally {
                unlock();
            }
        } catch (CouldNotPerformException ex) {
            throw new CouldNotPerformException("Could not register " + entry + " in " + this + "!", ex);
        } finally {
            syncSandbox();
        }
        return entry;
    }

    /**
     * {@inheritDoc}
     *
     * @param entry {@inheritDoc}
     * @return {@inheritDoc}
     * @throws CouldNotPerformException {@inheritDoc}
     */
    @Override
    public ENTRY update(final ENTRY entry) throws CouldNotPerformException {
        info("Update " + entry + "...");
        try {
            checkWriteAccess();
            lock();
            try {
                // validate update
                if (!entryMap.containsKey(entry.getId())) {
                    throw new InvalidStateException("Entry not registered!");
                }
                // perform update
                sandbox.update(entry);
                pluginPool.beforeUpdate(entry);
                entryMap.put(entry.getId(), entry);
                finishTransaction();
                pluginPool.afterUpdate(entry);
            } finally {
                unlock();
            }
        } catch (CouldNotPerformException ex) {
            throw new CouldNotPerformException("Could not update " + entry + " in " + this + "!", ex);
        } finally {
            syncSandbox();
        }
        notifyObservers();
        return get(entry);
    }

    /**
     * {@inheritDoc}
     *
     * @param key {@inheritDoc}
     * @return {@inheritDoc}
     * @throws CouldNotPerformException {@inheritDoc}
     */
    @Override
    public ENTRY remove(final KEY key) throws CouldNotPerformException {
        return remove(get(key));
    }

    /**
     * {@inheritDoc}
     *
     * @param entry {@inheritDoc}
     * @return {@inheritDoc}
     * @throws CouldNotPerformException {@inheritDoc}
     */
    @Override
    public ENTRY remove(final ENTRY entry) throws CouldNotPerformException {
        return superRemove(entry);
    }

    public ENTRY superRemove(final ENTRY entry) throws CouldNotPerformException {
        info("Remove " + entry + "...");
        ENTRY oldEntry;
        try {
            checkWriteAccess();
            lock();
            try {
                // validate removal
                if (!entryMap.containsKey(entry.getId())) {
                    throw new InvalidStateException("Entry not registered!");
                }
                // perform removal
                sandbox.remove(entry);
                pluginPool.beforeRemove(entry);
                try {
                    oldEntry = entryMap.remove(entry.getId());
                } finally {
                    finishTransaction();
                }
                pluginPool.afterRemove(entry);
            } finally {
                unlock();
            }
        } catch (CouldNotPerformException ex) {
            throw new CouldNotPerformException("Could not remove " + entry + " in " + this + "!", ex);
        } finally {
            syncSandbox();
        }
        notifyObservers();
        return oldEntry;
    }

    /**
     * {@inheritDoc}
     *
     * @param key {@inheritDoc}
     * @return {@inheritDoc}
     * @throws CouldNotPerformException {@inheritDoc}
     */
    @Override
    public ENTRY get(final KEY key) throws CouldNotPerformException {
        verifyID(key);
        registryLock.readLock().lock();
        try {
            if (!entryMap.containsKey(key)) {

                if (entryMap.isEmpty()) {
                    throw new NotAvailableException("Entry", key.toString(), new InvalidStateException(this + " is empty!"));
                }

                TreeMap<KEY, ENTRY> sortedMap = new TreeMap<>((KEY o1, KEY o2) -> {
                    if (o1 instanceof String && o2 instanceof String) {
                        return ((String) o1).toLowerCase().compareTo(((String) o2).toLowerCase());
                    } else if (o1 instanceof Comparable && o2 instanceof Comparable) {
                        return ((Comparable) o1).compareTo(((Comparable) o2));
                    }
                    return (o1).toString().compareTo(o2.toString());
                });
                sortedMap.putAll(entryMap);

                if (sortedMap.floorKey(key) != null && sortedMap.ceilingKey(key) != null) {
                    throw new NotAvailableException("Entry", key.toString(), "Nearest neighbor is " + get(sortedMap.floorKey(key)) + " or " + get(sortedMap.ceilingKey(key)) + ".");
                } else if (sortedMap.floorKey(key) != null) {
                    throw new NotAvailableException("Entry", key.toString(), "Nearest neighbor is " + get(sortedMap.floorKey(key)) + ".");
                } else if (sortedMap.ceilingKey(key) != null) {
                    throw new NotAvailableException("Entry", key.toString(), "Nearest neighbor is " + get(sortedMap.ceilingKey(key)) + ".");
                } else {
                    throw new InvalidStateException("Implementation error, case not handled.");
                }
            }
            pluginPool.beforeGet(key);
            return entryMap.get(key);
        } finally {
            registryLock.readLock().unlock();
        }
    }

    /**
     * {@inheritDoc}
     *
     * @return {@inheritDoc}
     * @throws CouldNotPerformException {@inheritDoc}
     */
    @Override
    public List<ENTRY> getEntries() throws CouldNotPerformException {
        registryLock.readLock().lock();
        try {
            pluginPool.beforeGetEntries();
            return new ArrayList<>(entryMap.values());
        } finally {
            registryLock.readLock().unlock();
        }
    }

    /**
     * {@inheritDoc}
     *
     * @return {@inheritDoc}
     */
    @Override
    public int size() {
        registryLock.readLock().lock();
        try {
            return entryMap.size();
        } finally {
            registryLock.readLock().unlock();
        }
    }

    /**
     * {@inheritDoc}
     *
     * @return {@inheritDoc}
     */
    @Override
    public boolean isEmpty() {
        registryLock.readLock().lock();
        try {
            return entryMap.isEmpty();
        } finally {
            registryLock.readLock().unlock();
        }
    }

    /**
     * {@inheritDoc}
     *
     * @param entry {@inheritDoc}
     * @return {@inheritDoc}
     * @throws CouldNotPerformException {@inheritDoc}
     */
    @Override
    public boolean contains(final ENTRY entry) throws CouldNotPerformException {
        return contains(entry.getId());
    }

    /**
     * {@inheritDoc}
     *
     * @param key {@inheritDoc}
     * @return {@inheritDoc}
     * @throws CouldNotPerformException {@inheritDoc}
     */
    @Override
    public boolean contains(final KEY key) throws CouldNotPerformException {
        return entryMap.containsKey(verifyID(key));
    }

    /**
     * {@inheritDoc}
     *
     * @throws CouldNotPerformException {@inheritDoc}
     */
    @Override
    public void clear() throws CouldNotPerformException {
        lock();
        try {
            pluginPool.beforeClear();
            sandbox.clear();
            entryMap.clear();
            consistent = true;
        } finally {
            unlock();
        }
        notifyObservers();
    }

    /**
     * Replaces the internal registry map by the given one.
     *
     * Use with care!
     *
     * @param map
     * @throws org.openbase.jul.exception.CouldNotPerformException
     */
    public void replaceInternalMap(final Map<KEY, ENTRY> map) throws CouldNotPerformException {
        replaceInternalMap(map, true);
    }

    /**
     * Replaces the internal registry map by the given one.
     *
     * Use with care!
     *
     * @param map
     * @param finishTransaction is true the registry transaction will be verified.
     * @throws org.openbase.jul.exception.CouldNotPerformException
     */
    public void replaceInternalMap(final Map<KEY, ENTRY> map, boolean finishTransaction) throws CouldNotPerformException {
        lock();
        try {
            try {
                sandbox.replaceInternalMap(map);
                entryMap.clear();
                entryMap.putAll(map);
                if (finishTransaction) {
                    finishTransaction();
                }
            } catch (CouldNotPerformException ex) {
                ExceptionPrinter.printHistory(new CouldNotPerformException("Internal map replaced by invalid data!", ex), logger, LogLevel.ERROR);
            } finally {
                syncSandbox();
            }
        } finally {
            unlock();
        }
        notifyObservers();
    }

    /**
     * {@inheritDoc}
     *
     * @throws RejectedException {@inheritDoc}
     */
    @Override
    public void checkWriteAccess() throws RejectedException {
        logger.debug("checkWriteAccess of " + this);
        try {
            if (JPService.getProperty(JPForce.class).getValue()) {
                return;
            }
        } catch (JPServiceException ex) {
            ExceptionPrinter.printHistory(new CouldNotPerformException("Could not access java property!", ex), logger);
        }

        try {
            if (JPService.getProperty(JPReadOnly.class).getValue()) {
                throw new RejectedException("ReadOnlyMode is detected!");
            }
        } catch (JPServiceException ex) {
            ExceptionPrinter.printHistory(new CouldNotPerformException("Could not access java property!", ex), logger);
        }

        if (!isDependingOnConsistentRegistries()) {
            throw new RejectedException("At least one depending registry is inconsistent!");
        }

        pluginPool.checkAccess();

        if (!consistent) {
            logger.warn("Registry is inconsistent! To fix registry manually start the registry in force mode.");
            throw new RejectedException("Registry is inconsistent!");
        }
    }

    /**
     * If this registry depends on other registries, this method can be used to tell this registry if all depending registries are consistent.
     *
     * @return The method returns should return false if at least one depending registry is not consistent!
     */
    protected boolean isDependingOnConsistentRegistries() {
        return new ArrayList<>(dependingRegistryMap.keySet()).stream().noneMatch((registry) -> (!registry.isConsistent()));
    }

    /**
     * If this registry depends on other registries, this method can be used to tell this registry all depending registries.
     * For instance this is used to switch to read only mode in case one depending registry is not consistent.
     * Consistency checks are skipped as well if at least one depending registry is not consistent.
     *
     * @param registry the dependency of these registry.
     * @throws org.openbase.jul.exception.CouldNotPerformException
     */
    public void registerDependency(final Registry registry) throws CouldNotPerformException {
        registryLock.writeLock().lock();
        try {
            // check if already registered
            if (dependingRegistryMap.containsKey(registry)) {
                return;
            }
            dependingRegistryMap.put(registry, new DependencyConsistencyCheckTrigger(registry));
        } finally {
            registryLock.writeLock().unlock();
        }
    }

    /**
     * This method allows the removal of a registered registry dependency.
     *
     * @param registry the dependency to remove.
     */
    public void removeDependency(final Registry registry) {
        registryLock.writeLock().lock();
        try {
            if (!dependingRegistryMap.containsKey(registry)) {
                logger.warn("Could not remove a dependency which was never registered!");
                return;
            }
            dependingRegistryMap.remove(registry).shutdown();
        } finally {
            registryLock.writeLock().unlock();
        }
    }

    /**
     * Removal of all registered registry dependencies in the reversed order in which they where added.
     */
    public void removeAllDependencies() {
        registryLock.writeLock().lock();
        try {
            List<Registry> dependingRegistryList = new ArrayList<>(dependingRegistryMap.keySet());
            Collections.reverse(dependingRegistryList);
            dependingRegistryList.stream().forEach((registry) -> {
                dependingRegistryMap.remove(registry).shutdown();
            });
        } finally {
            registryLock.writeLock().unlock();
        }
    }

    /**
     * {@inheritDoc}
     *
     * @return {@inheritDoc}
     */
    @Override
    public boolean isReadOnly() {
        try {
            checkWriteAccess();
        } catch (RejectedException ex) {
            return true;
        }
        return false;
    }

    protected final void notifyObservers() {
        try {
            // It is not waited until the write actions are finished because the notification will be triggered after the lock release.
            if (registryLock.isWriteLockedByCurrentThread()) {
                logger.debug("Notification of registry change skipped because of running write operations!");
                notificationSkiped = true;
                return;
            }

            if (super.notifyObservers(entryMap)) {
                try {
                    pluginPool.afterRegistryChange();
                } catch (CouldNotPerformException ex) {
                    MultiException.ExceptionStack exceptionStack = new MultiException.ExceptionStack();
                    exceptionStack.push(pluginPool, ex);
                    throw new MultiException("PluginPool could not execute afterRegistryChange", exceptionStack);
                }
            }
            notificationSkiped = false;
        } catch (MultiException ex) {
            ExceptionPrinter.printHistory(new CouldNotPerformException("Could not notify all registry observer!", ex), logger, LogLevel.ERROR);
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

        if (id instanceof String && ((String) id).isEmpty()) {
            throw new VerificationFailedException("Invalid id!", new InvalidStateException("id is empty!"));
        }
        return id;
    }

    public void registerConsistencyHandler(final ConsistencyHandler<KEY, ENTRY, MAP, R> consistencyHandler) throws CouldNotPerformException {
        consistencyHandlerList.add(consistencyHandler);
        sandbox.registerConsistencyHandler(consistencyHandler);
    }

    public void removeConsistencyHandler(final ConsistencyHandler<KEY, ENTRY, MAP, R> consistencyHandler) throws CouldNotPerformException {
        consistencyHandlerList.remove(consistencyHandler);
        sandbox.removeConsistencyHandler(consistencyHandler);
    }

    @SuppressWarnings("UseSpecificCatch")
    public final int checkConsistency() throws CouldNotPerformException {
        int modificationCounter = 0;

        if (consistencyHandlerList.isEmpty()) {
            logger.debug("Skip consistency check because no handler are registered.");
            return modificationCounter;
        }

        if (isEmpty()) {
            logger.debug("Skip consistency check because registry is empty.");
            return modificationCounter;
        }

        if (!isDependingOnConsistentRegistries()) {
            logger.warn("Skip consistency check because registry is depending on at least one inconsistent registry!");
            return modificationCounter;
        }

        if (consistencyCheckLock.isWriteLockedByCurrentThread()) {
            // Avoid triggering recursive consistency checks.
            logger.debug(this + " skipping consistency check because check is already running by same thread: " + Thread.currentThread().getId());
            return modificationCounter;
        }

        lock();
        try {
            consistencyCheckLock.writeLock().lock();
            try {
                try {
                    int iterationCounter = 0;
                    MultiException.ExceptionStack exceptionStack = null;

                    final ArrayDeque<ConsistencyHandler> consistencyHandlerQueue = new ArrayDeque<>();
                    Object lastModifieredEntry = null;
                    final ArrayList<ENTRY> entryValueCopy = new ArrayList<>();
                    int maxConsistencyChecks;
                    int errorCounter;
                    String note;

                    while (true) {

                        // do not burn cpu
                        Thread.yield();

                        // init next interation
                        iterationCounter++;

                        // handle handler interference
                        maxConsistencyChecks = consistencyHandlerList.size() * entryMap.size() * 2;
                        if (iterationCounter > maxConsistencyChecks) {
                            MultiException.checkAndThrow(MultiException.size(exceptionStack) + " error" + (MultiException.size(exceptionStack) == 1 ? "" : "s") + " occoured during processing!", exceptionStack);
                            throw new InvalidStateException("ConsistencyHandler" + Arrays.toString(consistencyHandlerQueue.toArray()) + " interference detected!");
                        }

                        // prepare for next iteraction
                        if (exceptionStack != null) {
                            errorCounter = exceptionStack.size();
                            exceptionStack.clear();
                        } else {
                            errorCounter = 0;
                        }
                        if (!consistencyHandlerQueue.isEmpty() || errorCounter != 0) {

                            if (errorCounter > 0) {
                                note = " with " + errorCounter + " errors";
                            } else {
                                note = "";
                            }

                            if (!consistencyHandlerQueue.isEmpty()) {
                                note += " after " + consistencyHandlerQueue.size() + " applied modifications";
                            }
                            consistencyFeedbackEventFilter.trigger(((int) (((double) iterationCounter) / ((double) maxConsistencyChecks) * 100)) + "% of max consistency checks passed of " + this + note + ".");
                        }
                        consistencyHandlerQueue.clear();

                        // consistency check
                        try {
                            for (ConsistencyHandler<KEY, ENTRY, MAP, R> consistencyHandler : consistencyHandlerList) {
                                consistencyHandler.reset();
                                entryValueCopy.clear();
                                entryValueCopy.addAll(new ArrayList<>(entryMap.values()));
                                for (ENTRY entry : entryValueCopy) {
                                    try {
                                        consistencyHandler.processData(entry.getId(), entry, entryMap, (R) this);
                                    } catch (CouldNotPerformException | NullPointerException ex) {
                                        logger.debug("Inconsisteny detected by ConsistencyHandler[" + consistencyHandler + "] in Entry[" + entry + "]!");
                                        exceptionStack = MultiException.push(consistencyHandler, new VerificationFailedException("Verification of Entry[" + entry + "] failed with " + consistencyHandler + "!", ex), exceptionStack);
                                    }
                                }
                            }
                        } catch (EntryModification ex) {

                            // check if consistency handler is looping
                            if (ex.getConsistencyHandler() == consistencyHandlerQueue.peekLast() && ex.getEntry().equals(lastModifieredEntry)) {
                                throw new InvalidStateException("ConsistencyHandler[" + consistencyHandlerQueue.peekLast() + "] is looping over same Entry[" + lastModifieredEntry + "] more than once!");
                            }

                            consistencyHandlerQueue.remove(ex.getConsistencyHandler());
                            consistencyHandlerQueue.offer(ex.getConsistencyHandler());
                            lastModifieredEntry = ex.getEntry();

                            // inform about modifications
                            try {
                                if (JPService.getProperty(JPVerbose.class).getValue()) {
                                    info("Consistency modification applied: " + ex.getMessage());
                                } else {
                                    logger.debug("Consistency modification applied: " + ex.getMessage());
                                }
                            } catch (JPNotAvailableException exx) {
                                ExceptionPrinter.printHistory(new CouldNotPerformException("JPVerbose property could not be loaded!", exx), logger, LogLevel.WARN);
                            }
                            modificationCounter++;
                            continue;
                        } catch (Throwable ex) {
                            throw ExceptionPrinter.printHistoryAndReturnThrowable(new InvalidStateException("Fatal error occured during consistency check!", ex), logger);
                        }

                        if (exceptionStack != null && !exceptionStack.isEmpty()) {
                            continue;
                        }

                        logger.debug(this + " consistend.");
                        break;
                    }
                    consistent = true;

                    if (modificationCounter > 0) {
                        consistencyFeedbackEventFilter.trigger("100% consistency checks passed of " + this + " after " + modificationCounter + " applied modifications.");
                    }

                    return modificationCounter;
                } catch (CouldNotPerformException ex) {
                    consistent = false;
                    try {
                        if (JPService.getProperty(JPForce.class).getValue()) {
                            ExceptionPrinter.printHistory(new CouldNotPerformException("Consistency process of " + this + " aborted after " + modificationCounter + " modifications but transaction passed because registry force mode is enabled!", ex), logger, LogLevel.WARN);
                            return modificationCounter;
                        }
                    } catch (JPServiceException exx) {
                        ExceptionPrinter.printHistory(new CouldNotPerformException("Could not access java property!", exx), logger);
                    }
                    throw new CouldNotPerformException("Consistency process of " + this + " aborted!", ex);
                }
            } finally {
                afterConsistencyCheck();
                consistencyCheckLock.writeLock().unlock();
            }
        } finally {
            unlock();
        }
    }

    /**
     * Can be overwritten for further registry actions scheduled after consistency checks.
     *
     * Don't forgett to pass-througt the call to the super class. (super.afterConsistencyCheck())
     */
    protected void afterConsistencyCheck() throws CouldNotPerformException {
        pluginPool.afterConsistencyCheck();
    }

    protected void finishTransaction() throws CouldNotPerformException {
        try {
            checkConsistency();
        } catch (CouldNotPerformException ex) {
            throw ExceptionPrinter.printHistoryAndReturnThrowable(new FatalImplementationErrorException("Registry consistency check failed but sandbox check was successful!", this, ex), logger, LogLevel.ERROR);
        }
    }

    private void syncSandbox() throws CouldNotPerformException {
        registryLock.readLock().lock();
        try {
            sandbox.sync(entryMap);
        } finally {
            registryLock.readLock().unlock();
        }
    }

    /**
     * {@inheritDoc}
     *
     * @return {@inheritDoc}
     */
    @Override
    public boolean isConsistent() {
        return consistent;
    }

    /**
     * {@inheritDoc}
     *
     * @return {@inheritDoc}
     */
    @Override
    public boolean isReady() {
        return isConsistent() && !isBusy();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void shutdown() {
        try {
            registryLock.writeLock().lock();
            try {
                super.shutdown();
                removeAllDependencies();
                pluginPool.shutdown();
                consistencyHandlerList.stream().forEach((consistencyHandler) -> {
                    consistencyHandler.shutdown();
                });
                clear();
            } finally {
                registryLock.writeLock().unlock();
            }
        } catch (CouldNotPerformException ex) {
            ExceptionPrinter.printHistory("Could not shutdown " + this, ex, logger);
        }
    }

    /**
     * Method can be used to register any compatible registry plugin for this
     * registry.
     *
     * @param plugin the plugin to register.
     * @throws CouldNotPerformException is thrown in case the plugin could not
     * be registered.
     * @throws InterruptedException is thrown if the thread is externally
     * interrupted.
     */
    public void registerPlugin(P plugin) throws CouldNotPerformException, InterruptedException {
        pluginPool.addPlugin(plugin);
    }

    /**
     * Method defines the name of this registry.
     *
     * @param name
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * Method returns the name of this registry. In case the name was never set
     * for this registry the simple class name of the registry class is returned
     * instead.
     *
     * @return the name of the registry.
     */
    @Override
    public String getName() {
        if (name == null) {
            return getClass().getSimpleName();
        }
        return name;
    }

    /**
     * {@inheritDoc}
     *
     * @return {@inheritDoc}
     */
    @Override
    public boolean isSandbox() {
        return false;
    }

    /**
     * This method can be used to log registry info messages which are only printed for the origin registry.
     * Info messages of a sandbox instance are redirected to the debug channel.
     *
     * @param message the info message to print as string.
     */
    public void info(final String message) {
        if (isSandbox()) {
            logger.debug(message);
        } else {
            logger.info(message);
        }
    }

    /**
     * {@inheritDoc}
     *
     * @return {@inheritDoc}
     */
    @Override
    public String toString() {
        return getName();
    }

    /**
     * Blocks until the registry write lock is acquired.
     *
     * @throws RejectedException is thrown in case the lock is not supported by this registry. E.g. this is the case for remote registries.
     */
    protected void lock() throws CouldNotPerformException {
        try {
            while (true) {
                if (registryLock.writeLock().tryLock()) {
                    try {
                        lockDependingRegistries();
                        return;
                    } catch (RejectedException ex) {
                        registryLock.writeLock().unlock();
                    } catch (CouldNotPerformException ex) {
                        registryLock.writeLock().unlock();
                        throw ex;
                    }
                }

                try {
                    Thread.sleep(20 + randomJitter.nextInt(5));
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    throw new CouldNotPerformException("Could not lock registry because thread was externally interrupted!", ex);
                }
            }
        } catch (CouldNotPerformException ex) {
            throw new CouldNotPerformException("Could not lock registry!", ex);
        }
    }

    public boolean isBusy() {
        return registryLock.isWriteLocked();
    }

    public boolean isBusyByCurrentThread() {
        return registryLock.isWriteLockedByCurrentThread();
    }

    protected void unlock() {
        unlockDependingRegistries();
        assert registryLock.writeLock().isHeldByCurrentThread();
        registryLock.writeLock().unlock();
    }

    protected boolean isWriteLockedByCurrentThread() {
        return registryLock.writeLock().isHeldByCurrentThread();
    }

    /**
     * {@inheritDoc}
     *
     * @return {@inheritDoc}
     * @throws RejectedException {@inheritDoc}
     */
    @Override
    public boolean tryLockRegistry() throws RejectedException {
        return registryLock.writeLock().tryLock();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void unlockRegistry() {
        assert registryLock.writeLock().isHeldByCurrentThread();
        registryLock.writeLock().unlock();
    }

    private synchronized void lockDependingRegistries() throws RejectedException, FatalImplementationErrorException {
        boolean success = true;
        final List<Registry> lockedRegistries = new ArrayList<>();

        try {
            // lock all depending registries except remote registries which will reject the locking.
            for (Registry registry : dependingRegistryMap.keySet()) {
                try {
                    if (registry.tryLockRegistry()) {
                        lockedRegistries.add(registry);
                    } else {
                        success = false;
                    }
                } catch (RejectedException ex) {
                    // ignore if registry does not support the lock.
                }
            }
        } catch (Exception ex) {
            success = false;
            throw new RejectedException("Could not lock all depending registries!", ex);
        } finally {
            try {
                // if not successfull release all already acquire locks.
                if (!success) {
                    lockedRegistries.stream().forEach((registry) -> {
                        registry.unlockRegistry();
                    });
                }
            } catch (Exception ex) {
                assert false;
                throw new FatalImplementationErrorException("Could not release depending locks!", this, ex);
            }
        }

        if (!success) {
            throw new RejectedException("Could not lock all depending registries!");
        }
    }

    private synchronized void unlockDependingRegistries() {
        new ArrayList<>(dependingRegistryMap.keySet()).stream().forEach((registry) -> {
            registry.unlockRegistry();
        });
    }

    private class DependencyConsistencyCheckTrigger implements Observer, Shutdownable {

        private final Registry dependency;

        public DependencyConsistencyCheckTrigger(final Registry dependency) {
            this.dependency = dependency;
            dependency.addObserver(this);
        }

        /**
         * {@inheritDoc}
         *
         * @param source {@inheritDoc}
         * @param data {@inheritDoc}
         * @throws Exception {@inheritDoc}
         */
        @Override
        public void update(Observable source, Object data) throws Exception {
            //TODO: update on sandbox level should be handled first
            try {
                if (dependency.isConsistent()) {
                    if (checkConsistency() > 0 || notificationSkiped) {
                        notifyObservers();
                    }
                }
            } catch (CouldNotPerformException ex) {
                ExceptionPrinter.printHistory("Registry inconsistend after change of depending " + source + " change.", ex, logger);
            } finally {
                syncSandbox();
            }
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void shutdown() {
            removeObserver(this);
        }
    }

    /////////////
    // START DEFAULT INTERFACE METHODS
    /////////////
    public boolean isWritable() {
        try {
            checkWriteAccess();
        } catch (RejectedException ex) {
            return false;
        }
        return true;
    }

    public ENTRY get(final ENTRY entry) throws CouldNotPerformException {
        return get(entry.getId());
    }

    public boolean isEmtpy() {
        return isEmpty();
    }
    /////////////
    // END DEFAULT INTERFACE METHODS
    /////////////
}
