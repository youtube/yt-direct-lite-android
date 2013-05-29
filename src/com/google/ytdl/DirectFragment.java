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

import com.google.android.youtube.player.YouTubeInitializationResult;
import com.google.android.youtube.player.YouTubePlayer;
import com.google.android.youtube.player.YouTubePlayer.OnFullscreenListener;
import com.google.android.youtube.player.YouTubePlayer.PlayerStateChangeListener;
import com.google.android.youtube.player.YouTubePlayerFragment;
import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson.JacksonFactory;
import com.google.api.services.youtube.YouTube;
import com.google.ytdl.util.ImageFetcher;
import com.google.ytdl.util.Upload;
import com.google.ytdl.util.VideoData;

import android.app.Activity;
import android.app.Fragment;
import android.app.FragmentTransaction;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import java.io.IOException;


/**
 * @author Ibrahim Ulukaya <ulukaya@google.com>
 *         <p/>
 *         Main fragment showing YouTube Direct Lite upload options and having YT Android Player.
 */
public class DirectFragment extends Fragment implements
        PlayerStateChangeListener, OnFullscreenListener {

    private YouTubePlayer mYouTubePlayer;
    private boolean mIsFullScreen = false;
    private static final String YOUTUBE_FRAGMENT_TAG = "youtube";

    public DirectFragment() {
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.direct_fragment, container, false);
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
    }

    @Override
    public void onStart() {
        super.onStart();
    }

    @Override
    public void onStop() {
        super.onStop();
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        if (!(activity instanceof Callbacks)) {
            throw new ClassCastException("Activity must implement callbacks.");
        }
    }

    @Override
    public void onDetach() {
        super.onDetach();
    }

    public interface Callbacks {
        public ImageFetcher onGetImageFetcher();

        public void onVideoSelected(VideoData video);

        public void onResume();

    }

    public void directLite(final VideoData video, final String token) {
        video.addTag(Constants.DEFAULT_KEYWORD);
        video.addTag(Upload.generateKeywordFromPlaylistId(Constants.UPLOAD_PLAYLIST));

        new AsyncTask<Void, Void, Void>() {
            @Override
            protected Void doInBackground(Void... voids) {

                GoogleCredential credential = new GoogleCredential();
                credential.setAccessToken(token);

                HttpTransport httpTransport = new NetHttpTransport();
                JsonFactory jsonFactory = new JacksonFactory();

                YouTube youtube =
                        new YouTube.Builder(httpTransport, jsonFactory, credential).setApplicationName(
                                Constants.APP_NAME).build();
                try {
                    youtube.videos().update("snippet", video.getVideo()).execute();
                } catch (IOException e) {
                    Log.e(this.getClass().toString(), e.getMessage());
                }
                return null;
            }

        }.execute((Void) null);

    }

    public void panToVideo(final VideoData video) {
        popPlayerFromBackStack();
        YouTubePlayerFragment playerFragment = YouTubePlayerFragment.newInstance();
        getFragmentManager().beginTransaction()
                .replace(R.id.detail_container, playerFragment, YOUTUBE_FRAGMENT_TAG)
                .setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE).addToBackStack(null).commit();
        playerFragment.initialize(Auth.KEY, new YouTubePlayer.OnInitializedListener() {
            @Override
            public void onInitializationSuccess(YouTubePlayer.Provider provider,
                                                YouTubePlayer youTubePlayer, boolean b) {
                youTubePlayer.loadVideo(video.getYouTubeId());
                mYouTubePlayer = youTubePlayer;
                youTubePlayer.setPlayerStateChangeListener(DirectFragment.this);
                youTubePlayer.setOnFullscreenListener(DirectFragment.this);
            }

            @Override
            public void onInitializationFailure(YouTubePlayer.Provider provider,
                                                YouTubeInitializationResult result) {
                showErrorToast(result.toString());
            }
        });
    }


    public boolean popPlayerFromBackStack() {
        if (mIsFullScreen) {
            mYouTubePlayer.setFullscreen(false);
            return false;
        }
        if (getFragmentManager().findFragmentByTag(YOUTUBE_FRAGMENT_TAG) != null) {
            getFragmentManager().popBackStack();
            return false;
        }
        return true;
    }

    @Override
    public void onAdStarted() {
    }

    @Override
    public void onError(YouTubePlayer.ErrorReason errorReason) {
        showErrorToast(errorReason.toString());
    }

    private void showErrorToast(String message) {
        Toast.makeText(getActivity(), message, Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onLoaded(String arg0) {
    }

    @Override
    public void onLoading() {
    }

    @Override
    public void onVideoEnded() {
        popPlayerFromBackStack();
    }

    @Override
    public void onVideoStarted() {
    }

    @Override
    public void onFullscreen(boolean fullScreen) {
        mIsFullScreen = fullScreen;
    }
}
