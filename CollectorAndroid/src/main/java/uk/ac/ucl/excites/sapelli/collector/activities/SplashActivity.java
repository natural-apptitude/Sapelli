package uk.ac.ucl.excites.sapelli.collector.activities;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.os.EnvironmentCompat;

import com.crashlytics.android.Crashlytics;
import com.crashlytics.android.core.CrashlyticsCore;
import com.facebook.stetho.InspectorModulesProvider;
import com.facebook.stetho.Stetho;
import com.facebook.stetho.inspector.database.DatabaseFilesProvider;
import com.facebook.stetho.inspector.database.DefaultDatabaseConnectionProvider;
import com.facebook.stetho.inspector.database.SqliteDatabaseDriver;
import com.facebook.stetho.inspector.protocol.ChromeDevtoolsDomain;

import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.List;

import gr.michalisvitos.timberutils.CrashlyticsTree;
import gr.michalisvitos.timberutils.DebugTree;
import io.fabric.sdk.android.Fabric;
import pub.devrel.easypermissions.AfterPermissionGranted;
import pub.devrel.easypermissions.EasyPermissions;
import timber.log.Timber;
import uk.ac.ucl.excites.sapelli.collector.BuildConfig;
import uk.ac.ucl.excites.sapelli.collector.BuildInfo;
import uk.ac.ucl.excites.sapelli.collector.CollectorApp;
import uk.ac.ucl.excites.sapelli.collector.R;
import uk.ac.ucl.excites.sapelli.collector.db.CollectorPreferences;
import uk.ac.ucl.excites.sapelli.collector.db.FileStorageHelper;
import uk.ac.ucl.excites.sapelli.collector.io.AndroidFileStorageProvider;
import uk.ac.ucl.excites.sapelli.collector.io.FileStorageProvider;
import uk.ac.ucl.excites.sapelli.collector.io.FileStorageRemovedException;
import uk.ac.ucl.excites.sapelli.collector.io.FileStorageUnavailableException;
import uk.ac.ucl.excites.sapelli.collector.util.CrashReporter;
import uk.ac.ucl.excites.sapelli.collector.util.ProjectRunHelpers;
import uk.ac.ucl.excites.sapelli.shared.io.FileHelpers;
import uk.ac.ucl.excites.sapelli.shared.io.FileStorageException;
import uk.ac.ucl.excites.sapelli.shared.util.android.Debug;
import uk.ac.ucl.excites.sapelli.shared.util.android.DeviceControl;
import uk.ac.ucl.excites.sapelli.storage.db.sql.sqlite.SQLiteRecordStore;

import static uk.ac.ucl.excites.sapelli.collector.CollectorApp.DATABASE_BASENAME;

public class SplashActivity extends BaseActivity implements EasyPermissions.PermissionCallbacks {

    // STATICS------------------------------------------------------------
    static protected final String TAG = "CollectorApp";

    static private final String CRASHLYTICS_VERSION_INFO = "VERSION_INFO";
    static private final String CRASHLYTICS_BUILD_INFO = "BUILD_INFO";
    static private final int PERMISSIONS_REQUEST = 123;
    String[] perms = {
            Manifest.permission.CAMERA,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.READ_EXTERNAL_STORAGE
    };
    private CollectorApp app;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

        if (!isAllPermissionsGranted()) {
            requestAllPermissions();
            return;
        }

        initializations();
    }

    private void requestAllPermissions() {
        EasyPermissions.requestPermissions(this, null,
                PERMISSIONS_REQUEST, perms);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        EasyPermissions.onRequestPermissionsResult(requestCode, permissions, grantResults, this);
    }

    private boolean isAllPermissionsGranted() {
        return EasyPermissions.hasPermissions(this, perms);
    }

    @AfterPermissionGranted(PERMISSIONS_REQUEST)
    private void initializations() {
        init();
//
        // Create shortcut to Sapelli Collector on Home Screen:
        if (getCollectorApp().getPreferences().isFirstInstallation()) {
            // Create shortcut
            ProjectRunHelpers.createCollectorShortcut(getApplicationContext());
            // Set first installation to false
            getCollectorApp().getPreferences().setFirstInstallation(false);
        }

        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                startActivity(new Intent(SplashActivity.this, ProjectManagerActivity.class));
                finish();
            }
        }, 1000);
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        // Debug.d(newConfig.toString());
    }

    public CollectorApp getCollectorApp() {
        if (app == null)
            app = (CollectorApp) getApplication();
        return app;
    }

    public CollectorApp.AndroidCollectorClient getCollectorClient() {
        return getCollectorApp().collectorClient;
    }

    @Override
    public void onPermissionsGranted(int requestCode, @NonNull List<String> perms) {
    }

    @Override
    public void onPermissionsDenied(int requestCode, @NonNull List<String> perms) {
        recreate();
    }

    public static enum StorageStatus {
        UNKNOWN, STORAGE_OK, STORAGE_UNAVAILABLE, STORAGE_REMOVED
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
}
