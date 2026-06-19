package cc.maao.vrchat.ui

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import cc.maao.vrchat.R
import cc.maao.vrchat.data.AppLanguage
import cc.maao.vrchat.data.GalleryContent
import cc.maao.vrchat.data.GalleryFilter
import cc.maao.vrchat.data.GalleryFlowItem
import cc.maao.vrchat.data.GalleryImage
import cc.maao.vrchat.data.GalleryRepository
import cc.maao.vrchat.data.GalleryRow
import cc.maao.vrchat.data.GallerySettings
import cc.maao.vrchat.data.GallerySettingsStore
import cc.maao.vrchat.data.SpecialEventView
import cc.maao.vrchat.data.ThemeMode
import cc.maao.vrchat.data.GallerySettingsStore.Companion.BASE_URLS
import cc.maao.vrchat.ui.theme.MarsVRChatGalleryTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.time.Duration
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

private val FriendTokenRegex = Regex("\\[\\[([a-zA-Z0-9_-]+)\\]\\]")
private val WorldTokenRegex = Regex("\\{\\{([a-zA-Z0-9_.-]+)\\}\\}")
private val EmphasisRegex = Regex("\\*([^*\\n]+)\\*")
private val BreakRegex = Regex("<br\\s*/?>", RegexOption.IGNORE_CASE)
private val TimeZoneRegex = Regex("([zZ]|[+-]\\d{2}:\\d{2})$")
private val WebBackground = Color(0xFF0A0A0B)
private val WebSurface = Color(0xFF131315)
private val WebSurfaceVariant = Color(0xFF1B1B1E)
private val WebText = Color(0xFFF3F0EA)
private val WebMuted = Color(0xFFC9C2BA)
private val WebFooterText = Color(0xFF8F8982)
private val WebMint = Color(0xFF9FD2BD)
private val WebGold = Color(0xFFF4D6AA)
private val WebViolet = Color(0xFFC7A6FF)
private val FriendLinkColor = Color(0xFF8EBEFF)
private val CardCornerRadius = 14.dp
private val MediaCornerRadius = 12.dp
private const val MaaoUrl = "https://maao.cc/"
private val MarsTitleGradient = Brush.linearGradient(
    colors = listOf(
        Color(0xFFF4D6AA),
        Color(0xFF9FD2BD),
        Color(0xFFB7C7FF),
        Color(0xFFF0B6D7),
    ),
)

@Composable
fun GalleryApp() {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val settingsStore = remember { GallerySettingsStore(context) }
    var settings by remember { mutableStateOf(settingsStore.read()) }
    val repository = remember(context, settings.baseUrl) { GalleryRepository(context, settings.baseUrl) }
    val resolvedLanguage = settings.language.resolveSystemLanguage(configuration.locales[0])
    val darkTheme = when (settings.themeMode) {
        ThemeMode.System -> isSystemInDarkTheme()
        ThemeMode.Light -> false
        ThemeMode.Dark -> true
    }

    MarsVRChatGalleryTheme(darkTheme = darkTheme, dynamicColor = true) {
        GalleryScreen(
            settings = settings,
            baseUrl = settings.baseUrl,
            resolvedLanguage = resolvedLanguage,
            onSettingsChange = { nextSettings ->
                settings = nextSettings
                settingsStore.save(nextSettings)
            },
            repository = repository,
        )
    }
}

private sealed interface GalleryLoadState {
    data object Loading : GalleryLoadState
    data class Ready(val content: GalleryContent) : GalleryLoadState
    data class Error(val message: String) : GalleryLoadState
}

private sealed interface DescriptionPart {
    data class Text(val text: String) : DescriptionPart
    data object Break : DescriptionPart
    data class Emphasis(val text: String) : DescriptionPart
    data class Friend(val id: String, val name: String) : DescriptionPart
    data class World(val id: String, val name: String) : DescriptionPart
}

private data class SocialLink(
    val label: String,
    val href: String,
)

private data class QrContact(
    val id: String,
    val label: String,
    val number: String,
    val url: String,
    val fileName: String,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GalleryScreen(
    settings: GallerySettings,
    baseUrl: String,
    resolvedLanguage: AppLanguage,
    onSettingsChange: (GallerySettings) -> Unit,
    repository: GalleryRepository,
) {
    val context = LocalContext.current
    val copy = copyFor(context, resolvedLanguage)
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var loadState by remember { mutableStateOf<GalleryLoadState>(GalleryLoadState.Loading) }
    var reloadKey by remember { mutableIntStateOf(0) }
    var showSettings by remember { mutableStateOf(false) }
    var activeFilter by remember { mutableStateOf<GalleryFilter?>(null) }
    var lightboxPhotos by remember { mutableStateOf<List<GalleryImage>>(emptyList()) }
    var lightboxIndex by remember { mutableIntStateOf(-1) }

    LaunchedEffect(reloadKey, repository) {
        loadState = GalleryLoadState.Loading
        loadState = runCatching { repository.loadGallery() }
            .fold(
                onSuccess = { GalleryLoadState.Ready(it) },
                onFailure = { GalleryLoadState.Error(it.message ?: copy.loadFailed) },
            )
    }

    LaunchedEffect(loadState) {
        val content = (loadState as? GalleryLoadState.Ready)?.content ?: return@LaunchedEffect
        val warmupUrls = buildList {
            content.rows.take(8).forEach { row ->
                add(row.photo.thumbnailUrl(baseUrl))
                row.linkedPhotos.take(2).forEach { add(it.thumbnailUrl(baseUrl)) }
            }
            content.specialEvents.take(2).forEach { event ->
                event.featuredPhotos.take(2).forEach { add(it.thumbnailUrl(baseUrl)) }
            }
        }
        GalleryImageCache.prefetch(context, warmupUrls, targetPixelSize = 900)
    }

    Scaffold(
        containerColor = WebBackground,
        contentColor = WebText,
        topBar = {
            TopAppBar(
                title = {
                    GradientTitle(
                        title = copy.title,
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 1,
                    )
                },
                actions = {
                    IconButton(onClick = { showSettings = true }) {
                        Text("⚙", style = MaterialTheme.typography.titleLarge, color = WebMuted)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = WebBackground,
                ),
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            when (val state = loadState) {
                GalleryLoadState.Loading -> LoadingScreen(copy.loading)
                is GalleryLoadState.Error -> ErrorScreen(
                    message = state.message,
                    retryLabel = copy.retry,
                    onRetry = { reloadKey += 1 },
                )

                is GalleryLoadState.Ready -> GalleryContentScreen(
                    content = state.content,
                    baseUrl = baseUrl,
                    copy = copy,
                    language = resolvedLanguage,
                    activeFilter = activeFilter,
                    onFilterChange = { activeFilter = it },
                    onOpenPhotos = { photos, index ->
                        lightboxPhotos = photos
                        lightboxIndex = index
                    },
                )
            }
        }
    }

    if (showSettings) {
        SettingsDialog(
            settings = settings,
            baseUrl = baseUrl,
            copy = copy,
            onDismiss = { showSettings = false },
            onSettingsChange = onSettingsChange,
            onClearCache = {
                repository.clearCache()
                GalleryImageCache.clear(context)
                reloadKey += 1
                scope.launch { snackbarHostState.showSnackbar(copy.cacheCleared) }
            },
        )
    }

    if (lightboxIndex >= 0 && lightboxPhotos.isNotEmpty()) {
        Lightbox(
            photos = lightboxPhotos,
            index = lightboxIndex,
            baseUrl = baseUrl,
            copy = copy,
            language = resolvedLanguage,
            content = (loadState as? GalleryLoadState.Ready)?.content,
            onIndexChange = { lightboxIndex = it },
            onDismiss = { lightboxIndex = -1 },
            onFilterChange = {
                activeFilter = it
                lightboxIndex = -1
            },
        )
    }
}

@Composable
private fun GradientTitle(
    title: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
    maxLines: Int = Int.MAX_VALUE,
) {
    Text(
        text = title,
        modifier = modifier,
        style = style.copy(
            brush = MarsTitleGradient,
            fontWeight = FontWeight.Bold,
        ),
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun LoadingScreen(label: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(label, style = MaterialTheme.typography.titleMedium, color = WebText)
    }
}

@Composable
private fun ErrorScreen(
    message: String,
    retryLabel: String,
    onRetry: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(message, color = Color(0xFFFFB4AB))
        Spacer(Modifier.height(16.dp))
        Button(onClick = onRetry) {
            Text(retryLabel)
        }
    }
}

@Composable
private fun GalleryContentScreen(
    content: GalleryContent,
    baseUrl: String,
    copy: UiCopy,
    language: AppLanguage,
    activeFilter: GalleryFilter?,
    onFilterChange: (GalleryFilter?) -> Unit,
    onOpenPhotos: (List<GalleryImage>, Int) -> Unit,
) {
    val rows = remember(content, activeFilter) {
        if (activeFilter == null) {
            content.rows
        } else {
            content.rows.filter { row ->
                (listOf(row.photo) + row.linkedPhotos).any { it.matches(activeFilter) }
            }
        }
    }
    val filteredSpecialEvents = remember(content, activeFilter) {
        if (activeFilter == null) {
            content.specialEvents
        } else {
            content.specialEvents.filter { it.matches(activeFilter) }
        }
    }
    val flowItems = remember(rows, filteredSpecialEvents) {
        buildGalleryFlowItems(rows, filteredSpecialEvents)
    }
    val galleryLightboxPhotos = remember(rows) {
        rows.flatMap { row -> listOf(row.photo) + row.linkedPhotos }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            start = 16.dp,
            top = 12.dp,
            end = 16.dp,
            bottom = 24.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Header(
                content = content,
                baseUrl = baseUrl,
                copy = copy,
                language = language,
                onFilterChange = onFilterChange,
                onOpenPhotos = onOpenPhotos,
            )
        }

        if (activeFilter != null) {
            item {
                ActiveFilterBar(
                    label = activeFilter.label(content, language),
                    copy = copy,
                    rowCount = rows.size + filteredSpecialEvents.size,
                    onClear = { onFilterChange(null) },
                )
            }
        }

        items(flowItems, key = { item ->
            when (item) {
                is GalleryFlowItem.Event -> "event-${item.event.event.id}"
                is GalleryFlowItem.Rows -> item.id
            }
        }) { item ->
            when (item) {
                is GalleryFlowItem.Event -> SpecialEventCard(
                    event = item.event,
                    content = content,
                    baseUrl = baseUrl,
                    copy = copy,
                    language = language,
                    onFilterChange = onFilterChange,
                    onOpenPhotos = onOpenPhotos,
                )

                is GalleryFlowItem.Rows -> Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    item.rows.forEach { row ->
                        GalleryRowCard(
                            row = row,
                            lightboxPhotos = galleryLightboxPhotos,
                            content = content,
                            baseUrl = baseUrl,
                            copy = copy,
                            language = language,
                            onFilterChange = onFilterChange,
                            onOpenPhotos = onOpenPhotos,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Header(
    content: GalleryContent,
    baseUrl: String,
    copy: UiCopy,
    language: AppLanguage,
    onFilterChange: (GalleryFilter?) -> Unit,
    onOpenPhotos: (List<GalleryImage>, Int) -> Unit,
) {
    val randomRow = remember(content.rows) { content.rows.randomOrNull() }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        ElevatedCard(
            shape = RoundedCornerShape(CardCornerRadius),
            colors = CardDefaults.elevatedCardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
            ),
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                IntroText(copy.intro)
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = copy.gallerySummary(content.photos.size, content.rows.size),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                    Text(
                        text = copy.daysSummary(daysSinceVrchatStart()),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
            }
        }

        randomRow?.let { row ->
            val photos = listOf(row.photo) + row.linkedPhotos
            ElevatedCard(
                shape = RoundedCornerShape(CardCornerRadius),
                colors = CardDefaults.elevatedCardColors(
                    containerColor = WebSurface,
                ),
            ) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = copy.randomMemory.uppercase(),
                        style = MaterialTheme.typography.labelLarge,
                        color = WebMint,
                        fontWeight = FontWeight.Bold,
                    )
                    CachedNetworkImage(
                        url = row.photo.thumbnailUrl(baseUrl),
                        contentDescription = row.photo.description(content, language) ?: row.photo.filename,
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(16f / 10f)
                            .clip(RoundedCornerShape(MediaCornerRadius))
                            .clickable { onOpenPhotos(photos, 0) },
                        targetPixelSize = 1_100,
                    )
                    PhotoMetaRow(
                        photo = row.photo,
                        content = content,
                        language = language,
                        onFilterChange = onFilterChange,
                    )
                    row.photo.localizedDescription(language)?.let {
                        RichDescription(
                            description = it,
                            content = content,
                            language = language,
                            onFilterChange = onFilterChange,
                        )
                    }
                    FriendTags(
                        photo = row.photo,
                        content = content,
                        copy = copy,
                        language = language,
                        onFilterChange = onFilterChange,
                    )
                    if (row.linkedPhotos.isNotEmpty()) {
                        LinkedPhotosRow(
                            photos = row.linkedPhotos,
                            parentPhoto = row.photo,
                            content = content,
                            baseUrl = baseUrl,
                            language = language,
                            onOpenPhoto = { photo -> onOpenPhotos(photos, photos.indexOf(photo)) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun IntroText(text: String) {
    val context = LocalContext.current
    val annotated = remember(text) {
        buildAnnotatedString {
            val linkText = "maao.cc"
            val linkStart = text.indexOf(linkText)
            if (linkStart < 0) {
                append(text)
                return@buildAnnotatedString
            }

            append(text.substring(0, linkStart))
            pushStringAnnotation("url", MaaoUrl)
            pushStyle(
                SpanStyle(
                    color = WebMint,
                    fontWeight = FontWeight.Bold,
                ),
            )
            append(linkText)
            pop()
            pop()
            append(text.substring(linkStart + linkText.length))
        }
    }

    ClickableText(
        text = annotated,
        style = MaterialTheme.typography.bodyMedium.copy(
            color = MaterialTheme.colorScheme.onSecondaryContainer,
        ),
        onClick = { offset ->
            annotated
                .getStringAnnotations("url", offset, offset)
                .firstOrNull()
                ?.let { annotation ->
                    runCatching {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(annotation.item)))
                    }
                }
        },
    )
}

@Composable
private fun ActiveFilterBar(
    label: String,
    copy: UiCopy,
    rowCount: Int,
    onClear: () -> Unit,
) {
    Card(
        shape = RoundedCornerShape(CardCornerRadius),
        colors = CardDefaults.cardColors(
            containerColor = WebSurface,
            contentColor = WebText,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(Modifier.weight(1f)) {
                Text("${copy.showing} $rowCount ${copy.outings}", color = WebMuted)
                Text(label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = WebText)
            }
            OutlinedButton(
                onClick = onClear,
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.primary,
                ),
            ) {
                Text(copy.clear)
            }
        }
    }
}

@Composable
private fun GalleryRowCard(
    row: GalleryRow,
    lightboxPhotos: List<GalleryImage>,
    content: GalleryContent,
    baseUrl: String,
    copy: UiCopy,
    language: AppLanguage,
    onFilterChange: (GalleryFilter?) -> Unit,
    onOpenPhotos: (List<GalleryImage>, Int) -> Unit,
) {
    val photos = listOf(row.photo) + row.linkedPhotos
    fun openGalleryPhoto(photo: GalleryImage) {
        val index = lightboxPhotos.indexOfFirst { it.id == photo.id }
        if (index >= 0) {
            onOpenPhotos(lightboxPhotos, index)
        } else {
            val localIndex = photos.indexOfFirst { it.id == photo.id }.coerceAtLeast(0)
            onOpenPhotos(photos, localIndex)
        }
    }

    ElevatedCard(
        shape = RoundedCornerShape(CardCornerRadius),
        colors = CardDefaults.elevatedCardColors(
            containerColor = WebSurface,
        ),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            CachedNetworkImage(
                url = row.photo.thumbnailUrl(baseUrl),
                contentDescription = row.photo.description(content, language) ?: row.photo.filename,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 10f)
                    .clip(RoundedCornerShape(MediaCornerRadius))
                    .clickable { openGalleryPhoto(row.photo) },
                targetPixelSize = 1_200,
            )

            PhotoMetaRow(
                photo = row.photo,
                content = content,
                language = language,
                onFilterChange = onFilterChange,
            )

            row.photo.localizedDescription(language)?.let {
                RichDescription(
                    description = it,
                    content = content,
                    language = language,
                    onFilterChange = onFilterChange,
                )
            }

            FriendTags(
                photo = row.photo,
                content = content,
                copy = copy,
                language = language,
                onFilterChange = onFilterChange,
            )

            if (row.linkedPhotos.isNotEmpty()) {
                LinkedPhotosRow(
                    photos = row.linkedPhotos,
                    parentPhoto = row.photo,
                    content = content,
                    baseUrl = baseUrl,
                    language = language,
                    onOpenPhoto = { photo -> openGalleryPhoto(photo) },
                )
            }
        }
    }
}

@Composable
private fun SpecialEventCard(
    event: SpecialEventView,
    content: GalleryContent,
    baseUrl: String,
    copy: UiCopy,
    language: AppLanguage,
    onFilterChange: (GalleryFilter?) -> Unit,
    onOpenPhotos: (List<GalleryImage>, Int) -> Unit,
) {
    ElevatedCard(
        shape = RoundedCornerShape(CardCornerRadius),
        colors = CardDefaults.elevatedCardColors(
            containerColor = WebSurface,
        ),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                text = copy.specialEvent.uppercase(),
                style = MaterialTheme.typography.labelLarge,
                color = WebMint,
                fontWeight = FontWeight.Bold,
            )
            Text(event.title(language), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = WebText)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = event.date(language),
                    style = MaterialTheme.typography.bodyMedium,
                    color = WebGold,
                    fontWeight = FontWeight.Bold,
                )
                event.event.world?.takeIf { it.isNotBlank() }?.let { worldId ->
                    WorldLink(
                        label = content.worldName(worldId, language),
                        onClick = { onFilterChange(GalleryFilter.World(worldId)) },
                    )
                }
            }
            event.localizedDescription(language)?.let {
                RichDescription(
                    description = it,
                    content = content,
                    language = language,
                    onFilterChange = onFilterChange,
                )
            }
            FriendTextRow(
                friendIds = event.event.friendIds,
                content = content,
                copy = copy,
                language = language,
                onFilterChange = onFilterChange,
            )
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(event.featuredPhotos, key = { it.id }) { photo ->
                    Box(
                        modifier = Modifier
                            .width(240.dp)
                            .clip(RoundedCornerShape(MediaCornerRadius))
                            .clickable { onOpenPhotos(event.photos, event.photos.indexOf(photo)) },
                    ) {
                        CachedNetworkImage(
                            url = photo.thumbnailUrl(baseUrl),
                            contentDescription = photo.filename,
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(4f / 3f),
                            targetPixelSize = 1_100,
                        )
                        Column(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(8.dp)
                                .background(
                                    color = WebSurface.copy(alpha = 0.82f),
                                    shape = RoundedCornerShape(MediaCornerRadius),
                                )
                                .padding(horizontal = 8.dp, vertical = 5.dp),
                            horizontalAlignment = Alignment.End,
                        ) {
                            Text(
                                text = "#${photo.id} · ${photo.formatSpecialEventDate(event, language)}",
                                style = MaterialTheme.typography.labelMedium,
                                color = WebGold,
                                fontWeight = FontWeight.Bold,
                            )
                            if (photo.world.isNotBlank() && photo.world != event.event.world) {
                                WorldLink(
                                    label = content.worldName(photo.world, language),
                                    onClick = {},
                                    enabled = false,
                                    textStyle = MaterialTheme.typography.labelSmall,
                                )
                            }
                        }
                    }
                }
            }
            if (event.linkedPhotos.isNotEmpty()) {
                LinkedPhotosRow(
                    photos = event.linkedPhotos,
                    parentPhoto = event.photos.firstOrNull(),
                    event = event,
                    content = content,
                    baseUrl = baseUrl,
                    language = language,
                    onOpenPhoto = { photo -> onOpenPhotos(event.photos, event.photos.indexOf(photo)) },
                )
            }
        }
    }
}

@Composable
private fun PhotoMetaRow(
    photo: GalleryImage,
    content: GalleryContent,
    language: AppLanguage,
    onFilterChange: (GalleryFilter?) -> Unit,
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = "#${photo.id}",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = WebText,
        )
        Text(
            text = photo.displayDate(language),
            style = MaterialTheme.typography.labelMedium,
            color = WebGold,
        )
        if (photo.world.isNotBlank()) {
            WorldLink(
                label = content.worldName(photo.world, language),
                onClick = { onFilterChange(GalleryFilter.World(photo.world)) },
            )
        }
    }
}

@Composable
private fun LinkedPhotosRow(
    photos: List<GalleryImage>,
    parentPhoto: GalleryImage?,
    content: GalleryContent,
    baseUrl: String,
    language: AppLanguage,
    onOpenPhoto: (GalleryImage) -> Unit,
    event: SpecialEventView? = null,
) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        items(photos, key = { it.id }) { photo ->
            Column(
                modifier = Modifier
                    .width(150.dp)
                    .clip(RoundedCornerShape(MediaCornerRadius))
                    .background(WebSurfaceVariant)
                    .clickable { onOpenPhoto(photo) },
            ) {
                CachedNetworkImage(
                    url = photo.thumbnailUrl(baseUrl),
                    contentDescription = photo.description(content, language) ?: photo.filename,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(4f / 3f),
                    targetPixelSize = 720,
                )
                Text(
                    text = buildString {
                        append(
                            event?.let { photo.formatSpecialEventDate(it, language) }
                                ?: parentPhoto?.let { photo.formatLinkedDate(it, language) }
                                ?: photo.displayDate(language),
                        )
                        append("  #")
                        append(photo.id)
                    },
                    modifier = Modifier.padding(8.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = WebMuted,
                )
                val comparisonWorld = event?.event?.world ?: parentPhoto?.world
                if (photo.world.isNotBlank() && photo.world != comparisonWorld) {
                    WorldLink(
                        label = content.worldName(photo.world, language),
                        onClick = {},
                        modifier = Modifier.padding(start = 8.dp, end = 8.dp, bottom = 8.dp),
                        enabled = false,
                        textStyle = MaterialTheme.typography.labelSmall,
                    )
                }
            }
        }
    }
}

@Composable
private fun RichDescription(
    description: String,
    content: GalleryContent,
    language: AppLanguage,
    onFilterChange: (GalleryFilter?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val linkStyle = SpanStyle(
        color = FriendLinkColor,
        fontWeight = FontWeight.Bold,
    )
    val worldStyle = SpanStyle(
        color = WebViolet,
        fontWeight = FontWeight.Bold,
    )
    val emphasisStyle = SpanStyle(
        fontStyle = FontStyle.Italic,
        fontWeight = FontWeight.SemiBold,
    )
    val parts = remember(description, content, language) {
        description.descriptionParts(content, language)
    }
    val annotated = buildAnnotatedString {
        parts.forEach { part ->
            when (part) {
                is DescriptionPart.Text -> append(part.text)
                DescriptionPart.Break -> append("\n")
                is DescriptionPart.Emphasis -> {
                    pushStyle(emphasisStyle)
                    append(part.text)
                    pop()
                }
                is DescriptionPart.Friend -> {
                    pushStringAnnotation("friend", part.id)
                    pushStyle(linkStyle)
                    append(part.name)
                    pop()
                    pop()
                }
                is DescriptionPart.World -> {
                    pushStringAnnotation("world", part.id)
                    pushStyle(worldStyle)
                    append(part.name)
                    pop()
                    pop()
                }
            }
        }
    }

    ClickableText(
        text = annotated,
        modifier = modifier,
        style = MaterialTheme.typography.bodyMedium.copy(color = WebText),
        onClick = { offset ->
            annotated.getStringAnnotations("friend", offset, offset).firstOrNull()?.let {
                onFilterChange(GalleryFilter.Friend(it.item))
                return@ClickableText
            }
            annotated.getStringAnnotations("world", offset, offset).firstOrNull()?.let {
                onFilterChange(GalleryFilter.World(it.item))
            }
        },
    )
}

@Composable
private fun FriendTags(
    photo: GalleryImage,
    content: GalleryContent,
    copy: UiCopy,
    language: AppLanguage,
    onFilterChange: (GalleryFilter?) -> Unit,
) {
    FriendTextRow(
        friendIds = photo.friendIds,
        content = content,
        copy = copy,
        language = language,
        onFilterChange = onFilterChange,
    )
}

@Composable
private fun FriendTextRow(
    friendIds: List<String>,
    content: GalleryContent,
    copy: UiCopy,
    language: AppLanguage,
    onFilterChange: (GalleryFilter?) -> Unit,
) {
    val cleanFriendIds = friendIds.filter { it.isNotBlank() }
    if (cleanFriendIds.isEmpty()) {
        return
    }

    val annotated = buildAnnotatedString {
        pushStyle(
            SpanStyle(
                color = WebMuted,
                fontWeight = FontWeight.Bold,
            ),
        )
        append(copy.with)
        pop()
        append("  ")
        cleanFriendIds.forEachIndexed { index, friendId ->
            if (index > 0) {
                append("  ")
            }
            pushStringAnnotation("friend", friendId)
            pushStyle(
                SpanStyle(
                    color = FriendLinkColor,
                    fontWeight = FontWeight.Bold,
                ),
            )
            append(content.friendName(friendId, language))
            pop()
            pop()
        }
    }

    ClickableText(
        text = annotated,
        style = MaterialTheme.typography.bodyMedium,
        onClick = { offset ->
            annotated.getStringAnnotations("friend", offset, offset).firstOrNull()?.let {
                onFilterChange(GalleryFilter.Friend(it.item))
            }
        },
    )
}

@Composable
private fun WorldLink(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    textStyle: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.labelMedium,
) {
    Row(
        modifier = modifier.clickable(enabled = enabled, onClick = onClick),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Filled.Place,
            contentDescription = null,
            modifier = Modifier.size(14.dp),
            tint = WebViolet,
        )
        Text(
            text = label,
            style = textStyle,
            fontWeight = FontWeight.Bold,
            color = WebViolet,
        )
    }
}

@Composable
private fun SettingsDialog(
    settings: GallerySettings,
    baseUrl: String,
    copy: UiCopy,
    onDismiss: () -> Unit,
    onSettingsChange: (GallerySettings) -> Unit,
    onClearCache: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var activeQrContact by remember { mutableStateOf<QrContact?>(null) }
    var pendingSaveContact by remember { mutableStateOf<QrContact?>(null) }
    fun saveQrToAlbum(contact: QrContact) {
        scope.launch {
            val saved = saveQrToGallery(context, contact)
            Toast.makeText(
                context,
                if (saved) copy.qrSaved else copy.qrSaveFailed,
                Toast.LENGTH_SHORT,
            ).show()
        }
    }
    val storagePermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        val contact = pendingSaveContact
        pendingSaveContact = null
        if (granted && contact != null) {
            saveQrToAlbum(contact)
        } else {
            Toast.makeText(
                context,
                copy.qrSaveFailed,
                Toast.LENGTH_SHORT,
            ).show()
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(copy.settings) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                SettingsMenuRow(
                    icon = Icons.Filled.Language,
                    label = copy.language,
                    value = copy.languageName(settings.language),
                    items = listOf(AppLanguage.System, AppLanguage.En, AppLanguage.Zh),
                    itemLabel = { copy.languageName(it) },
                    onItemSelected = { onSettingsChange(settings.copy(language = it)) },
                )
                SettingsMenuRow(
                    icon = Icons.Filled.Palette,
                    label = copy.theme,
                    value = copy.themeName(settings.themeMode),
                    items = listOf(ThemeMode.System, ThemeMode.Light, ThemeMode.Dark),
                    itemLabel = { copy.themeName(it) },
                    onItemSelected = { onSettingsChange(settings.copy(themeMode = it)) },
                )
                SettingsMenuRow(
                    icon = Icons.Filled.Link,
                    label = copy.baseUrl,
                    value = settings.baseUrl.displayBaseUrl(),
                    items = BASE_URLS,
                    itemLabel = { it.displayBaseUrl() },
                    onItemSelected = { onSettingsChange(settings.copy(baseUrl = it)) },
                )
                OutlinedButton(
                    onClick = onClearCache,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(
                        imageVector = Icons.Filled.CleaningServices,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(copy.clearCache)
                }
                HorizontalDivider()
                SettingsFooter(
                    baseUrl = baseUrl,
                    copy = copy,
                    onOpenLink = { href ->
                        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(href))) }
                            .onFailure {
                                Toast.makeText(context, copy.openFailed, Toast.LENGTH_SHORT).show()
                            }
                    },
                    onOpenQr = { activeQrContact = it },
                )
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text(copy.done)
            }
        },
    )

    activeQrContact?.let { contact ->
        QrContactDialog(
            contact = contact,
            copy = copy,
            onDismiss = { activeQrContact = null },
            onSave = {
                if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P &&
                    context.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
                ) {
                    pendingSaveContact = contact
                    storagePermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                } else {
                    saveQrToAlbum(contact)
                }
            },
        )
    }
}

@Composable
private fun SettingsFooter(
    baseUrl: String,
    copy: UiCopy,
    onOpenLink: (String) -> Unit,
    onOpenQr: (QrContact) -> Unit,
) {
    val socialLinks = remember { socialLinks() }
    val qrContacts = remember(copy, baseUrl) { qrContacts(copy, baseUrl) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            socialLinks.forEach { link ->
                OutlinedButton(onClick = { onOpenLink(link.href) }) {
                    Text(link.label)
                }
            }
            qrContacts.forEach { contact ->
                OutlinedButton(onClick = { onOpenQr(contact) }) {
                    Text(contact.label)
                }
            }
        }

        Text(
            text = copy.copyright,
            style = MaterialTheme.typography.labelSmall,
            color = WebFooterText,
        )
    }
}

@Composable
private fun QrContactDialog(
    contact: QrContact,
    copy: UiCopy,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 420.dp),
            shape = RoundedCornerShape(CardCornerRadius),
            color = WebSurface,
            tonalElevation = 6.dp,
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                CachedNetworkImage(
                    url = contact.url,
                    contentDescription = "${contact.label} QR code",
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(360.dp)
                        .clip(RoundedCornerShape(MediaCornerRadius)),
                    contentScale = ContentScale.Fit,
                )
                Text(contact.label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = WebText)
                Text(contact.number, style = MaterialTheme.typography.bodyMedium, color = WebMuted)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    OutlinedButton(onClick = onDismiss) {
                        Text(copy.close)
                    }
                    Button(onClick = onSave) {
                        Text(copy.saveQr)
                    }
                }
            }
        }
    }
}

@Composable
private fun <T> SettingsMenuRow(
    icon: ImageVector,
    label: String,
    value: String,
    items: List<T>,
    itemLabel: (T) -> String,
    onItemSelected: (T) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(label, style = MaterialTheme.typography.bodyLarge)
        }
        Box {
            OutlinedButton(onClick = { expanded = true }) {
                Text(value)
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                items.forEach { item ->
                    DropdownMenuItem(
                        text = { Text(itemLabel(item)) },
                        onClick = {
                            expanded = false
                            onItemSelected(item)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun Lightbox(
    photos: List<GalleryImage>,
    index: Int,
    baseUrl: String,
    copy: UiCopy,
    language: AppLanguage,
    content: GalleryContent?,
    onIndexChange: (Int) -> Unit,
    onDismiss: () -> Unit,
    onFilterChange: (GalleryFilter?) -> Unit,
) {
    val safeIndex = index.coerceIn(0, photos.lastIndex)
    val photo = photos[safeIndex]
    val context = LocalContext.current
    var zoom by remember(photo.id) { mutableFloatStateOf(1f) }
    var offset by remember(photo.id) { mutableStateOf(Offset.Zero) }
    var controlsVisible by remember(photo.id) { mutableStateOf(true) }
    var dragTotal by remember(photo.id) { mutableFloatStateOf(0f) }

    LaunchedEffect(photo.id, photos) {
        zoom = 1f
        offset = Offset.Zero
        controlsVisible = true
        val nearby = listOfNotNull(
            photos.getOrNull((safeIndex - 1 + photos.size) % photos.size),
            photo,
            photos.getOrNull((safeIndex + 1) % photos.size),
        ).map { it.fullImageUrl(baseUrl) }
        nearby.forEach { GalleryImageCache.ensureDiskCached(context, it) }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
            contentAlignment = Alignment.Center,
        ) {
            CachedNetworkImage(
                url = photo.fullImageUrl(baseUrl),
                contentDescription = photo.filename,
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(photo.id) {
                        detectTapGestures(
                            onTap = { controlsVisible = !controlsVisible },
                            onDoubleTap = {
                                zoom = if (zoom > 1f) 1f else 2.5f
                                offset = Offset.Zero
                                controlsVisible = zoom <= 1f
                            },
                        )
                    }
                    .pointerInput(photo.id, zoom) {
                        detectDragGestures(
                            onDragStart = {
                                dragTotal = 0f
                            },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                if (zoom <= 1.05f) {
                                    dragTotal += dragAmount.x
                                } else {
                                    offset += dragAmount
                                    controlsVisible = false
                                }
                            },
                            onDragEnd = {
                                if (zoom <= 1.05f) {
                                    when {
                                        dragTotal < -72f && photos.size > 1 -> onIndexChange((safeIndex + 1) % photos.size)
                                        dragTotal > 72f && photos.size > 1 -> onIndexChange((safeIndex - 1 + photos.size) % photos.size)
                                    }
                                }
                                dragTotal = 0f
                            },
                        )
                    }
                    .pointerInput(photo.id) {
                        detectTransformGestures { _, pan, gestureZoom, _ ->
                            val nextZoom = (zoom * gestureZoom).coerceIn(1f, 4f)
                            zoom = nextZoom
                            offset = if (nextZoom <= 1f) Offset.Zero else offset + pan
                            if (nextZoom > 1f) {
                                controlsVisible = false
                            }
                        }
                    }
                    .graphicsLayer {
                        scaleX = zoom
                        scaleY = zoom
                        translationX = offset.x
                        translationY = offset.y
                    },
                contentScale = ContentScale.Fit,
                targetPixelSize = 3_200,
                backgroundColor = Color.Black,
            )

            if (controlsVisible) {
                Row(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Button(onClick = onDismiss) {
                        Text(copy.close)
                    }
                    Surface(
                        shape = RoundedCornerShape(MediaCornerRadius),
                        color = WebBackground.copy(alpha = 0.78f),
                    ) {
                        Text(
                            text = "${safeIndex + 1} / ${photos.size}",
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = WebText,
                        )
                    }
                }

                content?.let { loadedContent ->
                    Surface(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .navigationBarsPadding(),
                        color = WebBackground.copy(alpha = 0.78f),
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            PhotoMetaRow(
                                photo = photo,
                                content = loadedContent,
                                language = language,
                                onFilterChange = onFilterChange,
                            )
                            photo.localizedDescription(language)?.let {
                                RichDescription(
                                    description = it,
                                    content = loadedContent,
                                    language = language,
                                    onFilterChange = onFilterChange,
                                )
                            }
                            FriendTags(
                                photo = photo,
                                content = loadedContent,
                                copy = copy,
                                language = language,
                                onFilterChange = onFilterChange,
                            )
                        }
                    }
                } ?: Surface(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .navigationBarsPadding(),
                    color = WebBackground.copy(alpha = 0.78f),
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text(
                            text = photo.filename,
                            style = MaterialTheme.typography.bodyMedium,
                            color = WebText,
                        )
                    }
                }
            }
        }
    }
}

private data class UiCopy(
    val title: String,
    val intro: String,
    val loading: String,
    val loadFailed: String,
    val retry: String,
    val settings: String,
    val language: String,
    val theme: String,
    val baseUrl: String,
    val clearCache: String,
    val cacheCleared: String,
    val done: String,
    val showing: String,
    val outings: String,
    val clear: String,
    val specialEvent: String,
    val randomMemory: String,
    val with: String,
    val close: String,
    val previous: String,
    val next: String,
    val saveQr: String,
    val qrSaved: String,
    val qrSaveFailed: String,
    val openFailed: String,
    val wechat: String,
    val qq: String,
    val copyright: String,
    val gallerySummary: (Int, Int) -> String,
    val daysSummary: (Long) -> String,
    val languageName: (AppLanguage) -> String,
    val themeName: (ThemeMode) -> String,
)

private fun copyFor(context: Context, language: AppLanguage): UiCopy {
    val strings = context.localizedStringContext(language)
    return UiCopy(
        title = strings.getString(R.string.ui_title),
        intro = strings.getString(R.string.ui_intro),
        loading = strings.getString(R.string.ui_loading),
        loadFailed = strings.getString(R.string.ui_load_failed),
        retry = strings.getString(R.string.ui_retry),
        settings = strings.getString(R.string.ui_settings),
        language = strings.getString(R.string.ui_language),
        theme = strings.getString(R.string.ui_theme),
        baseUrl = strings.getString(R.string.ui_source),
        clearCache = strings.getString(R.string.ui_clear_cache),
        cacheCleared = strings.getString(R.string.ui_cache_cleared),
        done = strings.getString(R.string.ui_done),
        showing = strings.getString(R.string.ui_showing),
        outings = strings.getString(R.string.ui_outings),
        clear = strings.getString(R.string.ui_clear),
        specialEvent = strings.getString(R.string.ui_special_event),
        randomMemory = strings.getString(R.string.ui_random_memory),
        with = strings.getString(R.string.ui_with),
        close = strings.getString(R.string.ui_close),
        previous = strings.getString(R.string.ui_previous),
        next = strings.getString(R.string.ui_next),
        saveQr = strings.getString(R.string.ui_save_qr),
        qrSaved = strings.getString(R.string.ui_qr_saved),
        qrSaveFailed = strings.getString(R.string.ui_qr_save_failed),
        openFailed = strings.getString(R.string.ui_open_failed),
        wechat = strings.getString(R.string.ui_wechat),
        qq = strings.getString(R.string.ui_qq),
        copyright = strings.getString(R.string.ui_copyright),
        gallerySummary = { photos, outings ->
            strings.getString(R.string.ui_gallery_summary, photos, outings)
        },
        daysSummary = { days ->
            strings.getString(R.string.ui_days_summary, days)
        },
        languageName = {
            when (it) {
                AppLanguage.System -> strings.getString(R.string.ui_language_system)
                AppLanguage.En -> strings.getString(R.string.ui_language_en)
                AppLanguage.Zh -> strings.getString(R.string.ui_language_zh)
            }
        },
        themeName = {
            when (it) {
                ThemeMode.System -> strings.getString(R.string.ui_theme_system)
                ThemeMode.Light -> strings.getString(R.string.ui_theme_light)
                ThemeMode.Dark -> strings.getString(R.string.ui_theme_dark)
            }
        },
    )
}

private fun Context.localizedStringContext(language: AppLanguage): Context {
    val locale = when (language) {
        AppLanguage.Zh -> Locale.SIMPLIFIED_CHINESE
        AppLanguage.En -> Locale.ENGLISH
        AppLanguage.System -> return this
    }
    val configuration = Configuration(resources.configuration).apply {
        setLocale(locale)
    }
    return createConfigurationContext(configuration)
}

private fun GalleryImage.thumbnailUrl(baseUrl: String): String = "$baseUrl/photos/thumbnails/$filename"

private fun GalleryImage.fullImageUrl(baseUrl: String): String = "$baseUrl/photos/$filename"

private fun String.displayBaseUrl(): String = removePrefix("https://").removePrefix("http://")

private fun socialLinks(): List<SocialLink> = listOf(
    SocialLink("GitHub", "https://github.com/maoawa/mars-vrchat-gallery"),
    SocialLink("X", "https://twitter.com/winmemzqwq"),
    SocialLink("Telegram", "https://t.me/maoawa"),
    SocialLink("Discord", "https://discord.com/users/742704239410675725"),
    SocialLink("Facebook", "https://www.facebook.com/profile.php?id=100088742570811"),
    SocialLink("Instagram", "https://www.instagram.com/winmemzqwq"),
    SocialLink("Email", "mailto:winmemzqwq@gmail.com"),
)

private fun qrContacts(copy: UiCopy, baseUrl: String): List<QrContact> {
    val source = baseUrl.trimEnd('/')
    return listOf(
        QrContact(
            id = "wechat",
            label = copy.wechat,
            number = "12133206888",
            url = "$source/wechat-qr.jpg",
            fileName = "wechat-qr.jpg",
        ),
        QrContact(
            id = "qq",
            label = copy.qq,
            number = "1874985948",
            url = "$source/qq-qr.jpg",
            fileName = "qq-qr.jpg",
        ),
    )
}

private suspend fun saveQrToGallery(context: android.content.Context, contact: QrContact): Boolean = withContext(Dispatchers.IO) {
    val bytes = downloadBytes(contact.url) ?: return@withContext false
    val resolver = context.contentResolver
    val values = ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, contact.fileName)
        put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/Mars VRChat Gallery")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
    }
    val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return@withContext false

    runCatching {
        resolver.openOutputStream(uri)?.use { output ->
            output.write(bytes)
        } ?: error("Unable to open output stream")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
        }
    }.onFailure {
        resolver.delete(uri, null, null)
    }.isSuccess
}

private fun downloadBytes(url: String): ByteArray? {
    val connection = (URL(url).openConnection() as HttpURLConnection).apply {
        connectTimeout = 15_000
        readTimeout = 30_000
        requestMethod = "GET"
        setRequestProperty("Accept", "image/jpeg,image/*,*/*")
    }

    return try {
        if (connection.responseCode in 200..299) {
            connection.inputStream.use { it.readBytes() }
        } else {
            null
        }
    } finally {
        connection.disconnect()
    }
}

private fun GalleryImage.matches(filter: GalleryFilter): Boolean {
    return when (filter) {
        is GalleryFilter.Friend -> filter.id in friendIds
        is GalleryFilter.World -> world == filter.id
    }
}

private fun SpecialEventView.matches(filter: GalleryFilter): Boolean {
    return when (filter) {
        is GalleryFilter.World -> event.world == filter.id || photos.any { it.matches(filter) }
        is GalleryFilter.Friend -> filter.id in event.friendIds || photos.any { it.matches(filter) }
    }
}

private fun buildGalleryFlowItems(
    rows: List<GalleryRow>,
    events: List<SpecialEventView>,
): List<GalleryFlowItem> {
    if (events.isEmpty()) {
        return listOf(GalleryFlowItem.Rows("all", rows))
    }

    val flowItems = mutableListOf<GalleryFlowItem>()
    var rowCursor = 0

    events.forEach { event ->
        val rowsBeforeEvent = mutableListOf<GalleryRow>()
        while (rowCursor < rows.size && rows[rowCursor].photo.captured > event.sortKey) {
            rowsBeforeEvent += rows[rowCursor]
            rowCursor += 1
        }

        if (rowsBeforeEvent.isNotEmpty()) {
            flowItems += GalleryFlowItem.Rows("gallery-before-${event.event.id}", rowsBeforeEvent)
        }
        flowItems += GalleryFlowItem.Event(event)
    }

    val remainingRows = rows.drop(rowCursor)
    if (remainingRows.isNotEmpty()) {
        flowItems += GalleryFlowItem.Rows("gallery-after-special-events", remainingRows)
    }

    return flowItems
}

private fun GalleryFilter.label(content: GalleryContent, language: AppLanguage): String {
    return when (this) {
        is GalleryFilter.Friend -> content.friendName(id, language)
        is GalleryFilter.World -> content.worldName(id, language)
    }
}

private fun GalleryContent.friendName(id: String, language: AppLanguage): String {
    val friend = friendsById[id] ?: return id
    return localisedText(friend.nameEn, friend.nameZh, language)
}

private fun GalleryContent.worldName(id: String, language: AppLanguage): String {
    val world = worldsById[id] ?: return id
    return localisedText(world.nameEn, world.nameZh, language)
}

private fun GalleryImage.description(content: GalleryContent, language: AppLanguage): String? {
    return localisedText(descriptionEn, descriptionZh, language)
        .takeIf { it.isNotBlank() }
        ?.resolveDescriptionTokens(content, language)
}

private fun SpecialEventView.title(language: AppLanguage): String {
    return localisedText(event.titleEn, event.titleZh, language)
}

private fun SpecialEventView.date(language: AppLanguage): String {
    return localisedText(event.dateEn, event.dateZh, language)
}

private fun SpecialEventView.description(content: GalleryContent, language: AppLanguage): String? {
    return localisedText(event.descriptionEn, event.descriptionZh, language)
        .takeIf { it.isNotBlank() }
        ?.resolveDescriptionTokens(content, language)
}

private fun GalleryImage.localizedDescription(language: AppLanguage): String? {
    return localisedText(descriptionEn, descriptionZh, language).takeIf { it.isNotBlank() }
}

private fun SpecialEventView.localizedDescription(language: AppLanguage): String? {
    return localisedText(event.descriptionEn, event.descriptionZh, language).takeIf { it.isNotBlank() }
}

private fun localisedText(en: String?, zh: String?, language: AppLanguage): String {
    val enText = en.orEmpty()
    val zhText = zh.orEmpty()
    return if (language == AppLanguage.Zh) {
        zhText.ifBlank { enText }
    } else {
        enText.ifBlank { zhText }
    }
}

private fun String.descriptionParts(content: GalleryContent, language: AppLanguage): List<DescriptionPart> {
    val parts = mutableListOf<DescriptionPart>()
    var cursor = 0

    while (cursor < length) {
        val matches = listOfNotNull(
            FriendTokenRegex.find(this, cursor)?.let { "friend" to it },
            WorldTokenRegex.find(this, cursor)?.let { "world" to it },
            EmphasisRegex.find(this, cursor)?.let { "emphasis" to it },
            BreakRegex.find(this, cursor)?.let { "break" to it },
        )
        val next = matches.minByOrNull { it.second.range.first }

        if (next == null) {
            parts += DescriptionPart.Text(substring(cursor))
            break
        }

        val match = next.second
        if (match.range.first > cursor) {
            parts += DescriptionPart.Text(substring(cursor, match.range.first))
        }

        when (next.first) {
            "friend" -> {
                val friendId = match.groupValues[1]
                parts += DescriptionPart.Friend(friendId, content.friendName(friendId, language))
            }
            "world" -> {
                val worldId = match.groupValues[1]
                parts += if (content.worldsById.containsKey(worldId)) {
                    DescriptionPart.World(worldId, content.worldName(worldId, language))
                } else {
                    DescriptionPart.Text(match.value)
                }
            }
            "emphasis" -> parts += DescriptionPart.Emphasis(match.groupValues[1])
            "break" -> parts += DescriptionPart.Break
        }

        cursor = match.range.last + 1
    }

    return parts
}

private fun String.resolveDescriptionTokens(content: GalleryContent, language: AppLanguage): String {
    return replace(FriendTokenRegex) { match ->
        content.friendName(match.groupValues[1], language)
    }
        .replace(WorldTokenRegex) { match ->
            content.worldName(match.groupValues[1], language)
        }
        .replace(BreakRegex, "\n")
        .replace("*", "")
}

private fun GalleryImage.displayDate(language: AppLanguage): String {
    val dateTime = parseGalleryDate(captured)
    return if (language == AppLanguage.Zh) {
        DateTimeFormatter.ofPattern("yyyy年M月d日 ah:mm", Locale.CHINA).format(dateTime)
    } else {
        DateTimeFormatter.ofPattern("MMMM d, yyyy 'at' h:mm a", Locale.US).format(dateTime)
    }
}

private fun GalleryImage.formatLinkedDate(parentPhoto: GalleryImage, language: AppLanguage): String {
    return if (parseGalleryDate(captured).toLocalDate() == parseGalleryDate(parentPhoto.captured).toLocalDate()) {
        formatTime(language)
    } else {
        displayDate(language)
    }
}

private fun GalleryImage.formatSpecialEventDate(event: SpecialEventView, language: AppLanguage): String {
    if (event.event.showFullDate) {
        return formatShortDateTime(language)
    }

    val firstPhoto = event.photos.firstOrNull()
    return if (firstPhoto != null && parseGalleryDate(captured).toLocalDate() == parseGalleryDate(firstPhoto.captured).toLocalDate()) {
        formatTime(language)
    } else {
        formatShortDateTime(language)
    }
}

private fun GalleryImage.formatShortDateTime(language: AppLanguage): String {
    val dateTime = parseGalleryDate(captured)
    return if (language == AppLanguage.Zh) {
        DateTimeFormatter.ofPattern("M月d日 ah:mm", Locale.CHINA).format(dateTime)
    } else {
        DateTimeFormatter.ofPattern("MMMM d 'at' h:mm a", Locale.US).format(dateTime)
    }
}

private fun GalleryImage.formatTime(language: AppLanguage): String {
    val dateTime = parseGalleryDate(captured)
    return if (language == AppLanguage.Zh) {
        DateTimeFormatter.ofPattern("ah:mm", Locale.CHINA).format(dateTime)
    } else {
        DateTimeFormatter.ofPattern("h:mm a", Locale.US).format(dateTime)
    }
}

private fun parseGalleryDate(captured: String): OffsetDateTime {
    val hasTimeZone = TimeZoneRegex.containsMatchIn(captured)
    val withTime = if ("T" in captured) captured else "${captured}T00:00:00"
    return OffsetDateTime.parse(if (hasTimeZone) withTime else "$withTime+08:00")
        .withOffsetSameInstant(ZoneOffset.ofHours(8))
}

private fun AppLanguage.resolveSystemLanguage(systemLocale: Locale): AppLanguage {
    if (this != AppLanguage.System) {
        return this
    }

    return if (systemLocale.language.equals("zh", ignoreCase = true)) {
        AppLanguage.Zh
    } else {
        AppLanguage.En
    }
}

private fun daysSinceVrchatStart(): Long {
    val start = OffsetDateTime.of(2026, 6, 3, 18, 0, 0, 0, ZoneOffset.UTC)
    return Duration.between(start.toInstant(), OffsetDateTime.now().toInstant()).toDays().coerceAtLeast(0)
}
