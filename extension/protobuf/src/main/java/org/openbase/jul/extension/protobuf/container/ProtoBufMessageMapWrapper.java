package org.openbase.jul.extension.protobuf.container;

/*
 * #%L
 * JUL Extension Protobuf
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

import com.google.protobuf.GeneratedMessage;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import java8.util.function.Consumer;
import java8.util.stream.StreamSupport;
import org.openbase.jul.exception.CouldNotPerformException;
import org.openbase.jul.extension.protobuf.IdentifiableMessage;

/**
 *
 * @author <a href="mailto:divine@openbase.org">Divine Threepwood</a>
 * @param <KEY>
 * @param <M>
 * @param <MB>
 * @param <SIB>
 */
public class ProtoBufMessageMapWrapper<KEY extends Comparable<KEY>, M extends GeneratedMessage, MB extends M.Builder<MB>, SIB extends GeneratedMessage.Builder<SIB>> extends HashMap<KEY, IdentifiableMessage<KEY, M, MB>> implements ProtoBufMessageMap<KEY, M, MB> {

    public ProtoBufMessageMapWrapper() {
    }

    public ProtoBufMessageMapWrapper(final ProtoBufMessageMap<KEY, M, MB> entryMap) {
        putAll(entryMap);
    }

    @Override
    public IdentifiableMessage<KEY, M, MB> put(IdentifiableMessage<KEY, M, MB> value) throws CouldNotPerformException {
        return super.put(value.getId(), value);
    }

    @Override
    public IdentifiableMessage<KEY, M, MB> get(KEY key) throws CouldNotPerformException {
        return super.get(key);
    }

    @Override
    public IdentifiableMessage<KEY, M, MB> get(IdentifiableMessage<KEY, M, MB> value) throws CouldNotPerformException {
        return super.get(value.getId());
    }

    @Override
    public List<M> getMessages() throws CouldNotPerformException {
        ArrayList<M> list = new ArrayList<>();
        StreamSupport.stream(values()).forEach(new Consumer<IdentifiableMessage<KEY, M, MB>>() {
            @Override
            public void accept(IdentifiableMessage<KEY, M, MB> identifiableMessage) {
                list.add(identifiableMessage.getMessage());
            }
        });
        return list;
    }

    @Override
    public M getMessage(KEY key) throws CouldNotPerformException {
        return get(key).getMessage();
    }
}
