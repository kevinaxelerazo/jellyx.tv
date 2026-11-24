package dev.jdtech.jellyfin.presentation.film

import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.tv.material3.Border
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import dev.jdtech.jellyfin.core.R as CoreR
import dev.jdtech.jellyfin.film.presentation.search.DEFAULT_STREAMING_PLATFORM_SHORTCUTS
import dev.jdtech.jellyfin.film.presentation.search.SearchAction
import dev.jdtech.jellyfin.film.presentation.search.SearchViewModel
import dev.jdtech.jellyfin.models.JellyxMovie
import dev.jdtech.jellyfin.models.JellyxShow
import dev.jdtech.jellyfin.models.toNativeRatings
import dev.jdtech.jellyfin.presentation.theme.spacings
import dev.jdtech.jellyfin.ui.components.Direction
import dev.jdtech.jellyfin.ui.components.ItemCard
import dev.jdtech.jellyfin.utils.TvVoiceIntentParser
import java.util.UUID
import kotlinx.coroutines.delay

@Composable
fun SearchScreen(
    navigateToMovie: (itemId: UUID) -> Unit,
    navigateToShow: (itemId: UUID) -> Unit,
    isLoading: (Boolean) -> Unit,
    prefilledQuery: String? = null,
    prefilledIsPlatform: Boolean = true,
    prefilledQueryNonce: Long = 0L,
    onPrefilledQueryConsumed: () -> Unit = {},
    viewModel: SearchViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    var query by rememberSaveable { mutableStateOf("") }
    var isPlatformFilterLocked by rememberSaveable { mutableStateOf(false) }
    val resultsGridFocusRequester = remember { FocusRequester() }
    val searchFieldFocusRequester = remember { FocusRequester() }
    var focusResultsOnLoad by remember { mutableStateOf(false) }
    var showVoiceFallbackHint by remember { mutableStateOf(false) }

    LaunchedEffect(state.loading) { isLoading(state.loading) }

    val context = LocalContext.current
    val voiceSearchLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                val spokenQuery =
                    result.data
                        ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                        ?.firstOrNull()
                        ?.let(TvVoiceIntentParser::cleanVoiceQuery)
                if (!spokenQuery.isNullOrBlank()) {
                    isPlatformFilterLocked = false
                    query = spokenQuery
                    focusResultsOnLoad = true
                }
            }
        }

    LaunchedEffect(prefilledQuery, prefilledQueryNonce) {
        if (prefilledQueryNonce > 0L || !prefilledQuery.isNullOrBlank()) {
            val trimmed = prefilledQuery?.trim().orEmpty()
            val matchedPlatform =
                if (trimmed.isNotBlank()) {
                    DEFAULT_STREAMING_PLATFORM_SHORTCUTS.firstOrNull {
                        it.searchQuery.equals(trimmed, ignoreCase = true) ||
                            it.id.equals(trimmed, ignoreCase = true)
                    }
                } else {
                    null
                }
            if (prefilledIsPlatform && matchedPlatform != null) {
                query = matchedPlatform.searchQuery
                isPlatformFilterLocked = true
                focusResultsOnLoad = true
            } else {
                val cleaned = TvVoiceIntentParser.cleanVoiceQuery(prefilledQuery.orEmpty())
                isPlatformFilterLocked = false
                query = cleaned
                if (cleaned.isNotBlank()) {
                    focusResultsOnLoad = true
                    viewModel.onAction(SearchAction.Search(cleaned))
                } else {
                    runCatching { searchFieldFocusRequester.requestFocus() }
                }
            }
            onPrefilledQueryConsumed()
        }
    }

    LaunchedEffect(state.items, state.loading, focusResultsOnLoad) {
        if (focusResultsOnLoad && !state.loading && state.items.isNotEmpty()) {
            focusResultsOnLoad = false
            runCatching { resultsGridFocusRequester.requestFocus() }
        }
    }

    LaunchedEffect(query) {
        val trimmed = query.trim()
        val matchedPlatform =
            DEFAULT_STREAMING_PLATFORM_SHORTCUTS.firstOrNull {
                it.searchQuery.equals(trimmed, ignoreCase = true) ||
                    it.id.equals(trimmed, ignoreCase = true)
            }
        if (matchedPlatform != null) {
            viewModel.onAction(SearchAction.SelectPlatform(matchedPlatform.id))
        } else {
            if (query.isNotBlank()) {
                delay(minOf(50L + (query.length * 50L), 300L))
            }
            viewModel.onAction(SearchAction.Search(query))
        }
    }

    Column(
        modifier =
            Modifier.fillMaxSize()
                .padding(horizontal = MaterialTheme.spacings.large)
                .padding(top = MaterialTheme.spacings.default),
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacings.medium),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacings.small),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = { if (!isPlatformFilterLocked) query = it },
                enabled = !isPlatformFilterLocked,
                readOnly = isPlatformFilterLocked,
                singleLine = true,
                leadingIcon = {
                    Icon(
                        painter = painterResource(CoreR.drawable.ic_search),
                        contentDescription = null,
                    )
                },
                placeholder = { Text(text = stringResource(CoreR.string.search_hint)) },
                colors =
                    OutlinedTextFieldDefaults.colors(
                        focusedTextColor = MaterialTheme.colorScheme.onBackground,
                        unfocusedTextColor = MaterialTheme.colorScheme.onBackground,
                        disabledTextColor = MaterialTheme.colorScheme.onBackground,
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.35f),
                        disabledBorderColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.25f),
                    ),
                modifier =
                    Modifier.weight(1f)
                        .focusRequester(searchFieldFocusRequester)
                        .focusProperties { canFocus = !isPlatformFilterLocked }
                        .onPreviewKeyEvent { keyEvent ->
                            if (
                                keyEvent.type == KeyEventType.KeyDown &&
                                    keyEvent.nativeKeyEvent.keyCode ==
                                        android.view.KeyEvent.KEYCODE_DPAD_DOWN &&
                                    state.items.isNotEmpty()
                            ) {
                                runCatching { resultsGridFocusRequester.requestFocus() }
                                true
                            } else {
                                false
                            }
                        },
            )

            Button(
                onClick = {
                    isPlatformFilterLocked = false
                    showVoiceFallbackHint = false
                    val intent =
                        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                            putExtra(
                                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM,
                            )
                            putExtra(RecognizerIntent.EXTRA_PROMPT, "Buscar por voz en JellyX")
                        }
                    val canResolve = context.packageManager.resolveActivity(intent, 0) != null
                    if (canResolve) {
                        runCatching { voiceSearchLauncher.launch(intent) }
                            .onFailure {
                                showVoiceFallbackHint = true
                                runCatching { searchFieldFocusRequester.requestFocus() }
                                Toast.makeText(
                                    context,
                                    "Usa el botón de micrófono de tu control remoto con el buscador enfocado",
                                    Toast.LENGTH_LONG,
                                ).show()
                            }
                    } else {
                        showVoiceFallbackHint = true
                        runCatching { searchFieldFocusRequester.requestFocus() }
                        Toast.makeText(
                            context,
                            "Usa el botón de micrófono de tu control remoto con el buscador enfocado",
                            Toast.LENGTH_LONG,
                        ).show()
                    }
                },
                colors =
                    ButtonDefaults.colors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        focusedContainerColor = MaterialTheme.colorScheme.primary,
                        focusedContentColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                border =
                    ButtonDefaults.border(
                        focusedBorder =
                            Border(BorderStroke(3.dp, MaterialTheme.colorScheme.onBackground))
                    ),
            ) {
                Icon(
                    painter = painterResource(CoreR.drawable.ic_mic),
                    contentDescription = "Buscar por voz",
                    modifier = Modifier.size(20.dp),
                )
            }
        }

        if (showVoiceFallbackHint) {
            Text(
                text =
                    "Fire TV / TV Box sin Google Voice: mantén presionado el botón de micrófono (Alexa) de tu control remoto mientras el campo de búsqueda está enfocado.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacings.small),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (isPlatformFilterLocked) {
                item(key = "unlock_search") {
                    Button(
                        onClick = {
                            isPlatformFilterLocked = false
                            query = ""
                        },
                        colors =
                            ButtonDefaults.colors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                focusedContainerColor = MaterialTheme.colorScheme.primary,
                                focusedContentColor = MaterialTheme.colorScheme.onPrimary,
                            ),
                        border =
                            ButtonDefaults.border(
                                focusedBorder =
                                    Border(
                                        BorderStroke(3.dp, MaterialTheme.colorScheme.onBackground)
                                    )
                            ),
                    ) {
                        Icon(
                            painter = painterResource(CoreR.drawable.ic_search),
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(text = "Búsqueda libre")
                    }
                }
            }
            items(DEFAULT_STREAMING_PLATFORM_SHORTCUTS, key = { it.id }) { platform ->
                val isSelected =
                    state.activePlatformId == platform.id ||
                        query.equals(platform.searchQuery, ignoreCase = true)
                Button(
                    onClick = {
                        if (query.equals(platform.searchQuery, ignoreCase = true)) {
                            viewModel.onAction(SearchAction.SelectPlatform(platform.id))
                        } else {
                            query = platform.searchQuery
                        }
                        isPlatformFilterLocked = true
                        focusResultsOnLoad = true
                    },
                    colors =
                        if (isSelected) {
                            ButtonDefaults.colors(
                                containerColor = Color(platform.brandColorHex),
                                contentColor = Color.White,
                                focusedContainerColor = Color(platform.brandColorHex),
                                focusedContentColor = Color.White,
                            )
                        } else {
                            ButtonDefaults.colors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                focusedContainerColor = MaterialTheme.colorScheme.primary,
                                focusedContentColor = MaterialTheme.colorScheme.onPrimary,
                            )
                        },
                    border =
                        ButtonDefaults.border(
                            focusedBorder =
                                Border(BorderStroke(3.dp, MaterialTheme.colorScheme.onBackground))
                        ),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier =
                                Modifier.size(22.dp)
                                    .clip(RoundedCornerShape(5.dp))
                                    .background(Color(platform.brandColorHex)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = platform.shortBadge,
                                color = Color.White,
                                fontSize = if (platform.shortBadge.length > 2) 8.sp else 10.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = platform.label)
                    }
                }
            }
        }

        val activePlatform =
            DEFAULT_STREAMING_PLATFORM_SHORTCUTS.firstOrNull { it.id == state.activePlatformId }
        if (activePlatform != null && !state.loading) {
            Text(
                text = "${activePlatform.label} · ${state.items.size} títulos",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.75f),
            )
        }

        if (query.isNotBlank() && !state.loading && state.items.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = stringResource(CoreR.string.no_search_results),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 140.dp),
                horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacings.default),
                verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacings.default),
                contentPadding = PaddingValues(bottom = MaterialTheme.spacings.large),
                modifier = Modifier.fillMaxSize().focusRequester(resultsGridFocusRequester),
            ) {
                items(state.items, key = { it.id }) { item ->
                    ItemCard(
                        item = item,
                        direction = Direction.VERTICAL,
                        ratings = state.ratingsByItemId[item.id] ?: item.toNativeRatings(),
                        showRatingsOverlay = true,
                        onClick = {
                            when (item) {
                                is JellyxMovie -> navigateToMovie(item.id)
                                is JellyxShow -> navigateToShow(item.id)
                                else -> Unit
                            }
                        },
                    )
                }
            }
        }
    }
}

