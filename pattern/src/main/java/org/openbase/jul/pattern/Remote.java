package org.openbase.jul.pattern;

/*
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
import org.openbase.jul.iface.Activatable;
import org.openbase.jul.iface.Lockable;
import org.openbase.jul.iface.Shutdownable;
import org.openbase.jul.pattern.provider.DataProvider;

/**
 *
 * @author <a href="mailto:divine@openbase.org">Divine Threepwood</a>
 * @param <M> the data type of the remote
 */
public interface Remote<M> extends Shutdownable, Activatable, Lockable, DataProvider<M> {

    // TODO mpohling: Should be moved to rst and reimplement for rsb 15.
    public enum ConnectionState {

        UNKNOWN, CONNECTING, CONNECTED, DISCONNECTED
    };

    /**
     * Method activates the remote instance and blocks until the first data synchronization is done.
     *
     * Equivalent of: activate(); waitForData(0);
     *
     * Caution: Method can blocks forever if the related main controller instance will be never available!
     *
     * @param waitForData if this flag is true the method will block until the first data synchronization is done.
     * @throws CouldNotPerformException if the activation could not be performed
     * @throws InterruptedException if the activation is interrupted
     */
    public void activate(boolean waitForData) throws CouldNotPerformException, InterruptedException;

    /**
     * This method allows the registration of connection state observers to get informed about connection state changes.
     *
     * @param observer the observer added
     */
    public void addConnectionStateObserver(final Observer<ConnectionState> observer);

    /**
     * This method removes already registered connection state observers.
     *
     * @param observer the observer removed
     */
    public void removeConnectionStateObserver(final Observer<ConnectionState> observer);

    /**
     * Method returns the class of the data object.
     *
     * @return the class of the data object
     */
    @Override
    public Class<M> getDataClass();

    /**
     * Method returns the data object of this remote which is synchronized with
     * the server data in background.
     *
     * In case the data was never received not available a NotAvailableException is thrown.
     * Use method getDataFuture() to get feedback about the synchronization state, or use method waitForData() to block until the needed data is synchronized.
     *
     * @return the data object of the remote.
     * @throws NotAvailableException is thrown in case the data is not yet synchronized with the main controller instance.
     */
    @Override
    public M getData() throws NotAvailableException;

    /**
     * Returns a future of the data object. This method can be useful after
     * remote initialization in case the data object was not received jet. The
     * future can be used to wait for the data object.
     *
     * @return a future object delivering the data if available.
     * @throws CouldNotPerformException In case something went wrong a
     * CouldNotPerformException is thrown.
     */
    public CompletableFuture<M> getDataFuture() throws CouldNotPerformException;
//    public default CompletableFuture<M> getDataFuture() throws CouldNotPerformException {
//        try {
//            if (!isDataAvailable()) {
//                return requestData();
//            }
//            return CompletableFuture.completedFuture(getData());
//        } catch (CouldNotPerformException ex) {
//            throw new NotAvailableException("data", ex);
//        }
//    }

    /**
     * Method blocks until an initial data message was received from the remote controller.
     *
     * @throws CouldNotPerformException is thrown if any error occurs.
     * @throws InterruptedException is thrown in case the thread is externally interrupted.
     */
    @Override
    public void waitForData() throws CouldNotPerformException, InterruptedException;

    /**
     * Method blocks until an initial data message was received from the main controller or the given timeout is reached.
     *
     * @param timeout maximal time to wait for the main controller data. After the timeout is reached a NotAvailableException is thrown which is caused by a TimeoutException.
     * @param timeUnit the time unit of the timeout.
     * @throws CouldNotPerformException is thrown in case the any error occurs, or if the given timeout is reached. In this case a TimeoutException is thrown.
     * @throws InterruptedException is thrown in case the thread is externally interrupted.
     */
    @Override
    public void waitForData(long timeout, TimeUnit timeUnit) throws CouldNotPerformException, InterruptedException;

    /**
     * Checks if a server connection is established.
     *
     * @return is true in case the connection is established.
     */
    public boolean isConnected();

    /**
     * Method returns the current connection state between this remote and its main controller.
     *
     * @return the current connection state.
     */
    public ConnectionState getConnectionState();

    /**
     * This method synchronizes this remote instance with the main controller
     * and returns the new data object. Normally, all server data changes are
     * automatically synchronized to all remote instances. In case you have
     * triggered many server changes, this changes are sequentially applied.
     * With this method you can force the sync to get instantly a data object
     * with all applied changes. This action can not be canceled! Use this
     * method with caution because high frequently calls will reduce the network
     * performance! The preferred by to access the data object
     *
     * @return A CompletableFuture which gives feedback about the successful
     * synchronization.
     * @throws CouldNotPerformException In case the sync could not be triggered
     * an CouldNotPerformException will be thrown.
     */
    public CompletableFuture<M> requestData() throws CouldNotPerformException;
}
