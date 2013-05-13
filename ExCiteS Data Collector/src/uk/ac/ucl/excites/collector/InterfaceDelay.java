package uk.ac.ucl.excites.collector;

import uk.ac.ucl.excites.collector.project.model.Field;
import android.app.ProgressDialog;
import android.content.Context;
import android.os.AsyncTask;
import android.util.Log;

public class InterfaceDelay extends AsyncTask<Void, Void, Void>
{
	private Context context;
	private CollectorActivity activity;
	private Field currentField;
	private long delay;
	private ProgressDialog dialog;

	public InterfaceDelay(Context context, long delay, CollectorActivity activity, Field currentField)
	{
		this.context = context;
		this.delay = delay;
		this.activity = activity;
		this.currentField = currentField;
	}


	@Override
	protected void onPreExecute()
	{
		dialog = new ProgressDialog(context);
		dialog.setCancelable(false);
		dialog.show();
	}

	@Override
	protected Void doInBackground(Void... params)
	{
		try
		{
			Thread.sleep(delay);
		}
		catch(InterruptedException e)
		{
			Log.e("UI_DELAY", "ERROR:", e);
		}
		return null;
	}

	@Override
	protected void onPostExecute(Void result)
	{
		// Close the dialog
		dialog.cancel();
		activity.setField(currentField);
	}

}
