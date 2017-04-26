package org.openbase.jul.processing;

/*
 * #%L
 * JUL Processing
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
import java8.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 *
 * @author <a href="mailto:divine@openbase.org">Divine Threepwood</a>
 * @deprecated Please use package org.openbase.jul.schedule.FutureProcessor instead!;
 */
@Deprecated
public class FutureProcessor {

    /**
     * Method transforms a callable into a CompletableFuture object.
     *
     * @param <T>
     * @param callable the callable to wrap.
     * @return the Future representing the call state.
     */
    public static <T> CompletableFuture<T> toCompletableFuture(final Callable<T> callable) {
        CompletableFuture<T> future = new CompletableFuture<>();
        CompletableFuture.runAsync(new Runnable() {
            @Override
            public void run() {
                try {
                    future.complete(callable.call());
                } catch (InterruptedException e) {
                    future.completeExceptionally(e);
                    Thread.currentThread().interrupt();
                } catch (Exception e) {
                    future.completeExceptionally(e);
                }
            }
        });
        return future;
    }

    /**
     * Method returns a future which is already canceled by the given cause.
     *
     * @param <T> the type of the future
     * @param futureType the type class of the future
     * @param cause the reason why the future was canceled.
     * @return the canceled future.
     */
    public static <T> Future<T> canceledFuture(final Class<T> futureType, final Exception cause) {
        return new Future<T>() {
            @Override
            public boolean cancel(boolean mayInterruptIfRunning) {
                return true;
            }

            @Override
            public boolean isCancelled() {
                return true;
            }

            @Override
            public boolean isDone() {
                return true;
            }

            @Override
            public T get() throws InterruptedException, ExecutionException {
                throw new ExecutionException("Future was canceled!", cause);
            }

            @Override
            public T get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
                throw new ExecutionException("Future was canceled!", cause);
            }
        };
    }

}
