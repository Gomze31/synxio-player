package fr.synxio.player.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.synxio.player.alarm.AlarmSchedule
import fr.synxio.player.data.db.AlarmEntity
import fr.synxio.player.data.db.AlarmSource
import fr.synxio.player.data.model.Playlist
import fr.synxio.player.ui.components.BrandButton
import fr.synxio.player.ui.components.EmptyState
import fr.synxio.player.ui.components.GlassCard
import fr.synxio.player.ui.components.LumenChip
import fr.synxio.player.ui.components.SheetHandle
import fr.synxio.player.ui.components.SynxioSnackbar
import fr.synxio.player.ui.theme.EyebrowTextStyle
import fr.synxio.player.ui.theme.Space
import fr.synxio.player.ui.theme.TabularNumbers
import fr.synxio.player.ui.theme.synxioTopBarColors
import fr.synxio.player.ui.viewmodel.AlarmsViewModel
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Réveils en musique : la liste, et une feuille pour en régler un.
 *
 * Chaque carte dit quand le réveil sonnera (« dans 7 h 12 min ») : c'est la question
 * qu'on se pose en le réglant, et la réponse évite de se tromper de jour.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlarmsScreen(onBack: () -> Unit) {
    val viewModel: AlarmsViewModel = hiltViewModel()
    val alarms by viewModel.alarms.collectAsStateWithLifecycle()
    val playlists by viewModel.playlists.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<AlarmEntity?>(null) }
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.messages.collect { snackbar.showSnackbar(it) }
    }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        containerColor = Color.Transparent,
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        snackbarHost = { SnackbarHost(snackbar) { SynxioSnackbar(it) } },
        topBar = {
            LargeTopAppBar(
                colors = synxioTopBarColors(),
                title = { Text("Réveils", maxLines = 1) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Retour")
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
        bottomBar = {
            Box(
                Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(Space.lg),
                contentAlignment = Alignment.Center,
            ) {
                BrandButton(text = "Nouveau réveil", onClick = { editing = NEW_ALARM }, icon = Icons.Rounded.Add)
            }
        },
    ) { padding ->
        if (alarms.isEmpty()) {
            EmptyState(
                title = "Aucun réveil",
                subtitle = "Réveille-toi avec tes favoris, ton mix du jour ou une playlist, " +
                    "avec un volume qui monte doucement.",
                icon = Icons.Rounded.Alarm,
                modifier = Modifier.padding(padding),
            )
        } else {
            LazyColumn(
                contentPadding = PaddingValues(
                    top = padding.calculateTopPadding(),
                    bottom = padding.calculateBottomPadding() + Space.xl,
                    start = Space.gutter,
                    end = Space.gutter,
                ),
                verticalArrangement = Arrangement.spacedBy(Space.md),
            ) {
                items(alarms, key = { it.id }) { alarm ->
                    AlarmCard(
                        alarm = alarm,
                        playlistName = playlists.firstOrNull { it.id == alarm.playlistId }?.name,
                        until = if (alarm.enabled) viewModel.untilLabel(alarm) else null,
                        onToggle = { viewModel.setEnabled(alarm, it) },
                        onOpen = { editing = alarm },
                        modifier = Modifier.animateItem(),
                    )
                }
            }
        }
    }

    editing?.let { alarm ->
        AlarmEditorSheet(
            initial = alarm,
            playlists = playlists,
            onDismiss = { editing = null },
            onSave = { viewModel.save(it); editing = null },
            onDelete = { viewModel.delete(alarm); editing = null },
            onTry = { viewModel.tryNow(alarm); editing = null },
        )
    }
}

private val NEW_ALARM = AlarmEntity(
    hour = 7,
    minute = 0,
    days = AlarmSchedule.WEEKDAYS,
    source = AlarmSource.FAVORITES.name,
    playlistId = null,
    rampSeconds = 60,
    enabled = true,
)

@Composable
private fun AlarmCard(
    alarm: AlarmEntity,
    playlistName: String?,
    until: String?,
    onToggle: (Boolean) -> Unit,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val dim = if (alarm.enabled) 1f else 0.5f
    GlassCard(modifier = modifier.fillMaxWidth(), onClick = onOpen) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    "%02d:%02d".format(alarm.hour, alarm.minute),
                    style = MaterialTheme.typography.displaySmall.merge(TabularNumbers),
                    color = scheme.onSurface.copy(alpha = dim),
                )
                Text(
                    AlarmSchedule.daysLabel(alarm.days),
                    style = MaterialTheme.typography.titleSmall,
                    color = scheme.primary.copy(alpha = dim),
                )
                Text(
                    sourceLabel(alarm, playlistName),
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (until != null) {
                    Text(
                        "Sonne $until",
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant,
                    )
                }
            }
            Switch(checked = alarm.enabled, onCheckedChange = onToggle)
        }
    }
}

private fun sourceLabel(alarm: AlarmEntity, playlistName: String?): String =
    if (alarm.sourceKind == AlarmSource.PLAYLIST && playlistName != null) "Playlist « $playlistName »"
    else alarm.sourceKind.label

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun AlarmEditorSheet(
    initial: AlarmEntity,
    playlists: List<Playlist>,
    onDismiss: () -> Unit,
    onSave: (AlarmEntity) -> Unit,
    onDelete: () -> Unit,
    onTry: () -> Unit,
) {
    val time = rememberTimePickerState(initialHour = initial.hour, initialMinute = initial.minute, is24Hour = true)
    var days by remember { mutableIntStateOf(initial.days) }
    var source by remember { mutableStateOf(initial.sourceKind) }
    var playlistId by remember { mutableStateOf(initial.playlistId ?: playlists.firstOrNull()?.id) }
    var ramp by remember { mutableFloatStateOf(initial.rampSeconds.toFloat()) }
    val isNew = initial.id == 0L

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        dragHandle = { SheetHandle() },
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = Space.gutter, vertical = Space.md),
        ) {
            Text(if (isNew) "Nouveau réveil" else "Modifier le réveil", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(Space.lg))
            // Le cadran plutôt que la saisie au clavier : TimeInput ouvrait le clavier
            // d'office, qui masquait la moitié de la feuille.
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                TimePicker(state = time)
            }

            Label("Jours")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.sm), verticalArrangement = Arrangement.spacedBy(Space.sm)) {
                DayOfWeek.entries.forEach { day ->
                    val on = AlarmSchedule.has(days, day)
                    LumenChip(
                        label = day.getDisplayName(TextStyle.SHORT, Locale.FRENCH).removeSuffix("."),
                        selected = on,
                        onClick = { days = days xor AlarmSchedule.bit(day) },
                    )
                }
            }
            Text(
                if (days == 0) "Aucun jour choisi : le réveil sonnera une seule fois." else AlarmSchedule.daysLabel(days),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Space.xs),
            )

            Label("Musique")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.sm), verticalArrangement = Arrangement.spacedBy(Space.sm)) {
                AlarmSource.entries
                    .filter { it != AlarmSource.PLAYLIST || playlists.isNotEmpty() }
                    .forEach { option ->
                        LumenChip(label = option.label, selected = source == option, onClick = { source = option })
                    }
            }
            if (source == AlarmSource.PLAYLIST) {
                Spacer(Modifier.height(Space.sm))
                playlists.forEach { playlist ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable(role = Role.RadioButton) { playlistId = playlist.id }
                            .padding(vertical = Space.sm),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        androidx.compose.material3.RadioButton(selected = playlistId == playlist.id, onClick = null)
                        Spacer(Modifier.padding(start = Space.sm))
                        Text(playlist.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }

            Label("Montée du volume")
            val seconds = ramp.roundToInt()
            Text(
                if (seconds == 0) "Volume plein dès le début" else "Le volume monte doucement pendant $seconds s",
                style = MaterialTheme.typography.bodyMedium,
            )
            Slider(value = ramp, onValueChange = { ramp = it }, valueRange = 0f..120f, steps = 7)

            Spacer(Modifier.height(Space.lg))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (!isNew) {
                    TextButton(onClick = onDelete) { Text("Supprimer") }
                    TextButton(onClick = onTry) { Text("Essayer") }
                }
                Spacer(Modifier.weight(1f))
                BrandButton(
                    text = "Enregistrer",
                    onClick = {
                        onSave(
                            initial.copy(
                                hour = time.hour,
                                minute = time.minute,
                                days = days,
                                source = source.name,
                                playlistId = if (source == AlarmSource.PLAYLIST) playlistId else null,
                                rampSeconds = seconds,
                            )
                        )
                    },
                    enabled = source != AlarmSource.PLAYLIST || playlistId != null,
                )
            }
            Spacer(Modifier.height(Space.lg))
        }
    }
}

@Composable
private fun Label(text: String) {
    Spacer(Modifier.height(Space.xl))
    Text(text.uppercase(), style = EyebrowTextStyle, color = MaterialTheme.colorScheme.primary)
    Spacer(Modifier.height(Space.sm))
}
