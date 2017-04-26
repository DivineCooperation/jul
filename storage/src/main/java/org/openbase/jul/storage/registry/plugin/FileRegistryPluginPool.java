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

import java8.util.function.Consumer;
import java8.util.stream.StreamSupport;
import org.openbase.jul.exception.CouldNotPerformException;
import org.openbase.jul.exception.RejectedException;
import org.openbase.jul.exception.printer.ExceptionPrinter;
import org.openbase.jul.exception.printer.LogLevel;
import org.openbase.jul.iface.Identifiable;
import org.openbase.jul.storage.file.FileSynchronizer;

/**
 *
 * @author <a href="mailto:divine@openbase.org">Divine Threepwood</a>
 * @param <KEY>
 * @param <ENTRY>
 * @param <P>
 */
public class FileRegistryPluginPool<KEY, ENTRY extends Identifiable<KEY>, P extends FileRegistryPlugin<KEY, ENTRY>> extends RegistryPluginPool<KEY, ENTRY, P> implements FileRegistryPlugin<KEY, ENTRY>{

    @Override
    public void beforeRegister(ENTRY entry, FileSynchronizer fileSynchronizer) throws RejectedException {
        for (P plugin : pluginList) {
            try {
                plugin.beforeRegister(entry, fileSynchronizer);
            } catch (RejectedException ex) {
                throw ex;
            } catch (Exception ex) {
                ExceptionPrinter.printHistory(new CouldNotPerformException("Could not inform RegistryPlugin[" + plugin + "] about planned Entry[" + entry + "] registration!", ex), logger, LogLevel.ERROR);
            }
        }
    }

    @Override
    public void afterRegister(ENTRY entry, FileSynchronizer fileSynchronizer) throws CouldNotPerformException {
        StreamSupport.stream(pluginList).forEach(new Consumer<P>() {
            @Override
            public void accept(P plugin) {
                try {
                    plugin.afterRegister(entry, fileSynchronizer);
                } catch (Exception ex) {
                    ExceptionPrinter.printHistory(new CouldNotPerformException("Could not inform RegistryPlugin[" + plugin + "] about successfully Entry[" + entry + "] registration!", ex), logger, LogLevel.ERROR);
                }
            }
        });
    }

    @Override
    public void beforeRemove(ENTRY entry, FileSynchronizer fileSynchronizer) throws RejectedException {
        for (P plugin : pluginList) {
            try {
                plugin.beforeRemove(entry, fileSynchronizer);
            } catch (RejectedException ex) {
                throw ex;
            } catch (Exception ex) {
                ExceptionPrinter.printHistory(new CouldNotPerformException("Could not inform RegistryPlugin[" + plugin + "] about planned Entry[" + entry + "] removal!", ex), logger, LogLevel.ERROR);
            }
        }
    }

    @Override
    public void afterRemove(ENTRY entry, FileSynchronizer fileSynchronizer) throws CouldNotPerformException {
        StreamSupport.stream(pluginList).forEach(new Consumer<P>() {
            @Override
            public void accept(P plugin) {
                try {
                    plugin.afterRemove(entry, fileSynchronizer);
                } catch (Exception ex) {
                    ExceptionPrinter.printHistory(new CouldNotPerformException("Could not inform RegistryPlugin[" + plugin + "] about successfully Entry[" + entry + "] removal!", ex), logger, LogLevel.ERROR);
                }
            }
        });
    }

    @Override
    public void beforeUpdate(ENTRY entry, FileSynchronizer fileSynchronizer) throws RejectedException {
        for (P plugin : pluginList) {
            try {
                plugin.beforeUpdate(entry, fileSynchronizer);
            } catch (RejectedException ex) {
                throw ex;
            } catch (Exception ex) {
                ExceptionPrinter.printHistory(new CouldNotPerformException("Could not inform RegistryPlugin[" + plugin + "] about planned Entry[" + entry + "] update!", ex), logger, LogLevel.ERROR);
            }
        }
    }

    @Override
    public void afterUpdate(ENTRY entry, FileSynchronizer fileSynchronizer) throws CouldNotPerformException {
         StreamSupport.stream(pluginList).forEach(new Consumer<P>() {
             @Override
             public void accept(P plugin) {
                 try {
                     plugin.afterUpdate(entry, fileSynchronizer);
                 } catch (Exception ex) {
                     ExceptionPrinter.printHistory(new CouldNotPerformException("Could not inform RegistryPlugin[" + plugin + "] about successfully Entry[" + entry + "] update!", ex), logger, LogLevel.ERROR);
                 }
             }
         });
    }

    @Override
    public void beforeGet(KEY key, FileSynchronizer fileSynchronizer) throws RejectedException {
        for (P plugin : pluginList) {
            try {
                plugin.beforeGet(key, fileSynchronizer);
            } catch (RejectedException ex) {
                throw ex;
            } catch (Exception ex) {
                ExceptionPrinter.printHistory(new CouldNotPerformException("Could not inform RegistryPlugin[" + plugin + "] about planned Entry[" + key + "] publishment!", ex), logger, LogLevel.ERROR);
            }
        }
    }
}
