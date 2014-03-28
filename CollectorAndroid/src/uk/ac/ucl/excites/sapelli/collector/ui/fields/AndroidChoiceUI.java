package uk.ac.ucl.excites.sapelli.collector.ui.fields;

import java.io.File;

import uk.ac.ucl.excites.sapelli.collector.control.CollectorController;
import uk.ac.ucl.excites.sapelli.collector.model.CollectorRecord;
import uk.ac.ucl.excites.sapelli.collector.model.Field;
import uk.ac.ucl.excites.sapelli.collector.model.fields.ChoiceField;
import uk.ac.ucl.excites.sapelli.collector.ui.CollectorView;
import uk.ac.ucl.excites.sapelli.collector.ui.animation.PressAnimator;
import uk.ac.ucl.excites.sapelli.collector.ui.drawables.SaltireCross;
import uk.ac.ucl.excites.sapelli.collector.ui.fields.ChoiceUI;
import uk.ac.ucl.excites.sapelli.collector.ui.picker.PickerAdapter;
import uk.ac.ucl.excites.sapelli.collector.ui.picker.PickerView;
import uk.ac.ucl.excites.sapelli.collector.ui.picker.items.DrawableItem;
import uk.ac.ucl.excites.sapelli.collector.ui.picker.items.FileImageItem;
import uk.ac.ucl.excites.sapelli.collector.ui.picker.items.Item;
import uk.ac.ucl.excites.sapelli.collector.ui.picker.items.LayeredItem;
import uk.ac.ucl.excites.sapelli.collector.ui.picker.items.TextItem;
import uk.ac.ucl.excites.sapelli.collector.util.ColourHelpers;
import uk.ac.ucl.excites.sapelli.collector.util.ScreenMetrics;
import uk.ac.ucl.excites.sapelli.shared.util.io.FileHelpers;
import android.content.Context;
import android.graphics.Color;
import android.view.View;
import android.widget.AdapterView;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * The UI for ChoiceFields
 * 
 * @author mstevens
 */
public class AndroidChoiceUI extends ChoiceUI<View>
{
	
	static public final int PAGE_CHOSEN_ITEM_SIZE_DIP = 60; // width = height
	static public final float CROSS_THICKNESS = 0.02f;
	
	private PageView pageView;
	private ChoiceView pickView;

	public AndroidChoiceUI(ChoiceField choice, CollectorController controller, CollectorView collectorView)
	{
		super(choice, controller, collectorView);
	}

	@Override
	public View getPlatformView(boolean onPage, CollectorRecord record)
	{
		if(onPage && field.isRoot())
		{
			if(pageView == null)
				pageView = new PageView(((CollectorView) collectorUI).getContext());
			
			// Update pageView:
			ChoiceField chosen = field.getSelectedChoice(record);
			if(chosen != null)
				pageView.setChosen(chosen);
			else
				pageView.setChosen(field);
			
			return pageView;
		}
		else
		{
			if(pickView == null)
				pickView = new ChoiceView(((CollectorView) collectorUI).getContext());
			
			// Update pickView:
			pickView.update();
			
			return pickView;
		}
	}
	
	public class PageView extends LinearLayout
	{

		private TextView label;
		private View chosenView;
		
		public PageView(Context context)
		{
			super(context);
			this.setOrientation(LinearLayout.HORIZONTAL);
			
			// Add label:
			label = new TextView(getContext());
			label.setText(field.getLabel());
			this.addView(label);
		}
		
		public void setChosen(ChoiceField chosenField)
		{
			// Remove previous:
			if(chosenView != null)
				this.removeView(chosenView);
			
			// New chosenView
			int px = ScreenMetrics.ConvertDipToPx(getContext(), PAGE_CHOSEN_ITEM_SIZE_DIP);
			chosenView = createItem(field, px, px, ScreenMetrics.ConvertDipToPx(getContext(), CollectorView.PADDING_DIP)).getView(getContext());
			chosenView.setOnClickListener(new OnClickListener()
			{
				@Override
				public void onClick(View v)
				{
					controller.goToFromPage(field);
				}
			});
			this.addView(chosenView);
		}
		
	}
	
	/**
	 * TODO later we may implement colSpan/rowSpan support here, but that would require us to base the ChoiceView on GridLayout rather than PickerView/GridView
	 * 
	 * @author Julia, mstevens, Michalis Vitos
	 */
	public class ChoiceView extends PickerView implements AdapterView.OnItemClickListener, AdapterView.OnItemLongClickListener
	{
		
		static public final String TAG = "ChoiceView";
				
		public ChoiceView(Context context)
		{
			super(context);
			
			// UI set-up:
			setBackgroundColor(Color.BLACK);
			int spacingPx = collectorUI.getSpacingPx();
			setHorizontalSpacing(spacingPx);
			setVerticalSpacing(spacingPx);
			
			// Number of columns:
			setNumColumns(field.getCols());
			
			// Item size & padding:
			int itemWidthPx = ((CollectorView) collectorUI).getIconWidthPx(field.getCols());
			int itemHeightPx = ((CollectorView) collectorUI).getIconHeightPx(field.getRows(), controller.getControlsState().isAnyButtonShown());
			int itemPaddingPx = ScreenMetrics.ConvertDipToPx(getContext(), CollectorView.PADDING_DIP);

			// Adapter & images:
			pickerAdapter = new PickerAdapter(getContext());
			for(ChoiceField child : field.getChildren())
				pickerAdapter.addItem(createItem(child, itemWidthPx, itemHeightPx, itemPaddingPx));
			
			// Click listeners:
			setOnItemClickListener(this);
			setOnItemLongClickListener(this);
		}
		
		public void update()
		{
			// Update visibility:
			int c = 0;
			for(ChoiceField child : field.getChildren())
				pickerAdapter.getItem(c++).setVisibility(controller.isFieldEndabled(child));
			setAdapter(pickerAdapter);
		}
		
		@Override
		public void onItemClick(AdapterView<?> parent, View v, final int position, long id)
		{
			// Task to perform after animation has finished:
			Runnable action = new Runnable()
			{
				public void run()
				{
					choiceMade(field.getChildren().get(position)); // pass the chosen child
				}
			};

			// Execute the "press" animation if allowed, then perform the action: 
			if(controller.getCurrentForm().isAnimation())
				(new PressAnimator(action, v, (CollectorView) collectorUI)).execute(); //execute animation and the action afterwards
			else
				action.run(); //perform task now (animation is disabled)
		}
		
		@Override
		public boolean onItemLongClick(AdapterView<?> parent, View v, int position, long id)
		{
			return false;
		}

	}
	
	/**
	 * Creates an DictionaryItem object responding to the provided child ChoiceField
	 * 
	 * Note: if we add colSpan/rowSpan support the right itemWidth/Height would need to be computed here (e.g.: for rowSpan=2 the itemWidth becomes (itemWidth*2)+spacingPx)
	 * 
	 * @param child
	 * @return corresponding item
	 */
	private Item createItem(ChoiceField child, int itemWidthPx, int itemHeightPx, int itemPaddingPx)
	{
		File imageFile = controller.getProject().getImageFile(child.getImageRelativePath());
		Item item = null;
		if(FileHelpers.isReadableFile(imageFile))
			item = new FileImageItem(imageFile);
		else
			item = new TextItem(child.getAltText()); //render alt text instead of image
		
		// Crossing
		if(child.isCrossed())
		{
			LayeredItem crossedItem = new LayeredItem();
			int crossColour = ColourHelpers.ParseColour(child.getCrossColor(), ChoiceField.DEFAULT_CROSS_COLOR);
			crossedItem.addLayer(item);
			crossedItem.addLayer(new DrawableItem(new SaltireCross(crossColour, CROSS_THICKNESS))); // later we may expose thickness in the XML as well
			item = crossedItem;
		}
		
		// Set size & padding:
		item.setWidthPx(itemWidthPx);
		item.setHeightPx(itemHeightPx);
		item.setPaddingPx(itemPaddingPx);
		
		// Set background colour:
		item.setBackgroundColor(ColourHelpers.ParseColour(child.getBackgroundColor(), Field.DEFAULT_BACKGROUND_COLOR));
		
		return item;
	}
	
}