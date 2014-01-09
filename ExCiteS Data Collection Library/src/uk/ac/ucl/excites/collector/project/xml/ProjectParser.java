/**
 *
 */
package uk.ac.ucl.excites.collector.project.xml;

import java.io.File;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map.Entry;

import org.xml.sax.Attributes;
import org.xml.sax.SAXException;

import uk.ac.ucl.excites.collector.project.model.Form;
import uk.ac.ucl.excites.collector.project.model.Project;
import uk.ac.ucl.excites.collector.project.model.fields.Relationship;
import uk.ac.ucl.excites.transmission.Settings;
import uk.ac.ucl.excites.util.io.UnclosableBufferedInputStream;
import uk.ac.ucl.excites.util.xml.DocumentParser;
import uk.ac.ucl.excites.util.xml.XMLHasher;

/**
 * Handler for project (i.e. survey) description XML files
 * 
 * Currently supported formats are the v1.x format and the new v2.x format.
 * Introducing new format version number is only really necessary when major changes are made that affect (backwards & forwards) compatibility.
 * Rules:
 * 	- If a file is parsed which does not mention a format:
 * 		- v1.x is assumed for "pre-Sapelli" projects (which have <ExCiteS-Collector-Project> tag);
 * 		- v2.x (DEFAULT_FORMAT) is assumed for Sapelli projects (with <Sapelli-Collector-Project> tag).
 * 	- If a file is parsed which mentions a higher format than the highest supported one the parser will issue a warning and attempt parsing it as the highest supported one (which could fail).
 *  - If a file is parsed which mentions a lower format than the lowest supported one the parser will throw an exception.
 * 
 * @author mstevens, julia, Michalis Vitos
 * 
 */
public class ProjectParser extends DocumentParser
{

	// STATICS--------------------------------------------------------
	
	static public enum Format
	{
		v0_x, 	// v0.1: trial versions: never used in field & not supported in any present implementation (only really listed here to reserver value 0 such that v1_x corresponds to 1, etc.)
		v1_x, 	// v1.x: "pre-Sapelli" versions of the ExCiteS Data Collection platform (used for AP, OIFLEG & Ethiopia/Jed)
		v2_x	// v2.x: first series of Sapelli-branded versions
		// future releases...
	}
	static public final Format LOWEST_SUPPORTED_FORMAT = Format.v1_x;
	static public final Format HIGHEST_SUPPORTED_FORMAT = Format.values()[Format.values().length - 1]; //last value in enum
	static public final Format DEFAULT_FORMAT = Format.v2_x;
	
	// Tags:
	static private final String TAG_PROJECT = "SapelliCollectorProject";
	static private final String TAG_PROJECT_V1X = "ExCiteS-Collector-Project";

	// Attributes:
	static private final String ATTRIBUTE_PROJECT_FORMAT = "format";
	static private final String ATTRIBUTE_PROJECT_ID = "id";
	static private final String ATTRIBUTE_PROJECT_NAME = "name";
	static private final String ATTRIBUTE_PROJECT_VARIANT = "variant";
	static private final String ATTRIBUTE_PROJECT_VERSION = "version";
	static private final String ATTRIBUTE_PROJECT_START_FORM = "startForm";
	

	// DYNAMICS-------------------------------------------------------
	private final String basePath;
	private final boolean createProjectFolder;
	
	private Format format = DEFAULT_FORMAT;
	
	private Project project;
	private long projectHash = -1;
	private String startFormID;
	private HashMap<Relationship, String> relationshipToFormID;

	public ProjectParser(String basePath, boolean createProjectFolder)
	{
		super();
		this.basePath = basePath;
		this.createProjectFolder = createProjectFolder;
		this.relationshipToFormID = new HashMap<Relationship, String>();
	}

	public Project parseProject(File xmlFile) throws Exception
	{
		return parseProject(open(xmlFile));
	}

	public Project parseProject(InputStream input) throws Exception
	{
		project = null;
		relationshipToFormID.clear();
		
		// Get XML hash...
		UnclosableBufferedInputStream ubInput = new UnclosableBufferedInputStream(input); // decorate stream to avoid it from being closed and to ensure we can use mark/reset
		ubInput.mark(Integer.MAX_VALUE);
		projectHash = (new XMLHasher()).getCRC32HashCode(ubInput);
		ubInput.reset();
		ubInput.makeClosable();
		
		// Parse XML:
		parse(ubInput); //!!!
		return project;
	}

	@Override
	public void startDocument() throws SAXException
	{
		// does nothing (for now)
	}
		
	@Override
	public void endDocument() throws SAXException
	{
		// does nothing (for now)
	}

	@Override
	protected void parseStartElement(String uri, String localName, String qName, Attributes attributes) throws SAXException
	{
		try
		{
			// <Sapelli-Collector-Project>, or <ExCiteS-Collector-Project> (for backwards compatibility)
			if(qName.equals(TAG_PROJECT) || qName.equals(TAG_PROJECT_V1X))
			{
				if(project != null)
				{
					addWarning("Ignoring additional " + TAG_PROJECT + " or " + TAG_PROJECT_V1X + " element.");
					return;
				}
				
				// Detect format version...
				int formatVersion = readIntegerAttribute(ATTRIBUTE_PROJECT_FORMAT, qName.equals(TAG_PROJECT_V1X) ? Format.v1_x.ordinal() : DEFAULT_FORMAT.ordinal(), attributes); //default: v1.x for ExCiteS tag, DEFAULT_FORMAT for Sapelli tag. 
				// 	too low:
				if(formatVersion < LOWEST_SUPPORTED_FORMAT.ordinal())
					throw new SAXException("Unsupported format version: " + formatVersion);
				// 	too high:
				else if(formatVersion > HIGHEST_SUPPORTED_FORMAT.ordinal())
				{	//issue warning and then try to parse as highest supported format (might fail)
					addWarning("Format version reported in XML file (" + formatVersion + ") is unsupported (" + LOWEST_SUPPORTED_FORMAT + " <= supported <= " + HIGHEST_SUPPORTED_FORMAT + "), attempting parsing with rules for version " + HIGHEST_SUPPORTED_FORMAT); 
					format = HIGHEST_SUPPORTED_FORMAT;
				}
				//	within range (or default because missing attribute):
				else
					format = Format.values()[formatVersion]; 
				
				// Project...
				project = new Project(	(format == Format.v1_x) ?
											Project.PROJECT_ID_V1X_TEMP : // for format = 1 we set a temp id value (will be replaced by Form:schema-id) 
											readRequiredIntegerAttribute(qName, ATTRIBUTE_PROJECT_ID, "because format is >= 2", attributes), // id is required for format >= 2
										projectHash,
										readRequiredStringAttribute(TAG_PROJECT, ATTRIBUTE_PROJECT_NAME, attributes),
										readStringAttribute(ATTRIBUTE_PROJECT_VERSION, Project.DEFAULT_VERSION, attributes),
										basePath,
										createProjectFolder);
				
				// Set variant:
				project.setVariant(readStringAttribute(ATTRIBUTE_PROJECT_VARIANT, null, attributes));
				
				// Read startForm ID:
				startFormID = readStringAttribute(ATTRIBUTE_PROJECT_START_FORM, null, attributes);
				
				// Add subtree parsers:
				addSubtreeParser(new ConfigurationParser(this, project));
				addSubtreeParser(new FormParser(this, project, format));
			}
			// <?>
			else
				addWarning("Ignored unrecognised or invalidly placed/repeated element <" + qName + ">.");
		}
		catch(SAXException se)
		{
			throw se;
		}
		catch(Exception e)
		{
			throw new SAXException("Error while parsing element <" + qName + ">: " + e.getMessage(), e);
		}
	}

	@Override
	protected void parseEndElement(String uri, String localName, String qName) throws SAXException
	{
		// </Sapelli-Collector-Project>, or </ExCiteS-Collector-Project> (for backwards compatibility)
		if(qName.equals(TAG_PROJECT) || qName.equals(TAG_PROJECT_V1X))
		{			if(project.getTransmissionSettings() == null)
			{
				project.setTransmissionSettings(new Settings());
				addWarning("No transmission settings found, defaults are used");
			}
			
			if(project.getForms().size() == 0)
				throw new SAXException("A project such have at least 1 form!");
			else
			{
				// Resolve startForm
				Form startForm = project.getForm(startFormID); //will return null if startFormID is null or there is no form with that name
				if(startForm != null)
					project.setStartForm(startForm);
				//else: first form of project will remain the startForm
				
				// Resolve form relationships:
				for(Entry<Relationship, String> entry : relationshipToFormID.entrySet())
				{
					Relationship rel = entry.getKey();
					Form relatedForm = project.getForm(entry.getValue());
					if(relatedForm == null)
						throw new SAXException("Relationship \"" + rel.getID() + "\" in form \"" + rel.getForm().getID() + "\" refers to unknown related form \"" + entry.getValue() + "\".");
					rel.setRelatedForm(relatedForm);
				}
				
				// Initialise forms...
				for(Form form : project.getForms())
				{	
					form.initialiseStorage(); // generates Schema, Column & ValueDictionaries
					addWarnings(form.getWarnings());		
				}
			}
		}
	}

	/**
	 * @return the format
	 */
	public Format getFormat()
	{
		return format;
	}

	/**
	 * @return the project
	 */
	public Project getProject()
	{
		return project;
	}
	
	/**
	 * Called from {@link FormParser}
	 * 
	 * @param relationship
	 * @param formID
	 */
	public void addRelationship(Relationship relationship, String formID)
	{
		relationshipToFormID.put(relationship, formID);
	}

}
