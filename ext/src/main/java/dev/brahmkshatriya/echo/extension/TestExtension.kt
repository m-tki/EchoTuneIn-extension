package dev.brahmkshatriya.echo.extension

import dev.brahmkshatriya.echo.common.clients.ExtensionClient
import dev.brahmkshatriya.echo.common.clients.HomeFeedClient
import dev.brahmkshatriya.echo.common.clients.RadioClient
import dev.brahmkshatriya.echo.common.clients.SearchFeedClient
import dev.brahmkshatriya.echo.common.clients.TrackClient
import dev.brahmkshatriya.echo.common.helpers.ClientException
import dev.brahmkshatriya.echo.common.helpers.ContinuationCallback.Companion.await
import dev.brahmkshatriya.echo.common.helpers.PagedData
import dev.brahmkshatriya.echo.common.models.Album
import dev.brahmkshatriya.echo.common.models.Artist
import dev.brahmkshatriya.echo.common.models.EchoMediaItem
import dev.brahmkshatriya.echo.common.models.EchoMediaItem.Companion.toMediaItem
import dev.brahmkshatriya.echo.common.models.ImageHolder.Companion.toImageHolder
import dev.brahmkshatriya.echo.common.models.Playlist
import dev.brahmkshatriya.echo.common.models.QuickSearchItem
import dev.brahmkshatriya.echo.common.models.Radio
import dev.brahmkshatriya.echo.common.models.Shelf
import dev.brahmkshatriya.echo.common.models.Streamable
import dev.brahmkshatriya.echo.common.models.Streamable.Media.Companion.toServerMedia
import dev.brahmkshatriya.echo.common.models.Streamable.Source.Companion.toSource
import dev.brahmkshatriya.echo.common.models.Tab
import dev.brahmkshatriya.echo.common.models.Track
import dev.brahmkshatriya.echo.common.models.User
import dev.brahmkshatriya.echo.common.settings.Setting
import dev.brahmkshatriya.echo.common.settings.Settings
import okhttp3.OkHttpClient
import okhttp3.Request

class TestExtension : ExtensionClient, HomeFeedClient, TrackClient, RadioClient, SearchFeedClient {
    override suspend fun onExtensionSelected() {}

    override val settingItems: List<Setting> = emptyList()
    private val apiLink = "https://opml.radiotime.com/"
    private val client = OkHttpClient()
    private lateinit var setting: Settings
    override fun setSettings(settings: Settings) {
        setting = settings
    }

    private val typeRegex = Regex("type=\"([^\"]+)\" (.*)")
    private val audioRegex =
        Regex("text=\"(.+)\" URL=\"(.+)\" bitrate=.+ subtext=\"(.+)\" genre_id=.+ image=\"(.+)\" ")
    private val linkRegex = Regex("text=\"(.+)\" URL=\"(.+)\" ")

    private fun String.toShelf(): List<Shelf> = typeRegex.findAll(this).mapNotNull { result ->
        val (type, content) = result.destructured
        when (type) {
            "audio" -> audioRegex.find(content)?.let {
                val (text, link, subtext, image) = it.destructured
                Track(
                    id = link,
                    title = text,
                    subtitle = subtext,
                    description = subtext,
                    cover = image.toImageHolder(),
                    streamables = listOf(Streamable.server(link, 0))
                ).toMediaItem().toShelf()
            }

            "link" -> linkRegex.find(content)?.let {
                val (text, link) = it.destructured
                Shelf.Category(
                    title = text,
                    items = PagedData.Single {
                        val request = Request.Builder().url(link).build()
                        val response = client.newCall(request).await()
                        val apiResponse = response.body.string()
                        apiResponse.toShelf()
                    }
                )
            }

            else -> throw IllegalArgumentException("Unknown type $type")
        }
    }.toList()

    private suspend fun get(url: String): String {
        val request = Request.Builder().url(url).build()
        val response = client.newCall(request).await()
        return response.body.string()
    }

    override fun getHomeFeed(tab: Tab?): PagedData<Shelf> {
        return PagedData.Single {
            val apiResponse = get(tab!!.id)
            apiResponse.toShelf()
        }
    }

    override suspend fun getHomeTabs(): List<Tab> {
        val apiResponse = get(apiLink)
        val tabs = linkRegex.findAll(apiResponse).map { result ->
            val (text, link) = result.destructured
            Tab(title = text, id = link)
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
        val response = get(streamable.id).split("\n")
        return Streamable.Media.Server(
            response.map { it.toSource() },
            false
        )
    }

    override suspend fun loadTrack(track: Track) = track
    override fun loadTracks(radio: Radio) = PagedData.empty<Track>()
    override suspend fun radio(track: Track, context: EchoMediaItem?) = Radio("", "Bruh")
    override suspend fun radio(album: Album) = throw ClientException.NotSupported("Album radio")
    override suspend fun radio(artist: Artist) = throw ClientException.NotSupported("Artist radio")
    override suspend fun radio(user: User) = throw ClientException.NotSupported("User radio")
    override suspend fun radio(playlist: Playlist) =
        throw ClientException.NotSupported("Playlist radio")

    override suspend fun deleteQuickSearch(item: QuickSearchItem) {}
    override suspend fun quickSearch(query: String): List<QuickSearchItem> {
        return emptyList()
    }

    override fun searchFeed(query: String, tab: Tab?): PagedData<Shelf> {
        val api = "${apiLink}Search.ashx?query=$query"
        return PagedData.Single {
            val apiResponse = get(api)
            apiResponse.toShelf()
        }
    }

    override suspend fun searchTabs(query: String) = emptyList<Tab>()
}