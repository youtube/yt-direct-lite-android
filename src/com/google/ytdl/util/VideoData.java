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

package com.google.ytdl.util;

import com.google.api.services.youtube.model.Video;
import com.google.api.services.youtube.model.VideoSnippet;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Ibrahim Ulukaya <ulukaya@google.com>
 *         <p/>
 *         Helper class to handle YouTube videos.
 */
public class VideoData {
    private Video mVideo;

    public void setVideo(Video video) {
        mVideo = video;
    }

    public Video getVideo() {
        return mVideo;
    }

    public String getYouTubeId() {
        return mVideo.getId();
    }

    public String getTitle() {
        return mVideo.getSnippet().getTitle();
    }

    public VideoSnippet addTag(String tag) {
        VideoSnippet mSnippet = mVideo.getSnippet();
        List<String> mTags = mSnippet.getTags();
        if (mTags == null) {
            mTags = new ArrayList<String>(2);
        }
        mTags.add(tag);
        return mSnippet;
    }

    public String getThumbUri() {
        return mVideo.getSnippet().getThumbnails().getDefault().getUrl();
    }

    public String getWatchUri() {
        return "http://www.youtube.com/watch?v=" + getYouTubeId();
    }
}
