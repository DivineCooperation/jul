/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package de.citec.jul.rsb.com;

import de.citec.jul.rsb.scope.ScopeProvider;
import com.google.protobuf.Descriptors;
import com.google.protobuf.GeneratedMessage;
import com.google.protobuf.GeneratedMessage.Builder;
import de.citec.jul.exception.CouldNotPerformException;
import de.citec.jul.exception.CouldNotTransformException;
import de.citec.jul.exception.ExceptionPrinter;
import de.citec.jul.exception.InitializationException;
import de.citec.jul.exception.InstantiationException;
import de.citec.jul.exception.NotAvailableException;
import de.citec.jul.iface.Activatable;
import de.citec.jul.rsb.com.RSBInformerInterface.InformerType;
import de.citec.jul.rsb.scope.ScopeTransformer;
import de.citec.jul.schedule.WatchDog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rsb.Event;
import rsb.Factory;
import rsb.InitializeException;
import rsb.RSBException;
import rsb.Scope;
import rsb.patterns.Callback;
import rsb.patterns.LocalServer;
import rst.rsb.ScopeType;

/**
 *
 * @author mpohling
 * @param <M>
 * @param <MB>
 */
public abstract class RSBCommunicationService<M extends GeneratedMessage, MB extends Builder> implements ScopeProvider, Activatable {

    public enum ConnectionState {

        Online, Offline
    };

    public final static Scope SCOPE_SUFFIX_RPC = new Scope("/ctrl");
    public final static Scope SCOPE_SUFFIX_INFORMER = new Scope("/status");

    public final static String RPC_REQUEST_STATUS = "requestStatus";
    public final static Event RPC_SUCCESS = new Event(String.class, "Success");

    protected final Logger logger = LoggerFactory.getLogger(getClass());

    protected RSBInformerInterface<M> informer;
    protected LocalServer server;
    protected WatchDog informerWatchDog;
    protected WatchDog serverWatchDog;

    protected final MB data;
    protected Scope scope;
    private ConnectionState state;

    public RSBCommunicationService(final ScopeType.Scope scope, final MB builder) throws CouldNotTransformException, InstantiationException {
        this(ScopeTransformer.transform(scope), builder);
    }

    public RSBCommunicationService(final Scope scope, final MB builder) throws InstantiationException {
        logger.debug("Create RSBCommunicationService for component " + getClass().getSimpleName() + " on " + scope + ".");
        this.data = builder;
        try {
            if (builder == null) {
                throw new NotAvailableException("builder");
            }

            if (scope == null) {
                throw new NotAvailableException("scope");
            }
            this.scope = new Scope(scope.toString().toLowerCase());
        } catch (CouldNotPerformException ex) {
            throw new InstantiationException(this, ex);
        }
    }

    public RSBCommunicationService(final String name, final String lable, final ScopeProvider location, final MB builder) throws InstantiationException, CouldNotPerformException {
        this(generateScope(name, lable, location), builder);
    }

    public static Scope generateScope(final String name, final String label, final ScopeProvider location) throws CouldNotPerformException {
        try {
            return location.getScope().concat(new Scope(Scope.COMPONENT_SEPARATOR + name).concat(new Scope(Scope.COMPONENT_SEPARATOR + label)));
        } catch (CouldNotPerformException ex) {
            throw new CouldNotPerformException("Coult not generate scope!", ex);
        }
    }

    public void init(final InformerType informerType) throws InitializationException {
        try {
            logger.info("Init " + informerType.name().toLowerCase() + " informer service...");
            switch (informerType) {
                case Single:
                    this.informer = new RSBSingleInformer(scope.concat(new Scope(Scope.COMPONENT_SEPARATOR).concat(SCOPE_SUFFIX_INFORMER)), detectMessageClass());
                    break;
                case Distributed:
                    this.informer = new RSBDistributedInformer(scope.concat(new Scope(Scope.COMPONENT_SEPARATOR).concat(SCOPE_SUFFIX_INFORMER)), detectMessageClass());
                    break;
                default:
                    throw new AssertionError("Could not handle unknown " + informerType.getClass().getSimpleName() + "[" + informerType.name() + "].");
            }
            informerWatchDog = new WatchDog(informer, "RSBInformer[" + scope.concat(new Scope(Scope.COMPONENT_SEPARATOR).concat(SCOPE_SUFFIX_INFORMER)) + "]");
        } catch (InitializeException | InstantiationException ex) {
            throw new InitializationException(this, ex);
        }

        try {
            logger.info("Init rpc server...");
            // Get local server object which allows to expose remotely callable methods.
            server = Factory.getInstance().createLocalServer(scope.concat(new Scope(Scope.COMPONENT_SEPARATOR).concat(SCOPE_SUFFIX_RPC)));

            // register rpc methods.
            server.addMethod(RPC_REQUEST_STATUS, new Callback() {

                @Override
                public Event internalInvoke(Event request) throws Throwable {

                    return new Event(detectMessageClass(), requestStatus());
                }
            });
            registerMethods(server);
            serverWatchDog = new WatchDog(server, "RSBLocalServer[" + scope.concat(new Scope(Scope.COMPONENT_SEPARATOR).concat(SCOPE_SUFFIX_RPC)) + "]");

        } catch (RSBException | InstantiationException ex) {
            throw new InitializationException(this, ex);
        }
    }

    private Class<? extends M> detectMessageClass() {
        return (Class<? extends M>) getData().buildPartial().getClass();
//        return (Class<? extends M>) ((M) data.clone().buildPartial()).getClass();
    }

    @Override
    public void activate() {
        logger.debug("Activate RSBCommunicationService for: " + this);
        informerWatchDog.activate();
        serverWatchDog.activate();
        state = ConnectionState.Online;
    }

    @Override
    public void deactivate() throws InterruptedException {
        try {
            informer.deactivate();
        } catch (RSBException ex) {
            throw new AssertionError(ex);
        }
        serverWatchDog.deactivate();
        state = ConnectionState.Offline;
    }

    @Override
    public boolean isActive() {
        return informerWatchDog.isActive() && serverWatchDog.isActive();
    }

    public M getMessage() throws RSBException {
        try {
            return (M) cloneData().build();
        } catch (Exception ex) {
            throw new RSBException("Could not build message!", ex);
        }
    }

    public MB cloneData() {
        synchronized (data) {
            return (MB) data.clone();
        }
    }

    public MB getData() {
        return data;
    }

    @Override
    public Scope getScope() {
        return scope;
    }

    public void notifyChange() {
        logger.debug("Notify change of " + this);
        try {
            informer.send(getMessage());
        } catch (Exception ex) {
            ExceptionPrinter.printHistory(logger, new CouldNotPerformException("Could not notify update", ex));
        }
    }

    protected final void setField(String name, Object value) {
        try {
            synchronized (data) {
                Descriptors.FieldDescriptor findFieldByName = data.getDescriptorForType().findFieldByName(name);
                if (findFieldByName == null) {
                    throw new NotAvailableException("Field[" + name + "] does not exist for type " + data.getClass().getName());
                }
            }
        } catch (Exception ex) {
            logger.warn("Could not set field [" + name + "=" + value + "] for " + this, ex);
        }
    }

    protected final Object getField(String name) throws CouldNotPerformException {
        try {
            synchronized (data) {
                Descriptors.FieldDescriptor findFieldByName = data.getDescriptorForType().findFieldByName(name);
                if (findFieldByName == null) {
                    throw new NotAvailableException("Field[" + name + "] does not exist for type " + data.getClass().getName());
                }
                return data.getField(findFieldByName);
            }
        } catch (Exception ex) {
            throw new CouldNotPerformException("Could not return value of field [" + name + "] for " + this, ex);
        }
    }

    protected final Descriptors.FieldDescriptor getFieldDescriptor(int fieldId) {
        return data.getDescriptorForType().findFieldByNumber(fieldId);
    }

    public ConnectionState getState() {
        return state;
    }

    public M requestStatus() throws CouldNotPerformException {
        try {
            notifyChange();
            return getMessage();
        } catch (Exception ex) {
            throw ExceptionPrinter.printHistory(logger, new CouldNotPerformException("Could not request status update.", ex));
        }
    }

    public abstract void registerMethods(final LocalServer server) throws RSBException;

    @Override
    public String toString() {
        return getClass().getSimpleName() + "[" + scope + "]";
    }
}