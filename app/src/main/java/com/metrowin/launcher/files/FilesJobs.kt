package com.metrowin.launcher.files

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** What kind of work a [FileJob] is, for what is offered when it ends. */
enum class JobKind { Copy, Move, Delete, Other }

/** The work that is going on: its name, how far it is and whether the window that shows it is hidden. */
class FileJob(val title: String, val progress: Progress, val kind: JobKind, val conflict: Conflict?) {
    var hidden by mutableStateOf(false)
    val startedAt = System.currentTimeMillis()
}

/** A work that has ended; [id] changes with each, so the screen takes each once. */
class JobResult(val id: Int, val job: FileJob)

/**
 * The long work of the file manager (copying, moving, deleting, compressing, extracting) runs here, not in the screen: leaving the screen or
 * turning the phone does not stop it, and the screen that comes back finds it and its result. One work at a time.
 * shortcut: no foreground service, so Android may still stop it when the whole app is killed in the background; a service needs its own manifest line.
 */
object FilesJobs {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var counter = 0

    var current by mutableStateOf<FileJob?>(null)
        private set

    /** The last work that ended and was not taken yet. */
    var result by mutableStateOf<JobResult?>(null)
        private set

    /** Starts [work]; false (and nothing started) when another is going on. */
    fun start(title: String, total: Long, byCount: Boolean, kind: JobKind, conflict: Conflict?, work: (Progress) -> Unit): Boolean {
        if (current != null) return false
        val progress = Progress().also { it.totalBytes = total; it.byCount = byCount }
        val job = FileJob(title, progress, kind, conflict)
        current = job
        scope.launch {
            runCatching { work(progress) }
            counter++
            result = JobResult(counter, job)
            current = null
        }
        return true
    }

    /** Says the screen has dealt with [r]. */
    fun taken(r: JobResult) { if (result?.id == r.id) result = null }
}
