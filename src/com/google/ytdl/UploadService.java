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

import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson.JacksonFactory;
import com.google.api.services.youtube.YouTube;

import android.app.IntentService;
import android.content.Intent;
import android.net.Uri;
import android.util.Log;

import java.io.FileNotFoundException;
import java.io.InputStream;

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
    private String mToken;
    private long mFileSize;

    @Override
    protected void onHandleIntent(Intent intent) {
        mFileUri = intent.getData();
        mToken = intent.getStringExtra("token");
        mFileSize = intent.getLongExtra("length", 0);
        GoogleCredential credential = new GoogleCredential();
        credential.setAccessToken(mToken);

        HttpTransport httpTransport = new NetHttpTransport();
        JsonFactory jsonFactory = new JacksonFactory();

        YouTube youtube =
                new YouTube.Builder(httpTransport, jsonFactory, credential).setApplicationName(
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
