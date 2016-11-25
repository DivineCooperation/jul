package org.openbase.jul.extension.rsb.com;

/*
 * #%L
 * JUL Extension RSB Communication
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
import com.google.protobuf.GeneratedMessage;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import org.openbase.jul.exception.CouldNotPerformException;
import org.openbase.jul.exception.InitializationException;
import org.openbase.jul.exception.InstantiationException;
import org.openbase.jul.exception.InvalidStateException;
import org.openbase.jul.exception.NotAvailableException;
import org.openbase.jul.exception.NotSupportedException;
import org.openbase.jul.exception.printer.ExceptionPrinter;
import org.openbase.jul.iface.Enableable;
import org.openbase.jul.schedule.GlobalExecutionService;
import org.openbase.jul.schedule.SyncObject;
import rst.domotic.state.ActivationStateType;
import rst.domotic.state.ActivationStateType.ActivationState;

/**
 *
 * @author <a href="mailto:divine@openbase.org">Divine Threepwood</a>
 * @param <M>
 * @param <MB>
 * @param <CONFIG>
 */
public abstract class AbstractExecutableController<M extends GeneratedMessage, MB extends M.Builder<MB>, CONFIG extends GeneratedMessage> extends AbstractEnableableConfigurableController<M, MB, CONFIG> implements Enableable {

    public static final String FIELD_ACTIVATION_STATE = "activation_state";
    public static final String FIELD_AUTOSTART = "autostart";

    private final SyncObject enablingLock = new SyncObject(AbstractExecutableController.class);
    private Callable<Void> setActivationStateCallable;
    private boolean executing;

    public AbstractExecutableController(final MB builder) throws InstantiationException {
        super(builder);

    }

    @Override
    public void init(final CONFIG config) throws InitializationException, InterruptedException {
        this.executing = false;
        super.init(config);
    }

    public ActivationState getActivationState() throws NotAvailableException {
        return (ActivationState) getDataField(FIELD_ACTIVATION_STATE);
    }

    public Future<Void> setActivationState(final ActivationState activation) throws CouldNotPerformException {
        if (setActivationStateCallable == null) {
            setActivationStateCallable = () -> {
                if (activation.getValue().equals(ActivationState.State.UNKNOWN)) {
                    throw new InvalidStateException("Unknown is not a valid state!");
                }

//                    try (ClosableDataBuilder<MB> dataBuilder = getDataBuilder(this)) {
//                        Descriptors.FieldDescriptor findFieldByName = dataBuilder.getInternalBuilder().getDescriptorForType().findFieldByName(ACTIVATION_STATE);
//                        if (findFieldByName == null) {
//                            throw new NotAvailableException("Field[" + ACTIVATION_STATE + "] does not exist for type " + dataBuilder.getClass().getName());
//                        }
//                        dataBuilder.getInternalBuilder().setField(findFieldByName, activation);
//                    } catch (Exception ex) {
//                        throw new CouldNotPerformException("Could not apply data change!", ex);
//                    }
                try {
                    setDataField(FIELD_ACTIVATION_STATE, activation);
                } catch (Exception ex) {
                    throw new CouldNotPerformException("Could not apply data change!", ex);
                }

                try {
                    if (activation.getValue().equals(ActivationState.State.ACTIVE)) {
                        if (!executing) {
                            executing = true;
                            execute();
                        }
                    } else {
                        if (executing) {
                            executing = false;
                            stop();
                        }
                    }
                } catch (CouldNotPerformException | InterruptedException ex) {
                    ExceptionPrinter.printHistory(new CouldNotPerformException("Could not update execution state!", ex), logger);
                }
                return null;
            };
        }
        return GlobalExecutionService.submit(setActivationStateCallable);
    }

    public boolean isExecuting() {
        return executing;
    }

    @Override
    public void enable() throws CouldNotPerformException, InterruptedException {
        try {
            synchronized (enablingLock) {
                super.enable();
                if (isAutostartEnabled()) {
                    setActivationState(ActivationStateType.ActivationState.newBuilder().setValue(ActivationStateType.ActivationState.State.ACTIVE).build()).get();
                }
            }
        } catch (ExecutionException ex) {
            throw new CouldNotPerformException("Could not diable " + this, ex);
        }
    }

    @Override
    public void disable() throws CouldNotPerformException, InterruptedException {
        try {
            synchronized (enablingLock) {
                executing = false;
                setActivationState(ActivationStateType.ActivationState.newBuilder().setValue(ActivationStateType.ActivationState.State.DEACTIVE).build()).get();
                super.disable();
            }
        } catch (ExecutionException ex) {
            throw new CouldNotPerformException("Could not diable " + this, ex);
        }
    }

    public boolean isAutostartEnabled() {
        try {
            return (Boolean) getConfigField(FIELD_AUTOSTART);
        } catch (CouldNotPerformException ex) {
            ExceptionPrinter.printHistory(new NotSupportedException("autostart", (AbstractExecutableController) this, (Throwable) ex), logger);
            return false;
        }
    }

    protected abstract void execute() throws CouldNotPerformException, InterruptedException;

    protected abstract void stop() throws CouldNotPerformException, InterruptedException;
}
