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

import com.google.api.client.extensions.android.http.AndroidHttp;
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.youtube.YouTube;
import com.google.api.services.youtube.YouTubeScopes;

import android.app.IntentService;
import android.content.Intent;
import android.net.Uri;
import android.util.Log;

import java.io.FileNotFoundException;
import java.io.InputStream;
import java.util.Collections;

/**
 * @author Ibrahim Ulukaya <ulukaya@google.com>
 *         <p/>
 *         Intent service to handle uploads.
 */
public class UploadService extends IntentService {

    public UploadService() {
        super("YTUploadService");
    }

    private Uri mFileUri;
    private String mChosenAccountName;
    private long mFileSize;
    
    GoogleAccountCredential credential;
    final HttpTransport transport = AndroidHttp.newCompatibleTransport();
    final JsonFactory jsonFactory = new GsonFactory();

    @Override
    protected void onHandleIntent(Intent intent) {
        mFileUri = intent.getData();
        mChosenAccountName = intent.getStringExtra(MainActivity.ACCOUNT_KEY);
        mFileSize = intent.getLongExtra("length", 0);

        credential =
                GoogleAccountCredential.usingOAuth2(getApplicationContext(), Collections.singleton(YouTubeScopes.YOUTUBE));
        credential.setSelectedAccountName(mChosenAccountName);

        YouTube youtube =
                new YouTube.Builder(transport, jsonFactory, credential).setApplicationName(
                        Constants.APP_NAME).build();

        InputStream fileInputStream = null;
        try {
            mFileSize = getContentResolver().openFileDescriptor(mFileUri, "r").getStatSize();
            fileInputStream = getContentResolver().openInputStream(mFileUri);
        } catch (FileNotFoundException e) {
            Log.e(getApplicationContext().toString(), e.getMessage());
        }
        ResumableUpload.upload(youtube, fileInputStream, mFileSize, getApplicationContext());
    }
}
