package org.openbase.jul.schedule;

/*
 * #%L
 * JUL Schedule
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
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.openbase.jul.exception.CouldNotPerformException;
import org.openbase.jul.exception.NotAvailableException;

/**
 *
 * @author <a href="mailto:divine@openbase.org">Divine Threepwood</a>
 */
public class GlobalScheduledExecutorService extends AbstractExecutorService<ScheduledThreadPoolExecutor> {

    /**
     * Keep alive time in milli seconds.
     */
    public static final long DEFAULT_KEEP_ALIVE_TIME = 60000;

    /**
     * The default maximal pool size. If this thread amount is reached further tasks will be rejected.
     */
    public static final int DEFAULT_MAX_POOL_SIZE = 100;

    /**
     * The core thread pool size.
     */
    public static final int DEFAULT_CORE_POOL_SIZE = 10;

    private static GlobalScheduledExecutorService instance;

    GlobalScheduledExecutorService() {
        super((ScheduledThreadPoolExecutor) Executors.newScheduledThreadPool(DEFAULT_CORE_POOL_SIZE));

        // configure executor service
        executorService.setKeepAliveTime(DEFAULT_KEEP_ALIVE_TIME, TimeUnit.MILLISECONDS);
        executorService.setMaximumPoolSize(DEFAULT_MAX_POOL_SIZE);
    }

    static synchronized GlobalScheduledExecutorService getInstance() {
        if (instance == null) {
            instance = new GlobalScheduledExecutorService();
        }
        return instance;
    }

    public static <T> Future<T> submit(Callable<T> task) {
        return getInstance().internalSubmit(task);
    }

    public static Future<?> submit(Runnable task) {
        return getInstance().internalSubmit(task);
    }

    public static void execute(final Runnable runnable) {
        getInstance().internalExecute(runnable);
    }

    /**
     * @see java.util.concurrent.ScheduledExecutorService
     *
     * @param command the task to execute
     * @param delay the time from now to delay execution
     * @param unit the time unit of the delay parameter
     * @return a ScheduledFuture representing pending completion of the task and whose {@code get()} method will return {@code null} upon completion
     * @throws RejectedExecutionException if the task cannot be scheduled for execution
     */
    public static ScheduledFuture<?> schedule(Runnable command, long delay, TimeUnit unit) {
        return getInstance().executorService.schedule(command, delay, unit);
    }

    /**
     * @see java.util.concurrent.ScheduledExecutorService
     *
     * @param callable the function to execute
     * @param delay the time from now to delay execution
     * @param unit the time unit of the delay parameter
     * @param <V> the type of the callable's result
     * @return a ScheduledFuture that can be used to extract result or cancel
     * @throws RejectedExecutionException if the task cannot be scheduled for execution
     * @throws NotAvailableException if callable is null
     */
    public static <V> ScheduledFuture<V> schedule(Callable<V> callable, long delay, TimeUnit unit) throws RejectedExecutionException, CouldNotPerformException {
        if (callable == null) {
            throw new NotAvailableException("callable");
        }
        return getInstance().executorService.schedule(callable, delay, unit);
    }

    /**
     * @see java.util.concurrent.ScheduledExecutorService
     *
     * @param command the task to execute
     * @param initialDelay the time to delay first execution
     * @param period the period between successive executions
     * @param unit the time unit of the initialDelay and period parameters
     * @return a ScheduledFuture representing pending completion of the task, and whose {@code get()} method will throw an exception upon cancellation
     * @throws RejectedExecutionException if the task cannot be scheduled for execution
     * @throws NotAvailableException if command is null
     * @throws IllegalArgumentException if period less than or equal to zero
     */
    public static ScheduledFuture<?> scheduleAtFixedRate(Runnable command, long initialDelay, long period, TimeUnit unit) throws NotAvailableException, IllegalArgumentException, RejectedExecutionException {
        if (command == null) {
            throw new NotAvailableException("command");
        }
        return getInstance().executorService.scheduleAtFixedRate(command, initialDelay, period, unit);
    }

    /**
     * @see java.util.concurrent.ScheduledExecutorService
     *
     * @param command the task to execute
     * @param initialDelay the time to delay first execution
     * @param delay the delay between the termination of one execution and the commencement of the next
     * @param unit the time unit of the initialDelay and delay parameters
     * @return a ScheduledFuture representing pending completion of the task, and whose {@code get()} method will throw an exception upon cancellation
     * @throws RejectedExecutionException if the task cannot be scheduled for execution
     * @throws NotAvailableException if command is null
     * @throws IllegalArgumentException if delay less than or equal to zero
     */
    public static ScheduledFuture<?> scheduleWithFixedDelay(Runnable command, long initialDelay, long delay, TimeUnit unit) throws NotAvailableException, IllegalArgumentException, RejectedExecutionException {
        if (command == null) {
            throw new NotAvailableException("command");
        }
        return getInstance().executorService.scheduleWithFixedDelay(command, initialDelay, delay, unit);
    }
}
