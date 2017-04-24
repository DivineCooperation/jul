package org.openbase.jul.storage.registry.version;

/*-
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

import com.google.gson.JsonObject;
import org.openbase.jul.exception.CouldNotPerformException;
import org.openbase.jul.exception.NotSupportedException;

import java.io.File;
import java.util.Map;

/**
 *
 * @author <a href="mailto:mpohling@cit-ec.uni-bielefeld.de">Divine Threepwood</a>
 */
public abstract class AbstractGlobalDBVersionConverter extends AbstractDBVersionConverter implements GlobalDBVersionConverter {

    public AbstractGlobalDBVersionConverter(DBVersionControl versionControl) {
        super(versionControl);
    }

    public JsonObject upgrade(final JsonObject outdatedDBEntry, final Map<File, JsonObject> dbSnapshot, final Map<String, Map<File, DatabaseEntryDescriptor>> globalDbSnapshots) throws CouldNotPerformException {
        return upgrade(outdatedDBEntry, dbSnapshot);
    }

    @Override
    public JsonObject upgrade(final JsonObject outdatedDBEntry, final Map<File, JsonObject> dbSnapshot) throws CouldNotPerformException {
        throw new NotSupportedException("upgrade", this);
    }

}
