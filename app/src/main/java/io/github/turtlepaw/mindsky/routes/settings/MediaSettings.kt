package io.github.turtlepaw.mindsky.routes.settings

import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import fyi.kittens.shared.R
import io.github.turtlepaw.mindsky.MindskyApplication
import io.github.turtlepaw.mindsky.db.ObjectBox
import io.github.turtlepaw.mindsky.db.Scrobble
import io.github.turtlepaw.mindsky.db.Scrobble_
import io.github.turtlepaw.mindsky.media.NowPlayingStore
import io.github.turtlepaw.mindsky.media.Track
import io.github.turtlepaw.mindsky.preferences.AppPrefs
import io.github.turtlepaw.mindsky.preferences.LocalPreferences
import io.github.turtlepaw.mindsky.preferences.rememberPreference
import io.github.turtlepaw.mindsky.utils.PermissionUtils
import io.github.turtlepaw.mindsky.utils.StringComposable
import io.objectbox.kotlin.query
import io.objectbox.query.QueryBuilder
import me.zhanghai.compose.preference.Preference
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Destination<RootGraph>
@Composable
fun MediaSettings(navigator: DestinationsNavigator) {
    val context = LocalContext.current
    val preferences = LocalPreferences.current
    val nowPlaying by NowPlayingStore.nowPlaying.collectAsState()
    val trackingEnabled by rememberPreference(AppPrefs.TrackingEnabled)
    val scrobblingDid by rememberPreference(AppPrefs.ScrobblingDid)
    val app = context.applicationContext as MindskyApplication
    val session by app.sessionManager.sessionFlow.collectAsState()
    
    val scrobbleBox = remember { ObjectBox.store.boxFor(Scrobble::class.java) }
    var history by remember { mutableStateOf(emptyList<Scrobble>()) }
    
    var showPermissionDialog by remember { mutableStateOf(false) }
    
    LaunchedEffect(scrobblingDid) {
        if (scrobblingDid.isNotBlank()) {
            val builder = scrobbleBox.query()
            builder.equal(Scrobble_.userId, scrobblingDid, QueryBuilder.StringOrder.CASE_INSENSITIVE)
            history = builder.orderDesc(Scrobble_.timestamp).build().find()
        } else {
            history = emptyList()
        }
    }

    Scaffold(
        topBar = {
            TopAppBarCommon.withBack(navigator, R.string.media_settings)
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            item {
                Preference(
                    title = { R.string.media_tracking_enabled.StringComposable() },
                    summary = { 
                        if (scrobblingDid.isNotBlank()) {
                            val scrobblingSession = remember(scrobblingDid) {
                                app.sessionManager.getSession(scrobblingDid)
                            }
                            Text("Tracking for ${scrobblingSession?.handle ?: scrobblingDid}")
                        } else {
                            R.string.media_tracking_description.StringComposable()
                        }
                    },
                    icon = { Icon(Icons.Rounded.MusicNote, contentDescription = null) },
                    widgetContainer = {
                        Switch(
                            checked = trackingEnabled,
                            onCheckedChange = { checked ->
                                if (checked) {
                                    if (!PermissionUtils.isNotificationListenerEnabled(context)) {
                                        showPermissionDialog = true
                                    } else {
                                        preferences.set(AppPrefs.TrackingEnabled, true)
                                        if (scrobblingDid.isBlank()) {
                                            session?.did?.let { preferences.set(AppPrefs.ScrobblingDid, it) }
                                        }
                                    }
                                } else {
                                    preferences.set(AppPrefs.TrackingEnabled, false)
                                }
                            }
                        )
                    },
                    onClick = {
                        val newValue = !trackingEnabled
                        if (newValue) {
                            if (!PermissionUtils.isNotificationListenerEnabled(context)) {
                                showPermissionDialog = true
                            } else {
                                preferences.set(AppPrefs.TrackingEnabled, true)
                                if (scrobblingDid.isBlank()) {
                                    session?.did?.let { preferences.set(AppPrefs.ScrobblingDid, it) }
                                }
                            }
                        } else {
                            preferences.set(AppPrefs.TrackingEnabled, false)
                        }
                    }
                )
            }
            
            if (scrobblingDid.isNotBlank() && session?.did != scrobblingDid) {
                item {
                    TextButton(
                        onClick = {
                            session?.did?.let { preferences.set(AppPrefs.ScrobblingDid, it) }
                        },
                        modifier = Modifier.padding(horizontal = 16.dp)
                    ) {
                        Text("Change tracking account to current (${session?.handle})")
                    }
                }
            }
            
            if (trackingEnabled) {
                item {
                    SectionHeader(R.string.now_playing)
                }
                
                item {
                    NowPlayingCard(nowPlaying)
                }
                
                item {
                    SectionHeader(R.string.scrobble_history)
                }
                
                if (history.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = stringResource(R.string.no_scrobbles),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    items(history) { scrobble ->
                        ScrobbleItem(scrobble)
                    }
                }
            }
        }
    }

    if (showPermissionDialog) {
        AlertDialog(
            onDismissRequest = { showPermissionDialog = false },
            title = { Text(stringResource(R.string.notification_access_required)) },
            text = { Text(stringResource(R.string.notification_access_description)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showPermissionDialog = false
                        context.startActivity(Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"))
                    }
                ) {
                    Text(stringResource(R.string.open_settings))
                }
            },
            dismissButton = {
                TextButton(onClick = { showPermissionDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

@Composable
fun SectionHeader(titleRes: Int) {
    Box(modifier = Modifier.padding(16.dp)) {
        titleRes.StringComposable(
            style = MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        )
    }
}

@Composable
fun NowPlayingCard(track: Track?) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        if (track != null) {
            Row(
                modifier = Modifier
                    .padding(12.dp)
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AsyncImage(
                    model = track.albumArtUrl,
                    contentDescription = "Album art",
                    modifier = Modifier
                        .size(64.dp)
                        .padding(end = 12.dp),
                    contentScale = ContentScale.Crop
                )
                Column {
                    Text(
                        text = track.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = track.artists.joinToString { it.name },
                        style = MaterialTheme.typography.bodyMedium
                    )
                    track.album?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.nothing_playing),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun ScrobbleItem(scrobble: Scrobble) {
    val formatter = remember {
        DateTimeFormatter.ofPattern("HH:mm, MMM d")
            .withZone(ZoneId.systemDefault())
    }
    val timeString = remember(scrobble.timestamp) {
        formatter.format(Instant.ofEpochMilli(scrobble.timestamp))
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = scrobble.albumArtUrl,
            contentDescription = null,
            modifier = Modifier
                .size(48.dp)
                .padding(end = 12.dp),
            contentScale = ContentScale.Crop
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = scrobble.trackName,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = scrobble.artistsJson,
                style = MaterialTheme.typography.bodyMedium
            )
        }
        Text(
            text = timeString,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
