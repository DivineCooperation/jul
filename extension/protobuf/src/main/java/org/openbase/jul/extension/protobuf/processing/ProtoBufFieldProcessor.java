package org.openbase.jul.extension.protobuf.processing;

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
import com.google.protobuf.Descriptors;
import com.google.protobuf.Descriptors.FieldDescriptor;
import com.google.protobuf.GeneratedMessage;
import com.google.protobuf.Message;
import com.google.protobuf.Message.Builder;
import java.lang.reflect.InvocationTargetException;
import java.util.List;

import java8.util.function.Consumer;
import java8.util.stream.StreamSupport;
import org.openbase.jul.exception.CouldNotPerformException;
import org.openbase.jul.iface.Identifiable;
import org.openbase.jul.iface.provider.LabelProvider;

/**
 *
 * @author <a href="mailto:pleminoq@openbase.org">Tamino Huxohl</a>
 * @author <a href="mailto:divine@openbase.org">Divine Threepwood</a>
 *
 */
public class ProtoBufFieldProcessor {

    //TODO: write java doc for all methods
    public static Descriptors.FieldDescriptor getFieldDescriptor(final Message builder, final int fieldNumber) {
        return builder.getDescriptorForType().findFieldByNumber(fieldNumber);
    }

    public static Descriptors.FieldDescriptor getFieldDescriptor(final Builder builder, final int fieldNumber) {
        return builder.getDescriptorForType().findFieldByNumber(fieldNumber);
    }

    public static Descriptors.FieldDescriptor getFieldDescriptor(final GeneratedMessage message, final int fieldNumber) {
        return message.getDescriptorForType().findFieldByNumber(fieldNumber);
    }

    public static FieldDescriptor[] getFieldDescriptors(final Class<? extends GeneratedMessage> messageClass, final int... fieldNumbers) throws CouldNotPerformException {
        try {
            FieldDescriptor[] fieldDescriptors = new FieldDescriptor[fieldNumbers.length];
            GeneratedMessage defaultMessage = (GeneratedMessage) messageClass.getMethod("getDefaultInstance").invoke(null);
            for (int i = 0; i < fieldNumbers.length; i++) {
                fieldDescriptors[i] = getFieldDescriptor(defaultMessage, fieldNumbers[i]);
            }
            return fieldDescriptors;
        } catch (NoSuchMethodException | SecurityException | IllegalArgumentException | IllegalAccessException | InvocationTargetException ex) {
            throw new CouldNotPerformException("Could not detect field descriptors!", ex);
        }
    }

    public static FieldDescriptor getFieldDescriptor(final Class<? extends GeneratedMessage> messageClass, final int fieldNumber) throws CouldNotPerformException {
        try {
            return getFieldDescriptor((GeneratedMessage) messageClass.getMethod("getDefaultInstance").invoke(null), fieldNumber);
        } catch (NoSuchMethodException | SecurityException | IllegalArgumentException | IllegalAccessException | InvocationTargetException ex) {
            throw new CouldNotPerformException("Could not detect field descriptor!", ex);
        }
    }

    public static Descriptors.FieldDescriptor getFieldDescriptor(final Builder builder, final String fieldName) {
        return builder.getDescriptorForType().findFieldByName(fieldName);
    }

    public static String getId(final Message msg) throws CouldNotPerformException {
        return getId(msg.toBuilder());
    }

    public static String getId(final Builder msg) throws CouldNotPerformException {
        try {
            return (String) msg.getField(getFieldDescriptor(msg, Identifiable.TYPE_FIELD_ID));
        } catch (Exception ex) {
            throw new CouldNotPerformException("Could not get id of [" + msg + "]", ex);
        }
    }

    public static String getDescription(final Message.Builder msg) throws CouldNotPerformException {
        try {
            return (String) msg.getField(getFieldDescriptor(msg, "description"));
        } catch (Exception ex) {
            throw new CouldNotPerformException("Could not get description of [" + msg + "]", ex);
        }
    }

    public static String getLabel(final Message.Builder msg) throws CouldNotPerformException {
        try {
            return (String) msg.getField(getFieldDescriptor(msg, LabelProvider.TYPE_FIELD_LABEL));
        } catch (Exception ex) {
            throw new CouldNotPerformException("Could not get label of [" + msg + "]", ex);
        }
    }

    public static void initRequiredFieldsWithDefault(final Message.Builder builder) {
        List<String> missingFieldList = builder.findInitializationErrors();
        StreamSupport.stream(missingFieldList).forEach(new Consumer<String>() {
            @Override
            public void accept(String initError) {
                initFieldWithDefault(builder, initError);
            }
        });
    }

    public static Message.Builder initFieldWithDefault(final Message.Builder builder, final String fieldPath) {
        Descriptors.FieldDescriptor fieldDescriptor;
        Message.Builder tmpBuilder = builder;

        String[] fields = fieldPath.split("\\.");
        for (int i = 0; i < fields.length - 1; ++i) {
            if (fields[i].endsWith("]")) {
                String fieldName = fields[i].split("\\[")[0];
                int number = Integer.parseInt(fields[i].split("\\[")[1].split("\\]")[0]);
                fieldDescriptor = ProtoBufFieldProcessor.getFieldDescriptor(tmpBuilder, fieldName);

                Message.Builder subBuilder = ((Message) tmpBuilder.getRepeatedField(fieldDescriptor, number)).toBuilder();
                String subPath = fields[i + 1];
                for (int j = i + 2; j < fields.length; ++j) {
                    subPath += "." + fields[j];
                }
                tmpBuilder.setRepeatedField(fieldDescriptor, number, initFieldWithDefault(subBuilder, subPath).buildPartial());
                return builder;
            } else {
                fieldDescriptor = ProtoBufFieldProcessor.getFieldDescriptor(tmpBuilder, fields[i]);
                tmpBuilder = tmpBuilder.getFieldBuilder(fieldDescriptor);
            }
        }
        fieldDescriptor = ProtoBufFieldProcessor.getFieldDescriptor(tmpBuilder, fields[fields.length - 1]);
        Object field = tmpBuilder.getField(fieldDescriptor);
        tmpBuilder.setField(fieldDescriptor, field);
        return builder;
    }

    public static void clearRequiredFields(final Message.Builder builder) {
        StreamSupport.stream(builder.findInitializationErrors()).forEach(new Consumer<String>() {
            @Override
            public void accept(String initError) {
                clearRequiredField(builder, initError);
            }
        });
    }

    public static Message.Builder clearRequiredField(final Message.Builder builder, final String fieldPath) {
        Descriptors.FieldDescriptor fieldDescriptor;
        Message.Builder tmpBuilder = builder;

        String[] fields = fieldPath.split("\\.");
        boolean alreadyRemoved = false;
        for (int i = 0; i < fields.length - 2; ++i) {
            if (fields[i].endsWith("]")) {
                String fieldName = fields[i].split("\\[")[0];
                int number = Integer.parseInt(fields[i].split("\\[")[1].split("\\]")[0]);
                fieldDescriptor = ProtoBufFieldProcessor.getFieldDescriptor(tmpBuilder, fieldName);

                Message.Builder subBuilder = ((Message) tmpBuilder.getRepeatedField(fieldDescriptor, number)).toBuilder();
                String subPath = fields[i + 1];
                for (int j = i + 2; j < fields.length; ++j) {
                    subPath += "." + fields[j];
                }
                tmpBuilder.setRepeatedField(fieldDescriptor, number, clearRequiredField(subBuilder, subPath).buildPartial());
                return builder;
            } else {
                fieldDescriptor = ProtoBufFieldProcessor.getFieldDescriptor(tmpBuilder, fields[i]);
                if (!tmpBuilder.hasField(fieldDescriptor)) {
                    alreadyRemoved = true;
                    continue;
                }
                tmpBuilder = tmpBuilder.getFieldBuilder(fieldDescriptor);
            }
        }
        if (!alreadyRemoved) {
            fieldDescriptor = ProtoBufFieldProcessor.getFieldDescriptor(tmpBuilder, fields[fields.length - 2]);
            tmpBuilder.clearField(fieldDescriptor);
        }
        return builder;
    }

    public enum BuilderInitializationStatus {

        ALL_REQUIRED_FIELDS_SET,
        NO_REQUIRED_FIELDS_SET,
        SOME_REQUIRED_FIELDS_SET;
    }

//    public static boolean checkIfSomeButNotAllRequiredFieldsAreSet(final Message.Builder builder) {
//        return checkBuilderInitialization(builder) == BuilderInitializationStatus.SOME_REQUIRED_FIELDS_SET;
//    }
//
//    /**
//     * Recursively check the initialization of a builder.
//     *
//     * @param builder the builder which is checked
//     * @return the initialization status of the builder
//     */
//    public static BuilderInitializationStatus checkBuilderInitialization(final Message.Builder builder) {
//        if (builder.isInitialized()) {
//            // all required fields are set, thus no problem
//            return BuilderInitializationStatus.ALL_REQUIRED_FIELDS_SET;
//        }
//
//        BuilderInitializationStatus status = null, tmp;
//        for (Descriptors.FieldDescriptor field : builder.getDescriptorForType().getFields()) {
//            // check if the field is set or a repeated field that does not contain further messages, if not continue
//            if (!field.isRepeated() && !builder.hasField(field)) {
//                continue;
//            }
//            if (field.isRepeated() && field.getType() != Descriptors.FieldDescriptor.Type.MESSAGE) {
//                continue;
//            }
//
//            // recursively check for all sub-messages
//            if (field.getType() == Descriptors.FieldDescriptor.Type.MESSAGE) {
//                if (field.isRepeated()) {
//                    for (int i = 0; i < builder.getRepeatedFieldCount(field); ++i) {
//                        tmp = checkBuilderInitialization(((Message) builder.getRepeatedField(field, i)).toBuilder());
//                        if (tmp == BuilderInitializationStatus.SOME_REQUIRED_FIELDS_SET) {
//                            return BuilderInitializationStatus.SOME_REQUIRED_FIELDS_SET;
//                        } else {
//                            if (status == null) {
//                                status = tmp;
//                            } else if (tmp != status) {
//                                return BuilderInitializationStatus.SOME_REQUIRED_FIELDS_SET;
//                            }
//                        }
//                    }
//                } else {
//                    tmp = checkBuilderInitialization(builder.getFieldBuilder(field));
//                    if (tmp == BuilderInitializationStatus.SOME_REQUIRED_FIELDS_SET) {
//                        return BuilderInitializationStatus.SOME_REQUIRED_FIELDS_SET;
//                    } else {
//                        if (status == null) {
//                            status = tmp;
//                        } else if (tmp != status) {
//                            return BuilderInitializationStatus.SOME_REQUIRED_FIELDS_SET;
//                        }
//                    }
//                }
//            } else if (field.isRequired()) {
//                // field is no message but still required
//                return BuilderInitializationStatus.SOME_REQUIRED_FIELDS_SET;
//            }
//        }
//        return BuilderInitializationStatus.NO_REQUIRED_FIELDS_SET;
//    }
    public static boolean checkIfSomeButNotAllRequiredFieldsAreSet(final Message.Builder builder) {
        if (builder.isInitialized()) {
            // all required fields are set, thus no problem
            return false;
        }

        BuilderInitializationStatus status = null, tmp;
        for (Descriptors.FieldDescriptor field : builder.getDescriptorForType().getFields()) {
            // check if the field is set or a repeated field that does not contain further messages, if not continue
            if (!field.isRepeated() && !builder.hasField(field)) {
                continue;
            }
            if (field.isRepeated() && field.getType() != Descriptors.FieldDescriptor.Type.MESSAGE) {
                continue;
            }

            // recursively check for all sub-messages
            if (field.getType() == Descriptors.FieldDescriptor.Type.MESSAGE) {
                if (field.isRepeated()) {
                    for (int i = 0; i < builder.getRepeatedFieldCount(field); ++i) {
                        if (checkIfSomeButNotAllRequiredFieldsAreSet(((Message) builder.getRepeatedField(field, i)).toBuilder())) {
                            return true;
                        }
                    }
                } else {
                    if (checkIfSomeButNotAllRequiredFieldsAreSet(builder.getFieldBuilder(field))) {
                        return true;
                    }
                }
            } else if (field.isRequired()) {
                // field is no message but still required
                return true;
            }
        }
        return false;
    }
}
