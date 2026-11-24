# Tv Ch

Android internet TV and radio player project.

## Added in this update
- Country catalog search using public IPTV-org country playlists.
- Topic filters for NEWS, SPORTS and DOCUMENTARY using IPTV-org category playlists.
- Radio station lookup through the public Radio-Browser directory (up to three results per selected country when available).
- Channel-name search against IPTV-org channel and stream indexes.
- Catalog results are added to the in-app channel list and retained locally.
- TXT export for saved bookmarks and catalog entries.
- Recording quality selector; for HLS master playlists, the recorder selects the available rendition closest to the chosen height (Auto selects the highest listed rendition).
- HLS recording saves stream segments only, not the Android screen or surrounding app interface.

## Important limitations
- Search results depend on network access and the public directories being reachable. The requested 15 TV + 3 radio entries are targets, not guaranteed counts; only returned entries are shown.
- IPTV-org channel-name search only returns channels for which a playable URL exists in its stream index.
- Website-page playback remains dependent on the website's own embedded player. Recording website/WebView playback is not implemented; recording is for compatible clear HLS streams in the direct player.
- DRM/encrypted HLS is not bypassed. Some HLS formats (for example encrypted or fragmented variants) may not be recordable by this basic segment recorder.
- TXT export currently writes bookmarks and catalog entries. M3U import remains available through OPEN M3U.

## Build
- JDK 17
- Android Gradle Plugin 8.7.3
- Kotlin 2.0.21
- Compile SDK 35
- Run `gradle assembleDebug` or use the included GitHub Actions workflow.

The workflow triggers on pushes to all branches and supports manual dispatch.

## Radio low-consumption mode
- Use the RADIO tab for audio-only streams. Video tracks are disabled and the player requests at most 48 kbps audio and mono audio when the stream offers compatible alternatives.
- For the largest real data saving, use a radio provider's low-bitrate audio URL (ideally 32–64 kbps). A fixed-bitrate source cannot be re-encoded by the app; the player-side cap only selects among variants offered by the stream.
- This build pauses playback when the activity goes to the background. Background radio playback requires a separate MediaSessionService implementation and is not claimed by this change.


### Per-minute data usage meter
A compact meter below the player reports the measured received-data delta for each 60-second interval in KB and MB. It uses Android per-UID TrafficStats, so the value includes all received traffic by Tv Ch (including WebView/catalog traffic), not only the current stream. Some devices may report the counter as unsupported.

## Website screen recording
In WEB TV mode, press RECORD after starting playback on a website. Android displays its
system screen-capture consent dialog; approve it to record the visible screen to the app-specific external `Recordings` directory.
Press STOP RECORDING to finish. This records the visible screen, not a source stream, so
DRM/secure surfaces may appear black or block capture. Optional microphone audio is subject
to Android permission and device restrictions; it is not guaranteed to capture internal site audio.
Direct HLS recording remains available in LIVE mode for supported clear `.m3u8` streams.
