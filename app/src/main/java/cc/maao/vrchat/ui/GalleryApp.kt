package cc.maao.vrchat.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
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
import kotlinx.coroutines.launch
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
private val FriendBlue = Color(0xFF8EBEFF)

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

    MarsVRChatGalleryTheme(darkTheme = darkTheme, dynamicColor = false) {
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
    val copy = copyFor(resolvedLanguage)
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
        topBar = {
            TopAppBar(
                title = { Text(copy.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                actions = {
                    IconButton(onClick = { showSettings = true }) {
                        Text("⚙", style = MaterialTheme.typography.titleLarge)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
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
private fun LoadingScreen(label: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(label, style = MaterialTheme.typography.titleMedium)
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
        Text(message, color = MaterialTheme.colorScheme.error)
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
            shape = RoundedCornerShape(8.dp),
            colors = CardDefaults.elevatedCardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
            ),
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(copy.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text(copy.intro, style = MaterialTheme.typography.bodyMedium)
                Text(
                    text = copy.summary(content.photos.size, content.rows.size, daysSinceVrchatStart()),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
        }

        randomRow?.let { row ->
            val photos = listOf(row.photo) + row.linkedPhotos
            Text(
                text = copy.randomMemory.uppercase(),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.tertiary,
                fontWeight = FontWeight.Bold,
            )
            ElevatedCard(
                shape = RoundedCornerShape(8.dp),
                colors = CardDefaults.elevatedCardColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            ) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = row.photo.displayDate(language),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                CachedNetworkImage(
                    url = row.photo.thumbnailUrl(baseUrl),
                    contentDescription = row.photo.description(content, language) ?: row.photo.filename,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 10f)
                        .clip(RoundedCornerShape(8.dp))
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
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(row.linkedPhotos, key = { it.id }) { photo ->
                        CachedNetworkImage(
                            url = photo.thumbnailUrl(baseUrl),
                            contentDescription = photo.filename,
                            modifier = Modifier
                                .size(width = 92.dp, height = 72.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { onOpenPhotos(photos, photos.indexOf(photo)) },
                            targetPixelSize = 720,
                        )
                    }
                }
                }
            }
        }
    }
}

@Composable
private fun ActiveFilterBar(
    label: String,
    copy: UiCopy,
    rowCount: Int,
    onClear: () -> Unit,
) {
    Card(shape = RoundedCornerShape(8.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(Modifier.weight(1f)) {
                Text("${copy.showing} $rowCount ${copy.outings}")
                Text(label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            }
            OutlinedButton(onClick = onClear) {
                Text(copy.clear)
            }
        }
    }
}

@Composable
private fun GalleryRowCard(
    row: GalleryRow,
    content: GalleryContent,
    baseUrl: String,
    copy: UiCopy,
    language: AppLanguage,
    onFilterChange: (GalleryFilter?) -> Unit,
    onOpenPhotos: (List<GalleryImage>, Int) -> Unit,
) {
    val photos = listOf(row.photo) + row.linkedPhotos
    ElevatedCard(shape = RoundedCornerShape(8.dp)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            CachedNetworkImage(
                url = row.photo.thumbnailUrl(baseUrl),
                contentDescription = row.photo.description(content, language) ?: row.photo.filename,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 10f)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onOpenPhotos(photos, 0) },
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
                    onOpenPhoto = { photo -> onOpenPhotos(photos, photos.indexOf(photo)) },
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
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
        ),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(copy.specialEvent, style = MaterialTheme.typography.labelLarge)
            Text(event.title(language), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(event.date(language), style = MaterialTheme.typography.bodyMedium)
            event.localizedDescription(language)?.let {
                RichDescription(
                    description = it,
                    content = content,
                    language = language,
                    onFilterChange = onFilterChange,
                )
            }
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(event.featuredPhotos, key = { it.id }) { photo ->
                    Box(
                        modifier = Modifier
                            .width(240.dp)
                            .clip(RoundedCornerShape(8.dp))
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
                        Text(
                            text = "#${photo.id} · ${photo.formatSpecialEventDate(event, language)}",
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(8.dp)
                                .background(
                                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.82f),
                                    shape = RoundedCornerShape(8.dp),
                                )
                                .padding(horizontal = 8.dp, vertical = 5.dp),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
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
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                event.event.world?.takeIf { it.isNotBlank() }?.let { worldId ->
                    TagButton(content.worldName(worldId, language)) {
                        onFilterChange(GalleryFilter.World(worldId))
                    }
                }
                event.event.friendIds.forEach { friendId ->
                    TagButton(content.friendName(friendId, language)) {
                        onFilterChange(GalleryFilter.Friend(friendId))
                    }
                }
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
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = photo.displayDate(language),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.tertiary,
        )
        if (photo.world.isNotBlank()) {
            Text(
                text = content.worldName(photo.world, language),
                modifier = Modifier.clickable { onFilterChange(GalleryFilter.World(photo.world)) },
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
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
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
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
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (parentPhoto != null && photo.world.isNotBlank() && photo.world != parentPhoto.world) {
                    Text(
                        text = content.worldName(photo.world, language),
                        modifier = Modifier.padding(start = 8.dp, end = 8.dp, bottom = 8.dp),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
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
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Bold,
    )
    val worldStyle = SpanStyle(
        color = MaterialTheme.colorScheme.secondary,
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
        style = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
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
    val friendIds = photo.friendIds.filter { it.isNotBlank() }
    if (friendIds.isEmpty()) {
        return
    }

    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = copy.with.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Bold,
        )
        friendIds.forEach { friendId ->
            TagButton(content.friendName(friendId, language)) {
                onFilterChange(GalleryFilter.Friend(friendId))
            }
        }
    }
}

@Composable
private fun TagButton(
    label: String,
    onClick: () -> Unit,
) {
    AssistChip(
        onClick = onClick,
        label = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        shape = RoundedCornerShape(8.dp),
    )
}

@Composable
private fun SettingsDialog(
    settings: GallerySettings,
    copy: UiCopy,
    onDismiss: () -> Unit,
    onSettingsChange: (GallerySettings) -> Unit,
    onClearCache: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(copy.settings) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SettingsMenuRow(
                    label = copy.language,
                    value = copy.languageName(settings.language),
                    items = listOf(AppLanguage.System, AppLanguage.Zh, AppLanguage.En),
                    itemLabel = { copy.languageName(it) },
                    onItemSelected = { onSettingsChange(settings.copy(language = it)) },
                )
                SettingsMenuRow(
                    label = copy.theme,
                    value = copy.themeName(settings.themeMode),
                    items = listOf(ThemeMode.System, ThemeMode.Light, ThemeMode.Dark),
                    itemLabel = { copy.themeName(it) },
                    onItemSelected = { onSettingsChange(settings.copy(themeMode = it)) },
                )
                SettingsMenuRow(
                    label = copy.baseUrl,
                    value = settings.baseUrl,
                    items = BASE_URLS,
                    itemLabel = { it },
                    onItemSelected = { onSettingsChange(settings.copy(baseUrl = it)) },
                )
                OutlinedButton(
                    onClick = onClearCache,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(copy.clearCache)
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text(copy.done)
            }
        },
    )
}

@Composable
private fun <T> SettingsMenuRow(
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
        Text(label, style = MaterialTheme.typography.bodyLarge)
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
    val photo = photos[index.coerceIn(0, photos.lastIndex)]
    val context = LocalContext.current
    var zoom by remember(photo.id) { mutableFloatStateOf(1f) }
    var offset by remember(photo.id) { mutableStateOf(Offset.Zero) }

    LaunchedEffect(photo.id, photos) {
        zoom = 1f
        offset = Offset.Zero
        val nearby = listOfNotNull(
            photos.getOrNull((index - 1 + photos.size) % photos.size),
            photo,
            photos.getOrNull((index + 1) % photos.size),
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
                .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.72f))
                .padding(12.dp)
                .statusBarsPadding()
                .navigationBarsPadding(),
            contentAlignment = Alignment.Center,
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 1_180.dp)
                    .fillMaxHeight(0.92f),
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp,
            ) {
            Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("${index + 1} / ${photos.size}", style = MaterialTheme.typography.titleMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("${(zoom * 100).toInt()}%", style = MaterialTheme.typography.labelMedium)
                        Button(onClick = onDismiss) { Text(copy.close) }
                    }
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.scrim)
                        .pointerInput(photo.id) {
                            detectTapGestures(
                                onDoubleTap = {
                                    zoom = if (zoom > 1f) 1f else 2.5f
                                    offset = Offset.Zero
                                },
                            )
                        }
                        .pointerInput(photo.id) {
                            detectTransformGestures { _, pan, gestureZoom, _ ->
                                val nextZoom = (zoom * gestureZoom).coerceIn(1f, 4f)
                                zoom = nextZoom
                                offset = if (nextZoom <= 1f) Offset.Zero else offset + pan
                            }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    CachedNetworkImage(
                        url = photo.fullImageUrl(baseUrl),
                        contentDescription = photo.filename,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                scaleX = zoom
                                scaleY = zoom
                                translationX = offset.x
                                translationY = offset.y
                            },
                        contentScale = ContentScale.Fit,
                        targetPixelSize = 3_200,
                    )
                }
                content?.let { loadedContent ->
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
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    OutlinedButton(
                        enabled = photos.size > 1,
                        onClick = {
                            onIndexChange((index - 1 + photos.size) % photos.size)
                        },
                    ) {
                        Text(copy.previous)
                    }
                    OutlinedButton(
                        enabled = photos.size > 1,
                        onClick = {
                            onIndexChange((index + 1) % photos.size)
                        },
                    ) {
                        Text(copy.next)
                    }
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
    val summary: (Int, Int, Long) -> String,
    val languageName: (AppLanguage) -> String,
    val themeName: (ThemeMode) -> String,
)

private fun copyFor(language: AppLanguage): UiCopy {
    return when (language) {
        AppLanguage.System,
        AppLanguage.En -> UiCopy(
            title = "Mars VRChat Gallery",
            intro = "Hi! I'm Mars. This native Android gallery reads the website data directly and caches images locally.",
            loading = "Loading gallery...",
            loadFailed = "Failed to load gallery",
            retry = "Retry",
            settings = "Settings",
            language = "Language",
            theme = "Theme",
            baseUrl = "Base URL",
            clearCache = "Clear cache",
            cacheCleared = "Cache cleared",
            done = "Done",
            showing = "Showing",
            outings = "outings",
            clear = "Clear",
            specialEvent = "Special event",
            randomMemory = "Random memory",
            with = "With",
            close = "Close",
            previous = "Previous",
            next = "Next",
            summary = { photos, outings, days -> "$photos photos · $outings outings · $days days since Mars joined VRChat" },
            languageName = {
                when (it) {
                    AppLanguage.System -> "System"
                    AppLanguage.Zh -> "中文"
                    AppLanguage.En -> "English"
                }
            },
            themeName = {
                when (it) {
                    ThemeMode.System -> "System"
                    ThemeMode.Light -> "Light"
                    ThemeMode.Dark -> "Dark"
                }
            },
        )

        AppLanguage.Zh -> UiCopy(
            title = "毛毛的 VRChat 相册",
            intro = "嗨！我是毛毛(Mars)。这是原生 Android 版 VRChat 相册，会直接读取网站数据并缓存图片。",
            loading = "正在读取相册...",
            loadFailed = "读取相册失败",
            retry = "重试",
            settings = "设置",
            language = "语言",
            theme = "外观",
            baseUrl = "数据源",
            clearCache = "清除缓存",
            cacheCleared = "缓存已清除",
            done = "完成",
            showing = "正在显示",
            outings = "个聚会",
            clear = "清除",
            specialEvent = "特别事件",
            randomMemory = "随机回忆",
            with = "与",
            close = "关闭",
            previous = "上一张",
            next = "下一张",
            summary = { photos, outings, days -> "$photos 张照片 · $outings 个聚会 · 毛毛加入 VRChat $days 天了" },
            languageName = {
                when (it) {
                    AppLanguage.System -> "跟随系统"
                    AppLanguage.Zh -> "中文"
                    AppLanguage.En -> "English"
                }
            },
            themeName = {
                when (it) {
                    ThemeMode.System -> "跟随系统"
                    ThemeMode.Light -> "浅色"
                    ThemeMode.Dark -> "深色"
                }
            },
        )
    }
}

private fun GalleryImage.thumbnailUrl(baseUrl: String): String = "$baseUrl/photos/thumbnails/$filename"

private fun GalleryImage.fullImageUrl(baseUrl: String): String = "$baseUrl/photos/$filename"

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
