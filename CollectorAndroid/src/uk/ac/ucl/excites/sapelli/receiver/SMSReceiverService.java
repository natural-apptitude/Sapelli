package uk.ac.ucl.excites.sapelli.receiver;

import uk.ac.ucl.excites.sapelli.transmission.modes.sms.InvalidMessageException;
import uk.ac.ucl.excites.sapelli.transmission.modes.sms.Message;
import uk.ac.ucl.excites.sapelli.transmission.modes.sms.SMSAgent;
import uk.ac.ucl.excites.sapelli.transmission.modes.sms.binary.BinaryMessage;
import uk.ac.ucl.excites.sapelli.transmission.modes.sms.text.TextMessage;
import android.app.IntentService;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.telephony.SmsMessage;
import android.util.Log;
import android.widget.Toast;

/**
 * IntentService which handles SMS messages that have been passed to it from SMSBroadcastReceiver,
 * converting them into SMSTransmission objects and passing them on to a ReceiveController.
 * 
 * @author benelliott
 *
 */
public class SMSReceiverService extends IntentService
{
	private static final String TAG = "SMSReceiverService";
	public static final String PDU_BYTES_EXTRA_NAME = "pdu"; // intent key for passing the PDU bytes
	public static final String BINARY_FLAG_EXTRA_NAME = "binary"; // intent key for passing whether or not the message is binary (data message)
	private Handler mainHandler; // only used in order to display Toasts

	public SMSReceiverService()
	{
		super("SMSReceiverService");
	}

	@Override
	public void onCreate()
	{
		super.onCreate();
		mainHandler = new Handler(Looper.getMainLooper());
	}

	/**
	 * Called when the service is started through an intent, which should also contain SMS messages to be read by the service.
	 */
	@Override
	protected void onHandleIntent(Intent intent)
	{
		Log.d(TAG, "SMS received by Sapelli SMSReceiverService");
		
		// get PDU from intent:
		byte[] pdu = intent.getByteArrayExtra(PDU_BYTES_EXTRA_NAME);
		boolean binary = intent.getBooleanExtra(BINARY_FLAG_EXTRA_NAME, false);
		
		if (pdu == null) // should never happen
			return;
		
		// try to create a (Sapelli) Message object from the PDU:
		try
		{
			final Message message = messageFromPDU(pdu, binary);
						
			// TODO pass this message object to the app's ReceiveController so it can be inserted into the TransmissionStore (and eventually RecordStore etc)
			
			mainHandler.post(new Runnable()
			{
				@Override
				public void run()
				{
					Toast.makeText(SMSReceiverService.this, "Sapelli SMS received from phone number "+message.getSender().getPhoneNumber(), Toast.LENGTH_SHORT).show();
				}
			});
		}
		catch(InvalidMessageException e)
		{
			Log.d(TAG, "Received SMS message was found not to be relevant to Sapelli.", e);
		}
		catch (Exception e)
		{
			Log.e(TAG, "An error occurred while trying to parse the received SMS.", e);
		}
	}
	
	/**
	 * Creates and returns a Message object from the provided PDU if possible
	 * @param pdu the message PDU (protocol data unit - header and payload) as a byte array
	 * @return a BinaryMessage or TextMessage object depending on the type of the message provided
	 * @throws InvalidMessageException if the message was definitely not relevant to Sapelli
	 * @throws Exception for any other errors that occur while trying to parse the message
	 */
	private Message messageFromPDU(byte[] pdu, boolean binary) throws InvalidMessageException, Exception
	{
		
		SmsMessage androidMsg = SmsMessage.createFromPdu(pdu);
		
		if (androidMsg == null)
			throw new Exception("Android could not parse the SMS message from its PDU.");
		
		if (binary)
			return new BinaryMessage(new SMSAgent(androidMsg.getOriginatingAddress()), androidMsg.getUserData());
		else
			return new TextMessage(new SMSAgent(androidMsg.getOriginatingAddress()), androidMsg.getMessageBody());
	}

}
