package org.openbase.jul.exception.printer;

import org.slf4j.Logger;

/*
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
 *
 * @author <a href="mailto:divine@openbase.org">Divine Threepwood</a>
 */
public interface Printer {

    public void print(String message);

    public void print(String message, Throwable throwable);

    public boolean isDebugEnabled();

    /**
     * Method prints the given {@code message} on the given {@code logger} with the given {@code logLevel}.
     *
     * @param message the message to print.
     * @param logLevel the level to log the message.
     * @param logger the message logger.
     */
    public static void print(final String message, final LogLevel logLevel, final Logger logger) {
        print(message, null, logLevel, logger);
    }

    /**
     * Method prints the given {@code message} on the given {@code logger} with the given {@code logLevel}.
     *
     * @param message the message to print.
     * @param throwable a cause of the message to print. Will be ignored if null.
     * @param logLevel the level to log the message.
     * @param logger the message logger.
     */
    public static void print(final String message, final Throwable throwable, final LogLevel logLevel, final Logger logger) {
        if (throwable == null) {
            switch (logLevel) {
                case TRACE:
                    logger.trace(message);
                    break;
                case DEBUG:
                    logger.debug(message);
                    break;
                case INFO:
                    logger.info(message);
                    break;
                case WARN:
                    logger.warn(message);
                    break;
                case ERROR:
                    logger.error(message);
                    break;
            }
        } else {
            switch (logLevel) {
                case TRACE:
                    logger.trace(message, throwable);
                    break;
                case DEBUG:
                    logger.debug(message, throwable);
                    break;
                case INFO:
                    logger.info(message, throwable);
                    break;
                case WARN:
                    logger.warn(message, throwable);
                    break;
                case ERROR:
                    logger.error(message, throwable);
                    break;
            }
        }
    }
}
