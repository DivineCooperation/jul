package org.openbase.jul.extension.protobuf.processing;

/*-
 * #%L
 * JUL Extension Protobuf
 * %%
 * Copyright (C) 2015 - 2018 openbase.org
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
import com.googlecode.protobuf.format.JsonFormat;
import org.openbase.jul.exception.CouldNotPerformException;
import org.openbase.jul.exception.InvalidStateException;
import org.openbase.jul.exception.NotSupportedException;
import org.openbase.jul.processing.StringProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.nio.charset.Charset;

public class ProtoBufJSonProcessor {

    private static final String UTF8 = "UTF8";
    private static final String EMPTY_MESSAGE = "{}";
    private static final String javaPrimitvePrefix = "java.lang.";
    private final JsonFormat jsonFormat;

    protected final Logger logger = LoggerFactory.getLogger(getClass());

    /**
     * Enumeration that maps from java primitives to proto field descriptor
     * types.
     */
    public enum JavaTypeToProto {

        BOOLEAN(Descriptors.FieldDescriptor.Type.BOOL),
        INTEGER(Descriptors.FieldDescriptor.Type.INT32),
        FLOAT(Descriptors.FieldDescriptor.Type.FLOAT),
        DOUBLE(Descriptors.FieldDescriptor.Type.DOUBLE),
        LONG(Descriptors.FieldDescriptor.Type.INT64),
        STRING(Descriptors.FieldDescriptor.Type.STRING),
        ENUM(Descriptors.FieldDescriptor.Type.ENUM);

        private final Descriptors.FieldDescriptor.Type protoType;

        JavaTypeToProto(final Descriptors.FieldDescriptor.Type protoType) {
            this.protoType = protoType;
        }

        public Descriptors.FieldDescriptor.Type getProtoType() {
            return protoType;
        }
    }

    public ProtoBufJSonProcessor() {
        this.jsonFormat = new JsonFormat();
    }

    /**
     * Serialize a serviceAttribute which can be a proto message, enumeration or
     * a java primitive to string. If its a primitive toString is called while
     * messages or enumerations will be serialized into JSon
     *
     * @param serviceAttribute
     * @return
     * @throws org.openbase.jul.exception.InvalidStateException in case the given service argument does not contain any context.
     * @throws CouldNotPerformException in case the serialization failed.
     *
     * TODO: release: change parameter type to message since java primitives cannot be de-/serialized anymore anyway
     */
    public String serialize(final Object serviceAttribute) throws InvalidStateException, CouldNotPerformException {
        String jsonStringRep;
        if (serviceAttribute instanceof Message) {
            try {
                jsonStringRep = jsonFormat.printToString((Message) serviceAttribute);
            } catch (Exception ex) {
                throw new CouldNotPerformException("Could not serialize service argument to string!", ex);
            }
        } else {
            throw new InvalidStateException("Service attribute is not a protobuf message!");
        }

        return jsonStringRep;
    }

    /**
     * Get the string representation for a given serviceAttribute which can be a
     * proto message, enumeration or a java primitive.
     *
     * @param serviceAttribute the serviceAttribute
     * @return a string representation of the serviceAttribute type
     * @throws CouldNotPerformException
     */
    public String getServiceAttributeType(final Object serviceAttribute) throws CouldNotPerformException {
        if (serviceAttribute.getClass().getName().startsWith("rst")) {
            return serviceAttribute.getClass().getName();
        }

        if (serviceAttribute.getClass().isEnum()) {
            logger.info(serviceAttribute.getClass().getName());
            return serviceAttribute.getClass().getName();
        }

        logger.debug("Simple class name of attribute to upper case [" + serviceAttribute.getClass().getSimpleName().toUpperCase() + "]");
        JavaTypeToProto javaToProto;
        try {
            javaToProto = JavaTypeToProto.valueOf(serviceAttribute.getClass().getSimpleName().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new CouldNotPerformException("ServiceAttribute is not a supported java primitive nor a supported rst type", ex);
        }
        logger.debug("According proto type [" + javaToProto.getProtoType().name() + "]");
        return javaToProto.getProtoType().name();
    }

    public <SAT> SAT deserialize(String jsonStringRep, Class<SAT> serviceAttributeTypeClass) throws CouldNotPerformException {
        return (SAT) deserialize(jsonStringRep, serviceAttributeTypeClass.getSimpleName());
    }

    /**
     * Deserialise a JSon string representation for an rst value given the class
     * name for the value or the type if its a primitive.
     *
     * @param jsonStringRep the string representation of the rst value
     * @param serviceAttributeType the class name or the type of the value
     * @return the deserialized message
     * @throws org.openbase.jul.exception.CouldNotPerformException
     */
    public Message deserialize(String jsonStringRep, String serviceAttributeType) throws CouldNotPerformException {
        try {
            if (serviceAttributeType.startsWith("rst")) {
                try {
                    Class attibuteClass = Class.forName(serviceAttributeType);
                    if (attibuteClass.isEnum()) {
                        throw new NotSupportedException(serviceAttributeType, this, "Service arguments must be a protobuf message!");
                        //return attibuteClass.getMethod("valueOf", String.class).invoke(null, jsonStringRep);
                    }
                    Message.Builder builder = (Message.Builder) attibuteClass.getMethod("newBuilder").invoke(null);
                    jsonFormat.merge(new ByteArrayInputStream(jsonStringRep.getBytes(Charset.forName(UTF8))), builder);
                    return builder.build();
                } catch (ClassNotFoundException ex) {
                    throw new CouldNotPerformException("Could not find class for serviceAttributeType [" + serviceAttributeType + "]", ex);
                } catch (IOException ex) {
                    throw new CouldNotPerformException("Could not merge [" + jsonStringRep + "] into builder", ex);
                } catch (NoSuchMethodException | SecurityException ex) {
                    throw new CouldNotPerformException("Could not find or acces newBuilder method for rst type [" + serviceAttributeType + "]", ex);
                } catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException ex) {
                    throw new CouldNotPerformException("Could not invoke newBuilder method for rst type [" + serviceAttributeType + "]", ex);
                }
            } else {
                throw new NotSupportedException(serviceAttributeType, this, "Service arguments must be a protobuf message!");
//                try {
//                    if (serviceAttributeType.split("\\.").length > 1) {
//                        Class attibuteClass = Class.forName(serviceAttributeType);
//                        if (attibuteClass.isEnum()) {
//                            return attibuteClass.getMethod("valueOf", String.class).invoke(null, jsonStringRep);
//                        }
//                    }
//                } catch (ClassNotFoundException ex) {
//                    throw new CouldNotPerformException("Could not find class [" + serviceAttributeType + "]", ex);
//                } catch (NoSuchMethodException ex) {
//                    throw new CouldNotPerformException("Java primitive [" + serviceAttributeType + "] has no valueOf method", ex);
//                } catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException ex) {
//                    throw new CouldNotPerformException("Could not invoke valueOf method of class [" + serviceAttributeType + "] with [" + jsonStringRep + "] as the argument", ex);
//                }
//                String className = getJavaPrimitiveClassName(Descriptors.FieldDescriptor.Type.valueOf(serviceAttributeType));
//                try {
//                    Class attibuteClass = Class.forName(className);
//                    // The simple types often offer a constructor by string
//                    Constructor constructor = attibuteClass.getConstructor(String.class);
//                    return constructor.newInstance(jsonStringRep);
//                } catch (ClassNotFoundException ex) {
//                    throw new CouldNotPerformException("Could not find class [" + className + "]", ex);
//                } catch (NoSuchMethodException ex) {
//                    throw new CouldNotPerformException("Java primitive [" + className + "] has no string constructor", ex);
//                } catch (InstantiationException | IllegalAccessException | IllegalArgumentException | InvocationTargetException ex) {
//                    throw new CouldNotPerformException("Could not invoke constructor of class [" + className + "] with [" + jsonStringRep + "] as the argument", ex);
//                }
            }
        } catch (CouldNotPerformException | NullPointerException ex) {
            throw new CouldNotPerformException("Could not deserialize json String[" + jsonStringRep + "] into ServiceAttributeType[" + serviceAttributeType + "]!", ex);
        }
    }

    public String getJavaPrimitiveClassName(Descriptors.FieldDescriptor.Type protoType) {
        switch (protoType.getJavaType()) {
            case INT:
                return javaPrimitvePrefix + "Integer";
            default:
                return javaPrimitvePrefix + StringProcessor.transformUpperCaseToCamelCase(protoType.getJavaType().name());
        }
    }
}
