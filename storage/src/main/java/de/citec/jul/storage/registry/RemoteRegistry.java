/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package de.citec.jul.storage.registry;

import com.google.protobuf.Descriptors;
import com.google.protobuf.GeneratedMessage;
import de.citec.jul.exception.CouldNotPerformException;
import de.citec.jul.exception.NotSupportedException;
import de.citec.jul.rsb.IdGenerator;
import de.citec.jul.rsb.IdentifiableMessage;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 *
 * @author mpohling
 * @param <KEY>
 * @param <M>
 * @param <MB>
 * @param <SIB>
 */
public class RemoteRegistry<KEY, M extends GeneratedMessage, MB extends M.Builder<MB>, SIB extends GeneratedMessage.Builder> extends AbstractRegistry<KEY, IdentifiableMessage<KEY, M>, Map<KEY, IdentifiableMessage<KEY, M>>, ProtoBufRegistryInterface<KEY, M, MB, SIB>> implements ProtoBufRegistryInterface<KEY, M, MB, SIB> {

    private final IdGenerator<KEY, M> idGenerator;

    public RemoteRegistry(final IdGenerator<KEY, M> idGenerator) {
        this(idGenerator, new HashMap());
    }

    public RemoteRegistry(final IdGenerator<KEY, M> idGenerator, final Map<KEY, IdentifiableMessage<KEY, M>> internalMap) {
        super(internalMap);
        this.idGenerator = idGenerator;
    }

    public synchronized void notifyRegistryUpdated(final Collection<M> values) throws CouldNotPerformException {
        Map<KEY, IdentifiableMessage<KEY, M>> newRegistryMap = new HashMap<>();
        for (M value : values) {
            IdentifiableMessage<KEY, M> data = new IdentifiableMessage<>(value, idGenerator);
            newRegistryMap.put(data.getId(), data);
        }
        replaceInternalMap(newRegistryMap);
    }

    @Override
    public M getMessage(final KEY key) throws CouldNotPerformException {
        return get(key).getMessage();
    }

    @Override
    public MB getBuilder(final KEY key) throws CouldNotPerformException {
        return (MB) getMessage(key).toBuilder();
    }

    @Override
    public M register(final M entry) throws CouldNotPerformException {
        throw new NotSupportedException("loadRegistry", this, "Operation not permitted!");
//        return super.register(new IdentifiableMessage<>(entry, idGenerator)).getMessage();
    }

    @Override
    public M update(final M entry) throws CouldNotPerformException {
//        return super.update(new IdentifiableMessage<>(entry, idGenerator)).getMessage();
        throw new NotSupportedException("loadRegistry", this, "Operation not permitted!");
    }

    @Override
    public M remove(final M entry) throws CouldNotPerformException {
        throw new NotSupportedException("loadRegistry", this, "Operation not permitted!");
//        return super.remove(new IdentifiableMessage<>(entry, idGenerator)).getMessage();
    }

    @Override
    public boolean contains(final M key) throws CouldNotPerformException {
        return super.contains(new IdentifiableMessage<>(key, idGenerator).getId());
    }

    @Override
    public void loadRegistry() throws CouldNotPerformException {
        throw new NotSupportedException("loadRegistry", this, "Operation not permitted!");
    }

    @Override
    public void saveRegistry() throws CouldNotPerformException {
        throw new NotSupportedException("saveRegistry", this, "Operation not permitted!");
    }
    
    @Override
    public IdGenerator<KEY, M> getIdGenerator() {
        return idGenerator;
    }
}
