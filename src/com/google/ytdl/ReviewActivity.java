package com.google.ytdl;

import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.support.v4.app.NavUtils;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.MediaController;
import android.widget.VideoView;

public class ReviewActivity extends Activity {
	private String mChosenAccountName;
	private Uri mFileUri;
	VideoView mVideoView;
	MediaController mc;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		getActionBar().setDisplayHomeAsUpEnabled(true);
		Intent intent = getIntent();
		mFileUri = intent.getData();
		loadAccount();
		setContentView(R.layout.activity_review);
		reviewVideo(mFileUri);
	}

	private void reviewVideo(Uri mFileUri) {
		try {
			mVideoView = (VideoView) findViewById(R.id.videoView);
			mc = new MediaController(this);
			mVideoView.setMediaController(mc);
			mVideoView.setVideoURI(mFileUri);
			mc.show();
			mVideoView.start();
		} catch (Exception e) {
			Log.e("dfhdh", e.toString());
		}
	}

	private void loadAccount() {
		SharedPreferences sp = PreferenceManager
				.getDefaultSharedPreferences(this);
		mChosenAccountName = sp.getString(MainActivity.ACCOUNT_KEY, null);
		invalidateOptionsMenu();
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.review, menu);
		return true;
	}

	public void uploadVideo(View view) {
		if (mChosenAccountName == null) {
			return;
		}
		// if a video is picked or recorded.
		if (mFileUri != null) {
			Intent uploadIntent = new Intent(this, UploadService.class);
			uploadIntent.setData(mFileUri);
			uploadIntent.putExtra(MainActivity.ACCOUNT_KEY, mChosenAccountName);
			startService(uploadIntent);
			// mButton.setEnabled(false);
		}
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		// Respond to the action bar's Up/Home button
		case android.R.id.home:
			NavUtils.navigateUpFromSameTask(this);
			return true;
		}
		return super.onOptionsItemSelected(item);
	}

}
