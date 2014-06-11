package uk.ac.ucl.excites.sapelli.sender;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import uk.ac.ucl.excites.sapelli.collector.CollectorApp;
import uk.ac.ucl.excites.sapelli.collector.SapelliCollectorClient;
import uk.ac.ucl.excites.sapelli.collector.db.ProjectStore;
import uk.ac.ucl.excites.sapelli.collector.model.Project;
import uk.ac.ucl.excites.sapelli.sender.gsm.SMSSender;
import uk.ac.ucl.excites.sapelli.sender.gsm.SignalMonitor;
import uk.ac.ucl.excites.sapelli.sender.util.SapelliAlarmManager;
import uk.ac.ucl.excites.sapelli.shared.db.StoreClient;
import uk.ac.ucl.excites.sapelli.shared.util.TimeUtils;
import uk.ac.ucl.excites.sapelli.util.Debug;
import uk.ac.ucl.excites.sapelli.util.DeviceControl;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;

/**
 * @author Michalis Vitos
 */
public class DataSenderService extends Service
{
	private BlockingQueue<Long> projectQueue = new ArrayBlockingQueue<Long>(1024);
	// Use a single Thread and Send the Projects sequential
	private ExecutorService projectsExecutor = Executors.newSingleThreadExecutor();

	@Override
	public synchronized int onStartCommand(Intent intent, int flags, int startId)
	{
		// TODO TEMP:
		long modelID = SapelliCollectorClient.GetSchemaID(intent.getExtras().getInt(SapelliAlarmManager.PROJECT_ID),
				intent.getExtras().getInt(SapelliAlarmManager.PROJECT_HASH));

		// Add project to queue:
		try
		{
			projectQueue.put(modelID);
		}
		catch(InterruptedException e)
		{
			Debug.e(e);
		}

		// Run Projects in the queue:
		if(!projectsExecutor.isTerminated())
			projectsExecutor.execute(new Runnable()
			{
				@Override
				public void run()
				{
					while(!projectQueue.isEmpty())
						try
						{
							new ProjectSendingTask(DataSenderService.this, projectQueue.take());
						}
						catch(InterruptedException e)
						{
							Debug.e(e);
							break;
						}

					// Stop the Android Service
					stopSelf();
				}
			});

		return Service.START_NOT_STICKY;
	}

	@Override
	public void onDestroy()
	{
		Debug.d("Service has been killed!");
	}

	@Override
	public IBinder onBind(Intent intent)
	{
		return null;
	}

	/**
	 * Transmitting the data for a project
	 * 
	 * @author Michalis Vitos
	 * 
	 */
	public class ProjectSendingTask implements StoreClient
	{
		private Project project;
		private SMSSendingTask smsSendingTask;

		ProjectSendingTask(Context context, long modelID)
		{
			// Load the project
			try
			{
				ProjectStore store = ((CollectorApp) getApplication()).getProjectStore(this);
				this.project = store.retrieveProject(modelID); // TODO Call appropriate method
			}
			catch(Exception e)
			{
				Debug.e("Cannot Load Project: ", e);
			}

			// TODO Get Project Settings:
			if(project != null)
				project.isLogging();

			// TODO query for records

			// TODO If project has records to send

			// TODO if project needs SMS transmission
			if(smsSendingTask == null)
				smsSendingTask = new SMSSendingTask(context);

			smsSendingTask.send(project);

			// TODO else if project needs Internet transmission

			// If there were SMS Sending Tasks, terminate them
			if(smsSendingTask != null)
			{
				smsSendingTask.close();
				smsSendingTask = null;
			}
		}

		public class SMSSendingTask
		{
			private Context context;
			private SignalMonitor gsmMonitor;

			public SMSSendingTask(final Context context)
			{
				this.context = context;

				// Check for Airplane Mode
				if(DeviceControl.canToogleAirplaneMode() /* TODO && project needs to change AirplaneMode */)
					DeviceControl.disableAirplaneModeAndWait(context, DeviceControl.POST_AIRPLANE_MODE_WAITING_TIME);

				// Check for SMS Signal
				setupSMSmonitor(context);
			}

			public void send(Project p)
			{
				// TODO get List of Records

				// Send records
				if(true /* gsmMonitor.isInService() */)
				{
					// TODO Send them

					// Test
					SMSSender smsSender = new SMSSender(context);
					smsSender.send("Sapelli SMS Demo!!! " + TimeUtils.getPrettyTimestamp(), "+447577144675"); // Me
					// smsSender.send("Sapelli SMS Demo!!! " + TimeUtils.getPrettyTimestamp(), "+447741098273"); // Julia
				}
			}

			/**
			 * Stop the Signal Monitor and Toggle the AirplaneMode
			 */
			public void close()
			{
				// Stop GSM Signal Monitor
				stopSingnalMonitor();

				// Put device back to AirplaneMode if needed
				if(true /* TODO projects toggle AirplaneMode */)
					DeviceControl.enableAirplaneMode(context);
			}

			/**
			 * Start a GSM SignalMonitor on the main UI thread by enqueuing the runnable action to be performed on a Handler
			 * 
			 * @param context
			 */
			private void setupSMSmonitor(final Context context)
			{
				Handler handler = new Handler(Looper.getMainLooper());
				handler.post(new Runnable()
				{
					@Override
					public void run()
					{
						gsmMonitor = new SignalMonitor(context);

					}
				});

				// Wait for the Signal Monitor listener to be established
				while(gsmMonitor == null)
				{
					try
					{
						Thread.sleep(100);
					}
					catch(InterruptedException e)
					{
						Debug.e(e);
					}
				}
			}

			private void stopSingnalMonitor()
			{
				// Stop SignalMonitor Listener
				if(gsmMonitor != null)
				{
					gsmMonitor.stopSignalMonitor();
					gsmMonitor = null;
				}
			}
		}
	}

}