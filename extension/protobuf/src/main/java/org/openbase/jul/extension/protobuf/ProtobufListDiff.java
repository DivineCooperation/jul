package org.openbase.jul.extension.protobuf;

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
import java8.util.function.Consumer;
import java8.util.stream.StreamSupport;
import org.openbase.jul.exception.CouldNotPerformException;
import org.openbase.jul.exception.printer.ExceptionPrinter;
import org.openbase.jul.exception.printer.LogLevel;
import java.util.List;
import org.slf4j.LoggerFactory;

/**
 *
 * @author <a href="mailto:divine@openbase.org">Divine Threepwood</a>
 * @param <KEY>
 * @param <M>
 * @param <MB>
 */
public class ProtobufListDiff<KEY, M extends GeneratedMessage, MB extends M.Builder<MB>> {

    protected final org.slf4j.Logger logger = LoggerFactory.getLogger(ProtobufListDiff.class);

    private IdentifiableMessageMap<KEY, M, MB> newMessages, updatedMessages, removedMessages, originMessages;

    public ProtobufListDiff(final List<M> originMessages) {
        this();
        this.originMessages.putAll(new IdentifiableMessageMap<>(originMessages));
    }

    public ProtobufListDiff(IdentifiableMessageMap<KEY, M, MB> originMessages) {
        this();
        this.originMessages.putAll(originMessages);

    }

    public ProtobufListDiff() {
        this.newMessages = new IdentifiableMessageMap<>();
        this.updatedMessages = new IdentifiableMessageMap<>();
        this.removedMessages = new IdentifiableMessageMap<>();
        this.originMessages = new IdentifiableMessageMap<>();
    }

    public void diff(final List<M> modifieredList) {
        diff(new IdentifiableMessageMap<>(modifieredList));
    }

    public void diff(final List<M> originalList, final List<M> modifieredList) {
        diff(new IdentifiableMessageMap<>(originalList), new IdentifiableMessageMap<>(modifieredList));
    }

    public void diff(final IdentifiableMessageMap<KEY, M, MB> modifieredMap) {
        diff(originMessages, modifieredMap);
    }

    public void diff(final IdentifiableMessageMap<KEY, M, MB> originalMap, final IdentifiableMessageMap<KEY, M, MB> modifieredMap) {
        newMessages.clear();
        updatedMessages.clear();
        removedMessages.clear();

        final IdentifiableMessageMap<KEY, M, MB> modifieredListCopy = new IdentifiableMessageMap<>(modifieredMap);

        StreamSupport.stream(originalMap.keySet()).forEach(new Consumer<KEY>() {
            @Override
            public void accept(KEY id) {
                try {
                    if (modifieredMap.containsKey(id)) {
                        if (!originalMap.get(id).getMessage().equals(modifieredMap.get(id).getMessage())) {
                            updatedMessages.put(modifieredMap.get(id));
                        }
                        modifieredListCopy.remove(id);
                    } else {
                        removedMessages.put(originalMap.get(id));
                    }
                } catch (CouldNotPerformException ex) {
                    ExceptionPrinter.printHistory(new CouldNotPerformException("Ignoring invalid Message[" + id + "]", ex), logger, LogLevel.ERROR);
                }
            }
        });
        // add all messages which are not included in original list.
        newMessages.putAll(modifieredListCopy);

        // update original messages.
        originMessages = modifieredMap;
    }

    public IdentifiableMessageMap<KEY, M, MB> getNewMessageMap() {
        return newMessages;
    }

    public IdentifiableMessageMap<KEY, M, MB> getUpdatedMessageMap() {
        return updatedMessages;
    }

    public IdentifiableMessageMap<KEY, M, MB> getRemovedMessageMap() {
        return removedMessages;
    }

    public int getChangeCounter() {
        return newMessages.size() + updatedMessages.size() + removedMessages.size();
    }

    public void replaceOriginMap(IdentifiableMessageMap<KEY, M, MB> originMap) {
        originMessages.clear();
        originMessages.putAll(originMap);
    }

    public IdentifiableMessageMap<KEY, M, MB> getOriginMessages() {
        return originMessages;
    }
}
