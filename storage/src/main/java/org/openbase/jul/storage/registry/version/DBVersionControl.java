package org.openbase.jul.storage.registry.version;

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
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonIOException;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import com.google.gson.stream.JsonReader;
import java.io.File;
import java.io.FileFilter;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.StringReader;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.DirectoryFileFilter;
import org.openbase.jps.core.JPService;
import org.openbase.jps.exception.JPServiceException;
import org.openbase.jul.exception.CouldNotPerformException;
import org.openbase.jul.exception.InstantiationException;
import org.openbase.jul.exception.InvalidStateException;
import org.openbase.jul.exception.NotAvailableException;
import org.openbase.jul.exception.RejectedException;
import org.openbase.jul.exception.printer.ExceptionPrinter;
import org.openbase.jul.iface.Writable;
import org.openbase.jul.storage.file.FileProvider;
import org.openbase.jul.storage.registry.AbstractVersionConsistencyHandler;
import org.openbase.jul.storage.registry.ConsistencyHandler;
import org.openbase.jul.storage.registry.FileSynchronizedRegistry;
import org.openbase.jul.storage.registry.jp.JPDatabaseDirectory;
import org.openbase.jul.storage.registry.jp.JPInitializeDB;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author <a href="mailto:divine@openbase.org">Divine Threepwood</a>
 */
public class DBVersionControl {

    public static final String VERSION_FILE_NAME = ".db-version";
    public static final String VERSION_FIELD = "version";
    public static final String DB_CONVERTER_PACKAGE_NAME = "dbconvert";

    public static final String APPLIED_VERSION_CONSISTENCY_HANDLER_FIELD = "applied_consistency_handler";
    public static final String VERSION_FILE_WARNING = "### PLEASE DO NOT MODIFY ###\n";

    protected final Logger logger = LoggerFactory.getLogger(DBVersionControl.class);
    private final JsonParser parser;
    private final Gson gson;
    private int latestDBVersion;
    private int versionOnStart;
    private final Package converterPackage;
    private final List<DBVersionConverter> converterPipeline;
    private final FileProvider entryFileProvider;
    private final String entryType;
    private final File databaseDirectory;
    private final Writable databaseWriteAccess;
    private final List<File> globalDatabaseDirectories;

    public DBVersionControl(final String entryType, final FileProvider entryFileProvider, final Package converterPackage, final File databaseDirectory, final Writable databaseWriteAccess) throws InstantiationException {
        try {
            this.databaseWriteAccess = databaseWriteAccess;
            this.entryType = entryType;
            this.gson = new GsonBuilder().setPrettyPrinting().create();
            this.parser = new JsonParser();
            this.converterPackage = converterPackage;
            this.entryFileProvider = entryFileProvider;
            this.converterPipeline = loadDBConverterPipelineAndDetectLatestVersion(converterPackage);
            this.databaseDirectory = databaseDirectory;
            this.globalDatabaseDirectories = detectGlobalDatabaseDirectories();
        } catch (CouldNotPerformException ex) {
            throw new InstantiationException(this, ex);
        }
    }

    public DBVersionControl(final String entryType, final FileProvider entryFileProvider, final Package converterPackage, final File databaseDirectory) throws InstantiationException {
        this(entryType, entryFileProvider, converterPackage, databaseDirectory, new Writable() {
            @Override
            public void checkWriteAccess() throws RejectedException {
                if (!databaseDirectory.canWrite()) {
                    throw new RejectedException("db directory not writable!");
                }
            }

            @Override
            public boolean isWritable() {
                try {
                    checkWriteAccess();
                } catch (RejectedException ex) {
                    return false;
                }
                return true;
            }});
    }

    public void validateAndUpgradeDBVersion() throws CouldNotPerformException {
        versionOnStart = detectAndUpgradeCurrentDBVersion();
        int latestVersion = getLatestDBVersion();

        if (versionOnStart == latestVersion) {
            logger.debug("Database[" + databaseDirectory.getName() + "] is up-to-date.");
            return;
        } else if (versionOnStart > latestVersion) {
            throw new InvalidStateException("DB Version[" + versionOnStart + "] is newer than the latest supported Version[" + latestVersion + "]!");
        }

        upgradeDB(versionOnStart, latestVersion);
    }

    public void upgradeDB(final int currentVersion, final int targetVersion) throws CouldNotPerformException {
        try {
            logger.info("Upgrade Database[" + databaseDirectory.getName() + "] from current Version[" + currentVersion + "] to target Version[" + targetVersion + "]...");

            // init
            Map<String, Map<File, DatabaseEntryDescriptor>> globalDbSnapshots = null;
            final List<DBVersionConverter> currentToTargetConverterPipeline = getDBConverterPipeline(currentVersion, latestDBVersion);

            int versionOfCurrentTransaction = currentVersion;

            // check if upgrade is needed and write access is permitted.
            if (!currentToTargetConverterPipeline.isEmpty()) {
                databaseWriteAccess.checkWriteAccess();
            }

            // load db entries
            final Map<File, JsonObject> dbFileEntryMap = loadDbSnapshot();

            // upgrade db entries
            for (DBVersionConverter converter : currentToTargetConverterPipeline) {

                // calculate current transaction version
                versionOfCurrentTransaction++;

                // load global dbs if needed
                if (globalDbSnapshots == null && converter instanceof GlobalDBVersionConverter) {
                    globalDbSnapshots = loadGlobalDbSnapshots();
                }

                for (Entry<File, JsonObject> dbEntry : dbFileEntryMap.entrySet()) {
                    // update converted entry
                    dbFileEntryMap.replace(dbEntry.getKey(), dbEntry.getValue(), upgradeDBEntry(dbEntry.getValue(), converter, dbFileEntryMap, globalDbSnapshots));
                }

                // upgrade latest version to current version
                latestDBVersion = versionOfCurrentTransaction;

                // format and store
                storeDbSnapshot(dbFileEntryMap);

                // format and store global db if changed
                storeGlobalDbSnapshots(globalDbSnapshots);

                // upgrade db version
                upgradeCurrentDBVersion();
            }
        } catch (CouldNotPerformException ex) {
            throw new CouldNotPerformException("Could not upgrade Database[" + databaseDirectory.getAbsolutePath() + "] to" + (targetVersion == latestDBVersion ? " latest" : "") + " Version[" + targetVersion + "]!", ex);
        }
    }

    public JsonObject upgradeDBEntry(final JsonObject entry, final DBVersionConverter converter, final Map<File, JsonObject> dbSnapshot, Map<String, Map<File, DatabaseEntryDescriptor>> globalDbSnapshots) throws CouldNotPerformException {
        try {
            // upgrade
            if (converter instanceof GlobalDBVersionConverter) {
                return ((GlobalDBVersionConverter) converter).upgrade(entry, dbSnapshot, globalDbSnapshots);
            } else {
                return converter.upgrade(entry, dbSnapshot);
            }
        } catch (Exception ex) {
            throw new CouldNotPerformException("Could not upgrade entry with Converter[" + converter.getClass().getSimpleName() + "]!", ex);
        }
    }

    private Map<File, JsonObject> loadDbSnapshot() throws CouldNotPerformException {
        final HashMap<File, JsonObject> dbFileEntryMap = new HashMap<>();
        for (File entry : databaseDirectory.listFiles(entryFileProvider.getFileFilter())) {
            dbFileEntryMap.put(entry, loadDBEntry(entry));
        }
        return dbFileEntryMap;
    }

    private Map<File, DatabaseEntryDescriptor> loadDbSnapshotAsDBEntryDescriptors(final File globalDatabaseDirectory) throws CouldNotPerformException {
        final HashMap<File, DatabaseEntryDescriptor> dbFileEntryMap = new HashMap<>();
        for (File entry : globalDatabaseDirectory.listFiles(entryFileProvider.getFileFilter())) {
            dbFileEntryMap.put(entry, new DatabaseEntryDescriptor(loadDBEntry(entry), entry, detectCurrentDBVersion(globalDatabaseDirectory), globalDatabaseDirectory));
        }
        return dbFileEntryMap;
    }

    private Map<String, Map<File, DatabaseEntryDescriptor>> loadGlobalDbSnapshots() {
        Map<String, Map<File, DatabaseEntryDescriptor>> globalDbSnapshotMap = new HashMap<>();
        globalDatabaseDirectories.stream().forEach((globalDatabaseDirectory) -> {
            try {
                if (globalDatabaseDirectory.canWrite()) {
                    globalDbSnapshotMap.put(globalDatabaseDirectory.getName(), loadDbSnapshotAsDBEntryDescriptors(globalDatabaseDirectory));
                } else {
                    logger.warn("Skip loading of global Database[" + globalDatabaseDirectory.getAbsolutePath() + "] because directory is write protected!");
                }
            } catch (CouldNotPerformException ex) {
                ExceptionPrinter.printHistory("Could not load db entries out of " + globalDatabaseDirectory.getAbsolutePath(), ex, logger);
            }
        });
        return globalDbSnapshotMap;
    }

    private void storeDbSnapshot(final Map<File, JsonObject> dbFileEntryMap) throws CouldNotPerformException {
        try {
            for (Entry<File, JsonObject> dbEntry : dbFileEntryMap.entrySet()) {
                storeEntry(formatEntryToHumanReadableString(dbEntry.getValue()), dbEntry.getKey());
            }
        } catch (CouldNotPerformException ex) {
            throw new CouldNotPerformException("Could not store db snapshot!", ex);
        }
    }

    private void storeDbSnapshotASDBEntryDescriptors(final Map<File, DatabaseEntryDescriptor> dbFileEntryMap) throws CouldNotPerformException {
        try {
            for (Entry<File, DatabaseEntryDescriptor> dbEntry : dbFileEntryMap.entrySet()) {
                storeEntry(formatEntryToHumanReadableString(dbEntry.getValue().getEntry()), dbEntry.getKey());
            }
        } catch (CouldNotPerformException ex) {
            throw new CouldNotPerformException("Could not store db snapshot!", ex);
        }
    }

    private void storeGlobalDbSnapshots(final Map<String, Map<File, DatabaseEntryDescriptor>> globalDbSnapshots) throws CouldNotPerformException {
        if (globalDbSnapshots == null) {
            // skip because globally databases were never loaded.
            return;
        }

        for (Entry<String, Map<File, DatabaseEntryDescriptor>> entry : globalDbSnapshots.entrySet()) {
            storeDbSnapshotASDBEntryDescriptors(entry.getValue());
        }
    }

    /**
     *
     * @param jsonEntry
     * @return
     * @throws CouldNotPerformException
     */
    public String formatEntryToHumanReadableString(final JsonObject jsonEntry) throws CouldNotPerformException {

        String entryAsString;
        try {
            // format human readable
            entryAsString = jsonEntry.toString();
            JsonElement el = parser.parse(entryAsString);
            entryAsString = gson.toJson(el);
        } catch (Exception ex) {
            throw new CouldNotPerformException("Could not format entry!", ex);
        }
        return entryAsString;
    }

    public void storeEntry(final String entryAsString, final File entryFile) throws CouldNotPerformException {
        try {
            FileUtils.writeStringToFile(entryFile, entryAsString, "UTF-8");
        } catch (Exception ex) {
            throw new CouldNotPerformException("Could not store entry!", ex);
        }
    }

    private JsonObject loadDBEntry(final File entry) throws CouldNotPerformException {
        JsonObject jSonEntry;

        // load entry
        try {
            JsonReader jsonReader = new JsonReader(new StringReader(FileUtils.readFileToString(entry, "UTF-8")));

            // needed to handle protobuf generated malformed json code.
            jsonReader.setLenient(true);

            jSonEntry = new JsonParser().parse(jsonReader).getAsJsonObject();
        } catch (IOException | JsonIOException | JsonSyntaxException | IllegalStateException ex) {
            throw new CouldNotPerformException("Could not load File[" + entry.getAbsolutePath() + "]!", ex);
        }
        return jSonEntry;
    }

    public int getLatestDBVersion() {
        return latestDBVersion;
    }

    /**
     * Method detects the current db version of the given database.
     *
     * @param databaseDirectory
     * @return the current db version as integer.
     * @throws CouldNotPerformException
     */
    public int detectCurrentDBVersion(final File databaseDirectory) throws CouldNotPerformException {
        try {

            // detect file
            File versionFile = new File(databaseDirectory, VERSION_FILE_NAME);

            if (!versionFile.exists()) {
                if (JPService.getProperty(JPInitializeDB.class).getValue()) {
                    return getLatestDBVersion();
                }
                throw new CouldNotPerformException("No version information available! Add \"" + JPInitializeDB.COMMAND_IDENTIFIERS[0] + "\" as registry argument to generate the version information.");
            }

            // load db version
            try {
                String versionAsString = FileUtils.readFileToString(versionFile, "UTF-8");
                versionAsString = versionAsString.replace(VERSION_FILE_WARNING, "");
                JsonObject versionJsonObject = new JsonParser().parse(versionAsString).getAsJsonObject();
                try {
                    return versionJsonObject.get(VERSION_FIELD).getAsInt();
                } catch (JsonSyntaxException ex) {
                    throw new CouldNotPerformException("Field[" + VERSION_FIELD + "] is missing!", ex);
                }
            } catch (IOException | JsonSyntaxException ex) {
                throw new CouldNotPerformException("Could not parse db version information!", ex);
            }
        } catch (CouldNotPerformException | JPServiceException ex) {
            throw new CouldNotPerformException("Could not detect current db version of Database[" + databaseDirectory.getName() + "]!", ex);
        }
    }

    /**
     * Method detects the current db version and returns the version number as integer.
     *
     * @return
     * @throws org.openbase.jul.exception.CouldNotPerformException
     */
    public int detectAndUpgradeCurrentDBVersion() throws CouldNotPerformException {
        try {
            // detect file
            File versionFile = new File(databaseDirectory, VERSION_FILE_NAME);

            if (!versionFile.exists()) {
                if (!JPService.getProperty(JPInitializeDB.class).getValue()) {
                    throw new CouldNotPerformException("No version information available! Add \"" + JPInitializeDB.COMMAND_IDENTIFIERS[0] + "\" as registry argument to generate the version information.");
                }
                upgradeCurrentDBVersion();
            }

            // load db version
            try {
                String versionAsString = FileUtils.readFileToString(versionFile, "UTF-8");
                versionAsString = versionAsString.replace(VERSION_FILE_WARNING, "");
                JsonObject versionJsonObject = new JsonParser().parse(versionAsString).getAsJsonObject();
                return versionJsonObject.get(VERSION_FIELD).getAsInt();
            } catch (IOException | JsonSyntaxException ex) {
                throw new CouldNotPerformException("Could not load Field[" + VERSION_FIELD + "]!", ex);
            }
        } catch (JPServiceException | CouldNotPerformException ex) {
            throw new CouldNotPerformException("Could not detect db version of Database[" + databaseDirectory.getName() + "]!", ex);
        }
    }

    /**
     * Method upgrades the current db version to the latest db version.
     *
     * @throws CouldNotPerformException
     */
    public void upgradeCurrentDBVersion() throws CouldNotPerformException {
        try {

            // detect or create version file
            File versionFile = new File(databaseDirectory, VERSION_FILE_NAME);
            if (!versionFile.exists()) {
                if (!versionFile.createNewFile()) {
                    throw new CouldNotPerformException("Could not create db version file!");
                }
                JsonObject versionJsonObject = new JsonObject();
                versionJsonObject.addProperty(VERSION_FIELD, latestDBVersion);
                FileUtils.writeStringToFile(versionFile, VERSION_FILE_WARNING + formatEntryToHumanReadableString(versionJsonObject), "UTF-8");
                return;
            }

            // upgrade version
            try {
                String versionAsString = FileUtils.readFileToString(versionFile, "UTF-8");
                versionAsString = versionAsString.replace(VERSION_FILE_WARNING, "");
                JsonObject versionJsonObject = new JsonParser().parse(versionAsString).getAsJsonObject();
                versionJsonObject.remove(VERSION_FIELD);
                versionJsonObject.addProperty(VERSION_FIELD, latestDBVersion);
                FileUtils.writeStringToFile(versionFile, VERSION_FILE_WARNING + formatEntryToHumanReadableString(versionJsonObject), "UTF-8");
            } catch (IOException | JsonSyntaxException | CouldNotPerformException ex) {
                throw new CouldNotPerformException("Could not write Field[" + VERSION_FIELD + "]!", ex);
            }

        } catch (IOException | CouldNotPerformException ex) {
            throw new CouldNotPerformException("Could not upgrade current db version of Database[" + databaseDirectory.getName() + "]!", ex);
        }
    }

    /**
     * Method returns a db entry converter pipeline which supports the db entry version conversion from the current to the target version.
     *
     * @param currentVersion
     * @param targetVersion
     * @return
     * @throws CouldNotPerformException
     */
    public List<DBVersionConverter> getDBConverterPipeline(final int currentVersion, final int targetVersion) throws CouldNotPerformException {
        return converterPipeline.subList(currentVersion, targetVersion);
    }

    private List<DBVersionConverter> loadDBConverterPipelineAndDetectLatestVersion(final Package converterPackage) throws CouldNotPerformException {
        List<DBVersionConverter> converterList = new ArrayList<>();

        int version = 0;
        String converterClassName = "";
        Class<DBVersionConverter> converterClass;
        Constructor<DBVersionConverter> converterConstructor;
        try {
            while (true) {
                try {
                    converterClassName = converterPackage.getName() + "." + entryType + "_" + version + "_To_" + (version + 1) + "_DBConverter";
                    converterClass = (Class<DBVersionConverter>) Class.forName(converterClassName);
                    converterConstructor = converterClass.getConstructor(DBVersionControl.class);
                    converterList.add(converterConstructor.newInstance(this));
                    version++;
                } catch (ClassNotFoundException | NoSuchMethodException | SecurityException | InvocationTargetException ex) {
                    logger.debug("Could not load Converter[" + converterClassName + "] so latest db version should be " + version + ".", ex);
                    break;
                }
            }
            latestDBVersion = version;
            return converterList;
        } catch (java.lang.InstantiationException | IllegalAccessException ex) {
            throw new CouldNotPerformException("Could not load converter db pipeline of Package[" + converterPackage.getName() + "]!", ex);
        }
    }

    /**
     * Loads a consistency handler list which is used for consistency reconstruction after a db upgrade.
     *
     * @param registry
     * @return the consistency handler list.
     * @throws CouldNotPerformException
     */
    public List<ConsistencyHandler> loadDBVersionConsistencyHandlers(final FileSynchronizedRegistry registry) throws CouldNotPerformException {
        List<ConsistencyHandler> consistencyHandlerList = new ArrayList<>();
        String consistencyHandlerPackage = converterPackage.getName() + ".consistency";

        Set<String> executedHandlerList = detectExecutedVersionConsistencyHandler();

        String consistencyHandlerName = null;
        Class<? extends AbstractVersionConsistencyHandler> consistencyHandlerClass;
        Constructor<? extends ConsistencyHandler> constructor;
        try {
            for (int version = versionOnStart; version <= latestDBVersion; version++) {
                try {
                    consistencyHandlerName = entryType + "_" + version + "_VersionConsistencyHandler";

                    // filter already applied handler
                    if (executedHandlerList.contains(consistencyHandlerName)) {
                        continue;
                    }

                    // load handler
                    consistencyHandlerClass = (Class<? extends AbstractVersionConsistencyHandler>) Class.forName(consistencyHandlerPackage + "." + consistencyHandlerName);
                } catch (ClassNotFoundException ex) {
                    logger.debug("No ConsistencyHandler[" + consistencyHandlerName + "] implemented for Version[" + version + "].", ex);
                    continue;
                }
                constructor = consistencyHandlerClass.getConstructor(getClass(), FileSynchronizedRegistry.class);
                ConsistencyHandler newInstance = constructor.newInstance(this, registry);
                consistencyHandlerList.add(newInstance);
            }
            return consistencyHandlerList;
        } catch (IllegalAccessException | NoSuchMethodException | InvocationTargetException | java.lang.InstantiationException | IllegalArgumentException ex) {
            throw new CouldNotPerformException("Could not load consistencyHandler of Package[" + consistencyHandlerPackage + "]!", ex);
        }
    }

    /**
     * Method upgrades the applied db version consistency handler list of the version file.
     *
     * @param versionConsistencyHandler
     * @throws CouldNotPerformException
     */
    public void registerConsistencyHandlerExecution(final ConsistencyHandler versionConsistencyHandler) throws CouldNotPerformException {
        try {

            // detect version file
            File versionFile = new File(databaseDirectory, VERSION_FILE_NAME);
            if (!versionFile.exists()) {
                throw new NotAvailableException("version db file");
            }

            // load db version
            JsonObject versionJsonObject;
            try {
                String versionAsString = FileUtils.readFileToString(versionFile, "UTF-8");
                versionAsString = versionAsString.replace(VERSION_FILE_WARNING, "");
                versionJsonObject = new JsonParser().parse(versionAsString).getAsJsonObject();
            } catch (IOException | JsonSyntaxException ex) {
                throw new CouldNotPerformException("Could not load version file!", ex);
            }

            // register handler
            try {
                JsonArray consistencyHandlerJsonArray = versionJsonObject.getAsJsonArray(APPLIED_VERSION_CONSISTENCY_HANDLER_FIELD);

                // create if not exists.
                if (consistencyHandlerJsonArray == null) {
                    consistencyHandlerJsonArray = new JsonArray();
                    versionJsonObject.add(APPLIED_VERSION_CONSISTENCY_HANDLER_FIELD, consistencyHandlerJsonArray);
                }

                String versionConsistencyHandlerName = versionConsistencyHandler.getClass().getSimpleName();
                for (int i = 0; i < consistencyHandlerJsonArray.size(); ++i) {
                    if (consistencyHandlerJsonArray.get(i).getAsString().equals(versionConsistencyHandlerName)) {
                        return;
                    }
                }
                consistencyHandlerJsonArray.add(versionConsistencyHandler.getClass().getSimpleName());
                FileUtils.writeStringToFile(versionFile, VERSION_FILE_WARNING + formatEntryToHumanReadableString(versionJsonObject), "UTF-8");
            } catch (CouldNotPerformException | IOException ex) {
                throw new CouldNotPerformException("Could not write Field[" + APPLIED_VERSION_CONSISTENCY_HANDLER_FIELD + "]!", ex);
            }

        } catch (CouldNotPerformException ex) {
            throw new CouldNotPerformException("Could not register ConsistencyHandler[" + versionConsistencyHandler.getClass().getSimpleName() + "] in current db version config of Database[" + databaseDirectory.getName() + "]!", ex);
        }
    }

    /**
     * Method detects the already executed version consistency handler which are registered within the db version file.
     *
     * @return
     * @throws org.openbase.jul.exception.CouldNotPerformException
     */
    public Set<String> detectExecutedVersionConsistencyHandler() throws CouldNotPerformException {
        try {
            // detect file
            File versionFile = new File(databaseDirectory, VERSION_FILE_NAME);

            if (!versionFile.exists()) {
                throw new CouldNotPerformException("No version information available! Add \"" + JPInitializeDB.COMMAND_IDENTIFIERS[0] + "\" as registry argument to generate the version information.");
            }

            // load db version
            try {
                String versionAsString = FileUtils.readFileToString(versionFile, "UTF-8");
                versionAsString = versionAsString.replace(VERSION_FILE_WARNING, "");
                JsonObject versionJsonObject = new JsonParser().parse(versionAsString).getAsJsonObject();
                Set<String> handlerList = new HashSet<>();
                JsonArray consistencyHandlerJsonArray = versionJsonObject.getAsJsonArray(APPLIED_VERSION_CONSISTENCY_HANDLER_FIELD);
                if (consistencyHandlerJsonArray != null) {
                    consistencyHandlerJsonArray.forEach(entry -> handlerList.add(entry.getAsString()));
                }
                return handlerList;
            } catch (IOException | JsonSyntaxException ex) {
                throw new CouldNotPerformException("Could not load Field[" + APPLIED_VERSION_CONSISTENCY_HANDLER_FIELD + "]!", ex);
            }
        } catch (CouldNotPerformException ex) {
            throw new CouldNotPerformException("Could not detect db version of Database[" + databaseDirectory.getName() + "]!", ex);
        }
    }

    private List<File> detectGlobalDatabaseDirectories() throws CouldNotPerformException {
        ArrayList<File> globalDatabaseDirectoryList = new ArrayList<>();
        try {
            FileFilter dbFilter = new DirectoryFileFilter() {

                @Override
                public boolean accept(File file) {
                    return super.accept(file) && !file.equals(databaseDirectory);
                }
            };

            globalDatabaseDirectoryList.addAll(Arrays.asList(JPService.getProperty(JPDatabaseDirectory.class).getValue().listFiles(dbFilter)));
            return globalDatabaseDirectoryList;
        } catch (JPServiceException ex) {
            throw new CouldNotPerformException("Could not detect neighbout databases!", ex);
        }
    }

    public Package getConverterPackage() {
        return converterPackage;
    }

    public List<DBVersionConverter> getConverterPipeline() {
        return converterPipeline;
    }

    public FileProvider getEntryFileProvider() {
        return entryFileProvider;
    }

    public File getDatabaseDirectory() {
        return databaseDirectory;
    }

}
