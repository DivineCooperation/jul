package org.openbase.jul.extension.protobuf;

/*-
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
import com.google.protobuf.Descriptors;
import com.google.protobuf.Message;
import com.google.protobuf.Message.Builder;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.openbase.jul.exception.CouldNotPerformException;
import org.openbase.jul.exception.NotAvailableException;
import org.openbase.jul.pattern.AbstractObservable;
import org.openbase.jul.pattern.provider.DataProvider;

/**
 * This class computes the hashCode to check if the observed value has changed invariant of
 * its timestamp.
 * Currently for efficiency reasons the timestamp of messages in repeated fields is still considered.
 *
 * @author <a href="mailto:pleminoq@openbase.org">Tamino Huxohl</a>
 */
public class MessageObservable<M extends Message> extends AbstractObservable<M> {

    final DataProvider<M> dataProvider;

    public MessageObservable(final DataProvider<M> source) {
        super(source);

        this.dataProvider = source;
    }

    @Override
    public void waitForValue(long timeout, TimeUnit timeUnit) throws CouldNotPerformException, InterruptedException {
        dataProvider.waitForData();
    }

    @Override
    public M getValue() throws NotAvailableException {
        return dataProvider.getData();
    }

    @Override
    public boolean isValueAvailable() {
        return dataProvider.isDataAvailable();
    }

    @Override
    public Future<M> getValueFuture() {
        return dataProvider.getDataFuture();
    }
    
    /**
     * Compute a timestamp invariant hash code.
     *
     * @param value
     * @return
     */
    @Override
    protected int computeHash(final M value) {
        return removeTimestamps(value.toBuilder()).build().hashCode();
    }

    public Builder removeTimestamps(final Builder builder) {
        Descriptors.Descriptor descriptorForType = builder.getDescriptorForType();
        for (Descriptors.FieldDescriptor field : descriptorForType.getFields()) {
            // if the field is not repeated, a message and a timestamp it is cleared
            if (!field.isRepeated() && field.getType() == Descriptors.FieldDescriptor.Type.MESSAGE) {
                if (field.getMessageType().getName().equals("Timestamp")) {
                    builder.clearField(field);
                } else {
                    removeTimestamps(builder.getFieldBuilder(field));
                }
            }
        }
        return builder;
    }
}
