package dev.jdtech.jellyfin.models

import java.util.Locale
import java.util.UUID

data class StreamingPlatformSpec(
    val id: String,
    val displayLabel: String,
    val queryAliases: Set<String>,
    val exactStudioNames: Set<String>,
    val studioContainsTokens: Set<String>,
    val excludedStudioTokens: Set<String> = emptySet(),
    val tagNames: Set<String>,
)

object StreamingPlatformCatalog {
    private val netflixSpec =
        StreamingPlatformSpec(
            id = "netflix",
            displayLabel = "Netflix",
            queryAliases = setOf("netflix"),
            exactStudioNames =
                setOf(
                    "Netflix",
                    "Netflix Animation",
                    "Netflix Studios",
                    "Netflix International Pictures",
                    "Netflix Kids & Family",
                    "Netflix Documentary Films",
                    "Netflix Original",
                ),
            studioContainsTokens = setOf("netflix"),
            tagNames = setOf("Netflix", "netflix"),
        )

    private val primeSpec =
        StreamingPlatformSpec(
            id = "prime",
            displayLabel = "Prime Video",
            queryAliases =
                setOf("prime", "prime video", "amazon", "amazon prime", "amazon prime video"),
            exactStudioNames =
                setOf(
                    "Prime Video",
                    "Amazon",
                    "Amazon Studios",
                    "Amazon MGM Studios",
                    "Amazon Prime Video",
                    "Amazon Content Services",
                    "Amazon Freevee",
                    "Freevee",
                    "Metro-Goldwyn-Mayer",
                    "MGM",
                    "MGM Television",
                    "MGM+",
                ),
            studioContainsTokens =
                setOf("amazon", "prime video", "freevee", "metro-goldwyn-mayer"),
            tagNames = setOf("Prime Video", "Amazon Prime Video", "Amazon", "prime video"),
        )

    private val hboSpec =
        StreamingPlatformSpec(
            id = "hbo",
            displayLabel = "HBO / Max",
            queryAliases = setOf("hbo", "max", "hbo max", "hbo / max"),
            exactStudioNames =
                setOf(
                    "HBO",
                    "Max",
                    "HBO Max",
                    "HBO Films",
                    "HBO Entertainment",
                    "HBO Documentary Films",
                    "HBO Original",
                    "HBO Originals",
                    "Cinemax",
                    "Warner Bros.",
                    "Warner Bros. Pictures",
                    "Warner Bros. Television",
                    "Warner Bros. Animation",
                    "Warner Horizon Television",
                    "Warner Bros. Discovery",
                    "New Line Cinema",
                    "DC Studios",
                    "DC Films",
                    "DC Entertainment",
                    "Adult Swim",
                    "Cartoon Network",
                    "Cartoon Network Studios",
                    "Turner Entertainment",
                    "Castle Rock Entertainment",
                ),
            // Do not add bare "max" here so names like "Mad Max" or "Miramax" never false-match.
            studioContainsTokens =
                setOf(
                    "hbo",
                    "warner bros",
                    "new line cinema",
                    "dc studios",
                    "dc films",
                    "dc entertainment",
                    "adult swim",
                    "cartoon network",
                    "cinemax",
                ),
            tagNames = setOf("HBO", "HBO Max", "Max", "hbo", "max"),
        )

    private val disneySpec =
        StreamingPlatformSpec(
            id = "disney",
            displayLabel = "Disney+",
            queryAliases = setOf("disney", "disney+", "disney plus"),
            exactStudioNames =
                setOf(
                    "Disney+",
                    "Disney",
                    "Walt Disney Pictures",
                    "Walt Disney Animation Studios",
                    "Walt Disney Productions",
                    "Walt Disney Television",
                    "Walt Disney Television Animation",
                    "Walt Disney Studios",
                    "Walt Disney Studios Motion Pictures",
                    "Disney Television Animation",
                    "Disney Channel",
                    "Disney Junior",
                    "Disney XD",
                    "Disneynature",
                    "Pixar",
                    "Pixar Animation Studios",
                    "Marvel Studios",
                    "Marvel Entertainment",
                    "Marvel Television",
                    "Marvel Animation",
                    "Lucasfilm",
                    "Lucasfilm Ltd.",
                    "Lucasfilm Animation",
                    "20th Century Studios",
                    "20th Century Fox",
                    "Twentieth Century Fox",
                    "Twentieth Century Fox Film Corporation",
                    "20th Television",
                    "Searchlight Pictures",
                    "Fox Searchlight Pictures",
                    "Blue Sky Studios",
                    "National Geographic",
                    "ABC Signature",
                    "Touchstone Pictures",
                    "Hollywood Pictures",
                    "Star+",
                    "Star Originals",
                    "FX",
                    "FX Productions",
                    "Hulu",
                ),
            studioContainsTokens =
                setOf(
                    "disney",
                    "pixar",
                    "marvel",
                    "lucasfilm",
                    "20th century",
                    "twentieth century",
                    "searchlight pictures",
                    "national geographic",
                    "blue sky studios",
                ),
            tagNames = setOf("Disney+", "Disney", "disney+", "disney", "Star+"),
        )

    private val paramountSpec =
        StreamingPlatformSpec(
            id = "paramount",
            displayLabel = "Paramount+",
            queryAliases = setOf("paramount", "paramount+", "paramount plus"),
            exactStudioNames =
                setOf(
                    "Paramount+",
                    "Paramount",
                    "Paramount Pictures",
                    "Paramount Television",
                    "Paramount Television Studios",
                    "Paramount Animation",
                    "Paramount Network",
                    "Paramount Players",
                    "Paramount Vantage",
                    "CBS",
                    "CBS Studios",
                    "CBS Television Studios",
                    "CBS Films",
                    "Showtime",
                    "Showtime Networks",
                    "Nickelodeon",
                    "Nickelodeon Movies",
                    "Nickelodeon Animation Studio",
                    "Nick Jr.",
                    "MTV",
                    "MTV Entertainment Studios",
                    "MTV Films",
                    "Comedy Central",
                    "BET",
                    "BET+",
                    "Smithsonian Channel",
                    "Republic Pictures",
                ),
            studioContainsTokens =
                setOf(
                    "paramount",
                    "showtime",
                    "nickelodeon",
                    "comedy central",
                    "cbs studios",
                    "cbs television",
                    "mtv entertainment",
                ),
            tagNames = setOf("Paramount+", "Paramount", "paramount+", "paramount"),
        )

    private val appleSpec =
        StreamingPlatformSpec(
            id = "apple",
            displayLabel = "Apple TV+",
            queryAliases = setOf("apple", "apple tv", "apple tv+", "apple tv plus"),
            exactStudioNames =
                setOf(
                    "Apple TV+",
                    "Apple TV",
                    "Apple",
                    "Apple Studios",
                    "Apple Original Films",
                    "Apple Originals",
                ),
            studioContainsTokens = setOf("apple tv", "apple studios", "apple original"),
            tagNames = setOf("Apple TV+", "Apple TV", "apple tv+"),
        )

    val allSpecs: List<StreamingPlatformSpec> =
        listOf(
            netflixSpec,
            primeSpec,
            hboSpec,
            disneySpec,
            paramountSpec,
            appleSpec,
        )

    fun findById(id: String): StreamingPlatformSpec? {
        val normalized = normalize(id)
        if (normalized.isEmpty()) return null
        return allSpecs.firstOrNull { normalize(it.id) == normalized }
    }

    fun findByQuery(query: String): StreamingPlatformSpec? {
        val normalized = normalize(query)
        if (normalized.isEmpty()) return null
        return allSpecs.firstOrNull { spec ->
            spec.queryAliases.any { alias -> normalize(alias) == normalized }
        }
    }

    fun matchesStudioName(spec: StreamingPlatformSpec, studioName: String): Boolean {
        val normalized = normalize(studioName)
        if (normalized.isEmpty()) return false
        if (spec.excludedStudioTokens.any { token -> normalized.contains(normalize(token)) }) {
            return false
        }
        if (spec.exactStudioNames.any { exact -> normalize(exact) == normalized }) {
            return true
        }
        return spec.studioContainsTokens.any { token ->
            containsWholePhrase(normalized, normalize(token))
        }
    }

    private fun containsWholePhrase(haystack: String, needle: String): Boolean {
        if (needle.isEmpty()) return false
        var startIndex = 0
        while (true) {
            val idx = haystack.indexOf(needle, startIndex)
            if (idx < 0) return false
            val leftOk = idx == 0 || !haystack[idx - 1].isLetterOrDigit()
            val endIdx = idx + needle.length
            val rightOk = endIdx == haystack.length || !haystack[endIdx].isLetterOrDigit()
            if (leftOk && rightOk) return true
            startIndex = idx + 1
        }
    }

    fun matchStudioIds(
        spec: StreamingPlatformSpec,
        serverStudios: List<Pair<UUID, String>>,
    ): List<UUID> =
        serverStudios
            .filter { (_, name) -> matchesStudioName(spec, name) }
            .map { (id, _) -> id }
            .distinct()

    private fun normalize(value: String): String =
        value.trim().lowercase(Locale.ROOT).replace(WHITESPACE_REGEX, " ")

    private val WHITESPACE_REGEX = Regex("\\s+")
}
