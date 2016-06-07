/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.dc.jul.schedule;

/*
 * #%L
 * JUL Schedule
 * %%
 * Copyright (C) 2015 - 2016 DivineCooperation
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
import java.util.Collection;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.dc.jul.exception.CouldNotPerformException;
import org.dc.jul.exception.MultiException;
import org.dc.jul.exception.printer.ExceptionPrinter;
import org.dc.jul.iface.Processable;
import org.dc.jul.iface.Shutdownable;
import org.slf4j.LoggerFactory;

/**
 *
 * @author divine
 */
public class GlobalExecutionService implements Shutdownable {

    protected final org.slf4j.Logger logger = LoggerFactory.getLogger(GlobalExecutionService.class);

    private static GlobalExecutionService instance;

    private final ExecutorService executionService;

    private GlobalExecutionService() {
        this.executionService = Executors.newCachedThreadPool();

        Runtime.getRuntime().addShutdownHook(new Thread() {

            @Override
            public void run() {
                if (instance != null) {
                    try {
                        instance.shutdown();
                    } catch (InterruptedException ex) {
                        ExceptionPrinter.printHistory(new CouldNotPerformException("ExecutionService shutdown was interruped!", ex), logger);
                        Thread.currentThread().interrupt();
                    }
                }
            }
        });
    }

    public static synchronized GlobalExecutionService getInstance() {
        if (instance == null) {
            instance = new GlobalExecutionService();
        }
        return instance;
    }

    public static <T> Future<T> submit(Callable<T> task) {
        return getInstance().executionService.submit(task);
    }

    public static Future<?> submit(Runnable task) {
        return getInstance().executionService.submit(task);
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
     * @throws CouldNotPerformException thrown by the errorProcessor
     */
    public static void applyErrorHandling(final Future future, final Processable<Exception, Void> errorProcessor, final long timeout, final TimeUnit timeUnit) throws CouldNotPerformException {
        GlobalExecutionService.submit(() -> {
            try {
                future.get(timeout, timeUnit);
            } catch (ExecutionException | InterruptedException | TimeoutException ex) {
                errorProcessor.process(ex);
            }
            return null;
        });
    }

    @Override
    public void shutdown() throws InterruptedException {
        executionService.shutdownNow();
    }

    public static <I> Future<Void> allOf(final Processable<I, Future<Void>> actionProcessor, final Collection<I> inputList) {
        return allOf(actionProcessor, (Collection<Future<Void>> input) -> null, inputList);
    }

    public static <I, O, R> Future<R> allOf(final Processable<I, Future<O>> actionProcessor, final Processable<Collection<Future<O>>, R> resultProcessor, final Collection<I> inputList) {
        return GlobalExecutionService.submit(new Callable<R>() {
            @Override
            public R call() throws Exception {
                MultiException.ExceptionStack exceptionStack = null;
                List<Future<O>> futureList = new ArrayList<>();
                for (I input : inputList) {
                    try {
                        futureList.add(actionProcessor.process(input));
                    } catch (CouldNotPerformException ex) {
                        exceptionStack = MultiException.push(this, ex, exceptionStack);
                    }
                }
                try {
                    for (Future<O> future : futureList) {
                        try {
                            future.get();
                        } catch (ExecutionException ex) {
                            exceptionStack = MultiException.push(this, ex, exceptionStack);
                        }
                    }
                } catch (InterruptedException ex) {
                    // cancel all pending actions.
                    futureList.stream().forEach((future) -> {
                        future.cancel(true);
                    });
                    throw ex;
                }
                MultiException.checkAndThrow("Could not apply all actions!", exceptionStack);
                return resultProcessor.process(futureList);
            }
        });
    }

}
