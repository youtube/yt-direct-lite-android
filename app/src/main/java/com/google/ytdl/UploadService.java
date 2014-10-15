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

import android.app.IntentService;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.provider.MediaStore;
import android.util.Log;

import com.google.api.client.extensions.android.http.AndroidHttp;
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.util.ExponentialBackOff;
import com.google.api.services.youtube.YouTube;
import com.google.common.collect.Lists;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;

/**
 * @author Ibrahim Ulukaya <ulukaya@google.com>
 *         <p/>
 *         Intent service to handle uploads.
 */
public class UploadService extends IntentService {

    /**
     * defines how long we'll wait for a video to finish processing
     */
    private static final int PROCESSING_TIMEOUT_SEC = 60 * 20; // 20 minutes

    /**
     * controls how often to poll for video processing status
     */
    private static final int PROCESSING_POLL_INTERVAL_SEC = 60;
    /**
     * how long to wait before re-trying the upload
     */
    private static final int UPLOAD_REATTEMPT_DELAY_SEC = 60;
    /**
     * max number of retry attempts
     */
    private static final int MAX_RETRY = 3;
    private static final String TAG = "UploadService";
    /**
     * processing start time
     */
    private static long mStartTime;
    final HttpTransport transport = AndroidHttp.newCompatibleTransport();
    final JsonFactory jsonFactory = new GsonFactory();
    GoogleAccountCredential credential;
    /**
     * tracks the number of upload attempts
     */
    private int mUploadAttemptCount;

    public UploadService() {
        super("YTUploadService");
    }

    private static void zzz(int duration) throws InterruptedException {
        Log.d(TAG, String.format("Sleeping for [%d] ms ...", duration));
        Thread.sleep(duration);
        Log.d(TAG, String.format("Sleeping for [%d] ms ... done", duration));
    }

    private static boolean timeoutExpired(long startTime, int timeoutSeconds) {
        long currTime = System.currentTimeMillis();
        long elapsed = currTime - startTime;
        if (elapsed >= timeoutSeconds * 1000) {
            return true;
        } else {
            return false;
        }
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        Uri fileUri = intent.getData();
        String chosenAccountName = intent.getStringExtra(MainActivity.ACCOUNT_KEY);

        credential =
                GoogleAccountCredential.usingOAuth2(getApplicationContext(), Lists.newArrayList(Auth.SCOPES));
        credential.setSelectedAccountName(chosenAccountName);
        credential.setBackOff(new ExponentialBackOff());

        String appName = getResources().getString(R.string.app_name);
        final YouTube youtube =
                new YouTube.Builder(transport, jsonFactory, credential).setApplicationName(
                        appName).build();


        try {
            tryUploadAndShowSelectableNotification(fileUri, youtube);
        } catch (InterruptedException e) {
            // ignore
        }
    }

    private void tryUploadAndShowSelectableNotification(final Uri fileUri, final YouTube youtube) throws InterruptedException {
        while (true) {
            Log.i(TAG, String.format("Uploading [%s] to YouTube", fileUri.toString()));
            String videoId = tryUpload(fileUri, youtube);
            if (videoId != null) {
                Log.i(TAG, String.format("Uploaded video with ID: %s", videoId));
                tryShowSelectableNotification(videoId, youtube);
                return;
            } else {
                Log.e(TAG, String.format("Failed to upload %s", fileUri.toString()));
                if (mUploadAttemptCount++ < MAX_RETRY) {
                    Log.i(TAG, String.format("Will retry to upload the video ([%d] out of [%d] reattempts)",
                            mUploadAttemptCount, MAX_RETRY));
                    zzz(UPLOAD_REATTEMPT_DELAY_SEC * 1000);
                } else {
                    Log.e(TAG, String.format("Giving up on trying to upload %s after %d attempts",
                            fileUri.toString(), mUploadAttemptCount));
                    return;
                }
            }
        }
    }

    private void tryShowSelectableNotification(final String videoId, final YouTube youtube)
            throws InterruptedException {
        mStartTime = System.currentTimeMillis();
        boolean processed = false;
        while (!processed) {
            processed = ResumableUpload.checkIfProcessed(videoId, youtube);
            if (!processed) {
                // wait a while
                Log.d(TAG, String.format("Video [%s] is not processed yet, will retry after [%d] seconds",
                        videoId, PROCESSING_POLL_INTERVAL_SEC));
                if (!timeoutExpired(mStartTime, PROCESSING_TIMEOUT_SEC)) {
                    zzz(PROCESSING_POLL_INTERVAL_SEC * 1000);
                } else {
                    Log.d(TAG, String.format("Bailing out polling for processing status after [%d] seconds",
                            PROCESSING_TIMEOUT_SEC));
                    return;
                }
            } else {
                ResumableUpload.showSelectableNotification(videoId, getApplicationContext());
                return;
            }
        }
    }

    private String tryUpload(Uri mFileUri, YouTube youtube) {
        long fileSize;
        InputStream fileInputStream = null;
        String videoId = null;
        try {
            fileSize = getContentResolver().openFileDescriptor(mFileUri, "r").getStatSize();
            fileInputStream = getContentResolver().openInputStream(mFileUri);
            String[] proj = {MediaStore.Images.Media.DATA};
            Cursor cursor = getContentResolver().query(mFileUri, proj, null, null, null);
            int column_index = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA);
            cursor.moveToFirst();

            videoId = ResumableUpload.upload(youtube, fileInputStream, fileSize, mFileUri, cursor.getString(column_index), getApplicationContext());


        } catch (FileNotFoundException e) {
            Log.e(getApplicationContext().toString(), e.getMessage());
        } finally {
            try {
                fileInputStream.close();
            } catch (IOException e) {
                // ignore
            }
        }
        return videoId;
    }

}
