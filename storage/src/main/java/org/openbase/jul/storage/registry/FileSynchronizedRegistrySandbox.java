package org.openbase.jul.storage.registry;

/*
 * #%L
 * JUL Storage
 * $Id:$
 * $HeadURL:$
 * %%
 * Copyright (C) 2015 - 2016 openbase.org
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

import java.util.Map;
import org.openbase.jul.exception.CouldNotPerformException;
import org.openbase.jul.exception.NotAvailableException;
import org.openbase.jul.exception.NotSupportedException;
import org.openbase.jul.iface.Identifiable;
import org.openbase.jul.storage.registry.clone.RegistryCloner;
import org.openbase.jul.storage.registry.plugin.FileRegistryPlugin;

/**
 *
 * @author mpohling
 * @param <KEY>
 * @param <ENTRY>
 * @param <MAP>
 * @param <R>
 */
public class FileSynchronizedRegistrySandbox<KEY, ENTRY extends Identifiable<KEY>, MAP extends Map<KEY, ENTRY>, R extends FileSynchronizedRegistryInterface<KEY, ENTRY, R>> extends RegistrySandbox<KEY, ENTRY, MAP, R, FileRegistryPlugin<KEY, ENTRY>> implements FileSynchronizedRegistryInterface<KEY, ENTRY, R> {

    public FileSynchronizedRegistrySandbox(MAP entryMap, RegistryCloner<KEY, ENTRY, MAP> cloner) throws CouldNotPerformException, InterruptedException {
        super(entryMap, cloner);
    }

    public FileSynchronizedRegistrySandbox(final MAP entryMap) throws CouldNotPerformException, InterruptedException {
        super(entryMap);
    }

    @Override
    public void loadRegistry() throws CouldNotPerformException {
    }

    @Override
    public void saveRegistry() throws CouldNotPerformException {
    }

    @Override
    public Integer getDBVersion() throws NotAvailableException {
        throw new NotAvailableException("dbversion", new NotSupportedException("getDBVersion", this));
    }
}