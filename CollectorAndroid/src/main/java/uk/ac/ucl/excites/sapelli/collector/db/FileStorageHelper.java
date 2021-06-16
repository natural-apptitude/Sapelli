package uk.ac.ucl.excites.sapelli.collector.db;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.os.Environment;

import androidx.core.os.EnvironmentCompat;

import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.channels.FileChannel;

import timber.log.Timber;
import uk.ac.ucl.excites.sapelli.collector.CollectorApp;
import uk.ac.ucl.excites.sapelli.collector.activities.SplashActivity;
import uk.ac.ucl.excites.sapelli.collector.io.AndroidFileStorageProvider;
import uk.ac.ucl.excites.sapelli.collector.io.FileStorageProvider;
import uk.ac.ucl.excites.sapelli.collector.io.FileStorageRemovedException;
import uk.ac.ucl.excites.sapelli.collector.io.FileStorageUnavailableException;
import uk.ac.ucl.excites.sapelli.shared.io.FileHelpers;
import uk.ac.ucl.excites.sapelli.shared.io.FileStorageException;
import uk.ac.ucl.excites.sapelli.shared.util.android.DeviceControl;
import uk.ac.ucl.excites.sapelli.storage.db.sql.sqlite.SQLiteRecordStore;

import static uk.ac.ucl.excites.sapelli.collector.CollectorApp.DATABASE_BASENAME;

public class FileStorageHelper {

    static public FileStorageProvider initialiseFileStorage(CollectorApp app, Context context) throws FileStorageException {
        File sapelliFolder = null;
        // Try to get Sapelli folder path from preferences:
        try {
            sapelliFolder = new File(app.getPreferences().getSapelliFolderPath());
        } catch (NullPointerException npe) {
        }

        // Did we get the folder path from preferences? ...
        if (sapelliFolder == null) {    // No: first installation or reset

            // Find appropriate files dir (using application-specific folder, which is removed upon app uninstall!):
            File[] paths = DeviceControl.getExternalFilesDirs(context, null);
            if (paths != null && paths.length != 0) {
                // We count backwards because we prefer secondary external storage (which is likely to be on an SD card rather unremovable memory)
                for (int p = paths.length - 1; p >= 0; p--)
                    if (isMountedReadableWritableDir(paths[p])) {
                        sapelliFolder = paths[p];
                        break;
                    }
            }

            // Do we have a path?
            if (sapelliFolder != null)
                // Yes: store it in the preferences:
                app.getPreferences().setSapelliFolder(sapelliFolder.getAbsolutePath());
            else
                // No :-(
                throw new FileStorageUnavailableException();
        } else {    // Yes, we got path from preferences, check if it is available ...
            if (!isMountedReadableWritableDir(sapelliFolder)) // (will also attempt to create the directory if it doesn't exist)
                // No :-(
                throw new FileStorageRemovedException(sapelliFolder.getAbsolutePath());
        }

        // If we get here this means we have a non-null sapelliFolder object representing an accessible path...

        // Try to get the Android Downloads folder...
        File downloadsFolder = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        if (!isMountedReadableWritableDir(downloadsFolder)) // check if we can access it (will also attempt to create the directory if it doesn't exist)
            // No :-(
            throw new FileStorageException("Cannot access downloads folder: " + downloadsFolder.getAbsolutePath());

        // Create a test database to get the DB folder
        FileStorageHelper.TestDatabaseHelper testDatabaseHelper = new FileStorageHelper.TestDatabaseHelper(context);

        // Try to get the Databases folder...
        File databaseFolder = testDatabaseHelper.getDatabaseFolder();
        if (!isMountedReadableWritableDir(databaseFolder)) // check if we can access it (will also attempt to create the directory if it doesn't exist)
            // No :-(
            throw new FileStorageException("Cannot access database folder: " + databaseFolder.getAbsolutePath());

        final AndroidFileStorageProvider androidFileStorageProvider = new AndroidFileStorageProvider(sapelliFolder, databaseFolder, downloadsFolder);

        moveDB(androidFileStorageProvider);

        return androidFileStorageProvider; // Android specific subclass of FileStorageProvider, which generates .nomedia files
    }

    private static void moveDB(AndroidFileStorageProvider androidFileStorageProvider) {

        final File oldDBFolder = androidFileStorageProvider.getOldDBFolder(false);
        final File nedDBFolder = androidFileStorageProvider.getDBFolder(false);
        Timber.d("Old DB path: %s", oldDBFolder);
        Timber.d("New DB path: %s", nedDBFolder);

        try {
            File oldDB = new File(SQLiteRecordStore.GetDBFileName(oldDBFolder.getAbsolutePath() + File.separator + DATABASE_BASENAME));
            File newDB = new File(nedDBFolder + File.separator + oldDB.getName());

            if (!newDB.exists())
                newDB.createNewFile();

            if (oldDB.exists() && newDB.exists()) {
                Timber.d("Move Old DB: %s to %s", oldDB, newDB);

                FileChannel src = new FileInputStream(oldDB).getChannel();
                FileChannel dst = new FileOutputStream(newDB).getChannel();
                dst.transferFrom(src, 0, src.size());
                src.close();
                dst.close();

                // Delete the old DB
                FileUtils.deleteQuietly(oldDB);

                // Delete all other files in the old DB e.g. the journal etc.
                for (File file : oldDBFolder.listFiles())
                    FileUtils.deleteQuietly(file);

                // Finally delete the old directory
                FileUtils.deleteQuietly(oldDBFolder);
            }
        } catch (Exception e) {
            Timber.e(e);
        }
    }

    private static boolean isMountedReadableWritableDir(File dir) throws FileStorageException {
        try {
            return    // Null check:
                    (dir != null)
                            // Try to create the directory if it is not there
                            && FileHelpers.createDirectory(dir)
                            /* Check storage state, accepting both MEDIA_MOUNTED and MEDIA_UNKNOWN.
                             * 	The MEDIA_UNKNOWN state occurs when a path isn't backed by known storage media; e.g. the SD Card on
                             * the Samsung Xcover 2 (the detection of which we have to force in DeviceControl#getExternalFilesDirs()). */
                            && (Environment.MEDIA_MOUNTED.equals(EnvironmentCompat.getStorageState(dir)) || EnvironmentCompat.MEDIA_UNKNOWN.equals(EnvironmentCompat.getStorageState(dir)))
                            // Check whether we have read & write access to the directory:
                            && FileHelpers.isReadableWritableDirectory(dir);
        } catch (Exception e) {
            throw new FileStorageException("Unable to create or determine status of directory: " + (dir != null ? dir.getAbsolutePath() : "null"), e);
        }
    }

    private static class TestDatabaseHelper extends SQLiteOpenHelper {
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
}
