package com.metrowin.launcher

import com.metrowin.launcher.ui.actioncenter.MetroToast
import com.metrowin.launcher.ui.start.ChoiceSheet
import com.metrowin.launcher.ui.start.Choice
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.metrowin.launcher.data.AppLists
import com.metrowin.launcher.data.AppLabels
import com.metrowin.launcher.data.LauncherSettings
import com.metrowin.launcher.data.StartEntries
import com.metrowin.launcher.data.StartPages
import com.metrowin.launcher.model.AppItem
import com.metrowin.launcher.model.Folder
import com.metrowin.launcher.model.StartEntry
import com.metrowin.launcher.ui.start.FolderNameDialog

/** Which dialog that names or sorts a folder, a tile, an app or a list of apps is open. */
internal class NameDialogState {
    var groupEditingId by mutableStateOf<String?>(null)
    var renamingFolderId by mutableStateOf<String?>(null)
    var renamingAppId by mutableStateOf<String?>(null)
    var newListOpen by mutableStateOf(false)
    var listMenuFor by mutableStateOf<String?>(null)
    var renamingListId by mutableStateOf<String?>(null)
    var addToListFor by mutableStateOf<AppItem?>(null)
}

/** The dialogs that name a group of tiles, a folder, an app or a list of apps, and the choices of a list. [onListCreated] gets the id of a new list. */
@Composable
internal fun NameDialogState.Dialogs(
    vm: LauncherViewModel,
    pages: List<List<StartEntry>>,
    items: List<AppItem>,
    settings: LauncherSettings,
    onListCreated: (String) -> Unit,
) {
        val appContext = LocalContext.current
        groupEditingId?.let { id ->
            val entry = StartPages.all(pages).firstOrNull { it.id == id }
            if (entry != null) {
                FolderNameDialog(
                    initial = entry.group,
                    title = R.string.group_title_prompt,
                    maxLength = StartEntries.MAX_GROUP,
                    onDone = { title ->
                        vm.setGroupTitle(id, title)
                        groupEditingId = null
                    },
                    onCancel = { groupEditingId = null },
                )
            }
        }

        renamingFolderId?.let { id ->
            val folder = StartPages.all(pages).firstOrNull { it.id == id } as? Folder
            if (folder != null) {
                FolderNameDialog(
                    initial = folder.name,
                    onDone = { name ->
                        vm.renameFolder(id, name)
                        renamingFolderId = null
                    },
                    onCancel = { renamingFolderId = null },
                )
            }
        }

        renamingAppId?.let { id ->
            val app = items.firstOrNull { it.id == id }
            if (app != null) {
                FolderNameDialog(
                    initial = app.label,
                    title = R.string.app_rename_title,
                    maxLength = AppLabels.MAX,
                    onDone = { name ->
                        // An empty name brings the original one back.
                        val clean = AppLabels.clean(name)
                        vm.updateSettings { s -> s.copy(appLabels = if (clean == null) s.appLabels - id else s.appLabels + (id to clean)) }
                        renamingAppId = null
                    },
                    onCancel = { renamingAppId = null },
                )
            }
        }

        if (newListOpen) {
            FolderNameDialog(
                initial = "",
                title = R.string.list_new_prompt,
                maxLength = AppLists.MAX_NAME,
                onDone = { name ->
                    val id = "u" + System.currentTimeMillis().toString(36)
                    vm.updateSettings { it.copy(appLists = AppLists.create(it.appLists, name, id)) }
                    if (AppLists.clean(name) != null) onListCreated(id)
                    newListOpen = false
                },
                onCancel = { newListOpen = false },
            )
        }

        listMenuFor?.let { id ->
            ChoiceSheet(
                title = stringResource(R.string.list_options),
                choices = listOf(
                    Choice("rename", null, stringResource(R.string.list_rename)),
                    Choice("delete", null, stringResource(R.string.list_delete)),
                ),
                onPick = { choice ->
                    listMenuFor = null
                    if (choice.id == "rename") {
                        renamingListId = id
                    } else {
                        // The apps stay where they are: only the chip goes, and a deleted list does not come back.
                        vm.updateSettings { it.copy(appLists = AppLists.delete(it.appLists, id)) }
                        MetroToast.show(appContext, R.string.list_deleted)
                    }
                },
                onDismiss = { listMenuFor = null },
                tagPrefix = "listmenu",
            )
        }

        renamingListId?.let { id ->
            val list = settings.appLists.firstOrNull { it.id == id }
            if (list != null) {
                FolderNameDialog(
                    initial = list.name.ifBlank { list.category?.let { stringResource(it.label) }.orEmpty() },
                    title = R.string.list_rename_prompt,
                    maxLength = AppLists.MAX_NAME,
                    onDone = { name ->
                        vm.updateSettings { it.copy(appLists = AppLists.rename(it.appLists, id, name)) }
                        renamingListId = null
                    },
                    onCancel = { renamingListId = null },
                )
            }
        }

        addToListFor?.let { item ->
            ChoiceSheet(
                title = stringResource(R.string.list_add_title),
                choices = settings.appLists.map { list ->
                    val name = list.name.ifBlank { list.category?.let { stringResource(it.label) }.orEmpty() }
                    Choice(list.id, null, (if (item.id in list.items) "✓ " else "") + name)
                },
                onPick = { choice ->
                    vm.updateSettings { it.copy(appLists = AppLists.toggle(it.appLists, choice.id, item.id)) }
                    addToListFor = null
                },
                onDismiss = { addToListFor = null },
                tagPrefix = "addtolist",
            )
        }
}
