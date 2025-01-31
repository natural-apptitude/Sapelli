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

package uk.ac.ucl.excites.sapelli.collector.tasks;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.view.ContextThemeWrapper;

import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import uk.ac.ucl.excites.sapelli.collector.CollectorApp;
import uk.ac.ucl.excites.sapelli.collector.CollectorClient;
import uk.ac.ucl.excites.sapelli.collector.R;
import uk.ac.ucl.excites.sapelli.collector.activities.BaseActivity;
import uk.ac.ucl.excites.sapelli.collector.fragments.ExportFragment;
import uk.ac.ucl.excites.sapelli.collector.io.FileStorageProvider;
import uk.ac.ucl.excites.sapelli.collector.io.FileStorageProvider.Folder;
import uk.ac.ucl.excites.sapelli.collector.util.AsyncTaskWithWaitingDialog;
import uk.ac.ucl.excites.sapelli.shared.io.FileHelpers;
import uk.ac.ucl.excites.sapelli.shared.io.Zipper;
import uk.ac.ucl.excites.sapelli.shared.util.ExceptionHelpers;
import uk.ac.ucl.excites.sapelli.shared.util.android.Debug;
import uk.ac.ucl.excites.sapelli.storage.eximport.ExportResult;
import uk.ac.ucl.excites.sapelli.storage.model.Record;
import uk.ac.ucl.excites.sapelli.storage.queries.Order;
import uk.ac.ucl.excites.sapelli.storage.queries.RecordsQuery;
import uk.ac.ucl.excites.sapelli.storage.queries.sources.Source;

/**
 * Sapelli Collector Back-up procedure 
 *
 * @author Michalis Vitos, mstevens
 */
public class Backup implements RecordsTasks.QueryCallback, RecordsTasks.ExportCallback
{

	// STATIC -----------------------------------------------------------------
	static public final Folder[] BACKUPABLE_FOLDERS = { Folder.Attachments, Folder.Crashes, Folder.Export, Folder.Logs, Folder.Projects };
	static public final String EMPTY_FILE = ".empty";

	static private int getFolderStringID(Folder folder)
	{
		switch(folder)
		{
			case Crashes:
				return R.string.folderCrashes;
			case Export:
				return R.string.folderExports;
			case Logs:
				return R.string.folderLogs;
			case Attachments:
				return R.string.folderAttachments;
			case Projects:
				return R.string.folderProjects;
			// Not back-upable:
			case Downloads:
			case Temp:
			case DB: // in fact this is will always included in back-up but not directly, only after DB(s) has/have been copied to a temp folder
			case OldDBVersions: // in fact this is will always included in back-up, but it isn't offered as a user choice
			default:
				throw new IllegalArgumentException("This folder (" + folder.name() + ") cannot be backed-up!");
		}
	}

	static private boolean isFolderDefaultSelected(Folder folder)
	{
		switch(folder)
		{
			case Attachments:
			case Crashes:
			case Export:
			case Logs:
				return true;
			case Projects:
				return false;
			// Not back-upable:
			case Downloads:
			case Temp:
			case DB: // (see comment above)
			case OLD_DB: // (see comment above)
			case OldDBVersions: // (see comment above)
			default:
				throw new IllegalArgumentException("This folder (" + folder.name() + ") cannot be backed-up!");
		}
	}

	static public void Run(BaseActivity activity, FileStorageProvider fileStorageProvider)
	{
		// create Backup instance and start the back-up process by showing the selection diagram...
		new Backup(activity, fileStorageProvider).showSelectionDialog();
	}

	// DYNAMIC ----------------------------------------------------------------
	private final BaseActivity activity;
	private final FileStorageProvider fileStorageProvider;
	private final Set<Folder> foldersToExport;

	private final Runnable runBackup;

	private Backup(BaseActivity activity, FileStorageProvider fileStorageProvider)
	{
		// Initialise:
		this.activity = activity;
		this.fileStorageProvider = fileStorageProvider;
		foldersToExport = new HashSet<>();

		// Create runnable:
		runBackup = new Runnable()
		{
			@Override
			public void run()
			{
				doBackup(); // go straight to back-up
			}
		};
	}

	/**
	 * Brings up the selection dialog (= start of back-up procedure)
	 *
	 * Note:
	 * 	Due to an Android bug (reported by mstevens: https://code.google.com/p/android/issues/detail?id=187416)
	 * 	we cannot insert a header into the ListView on this dialog as it causes Android to use incorrect list
	 * 	item indexes. Therefore we use the message as the title of the dialog (ugly). A future alternative (TODO)
	 * 	may be to refactor this class as a DialogFragment in which the message is shown in a separate TextView
	 * 	above the list instead of a headerView "in" the ListView.
	 */
	private void showSelectionDialog()
	{
		// Initialise folder selection:
		CharSequence[] checkboxItems = new CharSequence[BACKUPABLE_FOLDERS.length];
		boolean[] checkedItems = new boolean[BACKUPABLE_FOLDERS.length];
		int f = 0;
		for(Folder folder : BACKUPABLE_FOLDERS)
		{
			checkboxItems[f] = activity.getString(getFolderStringID(folder));
			if(checkedItems[f] = isFolderDefaultSelected(folder))
				foldersToExport.add(folder);
			f++;
		}

		// Get dialog builder & configure the dialog...
		AlertDialog.Builder builder = new AlertDialog.Builder(new ContextThemeWrapper(activity, R.style.AppTheme))
		//	Set icon:
		.setIcon(R.drawable.ic_content_save_black_36dp)
		//	Set title:
		.setTitle(R.string.selectForBackup) // R.string.backup)
		//	Set multiple choice:
		.setMultiChoiceItems(
			checkboxItems,
			checkedItems,
			// Choice click event handler:
			new DialogInterface.OnMultiChoiceClickListener()
			{
				@Override
				public void onClick(DialogInterface dialog, int which, boolean isChecked)
				{
					if(isChecked)
						// If the user checked the item, add it to the selected items:
						foldersToExport.add(BACKUPABLE_FOLDERS[which]);
					else
						// Else, if the item is already in the array, remove it:
						foldersToExport.remove(BACKUPABLE_FOLDERS[which]);
				}
			})
		// Set OK button:
		.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener()
		{
			@Override
			public void onClick(DialogInterface dialog, int id)
			{
				if(foldersToExport.contains(Folder.Export))
					showExportYesNoDialog(); // propose creation of a fresh full project data export
				else
					doBackup(); // go straight to back-up
			}
		})
		// Set Cancel button:
		.setNegativeButton(android.R.string.cancel, null);
		// Create the dialog:
		AlertDialog dialog = builder.create();
		// Add message above list:
		/*TextView lblMsg = new TextView(activity);
		int lrPadding = ViewHelpers.getDefaultDialogPaddingPx(activity);
		lblMsg.setPadding(lrPadding, 0, lrPadding, 0);
		lblMsg.setTextAppearance(activity, android.R.style.TextAppearance_Medium);
		lblMsg.setText(R.string.selectForBackup);
		dialog.getListView().addHeaderView(lblMsg);*/
		// Show the dialog:
		dialog.show();
	}

	private void showExportYesNoDialog()
	{
		// TODO query _before_ asking!?
		// Get dialog builder & configure the dialog...
		new AlertDialog.Builder(new ContextThemeWrapper(activity, R.style.AppTheme))
		//	Set title:
		.setTitle(R.string.preBackupExportTitle)
		//	Set message:
		.setMessage(R.string.preBackupExportMsg)
		// Set "Yes button:
		.setPositiveButton(R.string.preBackupExportYes, new DialogInterface.OnClickListener()
		{
			@Override
			public void onClick(DialogInterface dialog, int which)
			{
				// Query to see if there are any records to export:
				new RecordsTasks.QueryTask(activity, Backup.this).execute(new RecordsQuery(Source.With(CollectorClient.SCHEMA_FLAGS_COLLECTOR_DATA), Order.UNDEFINED));
				// TODO order by form, deviceid, timestamp
				// TODO let Backup & ExportFragment share this code somehow
			}
		})
		// Set "No" button:
		.setNegativeButton(R.string.no, new DialogInterface.OnClickListener()
		{
			@Override
			public void onClick(DialogInterface dialog, int which)
			{	// go straight to back-up:
				doBackup();
			}
		})
		// Create & show the dialog:
		.create().show();
	}

	@Override
	public void querySuccess(final List<Record> result)
	{
		if(result != null && !result.isEmpty())
		{
			String title = activity.getString(R.string.preBackupExportTitle);
			ExportFragment.ShowChoseFormatDialog(
				activity,
				title,
				activity.getString(R.string.preBackupExportFormatMsg, result.size()),
				false,
				new ExportFragment.FormatDialogCallback()
				{
					@Override
					public void onFormatChosen(ExportFragment formatFragment)
					{
						RecordsTasks.runExportTask(activity, result, formatFragment, fileStorageProvider.getExportFolder(true), activity.getString(R.string.backup), Backup.this);
					}
				});
		}
		else
		{
			activity.showOKDialog(	R.string.preBackupExportTitle,
									activity.getString(R.string.exportNoRecordsFound) + "\n" + activity.getString(R.string.backup_continue),
									false,
									runBackup);
		}
	}

	@Override
	public void queryFailure(Exception reason)
	{
		activity.showOKDialog(	R.string.preBackupExportTitle,
								activity.getString(R.string.exportQueryFailed, ExceptionHelpers.getMessageAndCause(reason)) + "\n" + activity.getString(R.string.backup_continue),
								false,
								runBackup);
	}

	@Override
	public void exportDone(ExportResult result)
	{
		if(!result.wasSuccessful())
		{
			activity.showOKDialog(	R.string.preBackupExportTitle,
									(result.getNumberedOfExportedRecords() > 0 ?
										activity.getString(R.string.exportPartialSuccessMsg, result.getNumberedOfExportedRecords(), result.getDestination(), result.getNumberOfUnexportedRecords(), ExceptionHelpers.getMessageAndCause(result.getFailureReason())) :
										activity.getString(R.string.exportFailureMsg, result.getDestination(), ExceptionHelpers.getMessageAndCause(result.getFailureReason())))
									+ "\n" + activity.getString(R.string.backup_continue),
									false,
									runBackup);
		}
		else
			doBackup();
	}

	@SuppressWarnings("unchecked")
	private void doBackup()
	{
		new AsyncBackup(activity).execute(foldersToExport);
	}

	private void showSuccessDialog(final File destZipFile)
	{
		// Get dialog builder & configure the dialog...
		new AlertDialog.Builder(new ContextThemeWrapper(activity, R.style.AppTheme))
		//	Set title:
		.setTitle(R.string.successful_backup)
		//	Set message:
		.setMessage(activity.getString(R.string.backup_in) + "\n" + destZipFile.getAbsolutePath())
		// Set "OK" button:
		.setPositiveButton(R.string.done, null)
		// Set "Share" button:
		.setNegativeButton(R.string.share, new DialogInterface.OnClickListener()
		{
			@Override
			public void onClick(DialogInterface dialog, int which)
			{
				Intent sendIntent = new Intent();
				sendIntent.setAction(Intent.ACTION_SEND);
				sendIntent.putExtra(Intent.EXTRA_STREAM, Uri.fromFile(destZipFile));
				sendIntent.setType("application/zip");
				activity.startActivity(sendIntent);
			}
		})
		// Create & show the dialog:
		.create().show();
	}

	private void showFailureDialog(Exception cause)
	{
		activity.showErrorDialog(activity.getString(R.string.backupFailDueTo, ExceptionHelpers.getMessageAndCause(cause)), false);
	}

	/**
	 *
	 * @author Michalis Vitos, mstevens
	 */
	private class AsyncBackup extends AsyncTaskWithWaitingDialog<BaseActivity, Set<Folder>, File>
	{

		private final CollectorApp app;
		private Exception failure = null;

		public AsyncBackup(BaseActivity activity)
		{
			super(activity);
			app = activity.getCollectorApp();
		}

		@Override
		protected File runInBackground(@SuppressWarnings("unchecked") Set<Folder>... params)
		{
			File destZipFile = null;
			File tmpFolder = null;
			try
			{
				// Phase 1: Preparation
				publishProgress(activity.getString(R.string.backup_progress_init));
				//	Create array with file paths of the selected items as well as the Temp/DB folder:
				Set<Folder> selectedFolders = params[0];
				File[] toZip = new File[selectedFolders.size()];
				tmpFolder = fileStorageProvider.getTempSubFolder("Backup_" + System.currentTimeMillis());
				int z = 0;
				for(Folder folder : foldersToExport)
					toZip[z++] = getFolderFile(folder, tmpFolder); // add folders as File objects

				// Phase 2: Create ZIP archive
				publishProgress(activity.getString(R.string.backup_progress_zipping));
				destZipFile = fileStorageProvider.getNewBackupFile();
				Zipper.Zip(destZipFile, toZip);
			}
			catch(Exception e)
			{
				Debug.e(e);
				failure = e;
				return null;
			}
			finally
			{	// Clean-up
				for(File file : fileStorageProvider.getTempFolder(false).listFiles())
					FileUtils.deleteQuietly(file);
			}
			return destZipFile;
		}

		private File getFolderFile(Folder folder, File tmpFolder) throws IOException
		{
			File folderFile = fileStorageProvider.getFolder(folder, false);
			if(!folderFile.exists() || !folderFile.isDirectory() || folderFile.listFiles().length == 0)
			{
				// Create matching folder in tmpFolder:
				folderFile = FileHelpers.getSubDirectory(tmpFolder, folder.name(), true); // Temp/Backup_[timestamp]/[folder]/
				// Create .empty file (to make sure folder is included in ZIP):
				(new File(folderFile, EMPTY_FILE)).createNewFile();
			}
			return folderFile;
		}

		@Override
		protected void onPostExecute(File destZipFile)
		{
			// Dismiss progress dialog:
			super.onPostExecute(destZipFile);

			// Show success or failure dialog:
			if(destZipFile != null && failure == null)
				showSuccessDialog(destZipFile);
			else
				showFailureDialog(failure);
		}

	}

}
