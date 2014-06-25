package uk.ac.ucl.excites.sapelli.transmission.db;

import java.io.File;
import java.nio.charset.Charset;

import uk.ac.ucl.excites.sapelli.shared.db.Store;
import uk.ac.ucl.excites.sapelli.shared.io.BitArray;
import uk.ac.ucl.excites.sapelli.storage.db.RecordStore;
import uk.ac.ucl.excites.sapelli.storage.model.AutoIncrementingPrimaryKey;
import uk.ac.ucl.excites.sapelli.storage.model.Model;
import uk.ac.ucl.excites.sapelli.storage.model.Record;
import uk.ac.ucl.excites.sapelli.storage.model.Schema;
import uk.ac.ucl.excites.sapelli.storage.model.columns.ByteArrayColumn;
import uk.ac.ucl.excites.sapelli.storage.model.columns.ForeignKeyColumn;
import uk.ac.ucl.excites.sapelli.storage.model.columns.IntegerColumn;
import uk.ac.ucl.excites.sapelli.storage.model.columns.StringColumn;
import uk.ac.ucl.excites.sapelli.storage.model.columns.TimeStampColumn;
import uk.ac.ucl.excites.sapelli.storage.types.TimeStamp;
import uk.ac.ucl.excites.sapelli.transmission.Payload;
import uk.ac.ucl.excites.sapelli.transmission.Transmission;
import uk.ac.ucl.excites.sapelli.transmission.TransmissionClient;
import uk.ac.ucl.excites.sapelli.transmission.modes.http.HTTPTransmission;
import uk.ac.ucl.excites.sapelli.transmission.modes.sms.Message;
import uk.ac.ucl.excites.sapelli.transmission.modes.sms.SMSAgent;
import uk.ac.ucl.excites.sapelli.transmission.modes.sms.SMSTransmission;
import uk.ac.ucl.excites.sapelli.transmission.modes.sms.binary.BinarySMSTransmission;
import uk.ac.ucl.excites.sapelli.transmission.modes.sms.text.TextSMSTransmission;

public class TransmissionStore implements Store
{
	
	// STATICS---------------------------------------------
	static private final Charset UTF8_CHARSET = Charset.forName("UTF-8");
	
	static public final Model TRANSMISSION_MANAGEMENT_MODEL = new Model(TransmissionClient.TRANSMISSION_MANAGEMENT_MODEL_ID, "TransmissionManagement");
	
	// Schema(s) & columns:
	//	Transmission Schema
	static final public Schema TRANSMISSION_SCHEMA = new Schema(TRANSMISSION_MANAGEMENT_MODEL, "Transmission");
	static final public IntegerColumn TRANSMISSION_COLUMN_ID = new IntegerColumn("ID", false, Transmission.TRANSMISSION_ID_FIELD);
	static final public IntegerColumn TRANSMISSION_COLUMN_REMOTE_ID = new IntegerColumn("RemoteID", true, Transmission.TRANSMISSION_ID_FIELD);
	static final public IntegerColumn TRANSMISSION_COLUMN_TYPE = new IntegerColumn("Type", false, false, Integer.SIZE);
	static final public IntegerColumn TRANSMISSION_COLUMN_PAYLOAD_TYPE = new IntegerColumn("PayloadType", true, Payload.PAYLOAD_TYPE_FIELD);
	static final public IntegerColumn TRANSMISSION_COLUMN_PAYLOAD_HASH = new IntegerColumn("PayloadHash", true, Transmission.PAYLOAD_HASH_FIELD);
	static final public StringColumn TRANSMISSION_COLUMN_SENDER = StringColumn.ForCharacterCount("Sender", false, Transmission.CORRESPONDENT_MAX_LENGTH);
	static final public StringColumn TRANSMISSION_COLUMN_RECEIVER = StringColumn.ForCharacterCount("Receiver", false, Transmission.CORRESPONDENT_MAX_LENGTH);
	static final public IntegerColumn TRANSMISSION_COLUMN_NUMBER_OF_PARTS = new IntegerColumn("NumberOfParts", false, false, Integer.SIZE);
	//	Columns shared with TransmisionPart:
	static final public TimeStampColumn COLUMN_SENT_AT = TimeStampColumn.JavaMSTime("SentAt", true, false);
	static final public TimeStampColumn COLUMN_RECEIVED_AT = TimeStampColumn.JavaMSTime("ReceivedAt", true, false);
	static
	{	// Add columns and index to Transmission Schema & seal it:
		TRANSMISSION_SCHEMA.addColumn(TRANSMISSION_COLUMN_ID);
		TRANSMISSION_SCHEMA.addColumn(TRANSMISSION_COLUMN_REMOTE_ID);
		TRANSMISSION_SCHEMA.addColumn(TRANSMISSION_COLUMN_TYPE);
		TRANSMISSION_SCHEMA.addColumn(TRANSMISSION_COLUMN_PAYLOAD_TYPE);
		TRANSMISSION_SCHEMA.addColumn(TRANSMISSION_COLUMN_PAYLOAD_HASH);
		TRANSMISSION_SCHEMA.addColumn(TRANSMISSION_COLUMN_SENDER);
		TRANSMISSION_SCHEMA.addColumn(TRANSMISSION_COLUMN_RECEIVER);
		TRANSMISSION_SCHEMA.addColumn(TRANSMISSION_COLUMN_NUMBER_OF_PARTS);
		TRANSMISSION_SCHEMA.addColumn(COLUMN_SENT_AT);
		TRANSMISSION_SCHEMA.addColumn(COLUMN_RECEIVED_AT);
		TRANSMISSION_SCHEMA.setPrimaryKey(new AutoIncrementingPrimaryKey("IDIdx", TRANSMISSION_COLUMN_ID));
		TRANSMISSION_SCHEMA.seal();
	}
	//	Transmission Part Schema
	static final public Schema TRANSMISSION_PART_SCHEMA = new Schema(TRANSMISSION_MANAGEMENT_MODEL, "TransmissionPart");
	static final public ForeignKeyColumn TRANSMISSION_PART_COLUMN_TRANSMISSION_ID = new ForeignKeyColumn("TransmissionID", TRANSMISSION_SCHEMA, false);
	static final public IntegerColumn TRANSMISSION_PART_COLUMN_NUMBER = new IntegerColumn("PartNumber", false, false, Integer.SIZE);
	static final public TimeStampColumn TRANSMISSION_PART_COLUMN_DELIVERED_AT = TimeStampColumn.JavaMSTime("DeliveredAt", true, false);
	static final public ByteArrayColumn TRANSMISSION_PART_COLUMN_BODY = new ByteArrayColumn("Body", false);
	static final public IntegerColumn TRANSMISSION_PART_COLUMN_BODY_BIT_LENGTH = new IntegerColumn("BodyBitLength", false, false, Integer.MAX_VALUE);
	static
	{	// Add columns to Transmission Part Schema & seal it:
		TRANSMISSION_PART_SCHEMA.addColumn(TRANSMISSION_PART_COLUMN_TRANSMISSION_ID);
		TRANSMISSION_PART_SCHEMA.addColumn(COLUMN_SENT_AT);
		TRANSMISSION_PART_SCHEMA.addColumn(TRANSMISSION_PART_COLUMN_DELIVERED_AT);
		TRANSMISSION_PART_SCHEMA.addColumn(COLUMN_RECEIVED_AT);
		TRANSMISSION_PART_SCHEMA.addColumn(TRANSMISSION_PART_COLUMN_BODY);
		TRANSMISSION_PART_SCHEMA.addColumn(TRANSMISSION_PART_COLUMN_BODY_BIT_LENGTH);
		TRANSMISSION_PART_SCHEMA.seal();
		// Seal the model:
		TRANSMISSION_MANAGEMENT_MODEL.seal();
	}
	
	// DYNAMICS--------------------------------------------
	private RecordStore recordStore;

	public TransmissionStore(RecordStore recordStore)
	{
		this.recordStore = recordStore;
	}
	
	/**
	 * Creates a Record representing a Transmission.
	 * The values of all columns will be set except for Sender, Receiver & NumberOfParts.
	 * 
	 * @param transmission
	 * @return
	 */
	private Record createTransmissionRecord(Transmission transmission)
	{
		// Create a transmission record:
		Record tRec = TRANSMISSION_SCHEMA.createRecord();
		// Set values:
		if(transmission.isLocalIDSet())
			TRANSMISSION_COLUMN_ID.storeValue(tRec, transmission.getLocalID());
		TRANSMISSION_COLUMN_REMOTE_ID.storeValue(tRec, transmission.getRemoteID());
		TRANSMISSION_COLUMN_TYPE.storeValue(tRec, transmission.getType().ordinal());
		if(transmission.isPayloadSet())
		{
			TRANSMISSION_COLUMN_PAYLOAD_TYPE.storeValue(tRec, transmission.getPayload().getType());
			TRANSMISSION_COLUMN_PAYLOAD_HASH.storeValue(tRec, transmission.getPayloadHash());
		}
		COLUMN_SENT_AT.storeValue(tRec, new TimeStamp(transmission.getSentAt()));
		COLUMN_RECEIVED_AT.storeValue(tRec, new TimeStamp(transmission.getReceivedAt()));
		// Return:
		return tRec;
	}
	
	/**
	 * Creates a Record representing an SMSTransmission.
	 * The values of all columns will be set.
	 * 
	 * @param transmission
	 * @return
	 */
	private Record createTransmissionRecord(SMSTransmission<?> transmission)
	{
		// Get record for transmission:
		Record tRec = createTransmissionRecord((Transmission) transmission);
		// Set remaining values:
		TRANSMISSION_COLUMN_NUMBER_OF_PARTS.storeValue(tRec, transmission.getTotalNumberOfParts());
		if(transmission.isSenderSet())
			TRANSMISSION_COLUMN_SENDER.storeValue(tRec, transmission.getSender().toString());
		if(transmission.isReceiverSet())
			TRANSMISSION_COLUMN_SENDER.storeValue(tRec, transmission.getReceiver().toString());
		// Return:
		return tRec;
	}
	
	/**
	 * @param transmission assumed to have all values set, except the (local) ID when inserting
	 * @throws Exception 
	 */
	private void doStoreTransmission(Transmission transmission, Record transmissionRecord) throws Exception
	{
		// Store the transmission
		recordStore.store(transmissionRecord);
		
		// Transmission ID should now be set in the record...
		if(transmission.isLocalIDSet())
		{
			// Verify whether it matches the local transmissionID on the object:
			if(transmission.getLocalID() != TRANSMISSION_COLUMN_ID.retrieveValue(transmissionRecord))
				throw new IllegalStateException("Non-matching transmission ID"); // this should never happen
		}
		else
			// Set transmissionID in object as the local one: 
			transmission.setLocalID(TRANSMISSION_COLUMN_ID.retrieveValue(transmissionRecord).intValue());
	}
	
	public void storeTransmission(SMSTransmission<?> smsTransmission) throws Exception
	{
		// TODO Start transaction
		
		// Create & store record:
		Record tRec = createTransmissionRecord(smsTransmission);
		doStoreTransmission(smsTransmission, tRec); // after this the localID should always be known
		
		// Parts...
		for(Message msg : smsTransmission.getParts())
		{
			Record tPartRec = TRANSMISSION_PART_SCHEMA.createRecord();
			TRANSMISSION_PART_COLUMN_TRANSMISSION_ID.storeValue(tPartRec, tRec.getReference()); // set foreign key
			TRANSMISSION_PART_COLUMN_NUMBER.storeValue(tPartRec, msg.getPartNumber());
			msg.setBody(this, tPartRec);
			COLUMN_SENT_AT.storeValue(tPartRec, new TimeStamp(msg.getSentAt()));
			TRANSMISSION_PART_COLUMN_DELIVERED_AT.storeValue(tPartRec, new TimeStamp(msg.getDeliveredAt()));
			COLUMN_RECEIVED_AT.storeValue(tPartRec, new TimeStamp(msg.getReceivedAt()));
			
			// Store part record:
			recordStore.store(tPartRec);
		}
		
		// TODO commit transaction
	}
	
	public void setPartBody(BitArray bodyBits, Record transmissionPartRecord)
	{
		TRANSMISSION_PART_COLUMN_BODY.storeValue(transmissionPartRecord, bodyBits.toByteArray());
		TRANSMISSION_PART_COLUMN_BODY_BIT_LENGTH.storeValue(transmissionPartRecord, bodyBits.length());
	}
	
	public void setPartBody(String bodyString, Record transmissionPartRecord)
	{
		byte[] bytes = bodyString.getBytes(UTF8_CHARSET);
		TRANSMISSION_PART_COLUMN_BODY.storeValue(transmissionPartRecord, bytes);
		TRANSMISSION_PART_COLUMN_BODY_BIT_LENGTH.storeValue(transmissionPartRecord, bytes.length * Byte.SIZE);
	}
	
	public void storeTransmission(HTTPTransmission httpTransmission) throws Exception
	{
		// TODO Start transaction
		
		// Create record:
		Record tRec = createTransmissionRecord(httpTransmission);
		
		// Set receiver (= serverURL) and number of parts (always = 1):
		TRANSMISSION_COLUMN_RECEIVER.storeValue(tRec, httpTransmission.getServerURL());
		TRANSMISSION_COLUMN_NUMBER_OF_PARTS.storeValue(tRec, 1);
		
		// Store the transmission record:
		doStoreTransmission(httpTransmission, tRec); // after this the localID should always be known
		
		// Create a single transmission part (only used to store the body):
		Record tPartRec = TRANSMISSION_PART_SCHEMA.createRecord();
		TRANSMISSION_PART_COLUMN_TRANSMISSION_ID.storeValue(tPartRec, tRec.getReference()); // set foreign key
		TRANSMISSION_PART_COLUMN_NUMBER.storeValue(tPartRec, 1);
		byte[] bytes = httpTransmission.getBody();
		TRANSMISSION_PART_COLUMN_BODY.storeValue(tPartRec, bytes);
		TRANSMISSION_PART_COLUMN_BODY_BIT_LENGTH.storeValue(tPartRec, bytes.length * Byte.SIZE);
		
		// Store the part:
		recordStore.store(tPartRec);
		
		// TODO commit transaction
	}
	
	public Transmission retrieveTransmission(int localID)
	{
		// TODO
	
		return null;
	}
	
	public BinarySMSTransmission retrieveBinarySMSTransmission(SMSAgent correspondent, boolean sent, int payloadHash)
	{
		// throw special exception when not unique		
		return null;
	}
	
	public TextSMSTransmission retrieveTextSMSTransmission(SMSAgent correspondent, boolean sent, int payloadType, int payloadHash)
	{
		
		return null;
	}

	public HTTPTransmission retrieveHTTPTransmission(int payloadType, int payloadHash)
	{
		
		return null;
	}

	@Override
	public void finalise()
	{
		// TODO Auto-generated method stub
		
	}

	@Override
	public void backup(File destinationFolder) throws Exception
	{
		// TODO Auto-generated method stub
		
	}
	
}
