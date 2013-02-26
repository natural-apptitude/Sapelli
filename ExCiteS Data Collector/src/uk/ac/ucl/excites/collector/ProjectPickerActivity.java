package uk.ac.ucl.excites.collector;

import java.io.File;
import java.io.IOException;
import java.util.List;

import uk.ac.ucl.excites.collect.R;
import uk.ac.ucl.excites.collector.project.db.DataAccess;
import uk.ac.ucl.excites.collector.project.model.Project;
import uk.ac.ucl.excites.collector.project.util.DuplicateException;
import uk.ac.ucl.excites.collector.project.xml.ProjectParser;
import uk.ac.ucl.excites.collector.ui.filedialog.FileDialog;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ListView;

/**
 * @author Julia, Michalis Vitos
 * 
 */
public class ProjectPickerActivity extends Activity
{
	private String dbPATH;

	// Define some variables
	public static final int SETTINGS_REQUEST_IMPORT = 1;
	private EditText enterURL;
	private ListView projectList;
	DataAccess dao;
	List<Project> parsedProjects;

	@Override
	protected void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		// Remove title
		requestWindowFeature(Window.FEATURE_NO_TITLE);
		// Hide soft keyboard on create
		getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN);
		setContentView(R.layout.activity_projectpicker);

		// TODO
		// Database instance (path may be changed)
		// Path is on Internal Storage
		// Why we use a hardcoded address here?
		dbPATH = this.getFilesDir().getAbsolutePath();
		Log.e("ExCiteS_Debug", "Internal Storage path:" + dbPATH);
		dao = uk.ac.ucl.excites.collector.project.db.DataAccess.getInstance(dbPATH);

		// TODO Copy function
		String dstFilePath =  Environment.getExternalStorageDirectory().getPath() + File.separator + "0000" + File.separator + "ExCiteS_copy.db4o";
		Log.e("ExCiteS_Debug", "Copy path:" + dstFilePath);
		dao.copyDBtoSD(dbPATH, dstFilePath);

		// Get View Elements
		enterURL = (EditText) findViewById(R.id.EnterURL);
		projectList = (ListView) findViewById(R.id.ProjectsList);

		// get scrolling right
		findViewById(R.id.scrollView).setOnTouchListener(new View.OnTouchListener()
		{
			@Override
			public boolean onTouch(View v, MotionEvent event)
			{
				projectList.getParent().requestDisallowInterceptTouchEvent(false);
				return false;
			}
		});
		projectList.setOnTouchListener(new View.OnTouchListener()
		{
			public boolean onTouch(View v, MotionEvent event)
			{
				// Disallow the touch request for parent scroll on touch of child view
				v.getParent().requestDisallowInterceptTouchEvent(true);
				return false;
			}
		});

		// display parsed projects
		populateProjectList();

	}

	public void browse(View view)
	{
		Intent mIntent = new Intent(getBaseContext(), FileDialog.class);
		// Start from "/sdcard"
		mIntent.putExtra(FileDialog.START_PATH, Environment.getExternalStorageDirectory().getPath());

		// can user select directories or not
		mIntent.putExtra(FileDialog.CAN_SELECT_DIR, true);

		// set file filter
		mIntent.putExtra(FileDialog.FORMAT_FILTER, new String[] { "xml" });
		startActivityForResult(mIntent, SETTINGS_REQUEST_IMPORT);
	}

	public void runProject(View view)
	{

		if(projectList.getCheckedItemPosition() == -1)
		{
			AlertDialog NoSelection = errorDialog("Please select a project");
			NoSelection.show();
		}

		String project = parsedProjects.get(projectList.getCheckedItemPosition()).getName();
		Intent i = new Intent(this, CollectorActivity.class);
		i.putExtra("Project", project);
		i.putExtra("Path", dbPATH);
		startActivity(i);

	}

	public void removeProject()
	{
		dao.deleteProject(parsedProjects.get(projectList.getCheckedItemPosition()));
		populateProjectList();
	}

	public void parseXML(View view)
	{

		if(enterURL.getText().length() == 0)
		{
			AlertDialog error = errorDialog("Please select an XML file");
			error.show();
			return;
		}

		// parse XML
		try
		{
			ProjectParser parser = new ProjectParser();
			String path = enterURL.getText().toString();
			enterURL.setText("");
			Project project = parser.parseProject(new File(path));
			dao.store(project);
		}
		catch(DuplicateException de)
		{
			showParseError("Project could not be stored: " + de.getLocalizedMessage());
			return;
		}
		catch(Exception e)
		{
			showParseError("XML file could not be parsed: " + e.getLocalizedMessage());
			return;
		}

		// Update project list:
		populateProjectList();
	}

	private void showParseError(String errorMsg)
	{
		AlertDialog error = errorDialog(errorMsg);
		error.show();
	}

	// retrieve all parsed projects from db and populate list
	public void populateProjectList()
	{
		parsedProjects = dao.retrieveProjects();
		String[] values = new String[parsedProjects.size()];
		for(int i = 0; i < parsedProjects.size(); i++)
		{
			values[i] = parsedProjects.get(i).getName();
		}
		ArrayAdapter<String> adapter = new ArrayAdapter<String>(this, android.R.layout.simple_list_item_single_choice, android.R.id.text1, values);
		projectList.setAdapter(adapter);

	}

	// file filter
	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data)
	{
		super.onActivityResult(requestCode, resultCode, data);

		if(resultCode == Activity.RESULT_OK)
		{
			switch(requestCode)
			{
			case SETTINGS_REQUEST_IMPORT:
				// Get the result file path and import the settings
				String fileSource = data.getStringExtra(FileDialog.RESULT_PATH).trim();
				enterURL.setText(fileSource);
				enterURL.setSelection(fileSource.length());
				break;
			}
		}
	}

	// dialog to check whether it is desired to remove project
	public void removeDialog(View view)
	{

		System.out.println(projectList.getCheckedItemPosition());

		if(projectList.getCheckedItemPosition() == -1)
		{
			AlertDialog NoSelection = errorDialog("Please select a project");
			NoSelection.show();
		}
		else
		{
			AlertDialog removeDialogBox = new AlertDialog.Builder(this).setMessage("Are you sure that you want to remove the project?")
					.setPositiveButton("Yes", new DialogInterface.OnClickListener()
					{
						public void onClick(DialogInterface dialog, int whichButton)
						{
							removeProject();
						}
					})

					.setNegativeButton("Cancel", new DialogInterface.OnClickListener()
					{
						public void onClick(DialogInterface dialog, int whichButton)
						{
						}
					}).create();

			removeDialogBox.show();
		}
	}

	// dialog shown when erroneous user interaction is detected
	private AlertDialog errorDialog(String message)
	{
		AlertDialog Error = new AlertDialog.Builder(this).setTitle("Warning").setMessage(message).setNeutralButton("OK", new DialogInterface.OnClickListener()
		{
			public void onClick(DialogInterface dialog, int whichButton)
			{
			}
		}).create();
		return Error;
	}

	@Override
	protected void onPause()
	{
		// close database
		super.onPause();
		dao.closeDB();
	}

	@Override
	protected void onResume()
	{
		// open database
		super.onResume();
		dao = DataAccess.getInstance(Environment.getExternalStorageDirectory().getPath());
	}

}
