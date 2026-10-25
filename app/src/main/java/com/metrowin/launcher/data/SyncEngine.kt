package com.metrowin.launcher.data

import org.json.JSONObject
import java.security.MessageDigest

/** Where the sync file lives: a folder with files by name. [SafFolder] is one; tests use a map. */
interface SyncStorage {
    fun read(name: String): ByteArray?
    fun write(name: String, bytes: ByteArray): Boolean
}

/** What a sync does, from what changed on each side since the last one. Pure, so it can be tested. */
enum class SyncAction {
    /** Neither side changed. */
    Nothing,

    /** Only this phone changed, or there is no sync file yet: put this phone's layout in the folder. */
    Upload,

    /** Only the other side changed: take the folder's layout. */
    Apply,

    /** Both changed: this phone's layout wins and the other one is kept as a separate file, so nothing is lost. */
    UploadKeepingTheOther,
}

object SyncPlanner {
    /**
     * [localHash] is a fingerprint of this phone's layout now and [lastHash] of the one at the last sync (empty if
     * there never was one); [remoteStamp] is when the folder's file was written and [lastStamp] the stamp seen at the
     * last sync. A phone that never synced takes what is in the folder: that is how a second phone joins.
     */
    fun decide(remoteExists: Boolean, localHash: String, lastHash: String, remoteStamp: Long, lastStamp: Long): SyncAction {
        if (!remoteExists) return SyncAction.Upload
        if (lastHash.isEmpty()) return SyncAction.Apply
        val localChanged = localHash != lastHash
        val remoteChanged = remoteStamp != lastStamp
        return when {
            localChanged && remoteChanged -> SyncAction.UploadKeepingTheOther
            localChanged -> SyncAction.Upload
            remoteChanged -> SyncAction.Apply
            else -> SyncAction.Nothing
        }
    }
}

/** What this phone knows about the last sync, and what it is now. */
data class SyncLocal(
    /** The layout as a [LayoutBackupCodec] file (with its time set to 0, so the same layout always reads the same). */
    val layoutJson: String,
    val passphrase: String,
    val deviceId: String,
    val lastHash: String,
    val lastStamp: Long,
)

sealed interface SyncOutcome {
    data object Nothing : SyncOutcome

    /** This phone's layout is in the folder now; remember [hash] and [stamp]. */
    data class Uploaded(val hash: String, val stamp: Long, val keptTheOtherAs: String? = null) : SyncOutcome

    /** The folder's layout is to be put in place; remember [stamp] (and the hash of what results). */
    data class Applied(val layoutJson: String, val stamp: Long) : SyncOutcome

    data object WrongPassphrase : SyncOutcome
    data object NotASyncFile : SyncOutcome
    data object Failed : SyncOutcome
}

/** One sync: reads the file in the folder, decides, and writes if it has to. */
class SyncEngine(
    private val storage: SyncStorage,
    private val now: () -> Long = System::currentTimeMillis,
    private val iterations: Int = SyncCrypto.ITERATIONS,
) {
    fun sync(local: SyncLocal): SyncOutcome {
        val localHash = hash(local.layoutJson)
        val blob = storage.read(FILE)
        var remoteStamp = 0L
        var remoteLayout: String? = null
        if (blob != null) {
            if (!SyncCrypto.isSyncFile(blob)) return SyncOutcome.NotASyncFile
            val plain = SyncCrypto.decrypt(blob, local.passphrase) ?: return SyncOutcome.WrongPassphrase
            val payload = runCatching { JSONObject(plain.decodeToString()) }.getOrNull() ?: return SyncOutcome.NotASyncFile
            remoteStamp = payload.optLong("stamp")
            remoteLayout = payload.optString("layout").ifEmpty { return SyncOutcome.NotASyncFile }
        }
        return when (SyncPlanner.decide(blob != null, localHash, local.lastHash, remoteStamp, local.lastStamp)) {
            SyncAction.Nothing -> SyncOutcome.Nothing
            SyncAction.Apply -> SyncOutcome.Applied(remoteLayout!!, remoteStamp)
            SyncAction.Upload -> upload(local, localHash, keptTheOtherAs = null)
            SyncAction.UploadKeepingTheOther -> {
                val copy = "$CONFLICT_PREFIX$remoteStamp.bin"
                if (!storage.write(copy, blob!!)) return SyncOutcome.Failed
                upload(local, localHash, keptTheOtherAs = copy)
            }
        }
    }

    private fun upload(local: SyncLocal, localHash: String, keptTheOtherAs: String?): SyncOutcome {
        val stamp = now()
        val payload = JSONObject().apply {
            put("deviceId", local.deviceId)
            put("stamp", stamp)
            put("layout", local.layoutJson)
        }.toString()
        val written = storage.write(FILE, SyncCrypto.encrypt(payload.toByteArray(), local.passphrase, iterations))
        return if (written) SyncOutcome.Uploaded(localHash, stamp, keptTheOtherAs) else SyncOutcome.Failed
    }

    companion object {
        const val FILE = "metrowin-sync.bin"
        const val CONFLICT_PREFIX = "metrowin-sync-other-"

        /** A fingerprint of a layout: it is what tells whether this phone changed since the last sync. */
        fun hash(layoutJson: String): String =
            MessageDigest.getInstance("SHA-256").digest(layoutJson.toByteArray()).joinToString("") { "%02x".format(it) }
    }
}
