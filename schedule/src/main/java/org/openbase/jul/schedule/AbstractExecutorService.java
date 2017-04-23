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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.openbase.jps.core.JPService;
import org.openbase.jps.exception.JPNotAvailableException;
import org.openbase.jps.preset.JPDebugMode;
import org.openbase.jul.exception.CouldNotPerformException;
import org.openbase.jul.exception.MultiException;
import org.openbase.jul.exception.NotAvailableException;
import org.openbase.jul.exception.printer.ExceptionPrinter;
import org.openbase.jul.iface.Processable;
import org.openbase.jul.iface.Shutdownable;
import static org.openbase.jul.schedule.GlobalScheduledExecutorService.getInstance;

import org.openbase.jul.iface.Shutdownable$;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author <a href="mailto:divine@openbase.org">Divine Threepwood</a>
 * @param <ES> The internal execution service.
 */
public abstract class AbstractExecutorService<ES extends ThreadPoolExecutor> implements Shutdownable {

    /**
     * Default shutdown delay in milliseconds.
     */
    public static final long DEFAULT_SHUTDOWN_DELAY = 3000;

    /**
     * Default shutdown time in milliseconds.
     */
    public static final long DEFAULT_SHUTDOWN_TIME = 30000;

    /**
     * Default shutdown time in milliseconds.
     */
    public static final long SMART_SHUTDOWN_TIMEOUT = 30000;

    /**
     * The rate for printing feedback if the shutdown is delayed.
     */
    public static final long SMART_SHUTDOWN_STATUS_PRINT_RATE = 1000;

    /**
     * Report rate for the debug mode in milliseconds.
     */
    public static final long DEFAULT_REPORT_RATE = 60000;

    /**
     * The ratio of the threads which can be used until pool overload warnings are periodically printed.
     */
    public static final double DEFAULT_WARNING_RATIO = .9d;

    /**
     * The internally used executor service.
     */
    protected final ES executorService;

    protected final Logger logger = LoggerFactory.getLogger(getClass());

    public AbstractExecutorService(final ES executorService) {
        this.executorService = executorService;
        this.initReportService();
        Shutdownable$.registerShutdownHook(this, DEFAULT_SHUTDOWN_DELAY);
    }

    private Runnable initReportService() {
        final Runnable reportService = () -> {
            try {
                final boolean overload;
                if (executorService.getActiveCount() == executorService.getMaximumPoolSize()) {
                    overload = true;
                    logger.warn("Further tasks will be rejected because executor service overload is detected!");
                } else if (executorService.getActiveCount() >= ((double) executorService.getMaximumPoolSize() * DEFAULT_WARNING_RATIO)) {
                    overload = true;
                    logger.warn("High Executor service load detected! This can cause system instability issues!");
                } else {
                    overload = false;
                }

                if (overload || JPService.getProperty(JPDebugMode.class).getValue()) {
                    logger.info("Executor load " + getExecutorLoad() + "% [" + executorService.getActiveCount() + " of " + executorService.getMaximumPoolSize() + " threads processing " + (executorService.getTaskCount() - executorService.getCompletedTaskCount()) + " tasks] in total " + executorService.getCompletedTaskCount() + " are completed.");
                }
            } catch (JPNotAvailableException ex) {
                logger.warn("Could not detect debug mode!", ex);
            }
        };
        final ScheduledExecutorService scheduledExecutorService;
        scheduledExecutorService = executorService instanceof ScheduledExecutorService ? ((ScheduledExecutorService) executorService) : GlobalScheduledExecutorService.getInstance().getExecutorService();
        scheduledExecutorService.scheduleAtFixedRate(reportService, DEFAULT_REPORT_RATE, DEFAULT_REPORT_RATE, TimeUnit.MILLISECONDS);
        return reportService;
    }

    public int getExecutorLoad() {
        if (executorService.getMaximumPoolSize() == 0) {
            if (executorService.getActiveCount() == 0) {
                return 0;
            } else {
                return 100;
            }
        }
        return ((int) (((double) executorService.getActiveCount() / (double) executorService.getMaximumPoolSize()) * 100));
    }

    public <T> Future<T> internalSubmit(Callable<T> task) {
        return executorService.submit(task);
    }

    public Future<?> internalSubmit(Runnable task) {
        return executorService.submit(task);
    }

    public void internalExecute(final Runnable runnable) {
        executorService.execute(runnable);
    }

    public ES getExecutorService() {
        return executorService;
    }

    @Override
    public void shutdown() {
        smartShutdown();
    }

    public void smartShutdown() {
        long timeout = SMART_SHUTDOWN_TIMEOUT;
        int lastTaskCount = Integer.MAX_VALUE;
        while (getExecutorService().getActiveCount() != 0) {

            if (getExecutorService().getActiveCount() >= lastTaskCount) {
                timeout -= SMART_SHUTDOWN_STATUS_PRINT_RATE;
            } else {
                logger.info("Waiting for " + getExecutorService().getActiveCount() + " tasks to continue the shutdown.");
            }

            if (timeout <= 0) {
                logger.warn("Smart shutdown timeout reached!");
                break;
            }

            lastTaskCount = getExecutorService().getActiveCount();
            try {
                Thread.sleep(SMART_SHUTDOWN_STATUS_PRINT_RATE);
            } catch (InterruptedException ex) {
                logger.warn("Smart shutdown skipped!");
                break;
            }
        }
        shutdown(DEFAULT_SHUTDOWN_TIME, TimeUnit.MILLISECONDS);
    }

    public void shutdown(final long shutdownTimeout, final TimeUnit timeUnit) {
        logger.debug("Shutdown global executor service...");

        final int activeCount = getExecutorService().getActiveCount();
        if (activeCount != 0) {
            logger.info("Global executor shutdown forced: " + activeCount + " tasks will be skipped...");
        }

        List<Runnable> droppedTasks = executorService.shutdownNow();
        if (!droppedTasks.isEmpty()) {
            logger.debug(droppedTasks.size() + " tasks dropped!");
        }
        try {
            if (!executorService.awaitTermination(shutdownTimeout, timeUnit)) {
                logger.error("Executor did not terminate before shutdown Timeout[" + shutdownTimeout + " " + timeUnit.name().toLowerCase() + "] expired!");
                forceShutdown();
            }
        } catch (InterruptedException ex) {
            forceShutdown();
            Thread.currentThread().interrupt();
        }
    }

    public void forceShutdown() {
        for (int i = 0; i < 10; ++i) {
            executorService.shutdownNow();
            try {
                Thread.sleep(100);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
        }
    }

    /**
     * This method applies an error handler to the given future object.
     * In case the given timeout is expired or the future processing fails the error processor is processed with the occurred exception as argument.
     * The receive a future should be submitted to any execution service or handled externally.
     *
     * @param future the future on which is the error processor is registered.
     * @param timeout the timeout.
     * @param errorProcessor the processable which handles thrown exceptions
     * @param timeUnit the unit of the timeout.
     * @param executorService the execution service to apply the handler.
     * @return the future of the error handler.
     * @throws CouldNotPerformException thrown by the errorProcessor
     */
    public static Future applyErrorHandling(final Future future, final Processable<Exception, Void> errorProcessor, final long timeout, final TimeUnit timeUnit, final ExecutorService executorService) throws CouldNotPerformException {
        return executorService.submit(() -> {
            try {
                future.get(timeout, timeUnit);
            } catch (ExecutionException | InterruptedException | TimeoutException ex) {
                errorProcessor.process(ex);
            }
            return null;
        });
    }

    /**
     * This method applies an error handler to the given future object.
     * In case the given timeout is expired or the future processing fails the error processor is processed with the occured exception as argument.
     * The receive a future should be submitted to any execution service or handled externally.
     *
     * @param future the future on which is the error processor is registered.
     * @param timeout the timeout.
     * @param errorProcessor the processable which handles thrown exceptions
     * @param timeUnit the unit of the timeout.
     * @return the future of the error handler.
     * @throws CouldNotPerformException thrown by the errorProcessor
     */
    public static Future applyErrorHandling(final Future future, final Processable<Exception, Void> errorProcessor, final long timeout, final TimeUnit timeUnit) throws CouldNotPerformException {
        return AbstractExecutorService.applyErrorHandling(future, errorProcessor, timeout, timeUnit, getInstance().getExecutorService());
    }

    /**
     *
     * @param <I>
     * @param taskProcessor
     * @param inputList
     * @param executorService
     * @return
     * @deprecated Please use allOf(final ExecutorService executorService, final Collection inputList, final Processable taskProcessor) instead.
     */
    @Deprecated
    public static <I> Future<Void> allOf(final Processable<I, Future<Void>> taskProcessor, final Collection<I> inputList, final ExecutorService executorService) {
        return allOf(executorService, inputList, taskProcessor);
    }

    public static <I> Future<Void> allOf(final ExecutorService executorService, final Collection<I> inputList, final Processable<I, Future<Void>> taskProcessor) {
        return allOf(executorService, inputList, (Collection<Future<Void>> input) -> null, taskProcessor);
    }

    public static <I> Future<Void> allOf(final Collection<I> inputList, final Processable<I, Future<Void>> taskProcessor) throws CouldNotPerformException {
        return allOf(getInstance().getExecutorService(), inputList, (Collection<Future<Void>> input) -> null, taskProcessor);
    }

    public static <I, O, R> Future<R> allOf(final Collection<I> inputList, final Processable<Collection<Future<O>>, R> resultProcessor, final Processable<I, Future<O>> taskProcessor) throws CouldNotPerformException, InterruptedException {
        return allOf(getInstance().getExecutorService(), inputList, resultProcessor, taskProcessor);
    }

    public static Future<Void> allOf(final Future... futures) {
        return allOf(Arrays.asList(futures));
    }

    public static Future<Void> allOf(final Callable resultCallable, final Future... futures) {
        return allOf(getInstance().getExecutorService(), resultCallable, Arrays.asList(futures));
    }

    public static <R> Future<R> allOfInclusiveResultFuture(final Future<R> resultFuture, final Future... futures) {
        final List<Future> futureList = Arrays.asList(futures);

        // Add result future to future list to make sure that the result is available if requested.
        futureList.add(resultFuture);

        return allOf(getInstance().getExecutorService(), () -> resultFuture.get(), futureList);
    }

    public static Future<Void> allOf(final Collection<Future> futureCollection) {
        return allOf(getInstance().getExecutorService(), () -> null, futureCollection);
    }

    /**
     *
     * @param <R>
     * @param futureCollection
     * @param returnValue
     * @return
     * @deprecated please use allOf(final T returnValue, final Collection futureCollection) instead.
     */
    @Deprecated
    public static <R> Future<R> allOf(final Collection<Future> futureCollection, R returnValue) {
        return allOf(returnValue, futureCollection);
    }

    public static <R> Future<R> allOf(final R returnValue, final Collection<Future> futureCollection) {
        return allOf(getInstance().getExecutorService(), returnValue, futureCollection);
    }

    /**
     *
     * @param <R>
     * @param futureCollection
     * @param returnValue
     * @param executorService
     * @return
     * @deprecated please use allOf(final ExecutorService executorService, T returnValue, final Collection futureCollection) instead.
     */
    @Deprecated
    public static <R> Future<R> allOf(final Collection<Future> futureCollection, R returnValue, final ExecutorService executorService) {
        return allOf(executorService, returnValue, futureCollection);
    }

    public static <R> Future<R> allOf(final ExecutorService executorService, R returnValue, final Collection<Future> futureCollection) {
        return allOf(executorService, () -> returnValue, futureCollection);
    }

    /**
     *
     * @param <I>
     * @param <O>
     * @param <R>
     * @param taskProcessor
     * @param resultProcessor
     * @param inputList
     * @param executorService
     * @return
     * @deprecated Please use allOf(final ExecutorService executorService, final Processable resultProcessor, final Collection inputList, final Processable taskProcessor) throws CouldNotPerformException, InterruptedException { instead.
     */
    public static <I, O, R> Future<R> allOf(final Processable<I, Future<O>> taskProcessor, final Processable<Collection<Future<O>>, R> resultProcessor, final Collection<I> inputList, final ExecutorService executorService) {
        return allOf(executorService, inputList, resultProcessor, taskProcessor);
    }

    public static <I, O, R> Future<R> allOf(final ExecutorService executorService, final Collection<I> inputList, final Processable<Collection<Future<O>>, R> resultProcessor, final Processable<I, Future<O>> taskProcessor) {
        return executorService.submit(new Callable<R>() {
            @Override
            public R call() throws Exception {
                try {
                    MultiException.ExceptionStack exceptionStack = null;
                    Collection<Future<O>> futureCollection = buildFutureCollection(inputList, taskProcessor);

                    try {
                        for (final Future<O> future : futureCollection) {
                            try {
                                future.get();
                            } catch (ExecutionException ex) {
                                exceptionStack = MultiException.push(this, ex, exceptionStack);
                            }
                        }
                    } catch (InterruptedException ex) {
                        // cancel all pending actions.
                        futureCollection.stream().forEach((future) -> {
                            future.cancel(true);
                        });
                        throw ex;
                    }
                    MultiException.checkAndThrow("Could not execute all tasks!", exceptionStack);
                    return resultProcessor.process(futureCollection);
                } catch (InterruptedException ex) {
                    throw ex;
                } catch (CouldNotPerformException ex) {
                    throw ExceptionPrinter.printHistoryAndReturnThrowable(new CouldNotPerformException("Task execution failed!", ex), LoggerFactory.getLogger(AbstractExecutorService.class));
                }
            }
        });
    }

    /**
     * Method generates a new futures which represents all futures provided by the futureCollection. If all futures are successfully finished the outer future will be completed with the result provided by the resultCallable.
     *
     * @param <R> The result type of the outer future.
     * @param resultCallable the callable which provides the result of the outer future.
     * @param futureCollection the inner future collection.
     * @return the outer future.
     */
    public static <R> Future<R> allOf(final Callable<R> resultCallable, final Collection<Future> futureCollection) {
        return allOf(getInstance().getExecutorService(), resultCallable, futureCollection);
    }

    /**
     * Method generates a new futures which represents all futures provided by the futureCollection. If all futures are successfully finished the outer future will be completed with the result provided by the resultCallable.
     *
     * @param <R> The result type of the outer future.
     * @param executorService the execution service which is used for the outer future execution.
     * @param resultCallable the callable which provides the result of the outer future.
     * @param futureCollection the inner future collection.
     * @return the outer future.
     */
    public static <R> Future<R> allOf(final ExecutorService executorService, final Callable<R> resultCallable, final Collection<Future> futureCollection) {
        return executorService.submit(new Callable<R>() {
            @Override
            public R call() throws Exception {
                try {
                    MultiException.ExceptionStack exceptionStack = null;
                    try {
                        for (final Future future : futureCollection) {
                            try {
                                future.get();
                            } catch (ExecutionException ex) {
                                exceptionStack = MultiException.push(this, ex, exceptionStack);
                            }
                        }
                    } catch (InterruptedException ex) {
                        // cancel all pending actions.
                        futureCollection.stream().forEach((future) -> {
                            future.cancel(true);
                        });
                        throw ex;
                    }
                    MultiException.checkAndThrow("Could not execute all tasks!", exceptionStack);
                    if (resultCallable == null) {
                        throw new NotAvailableException("resultCallable");
                    }
                    return resultCallable.call();
                } catch (CouldNotPerformException | InterruptedException ex) {
                    throw ex;
                } catch (Exception ex) {
                    throw ExceptionPrinter.printHistoryAndReturnThrowable(new CouldNotPerformException("Task execution failed!", ex), LoggerFactory.getLogger(AbstractExecutorService.class));
                }
            }
        });
    }

    /**
     * Method generates a new futures which represents all futures provided by the futureCollection. If all futures are successfully finished the outer future will be completed with the result provided by the resultProcessor.
     *
     * Node: For this method it's important that all futures provided by the future collection provide the same result type.
     *
     * @param <O> The output or result type of the futures provided by the future collection.
     * @param <R> The result type of the outer future.
     * @param resultProcessor the processor which provides the outer future result.
     * @param futureCollection the inner future collection.
     * @return the outer future.
     */
    public static <O, R> Future<R> allOf(final Processable<Collection<Future<O>>, R> resultProcessor, final Collection<Future<O>> futureCollection) {
        return allOf(getInstance().getExecutorService(), resultProcessor, futureCollection);
    }

    /**
     * Method generates a new futures which represents all futures provided by the futureCollection. If all futures are successfully finished the outer future will be completed with the result provided by the resultProcessor.
     *
     * Node: For this method it's important that all futures provided by the future collection provide the same result type.
     *
     * @param <O> The output or result type of the futures provided by the future collection.
     * @param <R> The result type of the outer future.
     * @param executorService the execution service which is used for the outer future execution.
     * @param resultProcessor the processor which provides the outer future result.
     * @param futureCollection the inner future collection.
     * @return the outer future.
     */
    public static <O, R> Future<R> allOf(final ExecutorService executorService, final Processable<Collection<Future<O>>, R> resultProcessor, final Collection<Future<O>> futureCollection) {
        return executorService.submit(new Callable<R>() {
            @Override
            public R call() throws Exception {
                try {
                    MultiException.ExceptionStack exceptionStack = null;
                    try {
                        for (final Future<O> future : futureCollection) {
                            try {
                                future.get();
                            } catch (ExecutionException ex) {
                                exceptionStack = MultiException.push(this, ex, exceptionStack);
                            }
                        }
                    } catch (InterruptedException ex) {
                        // cancel all pending actions.
                        futureCollection.stream().forEach((future) -> {
                            future.cancel(true);
                        });
                        throw ex;
                    }
                    MultiException.checkAndThrow("Could not execute all tasks!", exceptionStack);
                    return resultProcessor.process(futureCollection);
                } catch (CouldNotPerformException | InterruptedException ex) {
                    throw ex;
                } catch (Exception ex) {
                    throw ExceptionPrinter.printHistoryAndReturnThrowable(new CouldNotPerformException("Task execution failed!", ex), LoggerFactory.getLogger(AbstractExecutorService.class));
                }
            }
        });
    }

    /**
     * Method builds a future collection with the given task processor. The input list is passed to the future build process so the input is available during the build.
     *
     * @param <I> The type of the input value used for the future build.
     * @param <O> The type of the output value which the futures provide.
     * @param inputList the input list which is needed for the build process.
     * @param taskProcessor the task processor to build the futures.
     * @return the collection of all builded future instances.
     * @throws CouldNotPerformException is thrown if the future collection could not be generated.
     * @throws InterruptedException is thrown if the thread was externally interrupted.
     */
    public static <I, O> Collection<Future<O>> buildFutureCollection(final Collection<I> inputList, final Processable<I, Future<O>> taskProcessor) throws CouldNotPerformException, InterruptedException {
        try {
            MultiException.ExceptionStack exceptionStack = null;
            List<Future<O>> futureList = new ArrayList<>();
            for (final I input : inputList) {
                try {
                    futureList.add(taskProcessor.process(input));
                } catch (final CouldNotPerformException ex) {
                    exceptionStack = MultiException.push(AbstractExecutorService.class, ex, exceptionStack);
                }
            }
            MultiException.checkAndThrow("Could not execute all tasks!", exceptionStack);
            return futureList;
        } catch (CouldNotPerformException | InterruptedException ex) {
            throw ex;
        } catch (Exception ex) {
            throw ExceptionPrinter.printHistoryAndReturnThrowable(new CouldNotPerformException("Could not build future collection!", ex), LoggerFactory.getLogger(AbstractExecutorService.class));
        }
    }

    /**
     * Simply prints the class name.
     *
     * @return the class name of this instance.
     */
    @Override
    public String toString() {
        return getClass().getSimpleName();
    }
}
