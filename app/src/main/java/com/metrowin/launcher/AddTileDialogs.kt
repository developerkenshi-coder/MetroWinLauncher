package com.metrowin.launcher

import com.metrowin.launcher.ui.start.ChoiceSheet
import com.metrowin.launcher.ui.start.Choice
import com.metrowin.launcher.data.Steps
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.metrowin.launcher.data.QuickAction
import com.metrowin.launcher.data.GlobalSearch
import com.metrowin.launcher.data.LivePermissions
import com.metrowin.launcher.data.WidgetProvider
import com.metrowin.launcher.model.AppItem
import com.metrowin.launcher.ui.start.AddMenu
import com.metrowin.launcher.data.Countdown
import com.metrowin.launcher.ui.start.CountdownDialog
import com.metrowin.launcher.ui.start.ChecklistDialog
import com.metrowin.launcher.ui.start.CityPicker
import com.metrowin.launcher.ui.start.AlbumPicker
import com.metrowin.launcher.ui.start.GroupPicker
import com.metrowin.launcher.ui.start.PlaceDialog
import com.metrowin.launcher.ui.pages.Panel
import com.metrowin.launcher.ui.pages.PanelState
import com.metrowin.launcher.data.AlbumInfo
import com.metrowin.launcher.data.GroupInfo
import com.metrowin.launcher.ui.start.CounterDialog
import com.metrowin.launcher.model.TileData
import com.metrowin.launcher.ui.start.NoteDialog
import com.metrowin.launcher.ui.start.SettingPicker
import com.metrowin.launcher.ui.start.SiteDialog
import com.metrowin.launcher.ui.start.WidgetPicker
import kotlinx.coroutines.launch

/** Which dialog of the "add a tile" menu is open, and the Start screen the new tile goes on. */
internal class AddTileState {
    var addMenuOpen by mutableStateOf(false)
    var siteDialogOpen by mutableStateOf(false)
    var widgetPickerOpen by mutableStateOf(false)
    var settingPickerOpen by mutableStateOf(false)
    var togglePickerOpen by mutableStateOf(false)
    var placeDialogOpen by mutableStateOf(false)
    var albumPickerOpen by mutableStateOf(false)
    var groupPickerOpen by mutableStateOf(false)
    var cityPickerOpen by mutableStateOf(false)
    var noteEditingId by mutableStateOf<String?>(null)
    var counterEditingId by mutableStateOf<String?>(null)
    var checklistEditingId by mutableStateOf<String?>(null)
    var countdownNew by mutableStateOf(false)
    var countdownEditingId by mutableStateOf<String?>(null)

    /** The Start screen the add menu puts the new tile on: the selected tile's, or the one on screen after a long press on the empty part. */
    var addPage by mutableIntStateOf(0)
}

/** The add menu and the dialogs and pickers it opens. */
@Composable
internal fun AddTileState.Dialogs(
    vm: LauncherViewModel,
    items: List<AppItem>,
    panels: PanelState,
    onPickContact: () -> Unit,
    onPickNumber: () -> Unit,
    requestPermission: (String) -> Unit,
    ensureNotifications: () -> Unit,
    addWidget: (WidgetProvider) -> Unit,
) {
        val appContext = LocalContext.current
        if (addMenuOpen) {
            AddMenu(
                onContact = {
                    addMenuOpen = false
                    onPickContact()
                },
                onSite = {
                    addMenuOpen = false
                    siteDialogOpen = true
                },
                onWidget = {
                    addMenuOpen = false
                    widgetPickerOpen = true
                },
                onSpeedDial = {
                    addMenuOpen = false
                    onPickNumber()
                },
                onSetting = {
                    addMenuOpen = false
                    settingPickerOpen = true
                },
                onNote = {
                    addMenuOpen = false
                    noteEditingId = vm.addNote(addPage)
                },
                onCountdown = {
                    addMenuOpen = false
                    countdownNew = true
                },
                onCounter = {
                    addMenuOpen = false
                    counterEditingId = vm.addCounter(addPage)
                },
                onChecklist = {
                    addMenuOpen = false
                    checklistEditingId = vm.addChecklist(addPage)
                },
                onWorldClock = {
                    addMenuOpen = false
                    cityPickerOpen = true
                },
                onMoon = {
                    addMenuOpen = false
                    vm.addMoon(addPage)
                },
                onBirthdays = {
                    addMenuOpen = false
                    vm.addBirthdays(addPage)
                    if (!LivePermissions.isGranted(appContext, LivePermissions.contacts)) requestPermission(LivePermissions.contacts)
                },
                onPlace = {
                    addMenuOpen = false
                    placeDialogOpen = true
                },
                onAlbum = {
                    addMenuOpen = false
                    albumPickerOpen = true
                },
                onGroup = {
                    addMenuOpen = false
                    groupPickerOpen = true
                },
                onTimer = {
                    addMenuOpen = false
                    panels.open(Panel.Timer(vm.addTimer(addPage)))
                },
                onStopwatch = {
                    addMenuOpen = false
                    panels.open(Panel.Stopwatch(vm.addStopwatch(addPage)))
                },
                onReminders = {
                    addMenuOpen = false
                    vm.addRemindersTile(addPage)
                    ensureNotifications()
                },
                onNews = {
                    addMenuOpen = false
                    vm.addNews(addPage)
                },
                onFinance = {
                    addMenuOpen = false
                    vm.addFinance(addPage)
                },
                onDialGrid = {
                    addMenuOpen = false
                    vm.addDialGrid(addPage)
                    if (!LivePermissions.isGranted(appContext, LivePermissions.contacts)) requestPermission(LivePermissions.contacts)
                },
                onPeopleHub = {
                    addMenuOpen = false
                    vm.addPeopleHub(addPage)
                    if (!LivePermissions.isGranted(appContext, LivePermissions.contacts)) requestPermission(LivePermissions.contacts)
                },
                onSteps = {
                    addMenuOpen = false
                    vm.addSteps(addPage)
                    Steps.permission()?.takeIf { !LivePermissions.isGranted(appContext, it) }?.let(requestPermission)
                },
                onAppsRecap = {
                    addMenuOpen = false
                    vm.addAppsRecap(addPage)
                },
                onBatteryGraph = {
                    addMenuOpen = false
                    vm.addBatteryGraph(addPage)
                },
                onDataUse = {
                    addMenuOpen = false
                    vm.addDataUse(addPage)
                },
                onMemoryUse = {
                    addMenuOpen = false
                    vm.addMemoryUse(addPage)
                },
                onSystemMonitor = {
                    addMenuOpen = false
                    vm.addSystemMonitor(addPage)
                },
                onRecorder = {
                    addMenuOpen = false
                    vm.addRecorder(addPage)
                },
                onCamera = {
                    addMenuOpen = false
                    vm.addCamera(addPage)
                    if (!LivePermissions.canReadPhotos(appContext)) requestPermission(LivePermissions.photos)
                },
                onWallet = {
                    addMenuOpen = false
                    vm.addWallet(addPage)
                },
                onWhereAmI = {
                    addMenuOpen = false
                    vm.addWhereAmI(addPage)
                },
                onParking = {
                    addMenuOpen = false
                    vm.addParking(addPage)
                },
                onAnalogClock = {
                    addMenuOpen = false
                    vm.addAnalogClock(addPage)
                },
                onSun = {
                    addMenuOpen = false
                    vm.addSun(addPage)
                },
                onWeatherPlus = {
                    addMenuOpen = false
                    vm.addWeatherPlus(addPage)
                },
                onToggle = {
                    addMenuOpen = false
                    togglePickerOpen = true
                },
                onDismiss = { addMenuOpen = false },
            )
        }

        if (togglePickerOpen) {
            val choices = remember(appContext) {
                QuickAction.toggles.map { Choice(it.id, it.glyph, appContext.getString(it.label)) }
            }
            ChoiceSheet(
                title = stringResource(R.string.add_toggle_title), choices = choices, tagPrefix = "toggle",
                onPick = { choice ->
                    togglePickerOpen = false
                    QuickAction.fromId(choice.id)?.let { vm.addToggle(it, addPage) }
                },
                onDismiss = { togglePickerOpen = false },
            )
        }

        if (settingPickerOpen) {
            val defs = remember { GlobalSearch.systemSettings.map { it to appContext.getString(it.label) }.sortedBy { it.second.lowercase() } }
            SettingPicker(
                settings = defs,
                onPick = { def ->
                    settingPickerOpen = false
                    vm.pinSetting(def, addPage)
                },
                onDismiss = { settingPickerOpen = false },
            )
        }

        if (placeDialogOpen) {
            PlaceDialog(
                onDone = { name, address ->
                    val accepted = vm.addPlace(name, address, addPage)
                    if (accepted) placeDialogOpen = false
                    accepted
                },
                onCancel = { placeDialogOpen = false },
            )
        }

        if (albumPickerOpen) {
            var granted by remember { mutableStateOf(LivePermissions.canReadPhotos(appContext)) }
            val photosPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
                granted = LivePermissions.canReadPhotos(appContext)
            }
            val albums by produceState<List<AlbumInfo>?>(null, granted) { value = if (granted) vm.albums() else emptyList() }
            AlbumPicker(
                albums = albums, permissionMissing = !granted, onAllow = { photosPermission.launch(LivePermissions.photos) },
                onPick = { album ->
                    albumPickerOpen = false
                    vm.addAlbum(album, addPage)
                },
                onDismiss = { albumPickerOpen = false },
            )
        }

        if (groupPickerOpen) {
            var granted by remember { mutableStateOf(LivePermissions.isGranted(appContext, LivePermissions.contacts)) }
            val contactsPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
            val groups by produceState<List<GroupInfo>?>(null, granted) { value = if (granted) vm.groups() else emptyList() }
            GroupPicker(
                groups = groups, permissionMissing = !granted, onAllow = { contactsPermission.launch(LivePermissions.contacts) },
                onPick = { group ->
                    groupPickerOpen = false
                    vm.addGroup(group, addPage)
                },
                onDismiss = { groupPickerOpen = false },
            )
        }

        noteEditingId?.let { id ->
            val note = items.firstOrNull { it.id == id }
            if (note != null) {
                NoteDialog(
                    initial = note.note.orEmpty(),
                    onDone = { text ->
                        vm.saveNote(id, text)
                        noteEditingId = null
                    },
                    onCancel = { noteEditingId = null },
                )
            }
        }

        if (cityPickerOpen) {
            CityPicker(
                onPick = { city ->
                    cityPickerOpen = false
                    vm.addWorldClock(city, addPage)
                },
                onDismiss = { cityPickerOpen = false },
            )
        }

        counterEditingId?.let { id ->
            val counter = items.firstOrNull { it.id == id }
            if (counter != null) {
                CounterDialog(
                    initialName = counter.label,
                    count = (counter.data as? TileData.Counter)?.count ?: 0,
                    onDone = { name, reset ->
                        vm.saveCounter(id, name, reset)
                        counterEditingId = null
                    },
                    onCancel = { counterEditingId = null },
                )
            }
        }

        checklistEditingId?.let { id ->
            val list = items.firstOrNull { it.id == id }
            if (list != null) {
                ChecklistDialog(
                    initialRaw = (list.data as? TileData.Checklist)?.raw.orEmpty(),
                    onChange = { raw -> vm.saveChecklist(id, raw) },
                    onClose = { checklistEditingId = null },
                )
            }
        }

        if (countdownNew || countdownEditingId != null) {
            val existing = countdownEditingId?.let { id -> items.firstOrNull { it.id == id } }
            CountdownDialog(
                initialTitle = existing?.label.orEmpty().takeIf { existing != null } ?: "",
                initialDay = existing?.targetDay ?: Countdown.defaultDay(java.time.LocalDate.now()),
                onDone = { title, day ->
                    val editing = countdownEditingId
                    if (editing != null) vm.saveCountdown(editing, title, day) else vm.addCountdown(title, day, addPage)
                    countdownEditingId = null
                    countdownNew = false
                },
                onCancel = {
                    countdownEditingId = null
                    countdownNew = false
                },
            )
        }

        if (widgetPickerOpen) {
            WidgetPicker(
                providers = remember { vm.widgets.providers() },
                onPick = { provider ->
                    widgetPickerOpen = false
                    addWidget(provider)
                },
            )
        }

        if (siteDialogOpen) {
            SiteDialog(
                onDone = { title, address ->
                    val accepted = vm.pinSite(title, address, addPage)
                    if (accepted) siteDialogOpen = false
                    accepted
                },
                onCancel = { siteDialogOpen = false },
            )
        }
}
