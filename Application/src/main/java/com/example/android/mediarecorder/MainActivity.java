/*
 * Copyright (C) 2013 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.android.mediarecorder;

import com.example.android.common.media.CameraHelper;

import android.annotation.TargetApi;
import android.app.Activity;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.hardware.Camera;
import android.media.CamcorderProfile;
import android.media.MediaRecorder;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.widget.Button;

import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 * This activity uses the camera/camcorder as the A/V source for the {@link android.media.MediaRecorder} API.
 * A {@link android.view.TextureView} is used as the camera preview which limits the code to API 14+. This
 * can be easily replaced with a {@link android.view.SurfaceView} to run on older devices.
 */
public class MainActivity extends Activity
{

	private Camera mCamera;
	private TextureView mPreview;
	private MediaRecorder mMediaRecorder;
	private File mOutputFile;

	private boolean isRecording = false;
	private static final String TAG = "Recorder";
	private Button captureButton;

	@Override
	protected void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		setContentView(R.layout.sample_main);

		mPreview = (TextureView) findViewById(R.id.surface_view);
		captureButton = (Button) findViewById(R.id.button_capture);
	}

	/**
	 * The capture button controls all user interaction. When recording, the button click
	 * stops recording, releases {@link android.media.MediaRecorder} and {@link android.hardware.Camera}. When not recording,
	 * it prepares the {@link android.media.MediaRecorder} and starts recording.
	 *
	 * @param view the view generating the event.
	 */
	public void onCaptureClick(View view)
	{
		if (isRecording)
		{
			// BEGIN_INCLUDE(stop_release_media_recorder)

			// stop recording and release camera
			try
			{
				mMediaRecorder.stop();  // stop the recording
			}
			catch (RuntimeException e)
			{
				// RuntimeException is thrown when stop() is called immediately after start().
				// In this case the output file is not properly constructed ans should be deleted.
				Log.d(TAG, "RuntimeException: stop() is called immediately after start()");
				//noinspection ResultOfMethodCallIgnored
				mOutputFile.delete();
			}
			releaseMediaRecorder(); // release the MediaRecorder object
			mCamera.lock();         // take camera access back from MediaRecorder

			// inform the user that recording has stopped
			setCaptureButtonText("Capture");
			isRecording = false;
			releaseCamera();
			// END_INCLUDE(stop_release_media_recorder)

		}
		else
		{

			// BEGIN_INCLUDE(prepare_start_media_recorder)

			new MediaPrepareTask().execute(null, null, null);

			// END_INCLUDE(prepare_start_media_recorder)

		}
	}

	private void setCaptureButtonText(String title)
	{
		captureButton.setText(title);
	}

	@Override
	protected void onPause()
	{
		super.onPause();
		// if we are using MediaRecorder, release it first
		releaseMediaRecorder();
		// release the camera immediately on pause event
		releaseCamera();
	}

	private void releaseMediaRecorder()
	{
		if (mMediaRecorder != null)
		{
			// clear recorder configuration
			mMediaRecorder.reset();
			// release the recorder object
			mMediaRecorder.release();
			mMediaRecorder = null;
			// Lock camera for later use i.e taking it back from MediaRecorder.
			// MediaRecorder doesn't need it anymore and we will release it if the activity pauses.
			mCamera.lock();
		}
	}

	private void releaseCamera()
	{
		if (mCamera != null)
		{
			// release the camera for other applications
			mCamera.release();
			mCamera = null;
		}
	}

	@TargetApi(Build.VERSION_CODES.HONEYCOMB)
	private boolean prepareVideoRecorder()
	{

		// BEGIN_INCLUDE (configure_preview)
		mCamera = CameraHelper.getDefaultFrontFacingCameraInstance();
		// We need to make sure that our preview and recording video size are supported by the
		// camera. Query camera to find all the sizes and choose the optimal size given the
		// dimensions of our preview surface.
		Camera.Parameters parameters = mCamera.getParameters();
		List<Camera.Size> mSupportedPreviewSizes = parameters.getSupportedPreviewSizes();
		List<Camera.Size> mSupportedVideoSizes = parameters.getSupportedVideoSizes();
		Camera.Size optimalSize = CameraHelper.getOptimalVideoSize(mSupportedVideoSizes, mSupportedPreviewSizes, mPreview.getWidth(), mPreview.getHeight());

		// Use the same size for recording profile.
		CamcorderProfile profile = CamcorderProfile.get(CamcorderProfile.QUALITY_HIGH);
		profile.videoFrameWidth = optimalSize.width;
		profile.videoFrameHeight = optimalSize.height;

		// likewise for the camera object itself.
		parameters.setPreviewSize(profile.videoFrameWidth, profile.videoFrameHeight);
		mCamera.setParameters(parameters);
		try
		{
			final int  orientation = setCameraDisplayOrientation(this, Camera.CameraInfo.CAMERA_FACING_FRONT, mCamera);
			final CamcorderProfile safeProfile = profile;
			// Requires API level 11+, For backward compatibility use {@link setPreviewDisplay}
			runOnUiThread(new Runnable() {
				@Override
				public void run()
				{
					adjustAspectRatio(safeProfile.videoFrameWidth, safeProfile.videoFrameHeight,orientation);
				}
			});


			// with {@link SurfaceView}
			mCamera.setPreviewTexture(mPreview.getSurfaceTexture());
		}
		catch (IOException e)
		{
			Log.e(TAG, "Surface texture is unavailable or unsuitable" + e.getMessage());
			return false;
		}
		// END_INCLUDE (configure_preview)

		// BEGIN_INCLUDE (configure_media_recorder)
		mMediaRecorder = new MediaRecorder();
		mMediaRecorder.setOrientationHint(270);

		// Step 1: Unlock and set camera to MediaRecorder
		mCamera.unlock();
		mMediaRecorder.setCamera(mCamera);

		// Step 2: Set sources
		mMediaRecorder.setAudioSource(MediaRecorder.AudioSource.DEFAULT);
		mMediaRecorder.setVideoSource(MediaRecorder.VideoSource.CAMERA);

		// Step 3: Set a CamcorderProfile (requires API Level 8 or higher)
		mMediaRecorder.setProfile(profile);

		// Step 4: Set output file
		mOutputFile = CameraHelper.getOutputMediaFile(CameraHelper.MEDIA_TYPE_VIDEO);
		if (mOutputFile == null)
		{
			return false;
		}
		mMediaRecorder.setOutputFile(mOutputFile.getPath());
		// END_INCLUDE (configure_media_recorder)
		// Step 5: Prepare configured MediaRecorder
		try
		{
			mMediaRecorder.prepare();
		}
		catch (IllegalStateException e)
		{
			Log.d(TAG, "IllegalStateException preparing MediaRecorder: " + e.getMessage());
			releaseMediaRecorder();
			return false;
		}
		catch (IOException e)
		{
			Log.d(TAG, "IOException preparing MediaRecorder: " + e.getMessage());
			releaseMediaRecorder();
			return false;
		}
		return true;
	}

	/**
	 * Asynchronous task for preparing the {@link android.media.MediaRecorder} since it's a long blocking
	 * operation.
	 */
	class MediaPrepareTask extends AsyncTask<Void, Void, Boolean>
	{

		@Override
		protected Boolean doInBackground(Void... voids)
		{
			// initialize video camera
			if (prepareVideoRecorder())
			{
				// Camera is available and unlocked, MediaRecorder is prepared,
				// now you can start recording
				mMediaRecorder.start();
				isRecording = true;
			}
			else
			{
				// prepare didn't work, release the camera
				releaseMediaRecorder();
				return false;
			}
			return true;
		}

		@Override
		protected void onPostExecute(Boolean result)
		{
			if (!result)
			{
				MainActivity.this.finish();
			}
			// inform the user that recording has started
			setCaptureButtonText("Stop");

		}
	}

	public static int setCameraDisplayOrientation(Activity activity, int cameraId, android.hardware.Camera camera)
	{
		android.hardware.Camera.CameraInfo info = new android.hardware.Camera.CameraInfo();
		android.hardware.Camera.getCameraInfo(cameraId, info);
		int rotation = activity.getWindowManager()
		                       .getDefaultDisplay()
		                       .getRotation();
		int degrees = 0;
		switch (rotation)
		{
			case Surface.ROTATION_0:
				degrees = 0;
				break;
			case Surface.ROTATION_90:
				degrees = 90;
				break;
			case Surface.ROTATION_180:
				degrees = 180;
				break;
			case Surface.ROTATION_270:
				degrees = 270;
				break;
		}

		int result;
		if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT)
		{
			result = (info.orientation + degrees) % 360;
			result = (360 - result) % 360;  // compensate the mirror
		}
		else
		{  // back-facing
			result = (info.orientation - degrees + 360) % 360;
		}
		camera.setDisplayOrientation(result);
		return result;
	}

	private void adjustAspectRatio(int previewWidth, int previewHeight, int rotation)
	{
		Matrix txform = new Matrix();
		int viewWidth = mPreview.getWidth();
		int viewHeight = mPreview.getHeight();
		RectF rectView = new RectF(0, 0, viewWidth, viewHeight);
		float viewCenterX = rectView.centerX();
		float viewCenterY = rectView.centerY();
		RectF rectPreview = new RectF(0, 0, previewHeight, previewWidth);
		float previewCenterX = rectPreview.centerX();
		float previewCenterY = rectPreview.centerY();

		if (Surface.ROTATION_90 == rotation || Surface.ROTATION_270 == rotation)
		{
			rectPreview.offset(viewCenterX - previewCenterX, viewCenterY - previewCenterY);

			txform.setRectToRect(rectView, rectPreview, Matrix.ScaleToFit.FILL);

			float scale = Math.max((float) viewHeight / previewHeight, (float) viewWidth / previewWidth);

			txform.postScale(scale, scale, viewCenterX, viewCenterY);
			txform.postRotate(90 * (rotation - 2), viewCenterX, viewCenterY);
		}
		else
		{
			if (Surface.ROTATION_180 == rotation)
			{
				txform.postRotate(180, viewCenterX, viewCenterY);
			}
		}

		txform.postScale(-1, 1, viewCenterX, viewCenterY);

		mPreview.setTransform(txform);
	}

}
