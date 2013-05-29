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

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesClient;
import com.google.android.gms.plus.PlusClient;
import com.google.android.gms.plus.PlusOneButton;
import com.google.api.services.plus.model.Person;
import com.google.ytdl.util.ImageFetcher;
import com.google.ytdl.util.ImageWorker;
import com.google.ytdl.util.VideoData;

import android.app.Activity;
import android.app.ListFragment;
import android.content.IntentSender;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;

/**
 * @author Ibrahim Ulukaya <ulukaya@google.com>
 *         <p/>
 *         Left side fragment showing user's uploaded YouTube videos.
 */
public class UploadsListFragment extends ListFragment implements
        GooglePlayServicesClient.ConnectionCallbacks,
        GooglePlayServicesClient.OnConnectionFailedListener {

    private Callbacks mCallbacks;
    private ImageWorker mImageFetcher;
    private PlusClient mPlusClient;

    private static final String TAG = UploadsListFragment.class.getName();

    public UploadsListFragment() {
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mPlusClient = new PlusClient.Builder(getActivity(), this, this).build();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.list_fragment, container, false);
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        setProfileInfo(null);
    }

    public void setVideos(List<VideoData> videos) {
        if (!isAdded()) {
            return;
        }

        setListAdapter(new UploadedVideoAdapter(videos));
    }

    public void setProfileInfo(Person person) {
        if (person == null) {
            ((ImageView) getView().findViewById(R.id.avatar))
                    .setImageDrawable(null);
            ((TextView) getView().findViewById(R.id.display_name))
                    .setText(R.string.not_signed_in);
        } else {
            mImageFetcher.loadImage(person.getImage().getUrl(),
                    ((ImageView) getView().findViewById(R.id.avatar)));
            ((TextView) getView().findViewById(R.id.display_name))
                    .setText(person.getDisplayName());
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        mPlusClient.connect();
    }

    @Override
    public void onStop() {
        super.onStop();
        mPlusClient.disconnect();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (mPlusClient.isConnected() && getListAdapter() != null) {
            ((UploadedVideoAdapter) getListAdapter()).notifyDataSetChanged();
        }
    }

    @Override
    public void onConnected(Bundle bundle) {
        if (getListAdapter() != null) {
            ((UploadedVideoAdapter) getListAdapter()).notifyDataSetChanged();
        }
    }

    @Override
    public void onDisconnected() {
    }

    @Override
    public void onConnectionFailed(ConnectionResult connectionResult) {
        if (connectionResult.hasResolution()) {
            Toast.makeText(getActivity(),
                    R.string.connection_to_google_play_failed, Toast.LENGTH_SHORT)
                    .show();

            Log.e(TAG,
                    String.format(
                            "Connection to Play Services Failed, error: %d, reason: %s",
                            connectionResult.getErrorCode(),
                            connectionResult.toString()));
            try {
                connectionResult.startResolutionForResult(getActivity(), 0);
            } catch (IntentSender.SendIntentException e) {
                Log.e(TAG, e.toString(), e);
            }
        }
    }

    private class UploadedVideoAdapter extends BaseAdapter {
        private List<VideoData> mVideos;

        private UploadedVideoAdapter(List<VideoData> videos) {
            mVideos = videos;
        }

        @Override
        public int getCount() {
            return mVideos.size();
        }

        @Override
        public Object getItem(int i) {
            return mVideos.get(i);
        }

        @Override
        public long getItemId(int i) {
            return mVideos.get(i).getYouTubeId().hashCode();
        }

        @Override
        public View getView(final int position, View convertView,
                            ViewGroup container) {
            if (convertView == null) {
                convertView = LayoutInflater.from(getActivity()).inflate(
                        R.layout.list_item, container, false);
            }

            VideoData video = mVideos.get(position);
            ((TextView) convertView.findViewById(android.R.id.text1))
                    .setText(video.getTitle());
            mImageFetcher.loadImage(video.getThumbUri(),
                    (ImageView) convertView.findViewById(R.id.thumbnail));
            if (mPlusClient.isConnected()) {
                ((PlusOneButton) convertView.findViewById(R.id.plus_button))
                        .initialize(mPlusClient, video.getWatchUri(), null);
            }
            convertView.findViewById(R.id.main_target).setOnClickListener(
                    new View.OnClickListener() {
                        @Override
                        public void onClick(View view) {
                            mCallbacks.onVideoSelected(mVideos.get(position));
                        }
                    });
            return convertView;
        }
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        if (!(activity instanceof Callbacks)) {
            throw new ClassCastException("Activity must implement callbacks.");
        }

        mCallbacks = (Callbacks) activity;
        mImageFetcher = mCallbacks.onGetImageFetcher();
    }

    @Override
    public void onDetach() {
        super.onDetach();
        mCallbacks = null;
        mImageFetcher = null;
    }

    public interface Callbacks {
        public ImageFetcher onGetImageFetcher();

        public void onVideoSelected(VideoData video);
    }
}
