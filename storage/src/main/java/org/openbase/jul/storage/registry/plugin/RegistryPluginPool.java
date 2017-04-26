package org.openbase.jul.storage.registry.plugin;

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
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import java8.util.function.Consumer;
import java8.util.stream.StreamSupport;
import org.openbase.jps.core.JPService;
import org.openbase.jul.exception.CouldNotPerformException;
import org.openbase.jul.exception.InitializationException;
import org.openbase.jul.exception.RejectedException;
import org.openbase.jul.exception.printer.ExceptionPrinter;
import org.openbase.jul.exception.printer.LogLevel;
import org.openbase.jul.iface.Identifiable;
import org.openbase.jul.storage.registry.Registry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author <a href="mailto:divine@openbase.org">Divine Threepwood</a>
 * @param <KEY>
 * @param <ENTRY>
 * @param <P>
 */
public class RegistryPluginPool<KEY, ENTRY extends Identifiable<KEY>, P extends RegistryPlugin<KEY, ENTRY>> implements RegistryPlugin<KEY, ENTRY> {

    protected final Logger logger = LoggerFactory.getLogger(RegistryPluginPool.class);

    protected final List<P> pluginList;
    protected Registry<KEY, ENTRY> registry;
    private final ReentrantReadWriteLock lock;

    public RegistryPluginPool() {
        this.pluginList = new ArrayList<>();
        this.lock = new ReentrantReadWriteLock();
    }

    @Override
    public void init(final Registry<KEY, ENTRY> registry) throws InitializationException {
        this.registry = registry;
    }

    @Override
    public void shutdown() {
        StreamSupport.stream(pluginList).forEach(new Consumer<P>() {
            @Override
            public void accept(P plugin) {
                try {
                    plugin.shutdown();
                } catch (Exception ex) {
                    ExceptionPrinter.printHistory(new CouldNotPerformException("Could not shutdown RegistryPlugin[" + plugin + "]!", ex), logger, LogLevel.ERROR);
                    assert !JPService.testMode(); // fail during unit tests.
                }
            }
        });
    }

    public void addPlugin(final P plugin) throws InitializationException, InterruptedException {
        try {
            plugin.init(registry);
            pluginList.add(plugin);
        } catch (CouldNotPerformException ex) {
            throw new InitializationException("Could not add Plugin[" + plugin.getClass().getName() + "] to Registry[" + registry.getClass().getSimpleName() + "]", ex);
        }
    }

    @Override
    public void beforeRegister(final ENTRY entry) throws RejectedException {
        if (pluginList.isEmpty() || lock.isWriteLockedByCurrentThread()) {
            return;
        }

        lock.writeLock().lock();
        try {
            for (P plugin : pluginList) {
                try {
                    plugin.beforeRegister(entry);
                } catch (RejectedException ex) {
                    throw ex;
                } catch (Exception ex) {
                    ExceptionPrinter.printHistory(new CouldNotPerformException("Could not inform RegistryPlugin[" + plugin + "] about planned Entry[" + entry + "] registration!", ex), logger, LogLevel.ERROR);
                    assert !JPService.testMode(); // fail during unit tests.
                }
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public void afterRegister(final ENTRY entry) throws CouldNotPerformException {
        if (pluginList.isEmpty() || lock.isWriteLockedByCurrentThread()) {
            return;
        }

        lock.writeLock().lock();
        try {
            for (P plugin : pluginList) {
                try {
                    plugin.afterRegister(entry);
                } catch (Exception ex) {
                    ExceptionPrinter.printHistory(new CouldNotPerformException("Could not inform RegistryPlugin[" + plugin + "] about planned Entry[" + entry + "] registration!", ex), logger, LogLevel.ERROR);
                    assert !JPService.testMode(); // fail during unit tests.
                }
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public void beforeUpdate(final ENTRY entry) throws RejectedException {
        if (pluginList.isEmpty() || lock.isWriteLockedByCurrentThread()) {
            return;
        }

        lock.writeLock().lock();
        try {
            for (P plugin : pluginList) {
                try {
                    plugin.beforeUpdate(entry);
                } catch (RejectedException ex) {
                    throw ex;
                } catch (Exception ex) {
                    ExceptionPrinter.printHistory(new CouldNotPerformException("Could not inform RegistryPlugin[" + plugin + "] about planned Entry[" + entry + "] update!", ex), logger, LogLevel.ERROR);
                    assert !JPService.testMode(); // fail during unit tests.
                }
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public void afterUpdate(final ENTRY entry) throws CouldNotPerformException {
        if (pluginList.isEmpty() || lock.isWriteLockedByCurrentThread()) {
            return;
        }

        lock.writeLock().lock();
        try {
            for (P plugin : pluginList) {
                try {
                    plugin.afterUpdate(entry);
                } catch (Exception ex) {
                    ExceptionPrinter.printHistory(new CouldNotPerformException("Could not inform RegistryPlugin[" + plugin + "] about successfully Entry[" + entry + "] update!", ex), logger, LogLevel.ERROR);
                    assert !JPService.testMode(); // fail during unit tests.
                }
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public void beforeRemove(final ENTRY entry) throws RejectedException {
        if (pluginList.isEmpty() || lock.isWriteLockedByCurrentThread()) {
            return;
        }

        lock.writeLock().lock();
        try {
            for (P plugin : pluginList) {
                try {
                    plugin.beforeRemove(entry);
                } catch (RejectedException ex) {
                    throw ex;
                } catch (Exception ex) {
                    ExceptionPrinter.printHistory(new CouldNotPerformException("Could not inform RegistryPlugin[" + plugin + "] about planned Entry[" + entry + "] removal!", ex), logger, LogLevel.ERROR);
                    assert !JPService.testMode(); // fail during unit tests.
                }
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public void afterRemove(final ENTRY entry) throws CouldNotPerformException {
        if (pluginList.isEmpty() || lock.isWriteLockedByCurrentThread()) {
            return;
        }

        lock.writeLock().lock();
        try {
            for (P plugin : pluginList) {
                try {
                    plugin.afterRemove(entry);
                } catch (Exception ex) {
                    ExceptionPrinter.printHistory(new CouldNotPerformException("Could not inform RegistryPlugin[" + plugin + "] about successfully Entry[" + entry + "] removal!", ex), logger, LogLevel.ERROR);
                    assert !JPService.testMode(); // fail during unit tests.
                }
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public void beforeClear() throws CouldNotPerformException {
        if (pluginList.isEmpty() || lock.isWriteLockedByCurrentThread()) {
            return;
        }

        lock.writeLock().lock();
        try {
            for (P plugin : pluginList) {
                try {
                    plugin.beforeClear();
                } catch (Exception ex) {
                    ExceptionPrinter.printHistory(new CouldNotPerformException("Could not inform RegistryPlugin[" + plugin + "] about planned registry earsure!", ex), logger, LogLevel.ERROR);
                    assert !JPService.testMode(); // fail during unit tests.
                }
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public void beforeGet(final KEY key) throws RejectedException {
        if (pluginList.isEmpty()) {
            return;
        }

        for (P plugin : pluginList) {
            try {
                plugin.beforeGet(key);
            } catch (RejectedException ex) {
                throw ex;
            } catch (Exception ex) {
                ExceptionPrinter.printHistory(new CouldNotPerformException("Could not inform RegistryPlugin[" + plugin + "] about planned Entry[" + key + "] publishment!", ex), logger, LogLevel.ERROR);
                assert !JPService.testMode(); // fail during unit tests.
            }
        }
    }

    @Override
    public void beforeGetEntries() throws CouldNotPerformException {
        if (pluginList.isEmpty()) {
            return;
        }

        for (P plugin : pluginList) {
            try {
                plugin.beforeGetEntries();
            } catch (Exception ex) {
                ExceptionPrinter.printHistory(new CouldNotPerformException("Could not inform RegistryPlugin[" + plugin + "] about planned registry publishment!", ex), logger, LogLevel.ERROR);
                assert !JPService.testMode(); // fail during unit tests.
            }
        }
    }

    @Override
    public void checkAccess() throws RejectedException {
        if (pluginList.isEmpty()) {
            return;
        }

        for (P plugin : pluginList) {
            try {
                plugin.checkAccess();
            } catch (RejectedException ex) {
                throw ex;
            } catch (Exception ex) {
                ExceptionPrinter.printHistory(new CouldNotPerformException("Could not check registry access with RegistryPlugin[" + plugin + "]!", ex), logger, LogLevel.ERROR);
                assert !JPService.testMode(); // fail during unit tests.
            }
        }
    }

    @Override
    public void afterRegistryChange() throws CouldNotPerformException {
        if (pluginList.isEmpty() || lock.isWriteLockedByCurrentThread()) {
            return;
        }

        lock.writeLock().lock();
        try {
            for (P plugin : pluginList) {
                try {
                    plugin.afterRegistryChange();
                } catch (RejectedException ex) {
                    throw ex;
                } catch (Exception ex) {
                    ExceptionPrinter.printHistory(new CouldNotPerformException("Could not inform RegistryPlugin[" + plugin + "] about registry change!", ex), logger, LogLevel.ERROR);
                    assert !JPService.testMode(); // fail during unit tests. 
//                    break;
                }
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public void afterConsistencyCheck() throws CouldNotPerformException {
        if (pluginList.isEmpty() || lock.isWriteLockedByCurrentThread()) {
            return;
        }

        lock.writeLock().lock();
        try {
            for (P plugin : pluginList) {
                try {
                    plugin.afterConsistencyCheck();
                } catch (RejectedException ex) {
                    throw ex;
                } catch (Exception ex) {
                    ExceptionPrinter.printHistory(new CouldNotPerformException("Could not inform RegistryPlugin[" + plugin + "] about finished consistency check!", ex), logger, LogLevel.ERROR);
                    // ISSUE https://github.com/openbase/bco.registry/issues/11
                    // If the following line is activated some unit registry tests are failing during mockup registry init phase.
                    assert !JPService.testMode(); // fail during unit tests.
                    break; // please remove break after issue fix.  
                }
            }
        } finally {
            lock.writeLock().unlock();
        }
    }
}
