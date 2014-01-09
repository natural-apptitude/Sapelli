package uk.ac.ucl.excites.collector.project.ui;

import uk.ac.ucl.excites.collector.project.model.fields.*;
import uk.ac.ucl.excites.collector.project.model.fields.lists.MultiListField;

public interface CollectorUI
{

	public FieldUI createChoiceUI(ChoiceField cf);

	public FieldUI createPhotoUI(PhotoField pf);

	public FieldUI createAudioUI(AudioField af);

	public FieldUI createLocationUI(LocationField lf);
	
	public FieldUI createLabelUI(LabelField lf);
	
	public FieldUI createMultiListUI(MultiListField mlf);

	public void setField(Field currentField);

}