package org.openbase.jul.storage.registry;

/*
 * #%L
 * JUL Storage
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
import com.google.protobuf.Descriptors;
import com.google.protobuf.GeneratedMessage;
import org.openbase.jul.exception.CouldNotPerformException;
import org.openbase.jul.extension.protobuf.IdGenerator;
import org.openbase.jul.extension.protobuf.IdentifiableMessage;
import org.openbase.jul.extension.protobuf.container.ProtoBufMessageMap;
import org.openbase.jul.extension.protobuf.container.ProtoBufMessageMapWrapper;
import org.openbase.jul.storage.registry.clone.ProtoBufCloner;

import java.util.ArrayList;
import java.util.List;

/**
 *
 * @author <a href="mailto:divine@openbase.org">Divine Threepwood</a>
 * @param <KEY>
 * @param <M> Message
 * @param <MB> Message Builder
 * @param <SIB> Synchronized internal builder
 */
public class ProtoBufFileSynchronizedRegistrySandbox<KEY extends Comparable<KEY>, M extends GeneratedMessage, MB extends M.Builder<MB>, SIB extends GeneratedMessage.Builder<SIB>> extends FileSynchronizedRegistrySandbox<KEY, IdentifiableMessage<KEY, M, MB>, ProtoBufMessageMap<KEY, M, MB>, ProtoBufRegistry<KEY, M, MB>> implements ProtoBufRegistry<KEY, M, MB> {

    private final IdGenerator<KEY, M> idGenerator;

    public ProtoBufFileSynchronizedRegistrySandbox(final IdGenerator<KEY, M> idGenerator, final Descriptors.FieldDescriptor fieldDescriptor, final ProtoBufRegistry<KEY, M, MB> originRegistry) throws CouldNotPerformException, InterruptedException {
        super(new ProtoBufMessageMapWrapper<>(), new ProtoBufCloner<>(), originRegistry);
        this.idGenerator = idGenerator;
    }

    @Override
    public M register(final M message) throws CouldNotPerformException {
        return super.register(new IdentifiableMessage<>(message, idGenerator)).getMessage();
    }

    @Override
    public boolean contains(final M message) throws CouldNotPerformException {
        return contains(new IdentifiableMessage<KEY, M, MB>(message).getId());
    }

    @Override
    public M update(final M message) throws CouldNotPerformException {
        return update(new IdentifiableMessage<>(message)).getMessage();
    }

    @Override
    public M remove(M locationConfig) throws CouldNotPerformException {
        return remove(new IdentifiableMessage<>(locationConfig)).getMessage();
    }

    @Override
    public M getMessage(final KEY id) throws CouldNotPerformException {
        return get(id).getMessage();
    }

    @Override
    public MB getBuilder(KEY key) throws CouldNotPerformException {
        return (MB) getMessage(key).toBuilder();
    }

    public IdGenerator<KEY, M> getIdGenerator() {
        return idGenerator;
    }

    ///////////
    // START DEFAULT INTERFACE METHODS

    public List<M> getMessages() throws CouldNotPerformException {
        final List<M> messageList = new ArrayList<>();
        for (final IdentifiableMessage<KEY, M, MB> identifiableMessage : getEntries()) {
            messageList.add(identifiableMessage.getMessage());
        }
        return messageList;
    }

    // END DEFAULT INTERFACE METHODS
    ///////////
}
