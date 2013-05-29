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

import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.client.googleapis.media.MediaHttpUploader;
import com.google.api.client.googleapis.media.MediaHttpUploaderProgressListener;
import com.google.api.client.http.InputStreamContent;
import com.google.api.services.youtube.YouTube;
import com.google.api.services.youtube.model.Video;
import com.google.api.services.youtube.model.VideoSnippet;
import com.google.api.services.youtube.model.VideoStatus;
import com.google.ytdl.util.Upload;

import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.support.v4.app.NotificationCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;


/**
 * @author Ibrahim Ulukaya <ulukaya@google.com>
 *         <p/>
 *         YouTube Resumable Upload controller class.
 */
public class ResumableUpload {
    private static int NOTIFICATION_ID = 1001;
    /*
     * Global instance of the format used for the video being uploaded (MIME type).
     */
    private static String VIDEO_FILE_FORMAT = "video/*";

    /**
     * Uploads user selected video in the project folder to the user's YouTube account using OAuth2
     * for authentication.
     *
     * @param args command line args (not used).
     */

    public static String upload(YouTube youtube, final InputStream fileInputStream,
                                final long fileSize, Context context) {
        final NotificationManager mNotifyManager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        final NotificationCompat.Builder mBuilder = new NotificationCompat.Builder(context);
        mBuilder.setContentTitle(context.getString(R.string.youtube_upload))
                .setContentText(context.getString(R.string.youtube_direct_lite_upload_started))
                .setSmallIcon(R.drawable.icon);
        String videoId = null;
        try {
            // Add extra information to the video before uploading.
            Video videoObjectDefiningMetadata = new Video();

      /*
       * Set the video to public, so it is available to everyone (what most people want). This is
       * actually the default, but I wanted you to see what it looked like in case you need to set
       * it to "unlisted" or "private" via API.
       */
            VideoStatus status = new VideoStatus();
            status.setPrivacyStatus("public");
            videoObjectDefiningMetadata.setStatus(status);

            // We set a majority of the metadata with the VideoSnippet object.
            VideoSnippet snippet = new VideoSnippet();

      /*
       * The Calendar instance is used to create a unique name and description for test purposes, so
       * you can see multiple files being uploaded. You will want to remove this from your project
       * and use your own standard names.
       */
            Calendar cal = Calendar.getInstance();
            snippet.setTitle("Test Upload via Java on " + cal.getTime());
            snippet.setDescription("Video uploaded via YouTube Data API V3 using the Java library "
                    + "on " + cal.getTime());

            // Set your keywords.
            List<String> tags = new ArrayList<String>();
            tags.add(Constants.DEFAULT_KEYWORD);
            tags.add(Upload.generateKeywordFromPlaylistId(Constants.UPLOAD_PLAYLIST));
            snippet.setTags(tags);

            // Set completed snippet to the video object.
            videoObjectDefiningMetadata.setSnippet(snippet);

            InputStreamContent mediaContent =
                    new InputStreamContent(VIDEO_FILE_FORMAT, new BufferedInputStream(fileInputStream));
            mediaContent.setLength(fileSize);

      /*
       * The upload command includes: 1. Information we want returned after file is successfully
       * uploaded. 2. Metadata we want associated with the uploaded video. 3. Video file itself.
       */
            YouTube.Videos.Insert videoInsert =
                    youtube.videos().insert("snippet,statistics,status", videoObjectDefiningMetadata,
                            mediaContent);

            // Set the upload type and add event listener.
            MediaHttpUploader uploader = videoInsert.getMediaHttpUploader();

      /*
       * Sets whether direct media upload is enabled or disabled. True = whole media content is
       * uploaded in a single request. False (default) = resumable media upload protocol to upload
       * in data chunks.
       */
            uploader.setDirectUploadEnabled(false);

            MediaHttpUploaderProgressListener progressListener = new MediaHttpUploaderProgressListener() {
                public void progressChanged(MediaHttpUploader uploader) throws IOException {
                    switch (uploader.getUploadState()) {
                        case INITIATION_STARTED:
                            mBuilder.setContentText("Initiation Started").setProgress((int) fileSize,
                                    (int) uploader.getNumBytesUploaded(), false);
                            mNotifyManager.notify(NOTIFICATION_ID, mBuilder.build());
                            break;
                        case INITIATION_COMPLETE:
                            mBuilder.setContentText("Initiation Completed").setProgress((int) fileSize,
                                    (int) uploader.getNumBytesUploaded(), false);
                            mNotifyManager.notify(NOTIFICATION_ID, mBuilder.build());
                            break;
                        case MEDIA_IN_PROGRESS:
                            mBuilder
                                    .setContentTitle("YouTube Upload " + (int) (uploader.getProgress() * 100) + "%")
                                    .setContentText("Direct Lite upload in progress")
                                    .setProgress((int) fileSize, (int) uploader.getNumBytesUploaded(), false);
                            mNotifyManager.notify(NOTIFICATION_ID, mBuilder.build());
                            break;
                        case MEDIA_COMPLETE:
                            mBuilder.setContentTitle("YouTube Upload Completed")
                                    .setContentText("Upload complete")
                                            // Removes the progress bar
                                    .setProgress(0, 0, false);
                            mNotifyManager.notify(NOTIFICATION_ID, mBuilder.build());
                        case NOT_STARTED:
                            Log.d(this.getClass().getSimpleName(), "Upload Not Started!");
                            break;
                    }
                }
            };
            uploader.setProgressListener(progressListener);

            // Execute upload.
            Video returnedVideo = videoInsert.execute();
            videoId = returnedVideo.getId();

        } catch (final GoogleJsonResponseException e) {
            if (401 == e.getDetails().getCode()) {
                Log.e(ResumableUpload.class.getSimpleName(), e.getMessage());
                LocalBroadcastManager manager = LocalBroadcastManager.getInstance(context);
                manager.sendBroadcast(new Intent(MainActivity.INVALIDATE_TOKEN_INTENT));
            }
        } catch (IOException e) {
            Log.e("IOException", e.getMessage());
        } catch (Throwable t) {
            Log.e("Throwable", t.getMessage());
        }
        return videoId;
    }
}
