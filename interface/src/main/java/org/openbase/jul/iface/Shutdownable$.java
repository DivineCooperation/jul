package org.openbase.jul.iface;

import org.openbase.jul.exception.printer.ExceptionPrinter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/*
 * #%L
 * JUL Interface
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

/**
 *
 * @author <a href="mailto:divine@openbase.org">Divine Threepwood</a>
 */
public class Shutdownable$ {

    /**
     * Method registers a runtime shutdown hook for the given Shutdownable.
     * In case the application is finalizing the shutdown method of the Shutdownable will be invoked.
     *
     * @param shutdownable the instance which is automatically shutting down in case the application is finalizing.
     */
    public static void registerShutdownHook(final Shutdownable shutdownable) {
        Runtime.getRuntime().addShutdownHook(new ShutdownDeamon(shutdownable, 0));
    }

    /**
     * Method registers a runtime shutdown hook for the given Shutdownable.
     * In case the application is finalizing the shutdown method of the Shutdownable will be invoked.
     * The given delay can be used to delay the shutdown.
     *
     * Note: This method should be used with care because to delay the shutdown process can result in skipping the shutdown method call in case the operating system mark this application as not responding.
     *
     * @param shutdownable the instance which is automatically shutting down in case the application is finalizing.
     * @param shutdownDelay this time in milliseconds defines the delay of the shutdown after the application shutdown was initiated.
     */
    public static void registerShutdownHook(final Shutdownable shutdownable, final long shutdownDelay) {
        Runtime.getRuntime().addShutdownHook(new ShutdownDeamon(shutdownable, shutdownDelay));
    }

}
