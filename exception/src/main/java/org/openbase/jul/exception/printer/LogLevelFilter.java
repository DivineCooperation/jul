package org.openbase.jul.exception.printer;

/*-
 * #%L
 * JUL Exception
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
 * @author <a href="mailto:divine@openbase.org">Divine Threepwood</a>
 */
public class LogLevelFilter {

    /**
     * Method forces the debug log level if the {@code forceDebug} flag is {@code true}.
     * Otherwise the given log level is bypassed.
     *
     * @param logLevel the log level to return if the {@code forceDebug} is not true.
     * @param forceDebug the flag to force the debug mode.
     * @return the desired log level.
     */
    public static LogLevel getFilteredLogLevel(final LogLevel logLevel, final boolean forceDebug) {
        if (forceDebug) {
            return LogLevel.DEBUG;
        }
        return logLevel;
    }
}
