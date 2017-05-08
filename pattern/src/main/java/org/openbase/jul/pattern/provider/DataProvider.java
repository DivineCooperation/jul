package org.openbase.jul.pattern.provider;

/*-
 * #%L
 * JUL Pattern
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

import java8.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.openbase.jul.exception.CouldNotPerformException;
import org.openbase.jul.exception.NotAvailableException;
import org.openbase.jul.pattern.Observer;

/**
 * @author <a href="mailto:divine@openbase.org">Divine Threepwood</a>
 * @param <D>
 */
public interface DataProvider<D> {

    /**
     * Check if the data object is already available.
     *
     * @return if data is available
     */
    public boolean isDataAvailable();

    /**
     * Method returns the class of the data object.
     *
     * @return the class of the data object
     */
    public Class<D> getDataClass();
    
    /**
     * Method returns the data object of this instance.
     *
     * In case the data is not available a NotAvailableException is thrown.
     *
     * @return the data object.
     * @throws NotAvailableException is thrown in case the data is not available.
     */
    public D getData() throws NotAvailableException;

    
    /**
     * Returns a future of the data object. The future can be used to wait for the data object.
     *
     * @return a future object delivering the data if available.
     */
    public CompletableFuture<D> getDataFuture();
    
    /**
     * This method allows the registration of data observers to get informed about data updates.
     *
     * @param observer the observer added
     */
    public void addDataObserver(final Observer<D> observer);

    /**
     * This method removes already registered data observers.
     *
     * @param observer the observer removed
     */
    public void removeDataObserver(final Observer<D> observer);
    
    /**
     * Method blocks until an initial data is available.
     *
     * @throws CouldNotPerformException is thrown if any error occurs.
     * @throws InterruptedException is thrown in case the thread is externally interrupted.
     */
    public void waitForData() throws CouldNotPerformException, InterruptedException;

    /**
     * Method blocks until an initial data is available or the given timeout is reached.
     *
     * @param timeout maximal time to wait for the data. After the timeout is reached a NotAvailableException is thrown which is caused by a TimeoutException.
     * @param timeUnit the time unit of the timeout.
     * @throws NotAvailableException is thrown in case the any error occurs, or if the given timeout is reached. In this case a TimeoutException is thrown.
     * @throws InterruptedException is thrown in case the thread is externally interrupted.
     */
    public void waitForData(long timeout, TimeUnit timeUnit) throws CouldNotPerformException, InterruptedException;
}
