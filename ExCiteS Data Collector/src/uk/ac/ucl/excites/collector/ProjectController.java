/**
 * 
 */
package uk.ac.ucl.excites.collector;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Stack;

import uk.ac.ucl.excites.collector.database.DataAccess;
import uk.ac.ucl.excites.collector.geo.OrientationListener;
import uk.ac.ucl.excites.collector.geo.OrientationSensor;
import uk.ac.ucl.excites.collector.project.model.ChoiceField;
import uk.ac.ucl.excites.collector.project.model.Field;
import uk.ac.ucl.excites.collector.project.model.Field.Optionalness;
import uk.ac.ucl.excites.collector.project.model.Form;
import uk.ac.ucl.excites.collector.project.model.LocationField;
import uk.ac.ucl.excites.collector.project.model.MediaField;
import uk.ac.ucl.excites.collector.project.model.OrientationField;
import uk.ac.ucl.excites.collector.project.model.Project;
import uk.ac.ucl.excites.collector.project.ui.ButtonsState;
import uk.ac.ucl.excites.collector.util.DeviceID;
import uk.ac.ucl.excites.collector.util.LocationUtils;
import uk.ac.ucl.excites.storage.model.Record;
import uk.ac.ucl.excites.storage.types.Orientation;
import uk.ac.ucl.excites.util.DeviceControl;
import uk.ac.ucl.excites.util.Logger;
import android.content.Context;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.util.Log;

/**
 * @author mstevens, Michalis Vitos, Julia
 * 
 */
public class ProjectController implements LocationListener, OrientationListener
{

	// STATICS--------------------------------------------------------
	static private final String TAG = "ProjectController";
	static private final String LOG_PREFIX = "Collector_";

	public static final int LOCATION_LISTENER_UPDATE_MIN_TIME_MS = 15 * 1000;// 30 seconds
	public static final int LOCATION_LISTENER_UPDATE_MIN_DISTANCE_M = 5; // 5 meters

	private static final int VIBRATION_DURATION_MS = 600;

	// DYNAMICS-------------------------------------------------------
	private Project project;
	private DataAccess dao;
	private CollectorActivity activity;
	private Context context;

	private long deviceID; // 32 bit _unsigned_ CRC32 hashcode

	private Form currentForm;
	private Field currentField;
	private Set<Field> tempDisabledFields;
	private Stack<Field> fieldHistory;

	private Record currentRecord;
	private List<File> currentMediaAttachments;

	private LocationManager locationManager;
	private Location currentBestLocation = null;
	private OrientationSensor orientationSensor;

	private long formStartTime;
	private Logger logger;

	public ProjectController(Project project, DataAccess dao, CollectorActivity activity, Context context)
	{
		this.project = project;
		this.dao = dao;
		this.activity = activity;
		this.context = context;

		fieldHistory = new Stack<Field>();
		tempDisabledFields = new HashSet<Field>();
		currentMediaAttachments = new ArrayList<File>();
		deviceID = (new DeviceID(activity)).getCRC32Hash();

		orientationSensor = new OrientationSensor(activity);
	}

	public void startProject()
	{
		if(project.isLogging())
		{
			try
			{
				logger = new Logger(project.getLogFolder().getAbsolutePath(), LOG_PREFIX);

				// Log the start of the project
				logger.addLine("PROJECT_START", project.getName());
				logger.addBlankLine();
			}
			catch(IOException e)
			{
				Log.e(TAG, "Logger construction error", e);
			}
		}
		startForm(0); // For now projects have only one form
	}

	public void startForm(String formName)
	{
		// Find form with the given name:
		Form form = null;
		for(Form f : project.getForms())
			if(f.getName().equals(formName))
			{
				form = f;
				break;
			}
		if(form != null)
			startForm(form);
		else
			throw new IllegalArgumentException("Form " + formName + " could not be found in this project.");
	}

	public void startForm(int formIndex)
	{
		startForm(project.getForms().get(formIndex));
	}

	public void startForm(Form form)
	{
		currentForm = form;

		// Clear stuff:
		fieldHistory.clear();
		tempDisabledFields.clear();
		currentMediaAttachments.clear();
		currentField = null;

		// Create new currentRecord:
		currentRecord = currentForm.newEntry(deviceID);
		
		// Location...
		List<LocationField> lfStartWithForm = currentForm.getLocationFields(true);
		if(!lfStartWithForm.isEmpty())
			startLocationListener(lfStartWithForm); // start listening for location updates
		else
			stopLocationListener(); // stop listening for location updates (if we were still listening for another form for example)

		// log start form
		if(logger != null)
		{
			formStartTime = System.currentTimeMillis();
			logger.addLine("FORM_START", currentForm.getName());
		}
		
		// Begin filling out the form at the start field:
		goTo(currentForm.getStartField());
	}

	public void cancelAndRestartForm()
	{
		// cancel button pressed
		if(logger != null)
			logger.addLine("CANCEL_BUTTON", currentField.getID());
		
		cancel(true);
	}

	public void cancelAndStop()
	{		
		cancel(false);
	}

	private void cancel(boolean restart)
	{
		// Delete any attachments:
		for(File attachment : currentMediaAttachments)
			if(attachment.exists())
				attachment.delete();
		//restart or stop:
		if(restart)
		{
			// Restart the form:
			startForm(currentForm);
		}
		else
		{
			stopLocationListener(); // stop GPS!
			currentMediaAttachments.clear();
			fieldHistory.clear();
			currentForm = null;
			currentField = null;
			currentRecord = null;
			// close log file:
			if(logger != null)
			{
				logger.addFinalLine("PROJECT_END", project.getName());
				logger = null;
			}
		}
	}

	public void goForward(boolean requestedByUser)
	{
		// log interaction:
		if(requestedByUser && logger != null)
			logger.addLine("FORWARD_BUTTON", currentField.getID());

		if(currentField != null)
			goTo(currentForm.getNextField(currentField));
		else
			startForm(currentForm); // this shouldn't happen really...
	}

	public void goBack()
	{
		if(!fieldHistory.isEmpty())
		{
			// log interaction:
			if(logger != null)
				logger.addLine("BACK_BUTTON", currentField.getID());

			currentField = null; // !!! otherwise we create loops
			final Field previousField = fieldHistory.pop();

			// TODO Maybe there is a better way of handling back buttons
			if(previousField instanceof LocationField)
				goTo(fieldHistory.pop()); // Move two fields backwards
			else if(currentField instanceof OrientationField)
				goTo(fieldHistory.pop()); // Move two fields backwards
			else
				goTo(previousField);
		}
	}

	public synchronized void goTo(Field nextField)
	{
		// log interaction
		if(logger != null)
			logger.addLine("REACHED", nextField.getID());

		// Leafing current field...
		if(currentField != null && currentField != nextField)
			fieldHistory.add(currentField); // Add to history
		// Entering next field...
		currentField = nextField;
		// Handle LocationField:
		if(currentField instanceof LocationField)
		{
			LocationField lf = (LocationField) currentField;
			if(lf.isWaitAtField() || /*try to use currentBestLocation:*/ !lf.storeLocation(currentRecord, LocationUtils.getExCiteSLocation(currentBestLocation)))
				startLocationListener(lf); // start listening for a location
			else
			{ // we already have a (good enough) location
				goForward(false); // skip the wait screen
				return; // !!!
			}
		}
		// Handle OrientationField:
		else if(currentField instanceof OrientationField)
		{
			orientationSensor.start(this); // start listening for orientation updates
			return; // !!! (orientation values will be received almost instantaneously, so we don't update the GUI
		}
		// Handle media fields:
		else if(currentField instanceof MediaField)
		{
			if(((MediaField) currentField).isMaxReached(currentRecord))
			{ // Maximum number of attachments for this field is reached:
				goForward(false); // skip field
				return; // !!!
			}
		}

		// TODO Add a delay tag to the XML and load it from there
		// TODO Maybe add the above code to the AsyncTask
		// Update GUI (will also handle EndField & CancelField):
		InterfaceDelay ui = new InterfaceDelay(context, 700, activity, currentField);
		ui.execute();
	}

	public ButtonsState getButtonsState()
	{
		ButtonsState state = new ButtonsState(
				currentForm.isShowBack()	&& currentField.isShowBack()	&& !fieldHistory.empty(),
				currentForm.isShowCancel()	&& currentField.isShowCancel()	&& !fieldHistory.empty(),
				currentForm.isShowForward()	&& currentField.isShowForward()	&& currentField.getOptional() == Optionalness.ALWAYS);
		// Note: these paths may be null (in which case built-in defaults must be used)
		return state;
	}

	public boolean isFieldEndabled(Field field)
	{
		return field.isEnabled() && !tempDisabledFields.contains(field);
	}

	/**
	 * To be called from ChoiceView
	 * 
	 * @param chosenChild
	 */
	public void choiceMade(ChoiceField chosenChild)
	{
		// Note: chosenChild is not the currentField! The currentField (also a ChoiceField) is its parent.
		if(chosenChild.isLeaf())
		{
			// Store value
			if(!chosenChild.getRoot().isNoColumn())
				chosenChild.storeValue(currentRecord);
			// Go to next field
			goTo(currentForm.getNextField(chosenChild));
			/*
			 * We cannot use goForward() here because then we would first need to make the chosenChild the currentField, in which case it would end up in the
			 * fieldHistory which does not make sense because a leaf choice cannot be displayed on its own.
			 */
		}
		else
			goTo(chosenChild); // chosenChild becomes the new currentField (we go one level down in the choice tree)
	}

	public void mediaDone(File mediaAttachment)
	{
		MediaField ma = (MediaField) currentField;
		if(mediaAttachment != null && mediaAttachment.exists())
		{
			if(logger != null)
				logger.addLine("ATTACHMENT", currentField.getID(), mediaAttachment.getName());
			
			ma.incrementCount(currentRecord); // Store/increase number of pictures/recordings taken
			if(ma.isMaxReached(currentRecord) && ma.getDisableChoice() != null)
				tempDisabledFields.add(ma.getDisableChoice()); // disable the choice that makes the MA accessible
			currentMediaAttachments.add(mediaAttachment);
			goForward(false); // goto next/jump field
		}
		else
		{
			if(logger != null)
				logger.addLine("ATTACHMENT", currentField.getID(), "NONE");
			
			if(ma.getOptional() != Optionalness.ALWAYS)
				// at least one attachment is required:
				goTo(ma); // stay at this field
			else
				goForward(false); // goto next/jump field
		}
	}

	public void timeout(Field field)
	{
		if(field != currentField)
			return; // this shouldn't happen really
		//Log:
		if(logger != null)
			logger.addLine("TIMEOUT", currentField.getID());
		// Handle location field
		if(currentField instanceof LocationField)
		{
			LocationField lf = (LocationField) currentField;
			if(lf.retrieveLocation(currentRecord) == null && lf.isUseBestNonQualifyingLocationAfterTimeout())
				lf.storeLocation(currentRecord, LocationUtils.getExCiteSLocation(currentBestLocation), true);
		}
		// else if() //other fields with timeouts in the future?
		// ...
		// Continue:
		goForward(false);
	}

	public void endForm()
	{
		// Logging:
		if(logger != null)
			logger.addLine("FORM_END", currentForm.getName(), Long.toString((System.currentTimeMillis() - formStartTime) / 1000) + " seconds");
		
		// Finalise the currentRecord:
		currentForm.finish(currentRecord); // sets end-time if necessary

		// Store currentRecord
		dao.store(currentRecord);
		
		Log.d(TAG, "Stored record:");
		Log.d(TAG, currentRecord.toString());

		// logging:
		if(logger != null)
		{
			logger.addLine("RECORD", currentRecord.toString());
			logger.addBlankLine();
		}

		// Move attachments from temp to data folder:
		try
		{
			File dataFolder = project.getDataFolder();
			for(File attachment : currentMediaAttachments)
				attachment.renameTo(new File(dataFolder.getAbsolutePath() + File.separator + attachment.getName()));
		}
		catch(IOException ioe)
		{
			Log.w(TAG, "Error on moving attachements to data folder.");
		}

		// Signal the successful storage of the currentRecord
		// Vibration
		if(currentForm.isVibrateOnEnd())
			DeviceControl.vibrate(activity, VIBRATION_DURATION_MS);
		// Play sound
		String endSound = currentForm.getEndSoundPath();
		if(endSound != null && !endSound.isEmpty())
			DeviceControl.playSoundFile(activity, new File(project.getSoundFolderPath() + endSound));

		// End action:
		switch(currentForm.getEndAction())
		{
		case Form.END_ACTION_LOOP:
			startForm(currentForm);
			break;
		case Form.END_ACTION_EXIT:
			activity.finish();
			break; // leaves the application!
		}
	}

	public void onOrientationChanged(Orientation orientation)
	{
		if(currentField instanceof OrientationField)
		{
			((OrientationField) currentField).storeValue(currentRecord, orientation);
			orientationSensor.stop(); // stop listening for updates
			goForward(false);
		}
	}

	private void startLocationListener(LocationField locField)
	{
		startLocationListener(Arrays.asList(locField));
	}

	private void startLocationListener(List<LocationField> locFields)
	{
		if(locFields.isEmpty())
			return;
		// get locationmanager:
		if(locationManager == null)
			locationManager = (LocationManager) activity.getSystemService(Context.LOCATION_SERVICE);
		// determine which provider(s) we need:
		Set<String> providers = new HashSet<String>();
		for(LocationField lf : locFields)
			providers.addAll(LocationUtils.getProvider(locationManager, lf));
		// start listening to each provider:
		for(String provider : providers)
			locationManager.requestLocationUpdates(provider, LOCATION_LISTENER_UPDATE_MIN_TIME_MS, LOCATION_LISTENER_UPDATE_MIN_DISTANCE_M, this);
	}

	private void stopLocationListener()
	{
		if(locationManager != null)
			locationManager.removeUpdates(this);
	}

	@Override
	public synchronized void onLocationChanged(Location location)
	{
		if(LocationUtils.isBetterLocation(location, currentBestLocation))
		{
			currentBestLocation = location;
			// check if we can/need to use the location now:
			if(currentField instanceof LocationField)
			{ // user is currently waiting for a location for the currentField
				LocationField lf = (LocationField) currentField;
				// try to store location:
				if(lf.storeLocation(currentRecord, LocationUtils.getExCiteSLocation(location)))
				{ // location successfully stored:
					if(currentForm.getLocationFields(true).isEmpty())
						stopLocationListener(); // there are no locationfields with startWithForm=true (so there is no reason to keep listening for locations)
					goForward(false); // continue (will leave waiting screen & stop the timeout timer)
				}
				// else (location was not accepted): do nothing (keep listening for a better location)
			}
		}
	}

	@Override
	public void onProviderDisabled(String provider)
	{
		// does nothing for now
	}

	@Override
	public void onProviderEnabled(String provider)
	{
		// does nothing for now
	}

	@Override
	public void onStatusChanged(String provider, int status, Bundle extras)
	{
		// does nothing for now
	}

	/**
	 * @return the currentForm
	 */
	public Form getCurrentForm()
	{
		return currentForm;
	}

	/**
	 * @return the project
	 */
	public Project getProject()
	{
		return project;
	}

	/**
	 * @return the currentRecord
	 */
	public Record getCurrentRecord()
	{
		return currentRecord;
	}

	/**
	 * @return the currentField
	 */
	public Field getCurrentField()
	{
		return currentField;
	}

}
