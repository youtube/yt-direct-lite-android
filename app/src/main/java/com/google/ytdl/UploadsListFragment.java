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

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Fragment;
import android.content.Context;
import android.content.IntentSender;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.android.volley.toolbox.ImageLoader;
import com.android.volley.toolbox.NetworkImageView;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.GoogleApiClient.ConnectionCallbacks;
import com.google.android.gms.common.api.GoogleApiClient.OnConnectionFailedListener;
import com.google.android.gms.plus.Plus;
import com.google.android.gms.plus.PlusOneButton;
import com.google.android.gms.plus.model.people.Person;
import com.google.ytdl.util.VideoData;

import java.util.List;

/**
 * @author Ibrahim Ulukaya <ulukaya@google.com>
 *         <p/>
 *         Left side fragment showing user's uploaded YouTube videos.
 */
public class UploadsListFragment extends Fragment implements ConnectionCallbacks,
        OnConnectionFailedListener {

    private static final String TAG = UploadsListFragment.class.getName();
    private static Context mContext;
    private Callbacks mCallbacks;
    private GoogleApiClient mGoogleApiClient;
    private GridView mGridView;
    private ImageLoader mImageLoader;

    public UploadsListFragment() {
    }

    @SuppressLint("ValidFragment")
    public UploadsListFragment(Context context) {
        mContext = context;
    }


    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mGoogleApiClient = new GoogleApiClient.Builder(mContext)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .addApi(Plus.API)
                .addScope(Plus.SCOPE_PLUS_PROFILE)
                .build();

    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View listView = inflater.inflate(R.layout.list_fragment, container, false);
        mGridView = (GridView) listView.findViewById(R.id.grid_view);
        TextView emptyView = (TextView) listView.findViewById(android.R.id.empty);
        mGridView.setEmptyView(emptyView);
        return listView;
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        setProfileInfo();
    }

    public void setVideos(List<VideoData> videos) {
        if (!isAdded()) {
            return;
        }

        mGridView.setAdapter(new UploadedVideoAdapter(videos));
    }

    public void setProfileInfo() {
        //not sure if mGoogleapiClient.isConnect is appropriate...
        if (!mGoogleApiClient.isConnected() || Plus.PeopleApi.getCurrentPerson(mGoogleApiClient) == null) {
            ((ImageView) getView().findViewById(R.id.avatar))
                    .setImageDrawable(null);
            ((TextView) getView().findViewById(R.id.display_name))
                    .setText(R.string.not_signed_in);
        } else {
            Person currentPerson = Plus.PeopleApi.getCurrentPerson(mGoogleApiClient);
            if (currentPerson.hasImage()) {
                // Set the URL of the image that should be loaded into this view, and
                // specify the ImageLoader that will be used to make the request.
                ((NetworkImageView) getView().findViewById(R.id.avatar)).setImageUrl(currentPerson.getImage().getUrl(), mImageLoader);
            }
            if (currentPerson.hasDisplayName()) {
                ((TextView) getView().findViewById(R.id.display_name))
                        .setText(currentPerson.getDisplayName());
            }
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        mGoogleApiClient.connect();
    }

    @Override
    public void onPause() {
        super.onPause();
        mGoogleApiClient.disconnect();
    }

    @Override
    public void onConnected(Bundle bundle) {
        if (mGridView.getAdapter() != null) {
            ((UploadedVideoAdapter) mGridView.getAdapter()).notifyDataSetChanged();
        }

        setProfileInfo();
        mCallbacks.onConnected(Plus.AccountApi.getAccountName(mGoogleApiClient));
    }

    @Override
    public void onConnectionSuspended(int i) {

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

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        if (!(activity instanceof Callbacks)) {
            throw new ClassCastException("Activity must implement callbacks.");
        }

        mCallbacks = (Callbacks) activity;
        mImageLoader = mCallbacks.onGetImageLoader();
    }

    @Override
    public void onDetach() {
        super.onDetach();
        mCallbacks = null;
        mImageLoader = null;
    }

    public interface Callbacks {
        public ImageLoader onGetImageLoader();

        public void onVideoSelected(VideoData video);

        public void onConnected(String connectedAccountName);
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
            ((NetworkImageView) convertView.findViewById(R.id.thumbnail)).setImageUrl(video.getThumbUri(), mImageLoader);
            if (mGoogleApiClient.isConnected()) {
                ((PlusOneButton) convertView.findViewById(R.id.plus_button))
                        .initialize(video.getWatchUri(), null);
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
}
