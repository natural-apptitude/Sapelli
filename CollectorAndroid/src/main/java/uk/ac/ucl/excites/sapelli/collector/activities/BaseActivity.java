/**
 * Sapelli data collection platform: http://sapelli.org
 * 
 * Copyright 2012-2016 University College London - ExCiteS group
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *     http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and 
 * limitations under the License.
 */

package uk.ac.ucl.excites.sapelli.collector.activities;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import android.util.Log;

import com.crashlytics.android.Crashlytics;
import com.crashlytics.android.core.CrashlyticsCore;
import com.facebook.stetho.InspectorModulesProvider;
import com.facebook.stetho.Stetho;
import com.facebook.stetho.inspector.database.DatabaseFilesProvider;
import com.facebook.stetho.inspector.database.DefaultDatabaseConnectionProvider;
import com.facebook.stetho.inspector.database.SqliteDatabaseDriver;
import com.facebook.stetho.inspector.protocol.ChromeDevtoolsDomain;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import gr.michalisvitos.timberutils.CrashlyticsTree;
import gr.michalisvitos.timberutils.DebugTree;
import io.fabric.sdk.android.Fabric;
import timber.log.Timber;
import uk.ac.ucl.excites.sapelli.collector.BuildConfig;
import uk.ac.ucl.excites.sapelli.collector.BuildInfo;
import uk.ac.ucl.excites.sapelli.collector.CollectorApp;
import uk.ac.ucl.excites.sapelli.collector.CollectorApp.AndroidCollectorClient;
import uk.ac.ucl.excites.sapelli.collector.R;
import uk.ac.ucl.excites.sapelli.collector.db.CollectorPreferences;
import uk.ac.ucl.excites.sapelli.collector.db.FileStorageHelper;
import uk.ac.ucl.excites.sapelli.collector.io.FileStorageProvider;
import uk.ac.ucl.excites.sapelli.collector.io.FileStorageRemovedException;
import uk.ac.ucl.excites.sapelli.collector.io.FileStorageUnavailableException;
import uk.ac.ucl.excites.sapelli.collector.util.CrashReporter;
import uk.ac.ucl.excites.sapelli.shared.io.FileStorageException;
import uk.ac.ucl.excites.sapelli.shared.util.android.Debug;
import uk.ac.ucl.excites.sapelli.storage.db.sql.sqlite.SQLiteRecordStore;

/**
 * Abstract super class for our activities.
 * 
 * Provides dialog methods & other shared behaviour.
 * 
 * @author mstevens
 */
public abstract class BaseActivity extends AppCompatActivity
{
	
	static private final int HIDE_BUTTON = -1;
	static private final boolean DEFAULT_FINISH_ON_DIALOG_OK = false;
	static private final boolean DEFAULT_FINISH_ON_DIALOG_CANCEL = false;

	static private final String CRASHLYTICS_VERSION_INFO = "VERSION_INFO";
	static private final String CRASHLYTICS_BUILD_INFO = "BUILD_INFO";

	private CollectorApp app;
	private FileStorageProvider fileStorageProvider;

	@Override
	protected void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		getCollectorApp(); // set app variable
	}
	
	public CollectorApp getCollectorApp()
	{
		if(app == null)
			app = (CollectorApp) getApplication();
		return app;
	}
	
	public AndroidCollectorClient getCollectorClient()
	{
		return getCollectorApp().collectorClient;
	}
	
	public CollectorPreferences getPreferences()
	{
		return getCollectorApp().getPreferences();
	}
	
	@Override
	protected void onResume()
	{
		super.onResume();

		getFileStorageProvider(); // Initialise/check fileStorageProvider
	}

	/**
	 * @return the fileStorageProvider
	 */
	public FileStorageProvider getFileStorageProvider()
	{
		if(fileStorageProvider == null)
		{
			// Check if we can access read/write to the Sapelli folder (created on the SD card or internal mass storage if there is no physical SD card):
			try
			{
				fileStorageProvider = getCollectorApp().getFileStorageProvider();
			}
			catch(FileStorageRemovedException e)
			{
				Log.e(getClass().getSimpleName(), "Error getting fileStorageProvider", e);
				// Inform the user and close the application:
				final Runnable useAlternativeStorage = new Runnable()
				{
					@Override
					public void run()
					{
						// Clear the setting before restart
						getPreferences().clearSapelliFolder();
					}
				};
				showDialog(getString(R.string.app_name), getString(R.string.unavailableStorageAccess), R.drawable.sapelli_logo, R.string.useAlternativeStorage, useAlternativeStorage, true, R.string.insertSDcard, null, true);
			}
			catch(FileStorageUnavailableException e)
			{
				Log.e(getClass().getSimpleName(), "Error getting fileStorageProvider", e);
				// Inform the user and close the application:
				showErrorDialog(getString(R.string.app_name) + " " + getString(R.string.needsStorageAccess), true);
			}
		}
		return fileStorageProvider;
	}
	
	/**
	 * @author mstevens
	 *
	 */
	public interface FileStorageTask
	{
		
		/**
		 * @param fsp guaranteed non-null
		 */
		public void run(FileStorageProvider fsp);
		
	}
	
	/**
	 * @param task
	 */
	public void runFileStorageTask(FileStorageTask task)
	{
		FileStorageProvider fsp = getFileStorageProvider(); // if this returns null an error dialog will show and upon OK the activity will finish, ...
		if(fsp == null) // ... so abort task in that case...
			return;
		else
			task.run(fsp);
	}

	public void showOKDialog(int titleId, int messageId)
	{
		showOKDialog(titleId, messageId, null);
	}

	public void showOKDialog(int titleId, int messageId, Integer iconId)
	{
		showDialog(getString(titleId), getString(messageId), iconId, android.R.string.ok, null, DEFAULT_FINISH_ON_DIALOG_OK, HIDE_BUTTON, null, DEFAULT_FINISH_ON_DIALOG_CANCEL);
	}
	
	public void showOKDialog(String title, int messageId)
	{
		showOKDialog(title, messageId, null);
	}
	
	public void showOKDialog(String title, int messageId, Integer iconId)
	{
		showDialog(title, getString(messageId), iconId, android.R.string.ok, null, DEFAULT_FINISH_ON_DIALOG_OK, HIDE_BUTTON, null, DEFAULT_FINISH_ON_DIALOG_CANCEL);
	}
	
	public void showOKDialog(int titleId, String message)
	{
		showOKDialog(titleId, message, null);
	}
	
	public void showOKDialog(int titleId, String message, Integer iconId)
	{
		showDialog(getString(titleId), message, iconId, android.R.string.ok, null, DEFAULT_FINISH_ON_DIALOG_OK, HIDE_BUTTON, null, DEFAULT_FINISH_ON_DIALOG_CANCEL);
	}
	
	public void showOKDialog(String title, String message)
	{
		showOKDialog(title, message, null);
	}
	
	public void showOKDialog(String title, String message, Integer iconId)
	{
		showDialog(title, message, iconId, android.R.string.ok, null, DEFAULT_FINISH_ON_DIALOG_OK, HIDE_BUTTON, null, DEFAULT_FINISH_ON_DIALOG_CANCEL);
	}
	
	public void showOKDialog(int titleId, int messageId, boolean finishOnOK)
	{
		showOKDialog(titleId, messageId, null, finishOnOK);
	}
	
	public void showOKDialog(int titleId, int messageId, Integer iconId, boolean finishOnOK)
	{
		showDialog(getString(titleId), getString(messageId), iconId, android.R.string.ok, null, finishOnOK, HIDE_BUTTON, null, DEFAULT_FINISH_ON_DIALOG_CANCEL);
	}
	
	public void showOKDialog(String title, int messageId, boolean finishOnOK)
	{
		showOKDialog(title, messageId, null, finishOnOK);
	}
	
	public void showOKDialog(String title, int messageId, Integer iconId, boolean finishOnOK)
	{
		showDialog(title, getString(messageId), iconId, android.R.string.ok, null, finishOnOK, HIDE_BUTTON, null, DEFAULT_FINISH_ON_DIALOG_CANCEL);
	}
	
	public void showOKDialog(int titleId, String message, boolean finishOnOK)
	{
		showOKDialog(titleId, message, null, finishOnOK);
	}
	
	public void showOKDialog(int titleId, String message, Integer iconId, boolean finishOnOK)
	{
		showDialog(getString(titleId), message, iconId, android.R.string.ok, null, finishOnOK, HIDE_BUTTON, null, DEFAULT_FINISH_ON_DIALOG_CANCEL);
	}
	
	public void showOKDialog(String title, String message, boolean finishOnOK)
	{
		showOKDialog(title, message, null, finishOnOK);
	}
	
	public void showOKDialog(String title, String message, Integer iconId, boolean finishOnOK)
	{
		showDialog(title, message, iconId, android.R.string.ok, null, finishOnOK, HIDE_BUTTON, null, DEFAULT_FINISH_ON_DIALOG_CANCEL);
	}
	
	public void showOKDialog(int titleId, int messageId, boolean finishOnOK, Runnable okTask)
	{
		showOKDialog(titleId, messageId, null, finishOnOK, okTask);
	}
	
	public void showOKDialog(int titleId, int messageId, Integer iconId, boolean finishOnOK, Runnable okTask)
	{
		showDialog(getString(titleId), getString(messageId), iconId, android.R.string.ok, okTask, finishOnOK, HIDE_BUTTON, null, DEFAULT_FINISH_ON_DIALOG_CANCEL);
	}
	
	public void showOKDialog(String title, int messageId, boolean finishOnOK, Runnable okTask)
	{
		showOKDialog(title, messageId, null, finishOnOK, okTask);
	}
	
	public void showOKDialog(String title, int messageId, Integer iconId, boolean finishOnOK, Runnable okTask)
	{
		showDialog(title, getString(messageId), iconId, android.R.string.ok, okTask, finishOnOK, HIDE_BUTTON, null, DEFAULT_FINISH_ON_DIALOG_CANCEL);
	}
	
	public void showOKDialog(int titleId, String message, boolean finishOnOK, Runnable okTask)
	{
		showOKDialog(titleId, message, null, finishOnOK, okTask);
	}
	
	public void showOKDialog(int titleId, String message, Integer iconId, boolean finishOnOK, Runnable okTask)
	{
		showDialog(getString(titleId), message, iconId, android.R.string.ok, okTask, finishOnOK, HIDE_BUTTON, null, DEFAULT_FINISH_ON_DIALOG_CANCEL);
	}
	
	public void showOKDialog(String title, String message, boolean finishOnOK, Runnable okTask)
	{
		showOKDialog(title, message, null, finishOnOK, okTask);
	}
	
	public void showOKDialog(String title, String message, Integer iconId, boolean finishOnOK, Runnable okTask)
	{
		showDialog(title, message, iconId, android.R.string.ok, okTask, finishOnOK, HIDE_BUTTON, null, DEFAULT_FINISH_ON_DIALOG_CANCEL);
	}
	
	public void showOKCancelDialog(int titleId, int messageId, boolean finishOnOK, Runnable okTask, boolean finishOnCancel)
	{
		showOKCancelDialog(titleId, messageId, null, finishOnOK, okTask, finishOnCancel);
	}

	public void showOKCancelDialog(int titleId, int messageId, Integer iconId, boolean finishOnOK, Runnable okTask, boolean finishOnCancel)
	{
		showDialog(getString(titleId), getString(messageId), iconId, android.R.string.ok, okTask, finishOnOK, android.R.string.cancel, null, finishOnCancel);
	}
	
	public void showOKCancelDialog(String title, int messageId, boolean finishOnOK, Runnable okTask, boolean finishOnCancel)
	{
		showOKCancelDialog(title, messageId, null, finishOnOK, okTask, finishOnCancel);
	}
	
	public void showOKCancelDialog(String title, int messageId, Integer iconId, boolean finishOnOK, Runnable okTask, boolean finishOnCancel)
	{
		showDialog(title, getString(messageId), iconId, android.R.string.ok, okTask, finishOnOK, android.R.string.cancel, null, finishOnCancel);
	}
	
	public void showOKCancelDialog(int titleId, String message, boolean finishOnOK, Runnable okTask, boolean finishOnCancel)
	{
		showOKCancelDialog(titleId, message, null, finishOnOK, okTask, finishOnCancel);
	}
	
	public void showOKCancelDialog(int titleId, String message, Integer iconId, boolean finishOnOK, Runnable okTask, boolean finishOnCancel)
	{
		showDialog(getString(titleId), message, iconId, android.R.string.ok, okTask, finishOnOK, android.R.string.cancel, null, finishOnCancel);
	}
	
	public void showOKCancelDialog(String title, String message, boolean finishOnOK, Runnable okTask, boolean finishOnCancel)
	{
		showOKCancelDialog(title, message, null, finishOnOK, okTask, finishOnCancel);
	}
	
	public void showOKCancelDialog(String title, String message, Integer iconId, boolean finishOnOK, Runnable okTask, boolean finishOnCancel)
	{
		showDialog(title, message, iconId, android.R.string.ok, okTask, finishOnOK, android.R.string.cancel, null, finishOnCancel);
	}
	
	public void showYesNoDialog(int titleId, int messageId, Runnable yesTask, boolean finishOnYes, Runnable noTask, boolean finishOnNo)
	{
		showYesNoDialog(titleId, messageId, null, yesTask, finishOnYes, noTask, finishOnNo);
	}

	public void showYesNoDialog(int titleId, int messageId, Integer iconId, Runnable yesTask, boolean finishOnYes, Runnable noTask, boolean finishOnNo)
	{
		showDialog(getString(titleId), getString(messageId), iconId, R.string.yes, yesTask, finishOnYes, R.string.no, noTask, finishOnNo);
	}
	
	public void showYesNoDialog(String title, int messageId, Runnable yesTask, boolean finishOnYes, Runnable noTask, boolean finishOnNo)
	{
		showYesNoDialog(title, messageId, null, yesTask, finishOnYes, noTask, finishOnNo);
	}
	
	public void showYesNoDialog(String title, int messageId, Integer iconId, Runnable yesTask, boolean finishOnYes, Runnable noTask, boolean finishOnNo)
	{
		showDialog(title, getString(messageId), iconId, R.string.yes, yesTask, finishOnYes, R.string.no, noTask, finishOnNo);
	}
	
	public void showYesNoDialog(int titleId, String message, Runnable yesTask, boolean finishOnYes, Runnable noTask, boolean finishOnNo)
	{
		showYesNoDialog(titleId, message, null, yesTask, finishOnYes, noTask, finishOnNo);
	}
	
	public void showYesNoDialog(int titleId, String message, Integer iconId, Runnable yesTask, boolean finishOnYes, Runnable noTask, boolean finishOnNo)
	{
		showDialog(getString(titleId), message, iconId, R.string.yes, yesTask, finishOnYes, R.string.no, noTask, finishOnNo);
	}
	
	public void showYesNoDialog(String title, String message, Runnable yesTask, boolean finishOnYes, Runnable noTask, boolean finishOnNo)
	{
		showYesNoDialog(title, message, null, yesTask, finishOnYes, noTask, finishOnNo);
	}
	
	public void showYesNoDialog(String title, String message, Integer iconId, Runnable yesTask, boolean finishOnYes, Runnable noTask, boolean finishOnNo)
	{
		showDialog(title, message, iconId, R.string.yes, yesTask, finishOnYes, R.string.no, noTask, finishOnNo);
	}
	
	/**
	 * @param title
	 * @param message
	 * @param iconId
	 * @param postiveButtonId
	 * @param positiveTask
	 * @param finishOnPositive
	 * @param negativeButtonId
	 * @param finishOnNegative
	 */
	private void showDialog(String title, String message, Integer iconId, int postiveButtonId, final Runnable positiveTask, final boolean finishOnPositive, int negativeButtonId, final Runnable negativeTask, final boolean finishOnNegative)
	{
		// Builder:
		AlertDialog.Builder bldr = new AlertDialog.Builder(this);
		// set title:
		bldr.setTitle(title);
		// set message:
		bldr.setMessage(message);
		// set icon:
		if(iconId != null)
			bldr.setIcon(iconId);
		// set positive button:
		bldr.setPositiveButton(postiveButtonId, finishOnPositive || positiveTask != null ? new DialogInterface.OnClickListener()
		{
			public void onClick(DialogInterface dialog, int whichButton)
			{
				if(positiveTask != null)
					positiveTask.run();
				if(finishOnPositive)
					finish();
			}
		} : null);
		// set negative button:
		if(negativeButtonId != HIDE_BUTTON)
			bldr.setNegativeButton(negativeButtonId, finishOnNegative || negativeTask != null ? new DialogInterface.OnClickListener()
			{
				public void onClick(DialogInterface dialog, int whichButton)
				{
					if(negativeTask != null)
						negativeTask.run();
					if(finishOnNegative)
						finish();
				}
			} : null);
		// set cancelable (only true if there are no effects):
		bldr.setCancelable(!finishOnPositive && positiveTask == null && (negativeButtonId == HIDE_BUTTON || (!finishOnNegative && negativeTask == null)));
		// create & show
		bldr.create().show();
	}
	
	/**
	 * Show dialog with error message
	 * 
	 * @param message
	 * @param finish
	 */
	public void showErrorDialog(String message, boolean finish)
	{
		showOKDialog(R.string.error, message, finish);
	}
	
	/**
	 * Show dialog with error message
	 * 
	 * @param message
	 */
	public void showErrorDialog(String message)
	{
		showOKDialog(R.string.error, message, false);
	}

	/**
	 * Show dialog with error message
	 * 
	 * @param messageId
	 * @param finish
	 */
	public void showErrorDialog(int messageId, boolean finish)
	{
		showOKDialog(R.string.error, messageId, finish);
	}
	
	/**
	 * Show dialog with error message
	 * 
	 * @param messageId
	 */
	public void showErrorDialog(int messageId)
	{
		showOKDialog(R.string.error, messageId, false);
	}
	
	/**
	 * Show dialog with warning message
	 * 
	 * @param message
	 */
	public void showWarningDialog(String message)
	{
		showOKDialog(R.string.warning, message, false);
	}
	
	/**
	 * Show dialog with warning message
	 * 
	 * @param messageId
	 */
	public void showWarningDialog(int messageId)
	{
		showOKDialog(R.string.warning, messageId, false);
	}
	
	/**
	 * Show dialog with info message
	 * 
	 * @param message
	 */
	public void showInfoDialog(String message)
	{
		showOKDialog(R.string.info, message, false);
	}
	
	/**
	 * Show dialog with info message
	 * 
	 * @param messageId
	 */
	public void showInfoDialog(int messageId)
	{
		showOKDialog(R.string.info, messageId, false);
	}

	protected void init(){
		// Build info:
		getCollectorApp().setBuildInfo(BuildInfo.GetInstance(getApplicationContext()));

		Debug.d("CollectorApp started.\nBuild info:\n" + getCollectorApp().getBuildInfo().getAllInfo());

		// Start Fabric
		setFabric();

		// Set Timber for logging
		setTimber();

		// Set Stetho for debugging
		setStetho();

		// Get collector preferences:
		getCollectorApp().setPreferences(new CollectorPreferences(getApplicationContext()));

		// Initialise file storage:
		try {
			getCollectorApp().setFileStorageProvider(FileStorageHelper.initialiseFileStorage(getCollectorApp(),this));
		} catch (FileStorageException fse) {
//            getCollectorApp().fileStorageException = fse; // postpone throwing until getFileStorageProvider() is called!
		}

		// Set up a CrashReporter (will use dumps folder):
		if (getCollectorApp().getFileStorageProvider() != null)
			Thread.setDefaultUncaughtExceptionHandler(new CrashReporter(getCollectorApp().getFileStorageProvider(), getResources().getString(R.string.app_name)));

	}

	/**
	 * Set up Fabric
	 */
	private void setFabric() {
		// Set up Crashlytics, disabled for debug builds
		final CrashlyticsCore crashlyticsCore = new CrashlyticsCore.Builder().disabled(BuildConfig.DEBUG).build();
		final Crashlytics crashlyticsKit = new Crashlytics.Builder().core(crashlyticsCore).build();
		Fabric.with(this, crashlyticsKit);

		Crashlytics.setString(CRASHLYTICS_VERSION_INFO, getCollectorApp().getBuildInfo().getNameAndVersion() + " [" + getCollectorApp().getBuildInfo().getExtraVersionInfo() + "]");
		Crashlytics.setString(CRASHLYTICS_BUILD_INFO, getCollectorApp().getBuildInfo().getBuildInfo());
	}

	/**
	 * Set up Timber for logging
	 */
	private void setTimber() {
		// Enable Timber
		if (BuildConfig.DEBUG)
			Timber.plant(new DebugTree());
		else
			Timber.plant(new CrashlyticsTree());
	}

	/**
	 * Set up Stetho for debugging
	 */
	private void setStetho() {
		// Enable Stetho in Debug versions
		if (!BuildConfig.DEBUG)
			return;

		Timber.d("Enable Stetho");

		Stetho.initialize(Stetho.newInitializerBuilder(this)
				.enableWebKitInspector(new InspectorModulesProvider() {
					@Override
					public Iterable<ChromeDevtoolsDomain> get() {
						return new Stetho.DefaultInspectorModulesBuilder(BaseActivity.this)
								.provideDatabaseDriver(createCustomDatabaseDriver(BaseActivity.this))
								.finish();
					}
				}).build());
	}

	private SqliteDatabaseDriver createCustomDatabaseDriver(Context context) {
		return new SqliteDatabaseDriver(context, new DatabaseFilesProvider() {
			@Override
			public List<File> getDatabaseFiles() {
				List<File> dbs = new ArrayList<>();
				final String dbPath = SQLiteRecordStore.GetDBFileName(getCollectorApp().getFileStorageProvider().getDBFolder(false).getAbsolutePath() + File.separator + getCollectorApp().DATABASE_BASENAME);
				Timber.d("Try to connect to db at: %s", dbPath);
				dbs.add(new File(dbPath));
				return dbs;
			}
		}, new DefaultDatabaseConnectionProvider());
	}

}
