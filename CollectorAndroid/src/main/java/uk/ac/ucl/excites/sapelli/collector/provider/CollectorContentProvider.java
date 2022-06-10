package uk.ac.ucl.excites.sapelli.collector.provider;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.database.SQLException;

import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteQueryBuilder;
import android.os.Build;
import android.os.Environment;
import android.os.ParcelFileDescriptor;
import android.util.Log;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.core.os.EnvironmentCompat;
import uk.ac.ucl.excites.sapelli.collector.BuildConfig;
import uk.ac.ucl.excites.sapelli.collector.CollectorApp;
import uk.ac.ucl.excites.sapelli.collector.CollectorClient;
import uk.ac.ucl.excites.sapelli.collector.activities.SplashActivity;
import uk.ac.ucl.excites.sapelli.collector.db.CollectorPreferences;
import uk.ac.ucl.excites.sapelli.collector.db.CollectorSQLRecordStoreUpgrader;
import uk.ac.ucl.excites.sapelli.collector.db.FileStorageHelper;
import uk.ac.ucl.excites.sapelli.collector.db.ProjectRecordStore;
import uk.ac.ucl.excites.sapelli.collector.db.ProjectStore;
import uk.ac.ucl.excites.sapelli.collector.io.AndroidFileStorageProvider;
import uk.ac.ucl.excites.sapelli.collector.io.FileStorageProvider;
import uk.ac.ucl.excites.sapelli.collector.io.FileStorageRemovedException;
import uk.ac.ucl.excites.sapelli.collector.io.FileStorageUnavailableException;
import uk.ac.ucl.excites.sapelli.collector.model.Field;
import uk.ac.ucl.excites.sapelli.collector.model.Form;
import uk.ac.ucl.excites.sapelli.collector.model.MediaFile;
import uk.ac.ucl.excites.sapelli.collector.model.Project;
import uk.ac.ucl.excites.sapelli.collector.model.ProjectDescriptor;
import uk.ac.ucl.excites.sapelli.collector.model.fields.AudioField;
import uk.ac.ucl.excites.sapelli.collector.model.fields.MediaField;
import uk.ac.ucl.excites.sapelli.shared.db.StoreHandle;
import uk.ac.ucl.excites.sapelli.shared.db.exceptions.DBException;
import uk.ac.ucl.excites.sapelli.shared.io.FileHelpers;
import uk.ac.ucl.excites.sapelli.shared.io.FileStorageException;
import uk.ac.ucl.excites.sapelli.shared.util.ExceptionHelpers;
import uk.ac.ucl.excites.sapelli.shared.util.android.DeviceControl;
import uk.ac.ucl.excites.sapelli.storage.db.RecordStore;
import uk.ac.ucl.excites.sapelli.storage.db.sql.SQLRecordStoreUpgrader;
import uk.ac.ucl.excites.sapelli.storage.db.sql.sqlite.android.AndroidSQLiteRecordStore;
import uk.ac.ucl.excites.sapelli.storage.model.Column;
import uk.ac.ucl.excites.sapelli.storage.model.ColumnSet;
import uk.ac.ucl.excites.sapelli.storage.model.Record;
import uk.ac.ucl.excites.sapelli.storage.model.RecordValueSet;
import uk.ac.ucl.excites.sapelli.storage.model.Schema;
import uk.ac.ucl.excites.sapelli.storage.model.ValueSet;
import uk.ac.ucl.excites.sapelli.storage.model.ValueSetColumn;
import uk.ac.ucl.excites.sapelli.storage.queries.RecordsQuery;
import uk.ac.ucl.excites.sapelli.storage.queries.SingleRecordQuery;
import uk.ac.ucl.excites.sapelli.storage.queries.sources.Source;
import uk.ac.ucl.excites.sapelli.storage.util.ColumnPointer;
import uk.ac.ucl.excites.sapelli.storage.visitors.SimpleSchemaTraverser;

public class CollectorContentProvider extends ContentProvider implements StoreHandle.StoreUser {

    static final String PROVIDER_AUTHORITY = "uk.ac.ucl.excites.sapelli.collector.provider";

    private FileStorageProvider fileStorageProvider;
    public final AndroidCollectorClient collectorClient = new AndroidCollectorClient();

    private ProjectRecordStore projectRecordStore;
    private RecordStore recordStore;

    private CollectorApp app;
    private Context context;

    static final int PROJECTS = 1;
    static final int PROJECT_FILE = 2;
    static final int PROJECT_SUBFOLDER_FILE = 3;
    static final int RECORDS = 4;
    static final int ATTACHMENT = 5;

    private final List<ColumnPointer<?>> columnPointers = new ArrayList<ColumnPointer<?>>();

    private static HashMap<String, String> PROJECTS_PROJECTION_MAP;

//    private SQLiteDatabase db;
    static final String DATABASE_NAME = "Sapelli-RecordStore.sqlite3";

    static final UriMatcher uriMatcher;

    static {
        uriMatcher = new UriMatcher(UriMatcher.NO_MATCH);
        uriMatcher.addURI(PROVIDER_AUTHORITY, "projects", PROJECTS);
        uriMatcher.addURI(PROVIDER_AUTHORITY, "projects/#/file/*", PROJECT_FILE);
        uriMatcher.addURI(PROVIDER_AUTHORITY, "projects/#/file/*/*", PROJECT_SUBFOLDER_FILE);
        uriMatcher.addURI(PROVIDER_AUTHORITY, "projects/#/records", RECORDS);
        uriMatcher.addURI(PROVIDER_AUTHORITY, "projects/#/attachment/*", ATTACHMENT);
    }


    public CollectorContentProvider() {
    }

//    private static class DatabaseHelper extends SQLiteOpenHelper {
//        DatabaseHelper(Context context) {
//            super(context, DATABASE_NAME, null, 3);
//        }
//
//        @Override
//        public void onCreate(SQLiteDatabase sqLiteDatabase) {
//
//        }
//
//        @Override
//        public void onUpgrade(SQLiteDatabase sqLiteDatabase, int i, int i1) {
//
//        }
//    }

    private void initialise(){
        if(fileStorageProvider != null){
            return;
        }

        fileStorageProvider = FileStorageHelper.initialiseFileStorage(app,context);

        try {
            projectRecordStore = new ProjectRecordStore(collectorClient, fileStorageProvider);
            recordStore = new AndroidSQLiteRecordStore(collectorClient, getContext(), fileStorageProvider.getDBFolder(false), CollectorApp.DATABASE_BASENAME, CollectorClient.CURRENT_COLLECTOR_RECORDSTORE_VERSION, null);
            recordStore.initialise();
        } catch (DBException e) {
            e.printStackTrace();
        }
    }

    @Override
    public boolean onCreate() {
        context = getContext();
        app = (CollectorApp) context.getApplicationContext();
        app.setPreferences(new CollectorPreferences(app));
        return true;
    }

    private MatrixCursor projectListToCursor(List<Project> projects) {
        MatrixCursor cursor = new MatrixCursor(new String[]{"id", "name", "fingerprint"});
        for (Project project : projects) {
            cursor.newRow()
                    .add("name", project.getName())
                    .add("id", project.getID())
                    .add("fingerprint", project.getFingerPrint());
        }
        return cursor;
    }

    private MatrixCursor recordsListToCursor(List<Record> records, Project project) {
        Form form = project.getStartForm();
        Schema schema = form.getSchema();

        List<String> columnNames = new ArrayList<String>();
        List<Column<?>> columns = schema.getColumns(true);
        for (Column column : columns) {
            columnNames.add(column.getName());
        }

        MatrixCursor cursor = new MatrixCursor(columnNames.toArray(new String[columnNames.size()]));

        for(Record record: records){
            MatrixCursor.RowBuilder recordRow = cursor.newRow();
            for (Column column : columns) {
                    recordRow.add(column.getName(), column.retrieveValueAsString(record, true));
            }
        }
        return cursor;
    }

    private Project getProject(int projectId){
        List<Project> projects = projectRecordStore.retrieveProjects();
        for(Project project: projects){
            if(project.getID() == projectId){
                return project;
            }
        }
        return null;
    }

    @RequiresApi(api = Build.VERSION_CODES.KITKAT)
    @Override
    public Cursor query(Uri uri, String[] projection, String selection,
                        String[] selectionArgs, String sortOrder) {
        initialise();
        MatrixCursor cursor = null;
        List<String> path = uri.getPathSegments();
        Project project = null;

        int match = uriMatcher.match(uri);

        if (match > -1 && match != PROJECTS) {
            Integer projectID = Integer.parseInt(path.get(1));
            project = getProject(projectID);
        }

        switch (match) {
            case PROJECTS:
                List<Project> projects = projectRecordStore.retrieveProjects();
                cursor = projectListToCursor(projects);
                break;
            case RECORDS:
                RecordsQuery query = new RecordsQuery(Source.From(project.getStartForm().getSchema()));
                List<Record> records = recordStore.retrieveRecords(query);
                cursor = recordsListToCursor(records,project);
                break;
        }

        if(cursor != null) {
            cursor.setNotificationUri(getContext().getContentResolver(), uri);
        }

        return cursor;
    }

    @Nullable
    @Override
    public ParcelFileDescriptor openFile(@NonNull Uri uri, @NonNull String mode) throws FileNotFoundException {
        // project/<id>/file/<folder?>/file
        initialise();

        int match = uriMatcher.match(uri);
        List<String> path = uri.getPathSegments();

        String filename;
        if(path.size() == 4){
            filename = path.get(3);
        } else {
            filename = path.get(3) + "/" + path.get(4);
        }
        Integer projectID = Integer.parseInt(path.get(1));
        Project project = getProject(projectID);

        if(project == null){
            throw new FileNotFoundException("Unknown project");
        }

        switch(match){
            case PROJECT_FILE:
            case PROJECT_SUBFOLDER_FILE:
                File folder = fileStorageProvider.getProjectInstallationFolder(project.getName(),project.getVariant(),project.getVersion(),false);
                File xml = new File(folder.getAbsolutePath(), filename);
                return ParcelFileDescriptor.open(xml, ParcelFileDescriptor.MODE_READ_ONLY);
            case ATTACHMENT:
                ProjectDescriptor projectDescriptor = new ProjectDescriptor(project.getID(),project.getName(),project.getVariant(),project.getVersion(),project.getFingerPrint());
                File projectAttachmentFolder = fileStorageProvider.getProjectAttachmentFolder(projectDescriptor,false);
                File attachment = new File(projectAttachmentFolder.getAbsolutePath(), filename);
                return ParcelFileDescriptor.open(attachment, ParcelFileDescriptor.MODE_READ_ONLY);
            default:
                throw new IllegalStateException("Unexpected value: " + match);
        }
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection,
                      String[] selectionArgs) {
        // TODO: Implement this to handle requests to update one or more rows.
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        // TODO: Implement this to handle requests to insert a new row.
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        // Implement this to handle requests to delete one or more rows.
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public String getType(Uri uri) {
        switch (uriMatcher.match(uri)) {
            case PROJECTS:
                return "vnd.android.cursor.dir/vnd.collector/projects";
            default:
                throw new IllegalArgumentException("Unsupported URI: " + uri);
        }
    }


    /**
     * Create a "Test.db" in the default location of Android. Use this to get the directory where
     * Android stores by default the SQLite database
     */
    public class TestDatabaseHelper extends SQLiteOpenHelper {
        private static final String DATABASE_NAME = "Test.db";
        private static final int DATABASE_VERSION = 1;
        private Context context;

        public TestDatabaseHelper(Context context) {
            super(context, DATABASE_NAME, null, DATABASE_VERSION);
            this.context = context;
        }

        @Override
        public void onCreate(SQLiteDatabase sqLiteDatabase) {
            // Do nothing
        }

        @Override
        public void onUpgrade(SQLiteDatabase sqLiteDatabase, int i, int i1) {
            // Do nothing
        }

        public File getDatabaseFolder() {
            return context.getDatabasePath(DATABASE_NAME).getParentFile();
        }
    }

    public final class AndroidCollectorClient extends CollectorClient implements SQLRecordStoreUpgrader.UpgradeCallback {

        private int oldDatabaseVersion = CURRENT_COLLECTOR_RECORDSTORE_VERSION;
        private List<String> upgradeWarnings = Collections.<String>emptyList();

        @Override
        public FileStorageProvider getFileStorageProvider() {
            return fileStorageProvider;
        }

        @Override
        protected void createAndSetRecordStore(StoreHandle.StoreSetter<RecordStore> setter) throws DBException {
            @SuppressWarnings("resource")
            RecordStore recordStore = new AndroidSQLiteRecordStore(this, getContext(), getFileStorageProvider().getDBFolder(true), CollectorApp.DATABASE_BASENAME, CURRENT_COLLECTOR_RECORDSTORE_VERSION, new CollectorSQLRecordStoreUpgrader(this, this, getFileStorageProvider()));
            //RecordStore recordStore = new DB4ORecordStore(this, getFileStorageProvider().getDBFolder(true), getDemoPrefix() /*will be "" if not in demo mode*/ + DATABASE_BASENAME);
            setter.setAndInitialise(recordStore);

            // Enable logging if in debug mode (will display SQL statements being executed):
            if (BuildConfig.DEBUG)
                recordStore.setLoggingEnabled(true);
        }

        @Override
        protected void createAndSetProjectStore(StoreHandle.StoreSetter<ProjectStore> setter) throws DBException {
            setter.setAndInitialise(new ProjectRecordStore(this, getFileStorageProvider()));
            //setter.setAndInitialise(new PrefProjectStore(CollectorApp.this, getFileStorageProvider(), getDemoPrefix()));
            //setter.setAndInitialise(new DB4OProjectStore(getFileStorageProvider().getDBFolder(true), getDemoPrefix() /*will be "" if not in demo mode*/ + "ProjectStore"));
        }

        @Override
        public void upgradePerformed(int fromVersion, int toVersion, List<String> warnings) {
            oldDatabaseVersion = fromVersion;
            upgradeWarnings = warnings;
        }

        public boolean hasDatabaseBeenUpgraded() {
            return oldDatabaseVersion != CURRENT_COLLECTOR_RECORDSTORE_VERSION;
        }

        /**
         * @return the oldDatabaseVersion
         */
        public final int getOldDatabaseVersion() {
            return oldDatabaseVersion;
        }

        /**
         * @return the upgradeWarnings
         */
        public final List<String> getUpgradeWarnings() {
            return upgradeWarnings;
        }

        public final void forgetAboutUpgrade() {
            oldDatabaseVersion = CURRENT_COLLECTOR_RECORDSTORE_VERSION;
            upgradeWarnings = Collections.<String>emptyList();
        }

        @Override
        public void logError(String msg, Throwable throwable) {
            if (throwable != null)
                Log.e(getClass().getSimpleName(), msg, throwable);
            else
                Log.e(getClass().getSimpleName(), msg);
        }

        @Override
        public void logWarning(String msg) {
            Log.w(getClass().getSimpleName(), msg);
        }

        @Override
        public void logInfo(String msg) {
            Log.i(getClass().getSimpleName(), msg);
        }
    }

    public final class RecordParser extends SimpleSchemaTraverser {

        @Override
        public void visit(ColumnPointer<?> leafColumnPointer) {

        }

        @Override
        public boolean splitLocationTraversal() {
            return false;
        }

        @Override
        public boolean splitOrientationTraversal() {
            return false;
        }

        @Override
        public boolean splitForeignKeyTraversal() {
            return false;
        }

        @Override
        public boolean skipNonBinarySerialisedLocationSubColumns() {
            return false;
        }

        @Override
        public boolean skipNonBinarySerialisedOrientationSubColumns() {
            return false;
        }

        @Override
        public boolean includeVirtualColumns() {
            return false;
        }
    }
}