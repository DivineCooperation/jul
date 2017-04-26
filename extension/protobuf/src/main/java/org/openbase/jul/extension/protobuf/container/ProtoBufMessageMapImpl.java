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

import java8.util.function.Consumer;
import java8.util.stream.StreamSupport;
import org.openbase.jul.extension.protobuf.IdentifiableMessage;
import com.google.protobuf.Descriptors;
import com.google.protobuf.GeneratedMessage;
import com.google.protobuf.GeneratedMessage.Builder;
import org.openbase.jul.exception.CouldNotPerformException;
import org.openbase.jul.exception.NotAvailableException;
import org.openbase.jul.extension.protobuf.BuilderSyncSetup;
import org.openbase.jul.pattern.ObservableImpl;
import org.openbase.jul.pattern.Observer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.openbase.jul.exception.printer.ExceptionPrinter;
import org.openbase.jul.pattern.Observable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author <a href="mailto:divine@openbase.org">Divine Threepwood</a>
 * @param <KEY>
 * @param <M>
 * @param <MB>
 * @param <SIB> synchronized internal builder

 */
public class ProtoBufMessageMapImpl<KEY extends Comparable<KEY>, M extends GeneratedMessage, MB extends M.Builder<MB>, SIB extends Builder<SIB>> extends HashMap<KEY, IdentifiableMessage<KEY, M, MB>> implements ProtoBufMessageMap<KEY, M, MB> {

    protected final Logger logger = LoggerFactory.getLogger(ProtoBufMessageMapImpl.class);

    private boolean shudownDetected = false;
    private final BuilderSyncSetup<SIB> builderSetup;
    private final Observer<IdentifiableMessage<KEY, M, MB>> observer;
    private final ObservableImpl<IdentifiableMessage<KEY, M, MB>> observable;

    private final Descriptors.FieldDescriptor fieldDescriptor;

    public ProtoBufMessageMapImpl(final BuilderSyncSetup<SIB> builderSetup, final Descriptors.FieldDescriptor fieldDescriptor) {
        this.builderSetup = builderSetup;
        this.fieldDescriptor = fieldDescriptor;
        this.observable = new ObservableImpl<>();
        this.observer = new Observer<IdentifiableMessage<KEY, M, MB>>() {

            @Override
            public void update(final Observable<IdentifiableMessage<KEY, M, MB>> source, IdentifiableMessage<KEY, M, MB> data) throws Exception {
                syncBuilder();
                observable.notifyObservers(source, data);
            }
        };
    }

    public BuilderSyncSetup<SIB> getBuilderSetup() {
        return builderSetup;
    }

    public Descriptors.FieldDescriptor getFieldDescriptor() {
        return fieldDescriptor;
    }

    @Override
    public IdentifiableMessage<KEY, M, MB> put(KEY key, IdentifiableMessage<KEY, M, MB> value) {
        if (value == null) {
            ExceptionPrinter.printHistory(new CouldNotPerformException("Could not add value!", new NotAvailableException("value")), logger);
            return value;
        }
        IdentifiableMessage<KEY, M, MB> oldValue = super.put(key, value);
        if (oldValue != null) {
            oldValue.removeObserver(observer);
        }
        value.addObserver(observer);
        syncBuilder();
        return oldValue;
    }

    @Override
    public IdentifiableMessage<KEY, M, MB> put(IdentifiableMessage<KEY, M, MB> value) throws CouldNotPerformException {
        return put(value.getId(), value);
    }

    @Override
    public IdentifiableMessage<KEY, M, MB> remove(Object key) {
        IdentifiableMessage<KEY, M, MB> removedValue = super.remove(key);
        if (removedValue != null) {
            removedValue.removeObserver(observer);
            syncBuilder();
        }
        return removedValue;
    }

    @Override
    public void putAll(Map<? extends KEY, ? extends IdentifiableMessage<KEY, M, MB>> valueMap) {
        for (Entry<? extends KEY, ? extends IdentifiableMessage<KEY, M, MB>> entry : valueMap.entrySet()) {
            if (entry.getValue() == null) {
                continue;
            }
            IdentifiableMessage<KEY, M, MB> oldValue = super.put(entry.getKey(), entry.getValue());
            if (oldValue != null) {
                oldValue.removeObserver(observer);
            }
            entry.getValue().addObserver(observer);
        }
        syncBuilder();
    }

    @Override
    public void clear() {
        for (IdentifiableMessage<KEY, M, MB> value : values()) {
            value.removeObserver(observer);
        }
        super.clear();
        syncBuilder();
    }

    private void syncBuilder() {
        if(shudownDetected) {
            return;
        }
        synchronized (builderSetup) {
            try {
                builderSetup.lockWrite(this);
                SIB builder = builderSetup.getBuilder();
                builder.clearField(fieldDescriptor);
                for (IdentifiableMessage<KEY, M, MB> value : values()) {
                    builder.addRepeatedField(fieldDescriptor, value.getMessage());
                }
            } finally {
                builderSetup.unlockWrite(false);
            }
        }
    }

    public void addObserver(Observer<IdentifiableMessage<KEY, M, MB>> observer) {
        observable.addObserver(observer);
    }

    public void removeObserver(Observer<IdentifiableMessage<KEY, M, MB>> observer) {
        observable.removeObserver(observer);
    }

    @Override
    public M getMessage(final KEY key) throws CouldNotPerformException {
        return get(key).getMessage();
    }

    @Override
    public IdentifiableMessage<KEY, M, MB> get(final KEY key) throws CouldNotPerformException {
        if (key == null) {
            throw new NotAvailableException("key");
        }

        if (!containsKey(key)) {
            throw new NotAvailableException("Value for key[" + key + "]");
        }
        return super.get(key);
    }

    @Override
    public IdentifiableMessage<KEY, M, MB> get(final IdentifiableMessage<KEY, M, MB> value) throws CouldNotPerformException {
        return get(value.getId());
    }

    @Override
    public List<M> getMessages() throws CouldNotPerformException {
        List<M> messageList = new ArrayList<>();
        StreamSupport.stream(values()).forEach(new Consumer<IdentifiableMessage<KEY, M, MB>>() {
            @Override
            public void accept(IdentifiableMessage<KEY, M, MB> messageContainer) {
                messageList.add(messageContainer.getMessage());
            }
        });
        return messageList;
    }

    public void shutdown() {
        observable.shutdown();
        shudownDetected = true;
    }
}
