package org.openbase.jul.extension.openhab.binding;

/*
 * #%L
 * JUL Extension OpenHAB
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
import java8.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import org.openbase.jul.exception.CouldNotPerformException;
import org.openbase.jul.exception.InitializationException;
import org.openbase.jul.exception.InvalidStateException;
import org.openbase.jul.exception.NotAvailableException;
import org.openbase.jul.exception.NotSupportedException;
import org.openbase.jul.exception.printer.ExceptionPrinter;
import org.openbase.jul.exception.printer.LogLevel;
import org.openbase.jul.extension.openhab.binding.interfaces.OpenHABRemote;
import org.openbase.jul.extension.rsb.com.RPCHelper;
import org.openbase.jul.extension.rsb.com.RSBFactoryImpl;
import org.openbase.jul.extension.rsb.com.RSBRemoteService;
import org.openbase.jul.extension.rsb.iface.RSBListener;
import rsb.Event;
import rsb.Handler;
import rsb.Scope;
import rsb.converter.DefaultConverterRepository;
import rsb.converter.ProtocolBufferConverter;
import rst.domotic.binding.openhab.OpenhabCommandType.OpenhabCommand;
import rst.domotic.binding.openhab.OpenhabStateType.OpenhabState;

/**
 *
 * * @author <a href="mailto:pleminoq@openbase.org">Tamino Huxohl</a>
 */
public abstract class AbstractOpenHABRemote extends RSBRemoteService<OpenhabState> implements OpenHABRemote {

    public static final String ITEM_SUBSEGMENT_DELIMITER = "_";
    public static final String ITEM_SEGMENT_DELIMITER = "__";

    public static final String RPC_METHODE_SEND_COMMAND = "sendCommand";
    public static final String RPC_METHODE_POST_COMMAND = "postCommand";
    public static final String RPC_METHODE_POST_UPDATE = "postUpdate";

    public static final Scope SCOPE_OPENHAB = new Scope("/openhab");
    public static final Scope SCOPE_OPENHAB_UPDATE = SCOPE_OPENHAB.concat(new Scope("/update"));
    public static final Scope SCOPE_OPENHAB_COMMAND = SCOPE_OPENHAB.concat(new Scope("/command"));

    private String itemFilter;

    static {
        DefaultConverterRepository.getDefaultConverterRepository().addConverter(new ProtocolBufferConverter<>(OpenhabCommand.getDefaultInstance()));
        DefaultConverterRepository.getDefaultConverterRepository().addConverter(new ProtocolBufferConverter<>(OpenhabState.getDefaultInstance()));
    }

    private RSBListener openhabCommandListener, openhabUpdateListener;
    private final boolean hardwareSimulationMode;

    public AbstractOpenHABRemote(final boolean hardwareSimulationMode) {
        super(OpenhabState.class);
        this.hardwareSimulationMode = hardwareSimulationMode;
    }

    @Override
    public void init(String itemFilter) throws InitializationException, InterruptedException {
        init(SCOPE_OPENHAB);
        this.itemFilter = itemFilter;
    }

    @Override
    protected void postInit() throws InitializationException, InterruptedException {
        try {
            openhabCommandListener = RSBFactoryImpl.getInstance().createSynchronizedListener(SCOPE_OPENHAB_COMMAND);
            openhabUpdateListener = RSBFactoryImpl.getInstance().createSynchronizedListener(SCOPE_OPENHAB_UPDATE);

            openhabCommandListener.addHandler((Event event) -> {
                try {
                    OpenhabCommand openhabCommand = (OpenhabCommand) event.getData();
                    if (!openhabCommand.hasItemBindingConfig() || !openhabCommand.getItemBindingConfig().startsWith(itemFilter)) {
                        return;
                    }
                    internalReceiveCommand(openhabCommand);
                } catch (ClassCastException ex) {
                    ExceptionPrinter.printHistory(new NotSupportedException(event.getData().getClass().getSimpleName(), this), logger, LogLevel.DEBUG);
                } catch (CouldNotPerformException ex) {
                    ExceptionPrinter.printHistory(new CouldNotPerformException("Could not handle openhab command!", ex), logger);
                }
            }, true);

            openhabUpdateListener.addHandler(new Handler() {

                @Override
                public void internalNotify(Event event) {
                    try {
                        internalReceiveUpdate((OpenhabCommand) event.getData());
                    } catch (ClassCastException ex) {
                        ExceptionPrinter.printHistory(new NotSupportedException(event.getData().getClass().getSimpleName(), this), logger, LogLevel.DEBUG);
                    } catch (CouldNotPerformException ex) {
                        ExceptionPrinter.printHistory(new CouldNotPerformException("Could not handle openhab update!", ex), logger);
                    }
                }
            }, true);
        } catch (CouldNotPerformException ex) {
            throw new InitializationException(this, ex);
        }
    }

    @Override
    public void activate() throws CouldNotPerformException, InterruptedException {
        if (hardwareSimulationMode) {
            return;
        }
        super.activate();
        openhabCommandListener.activate();
        openhabUpdateListener.activate();
    }

    @Override
    public void deactivate() throws CouldNotPerformException, InterruptedException {

        try {
            super.deactivate();
        } catch (InterruptedException ex) {
            ExceptionPrinter.printHistory(new CouldNotPerformException("Unable to deactivate openhab remote!", ex), logger, LogLevel.WARN);
            Thread.currentThread().interrupt();
        } catch (CouldNotPerformException ex) {
            ExceptionPrinter.printHistory(new CouldNotPerformException("Unable to deactivate openhab remote!", ex), logger, LogLevel.WARN);
        }

        try {
            if (openhabUpdateListener != null) {
                openhabUpdateListener.deactivate();
            }
        } catch (InterruptedException ex) {
            ExceptionPrinter.printHistory(new CouldNotPerformException("Unable to deactivate openhab update listener!", ex), logger, LogLevel.WARN);
            Thread.currentThread().interrupt();
        } catch (CouldNotPerformException ex) {
            ExceptionPrinter.printHistory(new CouldNotPerformException("Unable to deactivate openhab update listener!", ex), logger, LogLevel.WARN);
        }

        try {
            if (openhabCommandListener != null) {
                openhabCommandListener.deactivate();
            }
        } catch (InterruptedException ex) {
            ExceptionPrinter.printHistory(new CouldNotPerformException("Unable to deactivate openhab command listener!", ex), logger, LogLevel.WARN);
            Thread.currentThread().interrupt();
        } catch (CouldNotPerformException ex) {
            ExceptionPrinter.printHistory(new CouldNotPerformException("Unable to deactivate openhab command listener!", ex), logger, LogLevel.WARN);
        }
    }

    @Override
    public Future<Void> postCommand(final OpenhabCommand command) throws CouldNotPerformException {
        try {
            validateCommand(command);
            if (hardwareSimulationMode) {
                internalReceiveUpdate(command);
                return CompletableFuture.completedFuture(null);
            }
            return (Future) RPCHelper.callRemoteMethod(command, this);
        } catch (CouldNotPerformException ex) {
            throw new CouldNotPerformException("Could not post Command[" + command + "]!", ex);
        }
    }

    @Override
    public Future<Void> sendCommand(OpenhabCommand command) throws CouldNotPerformException {
        try {
            validateCommand(command);
            if (hardwareSimulationMode) {
                internalReceiveUpdate(command);
                return CompletableFuture.completedFuture(null);
            }
            return (Future) RPCHelper.callRemoteMethod(command, this);
        } catch (CouldNotPerformException ex) {
            throw new CouldNotPerformException("Could not send Command[" + command + "]!", ex);
        }
    }

    @Override
    public Future<Void> postUpdate(OpenhabCommand command) throws CouldNotPerformException {
        try {
            validateCommand(command);
            if (hardwareSimulationMode) {
                return CompletableFuture.completedFuture(null);
            }
            return (Future) RPCHelper.callRemoteMethod(command, this);
        } catch (CouldNotPerformException ex) {
            throw new CouldNotPerformException("Could not post Update[" + command + "]!", ex);
        }
    }

    private void validateCommand(final OpenhabCommand command) throws InvalidStateException {
        try {
            if (!command.hasItem() || command.getItem().isEmpty()) {
                throw new NotAvailableException("command item");
            }

            if (!command.hasType()) {
                throw new NotAvailableException("command type");
            }
        } catch (CouldNotPerformException ex) {
            throw new InvalidStateException("Command invalid!", ex);
        }
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "[version=" + getClass().getPackage().getImplementationVersion() + "]";
    }
}
