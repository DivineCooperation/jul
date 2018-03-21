package org.openbase.jul.pattern.launch;

/*-
 * #%L
 * JUL Pattern Launch
 * %%
 * Copyright (C) 2015 - 2018 openbase.org
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
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.Callable;   
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.openbase.jul.pattern.launch.jp.JPExcludeLauncher;
import org.openbase.jul.pattern.launch.jp.JPPrintLauncher;
import org.openbase.jps.core.JPService;
import org.openbase.jps.exception.JPNotAvailableException;
import org.openbase.jul.exception.CouldNotPerformException;
import org.openbase.jul.exception.InitializationException;
import org.openbase.jul.exception.MultiException;
import org.openbase.jul.exception.NotAvailableException;
import org.openbase.jul.exception.VerificationFailedException;
import org.openbase.jul.exception.printer.ExceptionPrinter;
import org.openbase.jul.exception.printer.LogLevel;
import org.openbase.jul.extension.rsb.com.AbstractIdentifiableController;
import org.openbase.jul.extension.rsb.com.RPCHelper;
import org.openbase.jul.extension.rsb.iface.RSBLocalServer;
import org.openbase.jul.extension.rsb.scope.ScopeGenerator;
import org.openbase.jul.iface.Launchable;
import org.openbase.jul.iface.VoidInitializable;
import org.openbase.jul.iface.provider.NameProvider;
import org.openbase.jul.pattern.Launcher;
import org.openbase.jul.processing.StringProcessor;
import org.openbase.jul.schedule.GlobalCachedExecutorService;
import org.openbase.jul.schedule.SyncObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rsb.Scope;
import org.openbase.jul.exception.InstantiationException;
import rst.domotic.state.ActivationStateType.ActivationState;

/**
 *
 * @author <a href="mailto:divine@openbase.org">Divine Threepwood</a>
 * @param <L> the launchable to launch by this launcher.
 */
public abstract class AbstractLauncher<L extends Launchable> extends AbstractIdentifiableController<ActivationState, ActivationState.Builder> implements Launcher, VoidInitializable, NameProvider {

    protected final Logger logger = LoggerFactory.getLogger(getClass());

    public static final long LAUNCHER_TIMEOUT = 60000;
    public static final String SCOPE_PREFIX_LAUNCHER = Scope.COMPONENT_SEPARATOR + "launcher";

    private final Class<L> launchableClass;
    private final Class applicationClass;
    private L launchable;
    private long launchTime = -1;
    private LauncherState state;
    private boolean verified;
    private VerificationFailedException verificationFailedException;

    /**
     * Constructor prepares the launcher and registers already a shutdown hook.
     * The launcher class is used to instantiate a new launcher instance if the instantiateLaunchable() method is not overwritten.
     *
     * After instantiation of this class the launcher must be initialized and activated before the RSB interface is provided.
     *
     * @param launchableClass the class to be launched.
     * @param applicationClass the class representing this application. Those is used for scope generation if the getName() method is not overwritten.
     * @throws org.openbase.jul.exception.InstantiationException
     */
    public AbstractLauncher(final Class applicationClass, final Class<L> launchableClass) throws InstantiationException {
        super(ActivationState.newBuilder());
//        try {
            this.launchableClass = launchableClass;
            this.applicationClass = applicationClass;
//        } catch (CouldNotPerformException ex) {
//            throw new InstantiationException(this, ex);
//        }
    }

    @Override
    public void init() throws InitializationException, InterruptedException {
        try {
            super.init(SCOPE_PREFIX_LAUNCHER + Scope.COMPONENT_SEPARATOR + ScopeGenerator.convertIntoValidScopeComponent(getName()));
        } catch (NotAvailableException ex) {
            throw new InitializationException(this, ex);
        }
    }

    @Override
    public void registerMethods(RSBLocalServer server) throws CouldNotPerformException {
        RPCHelper.registerInterface(Launcher.class, this, server);
    }

    public L getLaunchable() {
        return launchable;
    }

    /**
     * Method returns the application name.
     *
     * By default the application name is the name of the given application class name.
     *
     * @return the name as string.
     * @throws NotAvailableException
     */
    @Override
    public String getName() throws NotAvailableException {
        return applicationClass.getSimpleName();
    }

    /**
     * Method creates a launchable instance without any arguments.. In case the launchable needs arguments you can overwrite this method and instantiate the launchable by ourself.
     *
     * @return the new instantiated launchable.
     * @throws CouldNotPerformException is thrown in case the launchable could not properly be instantiated.
     */
    protected L instantiateLaunchable() throws CouldNotPerformException {
        try {
            return launchableClass.newInstance();
        } catch (java.lang.InstantiationException | IllegalAccessException ex) {
            throw new CouldNotPerformException("Could not load launchable class!", ex);
        }
    }

    // Load application specific java properties.
    protected abstract void loadProperties();

    /**
     * Method verifies a running application.
     *
     * @throws VerificationFailedException is thrown if the application is started with any restrictions.
     * @throws InterruptedException is thrown if the verification process is externally interrupted.
     */
    protected void verify() throws VerificationFailedException, InterruptedException {
        // overwrite for verification.
    }

    private final SyncObject LAUNCHER_LOCK = new SyncObject(this);

    public enum LauncherState {

        INITALIZING,
        LAUNCHING,
        RUNNING,
        STOPPING,
        STOPPED,
        ERROR
    }

    private void setState(final LauncherState state) {
        this.state = state;
    }

    @Override
    public void launch() throws CouldNotPerformException, InterruptedException {
        synchronized (LAUNCHER_LOCK) {
            setState(LauncherState.INITALIZING);
            launchable = instantiateLaunchable();
            try {
                launchable.init();
                setState(LauncherState.LAUNCHING);
                launchable.activate();
                launchTime = System.currentTimeMillis();
                setState(LauncherState.RUNNING);
                try {
                    verify();
                    verified = true;
                } catch (VerificationFailedException ex) {
                    verified = false;
                    verificationFailedException = ex;
                }
            } catch (CouldNotPerformException ex) {
                setState(LauncherState.ERROR);
                launchable.shutdown();
                throw new CouldNotPerformException("Could not launch " + getName(), ex);
            }
        }
    }

    @Override
    public void relaunch() throws CouldNotPerformException, InterruptedException {
        synchronized (LAUNCHER_LOCK) {
            stop();
            launch();
        }
    }

    @Override
    public void stop() {
        synchronized (LAUNCHER_LOCK) {
            setState(LauncherState.STOPPING);
            if (launchable != null) {
                launchable.shutdown();
            }
            setState(LauncherState.STOPPED);
        }
    }

    @Override
    public void shutdown() {
        stop();
        super.shutdown();
    }

    @Override
    public long getUpTime() {
        if (launchTime < 0) {
            return 0;
        }
        return (System.currentTimeMillis() - launchTime);
    }

    @Override
    public long getLaunchTime() {
        return launchTime;
    }

    @Override
    public boolean isVerified() {
        return verified;
    }

    public VerificationFailedException getVerificationFailedCause() {
        return verificationFailedException;
    }

    public static void main(final String args[], final Class application, final Class<? extends AbstractLauncher>... launchers) {

        final Logger logger = LoggerFactory.getLogger(Launcher.class);
        JPService.setApplicationName(application);

        MultiException.ExceptionStack errorExceptionStack = null;
        MultiException.ExceptionStack verificationExceptionStack = null;

        Map<Class<? extends AbstractLauncher>, AbstractLauncher> launcherMap = new HashMap<>();
        for (final Class<? extends AbstractLauncher> launcherClass : launchers) {
            try {
                launcherMap.put(launcherClass, launcherClass.newInstance());
            } catch (java.lang.InstantiationException | IllegalAccessException ex) {
                errorExceptionStack = MultiException.push(application, new CouldNotPerformException("Could not load launcher class!", ex), errorExceptionStack);
            }
        }

        for (final AbstractLauncher launcher : launcherMap.values()) {
            launcher.loadProperties();
        }

        // regsiter launcher jps
        JPService.registerProperty(JPPrintLauncher.class);
        JPService.registerProperty(JPExcludeLauncher.class);

        JPService.parseAndExitOnError(args);

        // print launcher end exit
        try {
            if (JPService.getProperty(JPPrintLauncher.class).getValue()) {
                if (launcherMap.isEmpty()) {
                    System.out.println(JPService.getApplicationName() + " does not provide any launcher!");
                    System.exit(255);
                }
                System.out.println("Available launcher:");
                System.out.println("");
                int maxLauncherNameSize = 0;
                for (final Entry<Class<? extends AbstractLauncher>, AbstractLauncher> launcherEntry : launcherMap.entrySet()) {
                    maxLauncherNameSize = Math.max(maxLauncherNameSize, launcherEntry.getKey().getSimpleName().length());
                }
                for (final Entry<Class<? extends AbstractLauncher>, AbstractLauncher> launcherEntry : launcherMap.entrySet()) {
                    System.out.println("\t• " + StringProcessor.fillWithSpaces(launcherEntry.getKey().getSimpleName(), maxLauncherNameSize) + "  ⊳  " + launcherEntry.getKey().getName());
                }
                System.out.println("");
                System.exit(0);
            }
        } catch (JPNotAvailableException ex) {
            ExceptionPrinter.printHistory("Could not check if launcher should be printed.", ex, logger);
        }

        logger.info("Start " + JPService.getApplicationName() + "...");

        final Map<Entry<Class<? extends AbstractLauncher>, AbstractLauncher>, Future> launchableFutureMap = new HashMap<>();
        try {
            for (final Entry<Class<? extends AbstractLauncher>, AbstractLauncher> launcherEntry : launcherMap.entrySet()) {

                // check if launcher was excluded
                boolean exclude = false;
                try {
                    //filter excluded launcher
                    for (String exclusionPatter : JPService.getProperty(JPExcludeLauncher.class).getValue()) {
                        if (launcherEntry.getKey().getName().toLowerCase().contains(exclusionPatter.replace("-", "").replace("_", "").toLowerCase())) {
                            exclude = true;
                        }
                    }
                } catch (JPNotAvailableException ex) {
                    ExceptionPrinter.printHistory("Could not process launcher exclusion!", ex, logger);
                }
                if (exclude) {
                    logger.info(launcherEntry.getKey().getSimpleName() + " excluded from execution.");
                    continue;
                }

                launchableFutureMap.put(launcherEntry, GlobalCachedExecutorService.submit(new Callable<Void>() {

                    @Override
                    public Void call() throws Exception {
                        launcherEntry.getValue().launch();
                        return null;
                    }
                }));
            }

            while (!Thread.interrupted() && !launchableFutureMap.isEmpty()) {
                for (Entry<Entry<Class<? extends AbstractLauncher>, AbstractLauncher>, Future> launcherEntry : new ArrayList<>(launchableFutureMap.entrySet())) {
                    try {
                        launcherEntry.getValue().get(LAUNCHER_TIMEOUT, TimeUnit.MILLISECONDS);
                        if (!launcherEntry.getKey().getValue().isVerified()) {
                            verificationExceptionStack = MultiException.push(application, new CouldNotPerformException("Could not verify " + launcherEntry.getKey().getKey().getSimpleName() + "!", launcherEntry.getKey().getValue().getVerificationFailedCause()), verificationExceptionStack);
                        }
                        // remove from stack because launcher was successfully started.
                        launchableFutureMap.remove(launcherEntry.getKey());
                    } catch (InterruptedException ex) {
                        throw ex;
                    } catch (TimeoutException ex) {
                        ExceptionPrinter.printHistory(new CouldNotPerformException("Launcher " + launcherEntry.getKey().getKey().getSimpleName() + " startup delay detected!"), logger, LogLevel.WARN);
                    } catch (Exception ex) {
                        // remove from stack because launcher failed and generate exception report.
                        launchableFutureMap.remove(launcherEntry.getKey());
                        errorExceptionStack = MultiException.push(application, new CouldNotPerformException("Could not launch " + launcherEntry.getKey().getKey().getSimpleName() + "!", ex), errorExceptionStack);
                    }
                }
            }
        } catch (InterruptedException ex) {
            ExceptionPrinter.printHistoryAndExit(JPService.getApplicationName() + " catched shutdown signal during startup phase!", ex, logger);
        }

        try {
            MultiException.ExceptionStack exceptionStack = null;
            try {
                MultiException.checkAndThrow("Errors during startup phase!", errorExceptionStack);
            } catch (MultiException ex) {
                exceptionStack = MultiException.push(application, ex, exceptionStack);
            }
            try {
                MultiException.checkAndThrow("Cause detections during startup phase!", verificationExceptionStack);
            } catch (MultiException ex) {
                exceptionStack = MultiException.push(application, ex, exceptionStack);
            }

            MultiException.checkAndThrow(JPService.getApplicationName() + " was started with restrictions!", exceptionStack);
            logger.info(JPService.getApplicationName() + " successfully started.");
        } catch (MultiException ex) {
            ExceptionPrinter.printHistory(ex, logger);
        }
    }
}
