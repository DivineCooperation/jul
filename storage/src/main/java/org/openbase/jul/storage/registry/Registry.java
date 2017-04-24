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
import java.util.List;
import java.util.Map;
import org.openbase.jul.exception.CouldNotPerformException;
import org.openbase.jul.exception.RejectedException;
import org.openbase.jul.iface.Identifiable;
import org.openbase.jul.iface.Writable;
import org.openbase.jul.pattern.Observable;

/**
 *
 * @author <a href="mailto:divine@openbase.org">Divine Threepwood</a>
 * @param <KEY>
 * @param <ENTRY>
 */
public interface Registry<KEY, ENTRY extends Identifiable<KEY>> extends Writable, Observable<Map<KEY, ENTRY>> {

    public String getName();

    public ENTRY register(final ENTRY entry) throws CouldNotPerformException;

    public ENTRY update(final ENTRY entry) throws CouldNotPerformException;

    public ENTRY remove(final KEY key) throws CouldNotPerformException;

    public ENTRY remove(final ENTRY entry) throws CouldNotPerformException;

    public ENTRY get(final KEY key) throws CouldNotPerformException;

//    default public ENTRY get(final ENTRY entry) throws CouldNotPerformException {
//        return get(entry.getId());
//    }
    public ENTRY get(final ENTRY entry) throws CouldNotPerformException;

    public List<ENTRY> getEntries() throws CouldNotPerformException;

    public boolean contains(final ENTRY entry) throws CouldNotPerformException;

    public boolean contains(final KEY key) throws CouldNotPerformException;

    public void clear() throws CouldNotPerformException;

    public int size();

    public boolean isReadOnly();

    public boolean isConsistent();

    public boolean isSandbox();

    /**
     * This method checks if the registry is not handling any tasks and is currently consistent.
     *
     * @return Returns true if this registry is consistent and not busy.
     */
    public boolean isReady();

    /**
     * Return true if this registry does not contain any entries.
     *
     * @return is true if the registry is empty.
     */
    public boolean isEmpty();

    /**
     * Return true if this registry does not contain any entries.
     *
     * @return is true if the registry is empty.
     * @deprecated removed because of typo. Please use isEmpty() instead!
     */
    @Deprecated
//    default public boolean isEmtpy() {
//        return isEmpty();
//    }
    public boolean isEmtpy();

    public boolean tryLockRegistry() throws RejectedException;

    public void unlockRegistry();
}
