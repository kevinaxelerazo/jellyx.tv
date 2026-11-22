package dev.jdtech.jellyfin.models

import dev.jdtech.jellyfin.repository.JellyfinRepository
import java.util.UUID
import org.jellyfin.sdk.model.api.BaseItemDto

data class JellyxChannel(
    override val id: UUID,
    override val name: String,
    override val originalTitle: String?,
    override val overview: String,
    val channelNumber: String?,
    val currentProgram: JellyxProgram?,
    val nextProgram: JellyxProgram? = null,
    val group: String = "📡 General",
    override val played: Boolean = false,
    override val favorite: Boolean = false,
    override val canPlay: Boolean = true,
    override val canDownload: Boolean = false,
    override val sources: List<JellyxSource> = emptyList(),
    override val runtimeTicks: Long = 0L,
    override val playbackPositionTicks: Long = 0L,
    override val unplayedItemCount: Int? = null,
    override val images: JellyxImages = JellyxImages(),
    override val chapters: List<JellyxChapter> = emptyList(),
) : JellyxItem {
    val displayName: String
        get() = cleanChannelTitle(name)
}

suspend fun BaseItemDto.toJellyxChannel(
    jellyfinRepository: JellyfinRepository,
    nextProgram: JellyxProgram? = null,
): JellyxChannel {
    val rawName = name.orEmpty()
    val inferredGroup = inferChannelGroup(rawName, tags, genres)
    return JellyxChannel(
        id = id,
        name = rawName,
        originalTitle = originalTitle,
        overview = overview.orEmpty(),
        channelNumber = channelNumber,
        currentProgram = currentProgram?.toJellyxProgram(),
        nextProgram = nextProgram,
        group = inferredGroup,
        favorite = userData?.isFavorite == true,
        canPlay = true,
        canDownload = false,
        sources = mediaSources?.map { it.toJellyxSource(jellyfinRepository, id) }.orEmpty(),
        images = toJellyxImages(jellyfinRepository),
    )
}

fun cleanChannelTitle(rawName: String): String {
    if (rawName.isBlank()) return "Canal TV"
    return rawName
        .replace(Regex("\\s*\\[(?:Geo-blocked|Not 24/7|720p|1080p|576p|480p)\\]", RegexOption.IGNORE_CASE), "")
        .replace(Regex("\\s*\\((?:720p|1080p|576p|480p)\\)", RegexOption.IGNORE_CASE), "")
        .replace(Regex("\\s{2,}"), " ")
        .trim()
}

fun inferChannelGroup(
    name: String,
    tags: List<String>? = null,
    genres: List<String>? = null,
): String {
    val explicitTag =
        tags?.firstOrNull { it.isNotBlank() }?.trim()
            ?: genres?.firstOrNull { it.isNotBlank() }?.trim()
    if (!explicitTag.isNullOrBlank()) {
        return explicitTag
    }

    val lower = name.lowercase()
    return when {
        listOf(
            "espn",
            "fox sports",
            "tyc",
            "win sports",
            "goltv",
            "gol tv",
            "claro sports",
            "dsports",
            "directv sports",
            "bein",
            "azteca deportes",
            "tudn",
            "sky sports",
            "dazn",
            "deporte",
            "sport",
            "futbol",
            "fútbol",
            "nba",
            "ufc",
            "nfl",
            "mlb",
            "real madrid",
            "barça",
            "teledeporte",
            "aym sports",
            "px sports",
        ).any { lower.contains(it) } -> "⚽ Deportes"

        listOf(
            "teleamazonas",
            "ecuavisa",
            "rts",
            "oromar",
            "canal uno",
            "austral",
            "kch",
            "tc televisión",
            "tc television",
            "gamavision",
            "gamavisión",
            "ecuador tv",
            "telerama",
            "ecuador",
            "majestad",
            " Vito ",
            "unsion",
            "etv telerama",
        ).any { lower.contains(it) } -> "🇪🇨 Ecuador"

        listOf(
            "la 1",
            "la 2",
            "antena 3",
            "cuatro",
            "telecinco",
            "la sexta",
            "lasexta",
            "rtve",
            "tve ",
            "atreseries",
            "neox",
            "nova",
            "mega esp",
            "trece",
            "24h",
            "clan ",
            "boing",
            "movistar",
            "españa",
        ).any { lower.contains(it) } -> "🇪🇸 España"

        listOf(
            "hbo",
            "cinemax",
            "star channel",
            "fx",
            "cinecanal",
            "warner",
            "sony",
            "axn",
            "universal",
            "tnt",
            "space",
            "amc",
            "golden",
            "de pelicula",
            "de película",
            "paramount",
            "syfy",
            "tcm",
            "multipremier",
            "europa europa",
            "film",
            "cine",
            "movie",
            "hollywood",
            "lifetime",
            "a&e",
            "comedy central",
            "distrito comedia",
            "universal",
        ).any { lower.contains(it) } -> "🎬 Cine y Series"

        listOf(
            "cnn",
            "euronews",
            "dw ",
            "deutsche welle",
            "rt ",
            "france 24",
            "telesur",
            "ntn24",
            "todo noticias",
            "tn ",
            "milenio",
            "noticias",
            "news",
            "bloomberg",
            "bbc",
            "al jazeera",
            "c5n",
            "adn 40",
            "foro tv",
            "forotv",
            "cablenoticias",
        ).any { lower.contains(it) } -> "📰 Noticias"

        listOf(
            "cartoon",
            "disney",
            "nickelodeon",
            "nick ",
            "discovery kids",
            "babytv",
            "baby tv",
            "boomerang",
            "tooncast",
            "cartoonito",
            "senpai",
            "anime",
            "bitme",
            "kids",
            "infantil",
            "junior",
            "pakapaka",
            "plim plim",
            "dreaworks",
            "dreamworks",
        ).any { lower.contains(it) } -> "👶 Infantil y Anime"

        listOf(
            "discovery",
            "history",
            "nat geo",
            "national geographic",
            "animal planet",
            "h&h",
            "tlc",
            "investigación",
            "investigation",
            "id ",
            "gourmet",
            "food network",
            "docu",
            "ciencia",
            "science",
            "turbo",
            "theater",
            "world",
            "sun channel",
            "hola tv",
            "mas chic",
            "más chic",
        ).any { lower.contains(it) } -> "🌍 Cultura y Docs"

        listOf(
            "mtv",
            "htv",
            "telehit",
            "bandamax",
            "quiero musica",
            "quiero música",
            "music",
            "música",
            "concert",
            "vh1",
        ).any { lower.contains(it) } -> "🎵 Música"

        listOf(
            "caracol",
            "rcn",
            "las estrellas",
            "azteca",
            "telefe",
            "el trece",
            "eltrece",
            "latina",
            "américa tv",
            "america tv",
            "tvn",
            "chilevision",
            "chilevisión",
            "telemundo",
            "univision",
            "univisión",
            "galavision",
            "pasiones",
            "tlnovelas",
            "canal 5",
            "imagen tv",
        ).any { lower.contains(it) } -> "🌎 Latam y Variedades"

        else -> "📡 General"
    }
}
