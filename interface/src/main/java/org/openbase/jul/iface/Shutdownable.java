package org.openbase.jul.iface;

import org.openbase.jul.exception.printer.ExceptionPrinter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/*
 * #%L
 * JUL Interface
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
/**
 *
 * @author <a href="mailto:divine@openbase.org">Divine Threepwood</a>
 */
public interface Shutdownable {

    /**
     * This method initializes the shutdown phrase of this instance.
     *
     * All resources will be released.
     * In case of any errors no exception will/should be thrown and the method will/should not block.
     * These behavior guarantees a proper component shutdown without skipping any parts because of exception handling.
     */
    public void shutdown();

    /**
     * Method registers a runtime shutdown hook for the given Shutdownable.
     * In case the application is finalizing the shutdown method of the Shutdownable will be invoked.
     *
     * @param shutdownable the instance which is automatically shutting down in case the application is finalizing.
     */
    static void registerShutdownHook(final Shutdownable shutdownable) {
        Runtime.getRuntime().addShutdownHook(new ShutdownDeamon(shutdownable));
    }

    class ShutdownDeamon extends Thread {

        private final static Logger LOGGER = LoggerFactory.getLogger(ShutdownDeamon.class);

        private final Shutdownable shutdownable;

        public ShutdownDeamon(final Shutdownable shutdownable) {
            super(ShutdownDeamon.class.getSimpleName() + "[" + shutdownable + "]");
            this.shutdownable = shutdownable;
        }

        @Override
        public void run() {
            try {
                shutdownable.shutdown();
            } catch (Exception ex) {
                ExceptionPrinter.printHistory("Could not shutdown " + shutdownable + "!", ex, LOGGER);
            }
        }
    }

}
