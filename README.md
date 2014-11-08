YouTube Direct Lite for Android
===========

The code is a reference implementation for an Android OS application that captures video, uploads it to YouTube, and submits the video to a [YouTube Direct Lite](http://code.google.com/p/youtube-direct-lite/) instance.

For more information, you can read the [Youtube API blog post](http://apiblog.youtube.com/2013/08/heres-my-playlist-so-submit-video-maybe.html).

This application utilizes [YouTube Data API v3](https://developers.google.com/youtube/v3/) , [YouTube Android Player API](https://developers.google.com/youtube/android/player/), [YouTube Resumable Uploads](https://developers.google.com/youtube/v3/guides/using_resumable_upload_protocol?hl=en), [Google Play Services](https://developer.android.com/google/play-services/index.html) and [Plus API](https://developers.google.com/+/mobile/android/Google).

To use this application,

1. In your [Google Developers Console](https://console.developers.google.com),
 1. Enable the YouTube Data API v3 and Google+ API.
 1. Create a client ID for Android, using your SHA1 and package name.
1. [Register your Android app](https://developers.google.com/youtube/android/player/register)
1. Plug in your Playlist Id into Constants.java and Android API Key into Auth.java

![alt tag](https://ytd-android.googlecode.com/files/YTDL.png)

![alt tag](https://ytd-android.googlecode.com/files/YTDL-review.png)

![alt tag](https://ytd-android.googlecode.com/files/YTDL-upload.png)

![alt tag](https://ytd-android.googlecode.com/files/YTDL-watch.png)
