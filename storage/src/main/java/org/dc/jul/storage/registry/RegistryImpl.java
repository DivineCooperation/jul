/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.dc.jul.storage.registry;

/*
 * #%L
 * JUL Storage
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

import java.util.HashMap;
import org.dc.jul.exception.InstantiationException;
import org.dc.jul.iface.Identifiable;
import org.dc.jul.storage.registry.plugin.RegistryPlugin;

/**
 *
 * @author mpohling
 * @param <KEY>
 * @param <ENTRY>
 */
public class RegistryImpl<KEY, ENTRY extends Identifiable<KEY>> extends AbstractRegistry<KEY, ENTRY, HashMap<KEY, ENTRY>, RegistryImpl<KEY, ENTRY>, RegistryPlugin<KEY, ENTRY>> {

    public RegistryImpl(HashMap<KEY, ENTRY> entryMap) throws InstantiationException {
        super(entryMap);
    }

    public RegistryImpl() throws InstantiationException {
        super(new HashMap<>());
    }
}
