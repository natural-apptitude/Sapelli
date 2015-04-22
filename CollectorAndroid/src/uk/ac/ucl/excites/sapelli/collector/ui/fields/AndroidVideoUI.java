/**
 * Sapelli data collection platform: http://sapelli.org
 * 
 * Copyright 2012-2014 University College London - ExCiteS group
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

package uk.ac.ucl.excites.sapelli.collector.ui.fields;

import java.io.File;

import uk.ac.ucl.excites.sapelli.collector.R;
import uk.ac.ucl.excites.sapelli.collector.control.CollectorController;
import uk.ac.ucl.excites.sapelli.collector.control.Controller.LeaveRule;
import uk.ac.ucl.excites.sapelli.collector.model.fields.PhotoField.FlashMode;
import uk.ac.ucl.excites.sapelli.collector.model.fields.VideoField;
import uk.ac.ucl.excites.sapelli.collector.ui.CollectorView;
import uk.ac.ucl.excites.sapelli.collector.ui.items.DrawableItem;
import uk.ac.ucl.excites.sapelli.collector.ui.items.ImageItem;
import uk.ac.ucl.excites.sapelli.collector.ui.items.Item;
import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.media.MediaPlayer;
import android.media.MediaPlayer.OnCompletionListener;
import android.media.ThumbnailUtils;
import android.net.Uri;
import android.provider.MediaStore;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnTouchListener;
import android.widget.ImageView;
import android.widget.ImageView.ScaleType;
import android.widget.LinearLayout;
import android.widget.VideoView;

/**
 * A subclass of AndroidMediaUI which allows for the capture and review of videos from the device's camera.
 * 
 * NOTE: Samsung decided not to bother to enable portrait photo/video capture in the xCover 1 kernel, so captures may display incorrectly on that model.
 * -- See http://stackoverflow.com/questions/19176038/
 * 
 * @author mstevens, Michalis Vitos, benelliott
 */
public class AndroidVideoUI extends AndroidCameraUI<VideoField> implements OnCompletionListener
{
	
	static protected final String TAG = "AndroidVideoUI";

	private VideoView playbackView;
	private volatile boolean recording = false;
	private int playbackPosition = 0;

	public AndroidVideoUI(VideoField field, CollectorController controller, CollectorView collectorUI)
	{
		super(	field,
				controller,
				collectorUI,
				true,	// showing few finder before capture
				false);	// do not allow clicks as the capture process (see onCapture()) is asynchronous and returns immediately
	}

	@Override
	protected View getCaptureContent(Context context)
	{
		return getCaptureContent(context, field.isUseFrontFacingCamera(), FlashMode.OFF); // TODO expose flash mode inb XML for <Video>?
	}
	
	/**
	 * If not currently recording, will return a "start recording" button. If currently recording, will return a "stop recording" button.
	 */
	@Override
	protected ImageItem<?> generateCaptureButton(Context context)
	{
		if(!recording)
			// recording hasn't started yet, so present "record" button
			return collectorUI.getImageItemFromProjectFileOrResource(field.getStartRecImageRelativePath(), R.drawable.button_video_capture_svg);
		else
			// recording started, so present "stop" button instead
			return collectorUI.getImageItemFromProjectFileOrResource(field.getStopRecImageRelativePath(), R.drawable.button_stop_audio_svg);
	}

	@Override
	protected void onCapture()
	{
		if(!recording)
		{
			// start recording
			captureFile = field.getNewAttachmentFile(controller.getFileStorageProvider(), controller.getCurrentRecord());
			cameraController.startVideoCapture(captureFile);
			recording = true;
		}
		else
		{
			// stop recording
			cameraController.stopVideoCapture();
			// a capture has been made so show it for review:
			attachMedia(captureFile);
			recording = false;
			if(field.isShowReview())
				controller.goToCurrent(LeaveRule.UNCONDITIONAL_WITH_STORAGE);
			else
				controller.goForward(true);
		}
	}
	
	@Override
	protected View getReviewContent(Context context, File mediaFile)
	{
		LinearLayout reviewLayout = new LinearLayout(context);
		reviewLayout.setGravity(Gravity.CENTER);
		// instantiate the thumbnail that is shown before the video is started:
		final ImageView thumbnailView = new ImageView(context);
		// create thumbnail from video file:
		Bitmap thumbnail = ThumbnailUtils.createVideoThumbnail(mediaFile.getAbsolutePath(), MediaStore.Images.Thumbnails.FULL_SCREEN_KIND);
		thumbnailView.setScaleType(ScaleType.FIT_CENTER);
		thumbnailView.setImageBitmap(thumbnail);

		// instantiate the video view that plays the captured video:
		playbackView = new VideoView(context);
		playbackView.setOnCompletionListener(this);
		playbackView.setVideoURI(Uri.fromFile(mediaFile));
		// don't show the video view straight away - only once the thumbnail is clicked:
		playbackView.setVisibility(View.GONE);
		
		// layout params for the thumbnail and the video view are the same:
		LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.MATCH_PARENT);
		reviewLayout.addView(thumbnailView, params);
		reviewLayout.addView(playbackView, params);

		// Set the video view to play or pause the video when it is touched:
		playbackView.setOnTouchListener(new OnTouchListener()
		{
			@Override
			@SuppressLint("ClickableViewAccessibility")
			public boolean onTouch(View v, MotionEvent ev)
			{
				controller.blockUI();
				if(ev.getAction() == MotionEvent.ACTION_UP)
				{
					// only perform action when finger is lifted off screen
					if(playbackView.isPlaying())
					{
						// if playing, pause
						// Log.d(TAG, "Pausing video...");
						playbackPosition = playbackView.getCurrentPosition();
						playbackView.pause();
					}

					else
					{
						// if not playing, play
						// Log.d(TAG, "Playing video...");
						playbackView.seekTo(playbackPosition);
						playbackView.start();
					}
				}
				controller.unblockUI();
				return true;
			}
		});

		// replace the thumbnail with the video when the thumbnail is clicked:
		thumbnailView.setOnClickListener(new OnClickListener()
		{
			@Override
			public void onClick(View v)
			{
				controller.blockUI();
				thumbnailView.setVisibility(View.GONE);
				playbackView.setVisibility(View.VISIBLE);
				playbackView.start();
				controller.unblockUI();
			}
		});

		return reviewLayout;
	}

	@Override
	protected Item<?> getGalleryItem(int index, File videoFile)
	{
		// Create thumbnail from video file:
		Bitmap thumbnail = ThumbnailUtils.createVideoThumbnail(videoFile.getAbsolutePath(), MediaStore.Images.Thumbnails.MINI_KIND);
		return new DrawableItem(index, new BitmapDrawable(collectorUI.getResources(), thumbnail));
	}
	
	@Override
	public void onCompletion(MediaPlayer mp)
	{
		// playback has finished so go back to start
		playbackPosition = 0;
	}

	@Override
	protected int getCameraErrorStringId(boolean fatal)
	{
		return fatal ? R.string.videoCameraErrorFatal : R.string.videoCameraErrorSkip;
	}
	
	@Override
	protected void cancel()
	{
		super.cancel();		
		recording = false;
		
		if(playbackView != null)
			playbackView.stopPlayback();
		
		// TODO recycle views?
		playbackView = null;
	}
	
}