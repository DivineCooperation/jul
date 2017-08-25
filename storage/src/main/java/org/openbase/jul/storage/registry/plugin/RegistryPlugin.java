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
import org.openbase.jul.exception.CouldNotPerformException;
import org.openbase.jul.exception.InitializationException;
import org.openbase.jul.exception.RejectedException;
import org.openbase.jul.iface.Identifiable;
import org.openbase.jul.iface.Initializable;
import org.openbase.jul.iface.Shutdownable;
import org.openbase.jul.storage.registry.Registry;

/**
 *
 * * @author <a href="mailto:MarianPohling@cit-ec.uni-bielefeld.de">mpohling</a>
 * @param <KEY>
 * @param <ENTRY>
 */
public interface RegistryPlugin<KEY, ENTRY extends Identifiable<KEY>> extends Initializable<Registry<KEY, ENTRY>>, Shutdownable {

    public void beforeRegister(final ENTRY entry) throws RejectedException;

    public void afterRegister(final ENTRY entry) throws CouldNotPerformException;

    public void beforeUpdate(final ENTRY entry) throws RejectedException;

    public void afterUpdate(final ENTRY entry) throws CouldNotPerformException;

    public void beforeRemove(final ENTRY entry) throws RejectedException;

    public void afterRemove(final ENTRY entry) throws CouldNotPerformException;

    public void afterConsistencyModification(final ENTRY entry) throws CouldNotPerformException;
    
    public void afterRegistryChange() throws CouldNotPerformException;

    public void afterConsistencyCheck() throws CouldNotPerformException;
    
    public void beforeClear() throws CouldNotPerformException;

    public void beforeGet(final KEY key) throws RejectedException;

    public void beforeGetEntries() throws CouldNotPerformException;

    public void checkAccess() throws RejectedException;

    @Override
    public void init(Registry<KEY, ENTRY> registry) throws InitializationException, InterruptedException;
    
}
