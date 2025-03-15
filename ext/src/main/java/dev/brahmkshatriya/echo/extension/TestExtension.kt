package dev.brahmkshatriya.echo.extension

import dev.brahmkshatriya.echo.common.clients.ExtensionClient
import dev.brahmkshatriya.echo.common.clients.HomeFeedClient
import dev.brahmkshatriya.echo.common.clients.TrackClient
import dev.brahmkshatriya.echo.common.helpers.ContinuationCallback.Companion.await
import dev.brahmkshatriya.echo.common.helpers.PagedData
import dev.brahmkshatriya.echo.common.models.EchoMediaItem.Companion.toMediaItem
import dev.brahmkshatriya.echo.common.models.ImageHolder.Companion.toImageHolder
import dev.brahmkshatriya.echo.common.models.Shelf
import dev.brahmkshatriya.echo.common.models.Streamable
import dev.brahmkshatriya.echo.common.models.Streamable.Media.Companion.toServerMedia
import dev.brahmkshatriya.echo.common.models.Tab
import dev.brahmkshatriya.echo.common.models.Track
import dev.brahmkshatriya.echo.common.settings.Setting
import dev.brahmkshatriya.echo.common.settings.Settings
import okhttp3.OkHttpClient
import okhttp3.Request

class TestExtension : ExtensionClient, HomeFeedClient, TrackClient {
    override suspend fun onExtensionSelected() {}

    override val settingItems: List<Setting> = emptyList()
    val apiLink = "https://opml.radiotime.com/"
    val client = OkHttpClient()
    private lateinit var setting: Settings
    override fun setSettings(settings: Settings) {
        setting = settings
    }

    val homeFeedRegex =
        Regex("text=\\\"(.+)\\\" URL=\\\"(.+)\\\" bitrate=.+ subtext=\\\"(.+)\\\" genre_id=.+ image=\\\"(.+)\\\" ")

    override fun getHomeFeed(tab: Tab?): PagedData<Shelf> {

        return PagedData.Single {
            val request = Request.Builder().url(tab!!.id).build()
            val response = client.newCall(request).await()
            val apiResponse = response.body.string()
            homeFeedRegex.findAll(apiResponse).map {
                val (text, link, subtext, image) = it.destructured
                Track(
                    id = link,
                    title = text,
                    subtitle = subtext,
                    description = subtext,
                    cover = image.toImageHolder(),
                    streamables = listOf(Streamable.server(link, 0))
                ).toMediaItem().toShelf()
            }.toList()
        }
    }

    val homeTabRegex = Regex("text=\\\"(.+)\\\" URL=\\\"(.+)\\\" ")
    override suspend fun getHomeTabs(): List<Tab> {
        val request = Request.Builder().url(apiLink).build()
        val response = client.newCall(request).await()
        val apiResponse = response.body.string()
        val tabs = homeTabRegex.findAll(apiResponse).map {
            val (text, link) = it.destructured
            Tab(link, text)
        }.toList()
        return tabs
    }

    override fun getShelves(track: Track): PagedData<Shelf> {
        return PagedData.empty()
    }

    override suspend fun loadStreamableMedia(
        streamable: Streamable,
        isDownload: Boolean
    ): Streamable.Media {
        return streamable.id.toServerMedia(type = Streamable.SourceType.HLS)
    }

    override suspend fun loadTrack(track: Track) = track
}