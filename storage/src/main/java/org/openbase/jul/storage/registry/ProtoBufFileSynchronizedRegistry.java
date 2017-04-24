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
import java.io.File;
import java.util.ArrayList;
import java.util.List;

import org.openbase.jps.core.JPService;
import org.openbase.jps.exception.JPServiceException;
import org.openbase.jul.exception.CouldNotPerformException;
import org.openbase.jul.exception.InstantiationException;
import org.openbase.jul.exception.printer.ExceptionPrinter;
import org.openbase.jul.extension.protobuf.BuilderSyncSetup;
import org.openbase.jul.extension.protobuf.IdGenerator;
import org.openbase.jul.extension.protobuf.IdentifiableMessage;
import org.openbase.jul.extension.protobuf.container.ProtoBufMessageMap;
import org.openbase.jul.extension.protobuf.container.ProtoBufMessageMapImpl;
import org.openbase.jul.extension.protobuf.container.transformer.IdentifiableMessageTransformer;
import org.openbase.jul.extension.protobuf.processing.ProtoBufFileProcessor;
import org.openbase.jul.iface.Identifiable;
import org.openbase.jul.storage.file.FileProvider;
import org.openbase.jul.storage.registry.jp.JPGitRegistryPlugin;
import org.openbase.jul.storage.registry.plugin.GitRegistryPlugin;

/**
 *
 * @author <a href="mailto:divine@openbase.org">Divine Threepwood</a>
 * @param <KEY>
 * @param <M> Message
 * @param <MB> Message Builder
 * @param <SIB> Synchronized internal builder
 */
public class ProtoBufFileSynchronizedRegistry<KEY extends Comparable<KEY>, M extends GeneratedMessage, MB extends M.Builder<MB>, SIB extends GeneratedMessage.Builder<SIB>> extends FileSynchronizedRegistryImpl<KEY, IdentifiableMessage<KEY, M, MB>, ProtoBufMessageMap<KEY, M, MB>, ProtoBufRegistry<KEY, M, MB>> implements ProtoBufRegistry<KEY, M, MB> {

    private final ProtoBufMessageMapImpl<KEY, M, MB, SIB> protobufMessageMap;
    private final IdGenerator<KEY, M> idGenerator;
    private final Class<M> messageClass;

    public ProtoBufFileSynchronizedRegistry(final Class<M> messageClass, final BuilderSyncSetup<SIB> builderSetup, final Descriptors.FieldDescriptor fieldDescriptor, final IdGenerator<KEY, M> idGenerator, final File databaseDirectory, final FileProvider<Identifiable<KEY>> fileProvider) throws InstantiationException, InterruptedException {
        this(messageClass, new ProtoBufMessageMapImpl<>(builderSetup, fieldDescriptor), idGenerator, databaseDirectory, fileProvider);
    }

    public ProtoBufFileSynchronizedRegistry(final Class<M> messageClass, final ProtoBufMessageMapImpl<KEY, M, MB, SIB> internalMap, final IdGenerator<KEY, M> idGenerator, final File databaseDirectory, final FileProvider<Identifiable<KEY>> fileProvider) throws InstantiationException, InterruptedException {
        super(internalMap, databaseDirectory, new ProtoBufFileProcessor<IdentifiableMessage<KEY, M, MB>, M, MB>(new IdentifiableMessageTransformer<>(messageClass, idGenerator)), fileProvider);
        try {
            this.idGenerator = idGenerator;
            this.messageClass = messageClass;
            this.protobufMessageMap = internalMap;
            this.setName(getDatabaseName() + "Registry");

            try {
                if (JPService.getProperty(JPGitRegistryPlugin.class).getValue()) {
                    registerPlugin(new GitRegistryPlugin<>(this));
                }
            } catch (JPServiceException ex) {
                ExceptionPrinter.printHistory(new CouldNotPerformException("Could not access java property!", ex), logger);
            }

            setupSandbox(new ProtoBufFileSynchronizedRegistrySandbox<KEY, M, MB, SIB>(idGenerator, protobufMessageMap.getFieldDescriptor(), this));
        } catch (CouldNotPerformException ex) {
            throw new InstantiationException(this, ex);
        }
    }

    @Override
    public void shutdown() {
        protobufMessageMap.shutdown();
        super.shutdown();
    }

    /**
     * This method activate the version control unit of the underlying registry
     * db. The version check and db upgrade is automatically performed during
     * the registry db loading phase. The db will be upgraded to the latest db
     * format provided by the given converter package. The converter package
     * should contain only classes implementing the DBVersionConverter
     * interface. To fully support outdated db upgrade make sure that the
     * converter pipeline covers the whole version range!
     *
     * Activate version control before loading the registry. Please provide
     * within the converter package only converter with the naming structure
     * [$(EntryType)_$(VersionN)_To_$(VersionN+1)_DBConverter].
     *
     * Example:
     *
     * converter package myproject.db.converter containing the converter
     * pipeline
     *
     * myproject.db.converter.DeviceConfig_0_To_1_DBConverter.class
     * myproject.db.converter.DeviceConfig_1_To_2_DBConverter.class
     * myproject.db.converter.DeviceConfig_2_To_3_DBConverter.class
     *
     * Would support the db upgrade from version 0 till the latest db version 3.
     *
     * @param converterPackage the package containing all converter which
     * provides db entry updates from the first to the latest db version.
     * @throws CouldNotPerformException in case of an invalid converter pipeline
     * or initialization issues.
     */
    public void activateVersionControl(final Package converterPackage) throws CouldNotPerformException {
        try {
            String entryType;
            try {
                entryType = getDatabaseName();
            } catch (Exception ex) {
                throw new CouldNotPerformException("Could not detect entry type!", ex);
            }
            super.activateVersionControl(entryType, converterPackage);
        } catch (CouldNotPerformException ex) {
            throw new CouldNotPerformException("Could not activate version control!", ex);
        }
    }

    public Class<M> getMessageClass() {
        return messageClass;
    }

    @Override
    public M register(final M message) throws CouldNotPerformException {
        M result = super.register(new IdentifiableMessage<>(message, idGenerator)).getMessage();
        return result;
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
    public M remove(M message) throws CouldNotPerformException {
        return remove(new IdentifiableMessage<>(message)).getMessage();
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
