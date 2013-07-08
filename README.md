ytd-android
===========

The code is a reference implementation for an Android OS application that captures video, uploads it to YouTube, and submits the video to a [YouTube Direct Lite](http://code.google.com/p/youtube-direct-lite/) instance.

Note: We are currently in the process of updating this app and porting it over. We will post when it is ready to be forked and used.

This application utilizes [YouTube Data API v3](https://developers.google.com/youtube/v3/) , [YouTube Android Player API](https://developers.google.com/youtube/android/player/), [YouTube Resumable Uploads](https://developers.google.com/youtube/v3/guides/using_resumable_upload_protocol?hl=en), [Google Play Services](https://developer.android.com/google/play-services/index.html) and [Plus API](https://developers.google.com/+/mobile/android/Google).

To use this application,

1) [Register your project first](https://developers.google.com/youtube/android/player/register) and enable YouTube Data API v3 and Google+ API in your [API Console](https://code.google.com/apis/console/).

2) Include [Google Play Services library](http://developer.android.com/google/play-services/setup.html) into your project to build this application.

5) Plug in your Playlist Id into Contants.java and Android API Key into Auth.java
