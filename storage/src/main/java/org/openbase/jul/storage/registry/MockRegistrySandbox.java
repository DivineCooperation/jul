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
import java.util.concurrent.TimeUnit;
import org.openbase.jul.exception.CouldNotPerformException;
import org.openbase.jul.exception.NotAvailableException;
import org.openbase.jul.exception.RejectedException;
import org.openbase.jul.iface.Identifiable;
import org.openbase.jul.pattern.Observer;

/**
 *
 * @author <a href="mailto:divine@openbase.org">Divine Threepwood</a>
 * @param <KEY>
 * @param <ENTRY>
 * @param <MAP>
 * @param <R>
 */
public class MockRegistrySandbox<KEY, ENTRY extends Identifiable<KEY>, MAP extends Map<KEY, ENTRY>, R extends Registry<KEY, ENTRY>> implements RegistrySandbox<KEY, ENTRY, MAP, R> {

    private final Registry<KEY, ENTRY> registry;

    public MockRegistrySandbox(Registry<KEY, ENTRY> registry) {
        this.registry = registry;
    }

    @Override
    public String toString() {
        return getName();
    }

    @Override
    public ENTRY register(ENTRY entry) throws CouldNotPerformException {
        return entry;
    }

    @Override
    public ENTRY update(ENTRY entry) throws CouldNotPerformException {
        return entry;
    }

    @Override
    public void sync(MAP map) {
        // Not needed for mock sandbox!
    }

    @Override
    public void registerConsistencyHandler(ConsistencyHandler<KEY, ENTRY, MAP, R> consistencyHandler) throws CouldNotPerformException {
        // Not needed for mock sandbox!
    }

    @Override
    public void removeConsistencyHandler(ConsistencyHandler<KEY, ENTRY, MAP, R> consistencyHandler) throws CouldNotPerformException {
        // Not needed for mock sandbox!
    }

    @Override
    public ENTRY remove(ENTRY entry) throws CouldNotPerformException {
        return null;
    }

    @Override
    public ENTRY remove(KEY entry) throws CouldNotPerformException {
        return null;
    }

    @Override
    public ENTRY get(KEY key) throws CouldNotPerformException {
        throw new UnsupportedOperationException("Not supported for mock sandbox.");
    }

    @Override
    public List<ENTRY> getEntries() {
        throw new UnsupportedOperationException("Not supported for mock sandbox.");
    }

    @Override
    public boolean contains(ENTRY entry) throws CouldNotPerformException {
        throw new UnsupportedOperationException("Not supported for mock sandbox.");
    }

    @Override
    public boolean contains(KEY key) throws CouldNotPerformException {
        throw new UnsupportedOperationException("Not supported for mock sandbox.");
    }

    @Override
    public void clear() throws CouldNotPerformException {
        // Not needed for mock sandbox!
    }

    @Override
    public void checkWriteAccess() throws RejectedException {
        // Not needed for mock sandbox!
    }

    @Override
    public boolean isReadOnly() {
        return false;
    }

    @Override
    public void replaceInternalMap(Map<KEY, ENTRY> map) {
        // Not needed for mock sandbox!
    }

    @Override
    public int size() {
        throw new UnsupportedOperationException("Not supported for mock sandbox.");
    }

    @Override
    public String getName() {
        return getClass().getSimpleName();
    }

    @Override
    public ENTRY load(ENTRY entry) throws CouldNotPerformException {
        return entry;
    }

    @Override
    public boolean isConsistent() {
        return true;
    }

    @Override
    public void addObserver(Observer<Map<KEY, ENTRY>> observer) {
        throw new UnsupportedOperationException("Not supported for mock sandbox.");
    }

    @Override
    public void removeObserver(Observer<Map<KEY, ENTRY>> observer) {
        throw new UnsupportedOperationException("Not supported for mock sandbox.");
    }

    @Override
    public Map<KEY, ENTRY> getValue() throws NotAvailableException {
        throw new UnsupportedOperationException("Not supported for mock sandbox.");
    }

    @Override
    public boolean isValueAvailable() {
        return true;
    }
    
    @Override
    public void shutdown() {
        // Not needed for mock sandbox!
    }

    @Override
    public boolean isSandbox() {
        return true;
    }

    @Override
    public boolean isEmpty() {
        throw new UnsupportedOperationException("Not supported for mock sandbox.");
    }

    @Override
    public boolean isReady() {
        return true;
    }
    
    @Override
    public void waitForValue(long timeout, TimeUnit timeUnit) throws NotAvailableException, InterruptedException {
        throw new UnsupportedOperationException("Not supported for mock sandbox.");
    }

    @Override
    public boolean tryLockRegistry() throws RejectedException {
        throw new RejectedException("MockRegistrySandbox not lockable!");
    }

    @Override
    public void unlockRegistry() {
        // MockRegistrySandbox is not unlockable
    }

    // BEGIN DEFAULT INTERFACE METHODS
    public boolean isWritable() {
        try {
            checkWriteAccess();
        } catch (RejectedException ex) {
            return false;
        }
        return true;
    }
    // END DEFAULT INTERFACE METHODS
}
