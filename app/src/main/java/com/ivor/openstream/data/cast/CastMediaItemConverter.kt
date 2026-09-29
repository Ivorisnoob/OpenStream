package com.ivor.openstream.data.cast

import android.net.Uri
import android.os.Bundle
import androidx.media3.cast.MediaItemConverter
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.google.android.gms.cast.MediaInfo
import com.google.android.gms.cast.MediaQueueItem
import com.google.android.gms.cast.MediaTrack
import com.google.android.gms.common.images.WebImage
import org.json.JSONArray
import org.json.JSONObject
import com.google.android.gms.cast.MediaMetadata as CastMetadata

/**
 * Turns the app's media items into what the Default Media Receiver loads, and back.
 *
 * Unlike Media3's default converter this sends subtitle configurations as WebVTT text tracks (the
 * one marked [C.SELECTION_FLAG_DEFAULT] starts active) and keeps the item's extras, which carry what
 * is playing, so a session the app rejoins after a restart still knows its title and episode.
 */
class CastMediaItemConverter : MediaItemConverter {

    override fun toMediaQueueItem(mediaItem: MediaItem): MediaQueueItem {
        val configuration = requireNotNull(mediaItem.localConfiguration) { "Cast items need a URI" }
        val metadata = mediaItem.mediaMetadata
        val isEpisode = metadata.extras?.getBoolean(EXTRA_IS_EPISODE) == true

        val castMetadata = CastMetadata(if (isEpisode) CastMetadata.MEDIA_TYPE_TV_SHOW else CastMetadata.MEDIA_TYPE_MOVIE)
        metadata.title?.let { castMetadata.putString(CastMetadata.KEY_TITLE, it.toString()) }
        metadata.subtitle?.let { castMetadata.putString(CastMetadata.KEY_SUBTITLE, it.toString()) }
        metadata.albumTitle?.let { castMetadata.putString(CastMetadata.KEY_SERIES_TITLE, it.toString()) }
        metadata.artworkUri?.let { castMetadata.addImage(WebImage(it)) }

        val tracks = configuration.subtitleConfigurations.mapIndexed { index, subtitle ->
            MediaTrack.Builder(index + 1L, MediaTrack.TYPE_TEXT)
                .setSubtype(MediaTrack.SUBTYPE_SUBTITLES)
                .setContentId(subtitle.uri.toString())
                .setContentType("text/vtt")
                .setName(subtitle.label)
                .setLanguage(subtitle.language ?: "und")
                .build()
        }
        val activeTrackIds = configuration.subtitleConfigurations
            .withIndex()
            .filter { (_, subtitle) -> subtitle.selectionFlags and C.SELECTION_FLAG_DEFAULT != 0 }
            .map { (index, _) -> index + 1L }
            .take(1)
            .toLongArray()

        val customData = JSONObject()
            .put(KEY_MEDIA_ID, mediaItem.mediaId)
            .put(KEY_URI, configuration.uri.toString())
            .put(KEY_MIME, configuration.mimeType)
            .put(KEY_TITLE, metadata.title?.toString())
            .put(KEY_SUBTITLE, metadata.subtitle?.toString())
            .put(KEY_ALBUM, metadata.albumTitle?.toString())
            .put(KEY_ARTWORK, metadata.artworkUri?.toString())
            .put(KEY_EXTRAS, metadata.extras?.let(::bundleToJson))
            .put(KEY_TRACKS, JSONArray().apply {
                configuration.subtitleConfigurations.forEach { subtitle ->
                    put(
                        JSONObject()
                            .put("uri", subtitle.uri.toString())
                            .put("label", subtitle.label)
                            .put("language", subtitle.language)
                            .put("id", subtitle.id)
                    )
                }
            })

        val info = MediaInfo.Builder(configuration.uri.toString())
            .setContentUrl(configuration.uri.toString())
            .setStreamType(MediaInfo.STREAM_TYPE_BUFFERED)
            .setContentType(configuration.mimeType ?: "video/mp4")
            .setMetadata(castMetadata)
            .setMediaTracks(tracks)
            .setCustomData(customData)
            .apply {
                metadata.extras?.getString(EXTRA_HLS_VIDEO_FORMAT)?.let { setHlsVideoSegmentFormat(it) }
            }
            .build()
        return MediaQueueItem.Builder(info)
            .setActiveTrackIds(activeTrackIds)
            .setCustomData(customData)
            .build()
    }

    override fun toMediaItem(mediaQueueItem: MediaQueueItem): MediaItem {
        val info = mediaQueueItem.media
        val data = info?.customData ?: mediaQueueItem.customData ?: JSONObject()
        val uri = data.optStringOrNull(KEY_URI) ?: info?.contentUrl ?: info?.contentId ?: ""
        val extras = data.optJSONObject(KEY_EXTRAS)?.let(::jsonToBundle)
        val metadata = MediaMetadata.Builder()
            .setTitle(data.optStringOrNull(KEY_TITLE) ?: info?.metadata?.getString(CastMetadata.KEY_TITLE))
            .setSubtitle(data.optStringOrNull(KEY_SUBTITLE))
            .setAlbumTitle(data.optStringOrNull(KEY_ALBUM))
            .setArtworkUri(data.optStringOrNull(KEY_ARTWORK)?.let(Uri::parse))
            .setExtras(extras)
            .build()
        val tracks = data.optJSONArray(KEY_TRACKS)
        val subtitles = (0 until (tracks?.length() ?: 0)).mapNotNull { index ->
            val track = tracks?.optJSONObject(index) ?: return@mapNotNull null
            val trackUri = track.optStringOrNull("uri") ?: return@mapNotNull null
            MediaItem.SubtitleConfiguration.Builder(Uri.parse(trackUri))
                .setMimeType("text/vtt")
                .setLabel(track.optStringOrNull("label"))
                .setLanguage(track.optStringOrNull("language"))
                .setId(track.optStringOrNull("id"))
                .build()
        }
        return MediaItem.Builder()
            .setMediaId(data.optStringOrNull(KEY_MEDIA_ID) ?: MediaItem.DEFAULT_MEDIA_ID)
            .setUri(uri)
            .setMimeType(data.optStringOrNull(KEY_MIME) ?: info?.contentType)
            .setMediaMetadata(metadata)
            .setSubtitleConfigurations(subtitles)
            .build()
    }

    private fun bundleToJson(bundle: Bundle): JSONObject = JSONObject().apply {
        bundle.keySet().forEach { key ->
            when (val value = @Suppress("DEPRECATION") bundle.get(key)) {
                is String, is Int, is Long, is Boolean, is Double -> put(key, value)
            }
        }
    }

    private fun jsonToBundle(json: JSONObject): Bundle = Bundle().apply {
        json.keys().forEach { key ->
            when (val value = json.get(key)) {
                is String -> putString(key, value)
                is Int -> putInt(key, value)
                is Long -> putLong(key, value)
                is Boolean -> putBoolean(key, value)
                is Double -> putDouble(key, value)
            }
        }
    }

    private fun JSONObject.optStringOrNull(key: String): String? =
        if (isNull(key)) null else optString(key).takeIf { it.isNotEmpty() }

    companion object {
        /** Extras key: true for a TV episode (the receiver shows the series title). */
        const val EXTRA_IS_EPISODE = "openstream.is_episode"
        /** Extras key: `fmp4` or `mpeg2_ts` for HLS streams, when known. */
        const val EXTRA_HLS_VIDEO_FORMAT = "openstream.hls_video_format"

        private const val KEY_MEDIA_ID = "mediaId"
        private const val KEY_URI = "uri"
        private const val KEY_MIME = "mimeType"
        private const val KEY_TITLE = "title"
        private const val KEY_SUBTITLE = "subtitle"
        private const val KEY_ALBUM = "album"
        private const val KEY_ARTWORK = "artwork"
        private const val KEY_EXTRAS = "extras"
        private const val KEY_TRACKS = "tracks"
    }
}
