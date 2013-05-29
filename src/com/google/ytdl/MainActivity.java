/*
 * Copyright (c) 2013 Google Inc.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package com.google.ytdl;

import com.google.android.gms.auth.GoogleAuthException;
import com.google.android.gms.auth.GoogleAuthUtil;
import com.google.android.gms.auth.GooglePlayServicesAvailabilityException;
import com.google.android.gms.auth.UserRecoverableAuthException;
import com.google.android.gms.common.AccountPicker;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesUtil;
import com.google.android.gms.common.Scopes;
import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson.JacksonFactory;
import com.google.api.services.plus.Plus;
import com.google.api.services.plus.model.Person;
import com.google.api.services.youtube.YouTube;
import com.google.api.services.youtube.YouTubeScopes;
import com.google.api.services.youtube.model.ChannelListResponse;
import com.google.api.services.youtube.model.PlaylistItem;
import com.google.api.services.youtube.model.PlaylistItemListResponse;
import com.google.api.services.youtube.model.Video;
import com.google.api.services.youtube.model.VideoListResponse;
import com.google.ytdl.util.ImageFetcher;
import com.google.ytdl.util.VideoData;

import android.accounts.AccountManager;
import android.app.Activity;
import android.app.Dialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.provider.MediaStore;
import android.support.v4.content.LocalBroadcastManager;
import android.text.TextUtils;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.Toast;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * @author Ibrahim Ulukaya <ulukaya@google.com>
 *         <p/>
 *         Main activity class which handles authorization and intents.
 */
public class MainActivity extends Activity implements UploadsListFragment.Callbacks,
        DirectFragment.Callbacks {
    private static final int REQUEST_GMS_ERROR_DIALOG = 1;
    private static final int REQUEST_PICK_ACCOUNT = 2;
    private static final int REQUEST_AUTHENTICATE = 3;
    private static final int RESULT_PICK_IMAGE_CROP = 4;
    private static final int RESULT_VIDEO_CAP = 5;
    static final String INVALIDATE_TOKEN_INTENT = "com.google.ytdl.invalidate";
    public static final String ACCOUNT_KEY = "account";
    public static final String TOKEN_KEY = "token";
    public static final String YOUTUBE_WATCH_URL_PREFIX = "http://www.youtube.com/watch?v=";

    private ImageFetcher mImageFetcher;

    private InvalidateTokenReceiver invalidateTokenReceiver;

    private String mChosenAccountName;
    private String mToken;
    private Uri mFileURI = null;
    private Handler mHandler = new Handler();

    private VideoData mVideoData;

    private int mCurrentBackoff = 0;
    private Button mButton;

    private UploadsListFragment mUploadsListFragment;
    private DirectFragment mDirectFragment;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        getWindow().requestFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        ensureFetcher();
        mButton = (Button) findViewById(R.id.upload_button);
        mButton.setEnabled(false);

        int errorCode = GooglePlayServicesUtil.isGooglePlayServicesAvailable(this);
        if (errorCode != ConnectionResult.SUCCESS) {
            if (GooglePlayServicesUtil.isUserRecoverableError(errorCode)) {
                Dialog errorDialog =
                        GooglePlayServicesUtil.getErrorDialog(errorCode, this, REQUEST_GMS_ERROR_DIALOG);
                if (errorDialog != null) {
                    errorDialog.show();
                }
            } else {
                Toast.makeText(this, R.string.google_play_not_available , Toast.LENGTH_SHORT).show();
                finish();
            }
        }

        if (savedInstanceState != null) {
            mToken = savedInstanceState.getString(TOKEN_KEY);
            mChosenAccountName = savedInstanceState.getString(ACCOUNT_KEY);
        } else {
            loadAccount();
        }

        mHandler.post(new Runnable() {
            @Override
            public void run() {
                loadData();
            }
        });

        mUploadsListFragment =
                (UploadsListFragment) getFragmentManager().findFragmentById(R.id.list_fragment);
        mDirectFragment = (DirectFragment) getFragmentManager().findFragmentById(R.id.direct_fragment);
    }

    @Override
    public void onResume() {
        super.onResume();
        if (invalidateTokenReceiver == null) invalidateTokenReceiver = new InvalidateTokenReceiver();
        IntentFilter intentFilter = new IntentFilter(INVALIDATE_TOKEN_INTENT);
        LocalBroadcastManager.getInstance(this).registerReceiver(invalidateTokenReceiver, intentFilter);
    }

    private void ensureFetcher() {
        if (mImageFetcher == null) {
            mImageFetcher = new ImageFetcher(this, 512, 512);
            mImageFetcher.addImageCache(getFragmentManager(),
                    new com.google.ytdl.util.ImageCache.ImageCacheParams(this, "cache"));
        }
    }

    private void loadAccount() {
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(this);
        mChosenAccountName = sp.getString(ACCOUNT_KEY, null);
        mToken = sp.getString(TOKEN_KEY, null);
        invalidateOptionsMenu();
    }

    private void saveAccount() {
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(this);
        sp.edit().putString(ACCOUNT_KEY, mChosenAccountName).putString(TOKEN_KEY, mToken).commit();
    }

    private void loadData() {
        if (mToken == null) {
            return;
        }

        loadProfile();
        loadUploadedVideos();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (invalidateTokenReceiver != null) {
            LocalBroadcastManager.getInstance(this).unregisterReceiver(invalidateTokenReceiver);
        }
        if (isFinishing()) {
            mHandler.removeCallbacksAndMessages(null);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        getMenuInflater().inflate(R.menu.activity_main, menu);
        menu.findItem(R.id.action_sign_in).setVisible(mToken == null);
        menu.findItem(R.id.action_refresh).setVisible(mToken != null);
        menu.findItem(R.id.action_sign_out).setVisible(mToken != null);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_sign_in) {
            authenticate();
            return true;
        } else if (id == R.id.action_refresh) {
            loadData();
            return true;
        } else if (id == R.id.action_sign_out) {
            mToken = null;
            mChosenAccountName = null;
            mUploadsListFragment.setVideos(new ArrayList<VideoData>());
            mUploadsListFragment.setProfileInfo(null);
            saveAccount();
            invalidateOptionsMenu();
            return true;
        } else if (id == R.id.action_youtube) {
            if (mVideoData != null) {
                Intent intent = new Intent(Intent.ACTION_VIEW);
                intent.setData(Uri.parse(YOUTUBE_WATCH_URL_PREFIX + mVideoData.getYouTubeId()));
                startActivity(intent);
            }
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    public void authenticate() {
        Intent accountChooserIntent =
                AccountPicker.newChooseAccountIntent(null, null,
                        new String[]{GoogleAuthUtil.GOOGLE_ACCOUNT_TYPE}, true, "Can haz token plz?", null,
                        null, null);
        startActivityForResult(accountChooserIntent, REQUEST_PICK_ACCOUNT);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            case REQUEST_GMS_ERROR_DIALOG:
                break;

            case REQUEST_PICK_ACCOUNT:
                if (resultCode == RESULT_OK) {
                    mChosenAccountName = data.getStringExtra(AccountManager.KEY_ACCOUNT_NAME);
                    tryAuthenticate();
                } else {
                    // TODO
                }
                break;

            case REQUEST_AUTHENTICATE:
                if (resultCode == RESULT_OK) {
                    tryAuthenticate();
                }
                setProgressBarIndeterminateVisibility(false);
                break;

            case RESULT_PICK_IMAGE_CROP:
                if (resultCode == RESULT_OK) {
                    mFileURI = data.getData();
                    mVideoData = null; // TODO
                }
                break;

            case RESULT_VIDEO_CAP:
                if (resultCode == RESULT_OK) {
                    mFileURI = data.getData();
                    mVideoData = null; // TODO
                }
                break;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(ACCOUNT_KEY, mChosenAccountName);
        outState.putString(TOKEN_KEY, mToken);
    }

    private void tryAuthenticate() {
        if (isFinishing()) {
            return;
        }

        mToken = null;
        setProgressBarIndeterminateVisibility(true);
        AsyncTask<Void, Void, Boolean> task = new AsyncTask<Void, Void, Boolean>() {
            @Override
            protected Boolean doInBackground(Void... params) {
                try {
                    // Retrieve a token for the given account and scope. It will
                    // always return either
                    // a non-empty String or throw an exception.
                    mToken =
                            GoogleAuthUtil.getToken(MainActivity.this, mChosenAccountName, "oauth2:"
                                    + Scopes.PLUS_PROFILE + " " + YouTubeScopes.YOUTUBE + " "
                                    + YouTubeScopes.YOUTUBE_UPLOAD);
                } catch (GooglePlayServicesAvailabilityException playEx) {
                    GooglePlayServicesUtil.getErrorDialog(playEx.getConnectionStatusCode(),
                            MainActivity.this, REQUEST_GMS_ERROR_DIALOG).show();
                } catch (UserRecoverableAuthException userAuthEx) {
                    // Start the user recoverable action using the intent
                    // returned by
                    // getIntent()
                    startActivityForResult(userAuthEx.getIntent(), REQUEST_AUTHENTICATE);
                    return false;
                } catch (IOException transientEx) {
                    // TODO: backoff
                    Log.e(this.getClass().getSimpleName(), transientEx.getMessage());
                } catch (GoogleAuthException authEx) {
                    Log.e(this.getClass().getSimpleName(), authEx.getMessage());
                }
                return true;
            }

            @Override
            protected void onPostExecute(Boolean hideProgressBar) {
                invalidateOptionsMenu();

                if (hideProgressBar) {
                    setProgressBarIndeterminateVisibility(false);
                }

                if (mToken != null) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            saveAccount();
                        }
                    });
                }
                loadData();
            }
        };
        task.execute((Void) null);
    }

    private void loadProfile() {
        new AsyncTask<Void, Void, Person>() {
            @Override
            protected Person doInBackground(Void... voids) {
                GoogleCredential credential = new GoogleCredential();
                credential.setAccessToken(mToken);

                HttpTransport httpTransport = new NetHttpTransport();
                JsonFactory jsonFactory = new JacksonFactory();

                Plus plus =
                        new Plus.Builder(httpTransport, jsonFactory, credential).setApplicationName(
                                Constants.APP_NAME).build();

                try {
                    return plus.people().get("me").execute();

                } catch (final GoogleJsonResponseException e) {
                    if (401 == e.getDetails().getCode()) {
                        Log.e(this.getClass().getSimpleName(), e.getMessage());
                        GoogleAuthUtil.invalidateToken(MainActivity.this, mToken);
                        mHandler.postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                tryAuthenticate();
                            }
                        }, mCurrentBackoff * 1000);

                        mCurrentBackoff *= 2;
                        if (mCurrentBackoff == 0) {
                            mCurrentBackoff = 1;
                        }
                    }

                } catch (final IOException e) {
                    Log.e(this.getClass().getSimpleName(), e.getMessage());
                }
                return null;
            }

            @Override
            protected void onPostExecute(Person me) {
                mUploadsListFragment.setProfileInfo(me);
            }

        }.execute((Void) null);
    }

    private void loadUploadedVideos() {
        if (mToken == null) {
            return;
        }

        setProgressBarIndeterminateVisibility(true);
        new AsyncTask<Void, Void, List<VideoData>>() {
            @Override
            protected List<VideoData> doInBackground(Void... voids) {
                GoogleCredential credential = new GoogleCredential();
                credential.setAccessToken(mToken);

                HttpTransport httpTransport = new NetHttpTransport();
                JsonFactory jsonFactory = new JacksonFactory();

                YouTube yt =
                        new YouTube.Builder(httpTransport, jsonFactory, credential).setApplicationName(
                                Constants.APP_NAME).build();

                try {
                    ChannelListResponse clr = yt.channels().list("contentDetails").setMine(true).execute();
                    String uploadsPlaylistId =
                            clr.getItems().get(0).getContentDetails().getRelatedPlaylists().getUploads();

                    List<VideoData> videos = new ArrayList<VideoData>();
                    PlaylistItemListResponse pilr =
                            yt.playlistItems().list("id,contentDetails").setPlaylistId(uploadsPlaylistId)
                                    .setMaxResults(20l).execute();
                    List<String> videoIds = new ArrayList<String>();
                    for (PlaylistItem item : pilr.getItems()) {
                        videoIds.add(item.getContentDetails().getVideoId());
                    }

                    VideoListResponse vlr =
                            yt.videos().list(TextUtils.join(",", videoIds), "id,snippet,status").execute();

                    for (Video video : vlr.getItems()) {
                        if ("public".equals(video.getStatus().getPrivacyStatus())) {
                            VideoData videoData = new VideoData();
                            videoData.setVideo(video);
                            videos.add(videoData);
                        }
                    }

                    Collections.sort(videos, new Comparator<VideoData>() {
                        @Override
                        public int compare(VideoData videoData, VideoData videoData2) {
                            return videoData.getTitle().compareTo(videoData2.getTitle());
                        }
                    });

                    mCurrentBackoff = 0;
                    return videos;

                } catch (final GoogleJsonResponseException e) {
                    if (401 == e.getDetails().getCode()) {
                        Log.e(this.getClass().getSimpleName(), e.getMessage());
                        GoogleAuthUtil.invalidateToken(MainActivity.this, mToken);
                        mHandler.postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                tryAuthenticate();
                            }
                        }, mCurrentBackoff * 1000);

                        mCurrentBackoff *= 2;
                        if (mCurrentBackoff == 0) {
                            mCurrentBackoff = 1;
                        }
                    }

                } catch (final IOException e) {
                    Log.e(this.getClass().getSimpleName(), e.getMessage());
                }
                return null;
            }

            @Override
            protected void onPostExecute(List<VideoData> videos) {
                setProgressBarIndeterminateVisibility(false);

                if (videos == null) {
                    return;
                }

                mUploadsListFragment.setVideos(videos);
            }

        }.execute((Void) null);
    }

    @Override
    public void onBackPressed() {
        // if (mDirectFragment.popPlayerFromBackStack()) {
        // super.onBackPressed();
        // }
    }

    @Override
    public ImageFetcher onGetImageFetcher() {
        ensureFetcher();
        return mImageFetcher;
    }

    @Override
    public void onVideoSelected(VideoData video) {
        mVideoData = video;
        mButton.setEnabled(true);
        mDirectFragment.panToVideo(video);
    }

    public void pickFile(View view) {
        Intent intent = new Intent(Intent.ACTION_PICK); // TODO
        // ACTION_GET_CONTENT
        intent.setType("video/*");
        startActivityForResult(intent, RESULT_PICK_IMAGE_CROP);
        mButton.setEnabled(true);
    }

    public void recordVideo(View view) {
        Intent intent = new Intent(MediaStore.ACTION_VIDEO_CAPTURE); // TODO
        // ACTION_GET_CONTENT
        startActivityForResult(intent, RESULT_VIDEO_CAP);
        mButton.setEnabled(true);
    }

    public void uploadVideo(View view) {
        if (mToken == null) {
            return;
        }
        // if an upload video is selected.
        if (mVideoData != null) {
            mDirectFragment.directLite(mVideoData, mToken);
            mButton.setEnabled(false);
            return;
        }
        // if a video is picked or recorded.
        if (mFileURI != null) {
            Intent uploadIntent = new Intent(this, UploadService.class);
            uploadIntent.setData(mFileURI);
            uploadIntent.putExtra(ACCOUNT_KEY, mChosenAccountName);
            uploadIntent.putExtra(TOKEN_KEY, mToken);
            startService(uploadIntent);
            mButton.setEnabled(false);
        }
    }

    private class InvalidateTokenReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equals(INVALIDATE_TOKEN_INTENT)) {
                Log.d(InvalidateTokenReceiver.class.getName(), "Invalidating token");
                GoogleAuthUtil.invalidateToken(MainActivity.this, mToken);
                mHandler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        tryAuthenticate();
                    }
                }, mCurrentBackoff * 1000);

                mCurrentBackoff *= 2;
                if (mCurrentBackoff == 0) {
                    mCurrentBackoff = 1;
                }
            }
        }
    }
}
