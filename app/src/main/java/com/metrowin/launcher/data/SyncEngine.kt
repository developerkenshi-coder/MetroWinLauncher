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

    /**
     * Both changed: the two are merged (see [SyncMerge]) when the layout of the last sync is known; otherwise, or when the same thing
     * changed on both phones, this phone's layout wins and the other one is kept as a separate file, so nothing is lost.
     */
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

/** The copies of the other phone's layout that a sync with a conflict kept in the folder (see [SyncAction.UploadKeepingTheOther]). */
object SyncConflicts {
    /** The copies among [names], the newest first. */
    fun newestFirst(names: Collection<String>): List<String> =
        names.filter { stamp(it) != null }.distinct().sortedByDescending { stamp(it) }

    /** When the other layout was written (epoch milliseconds), or null when [name] is not the name of a copy. */
    fun stamp(name: String): Long? =
        if (name.startsWith(SyncEngine.CONFLICT_PREFIX) && name.endsWith(".bin")) name.removePrefix(SyncEngine.CONFLICT_PREFIX).removeSuffix(".bin").toLongOrNull() else null

    /** The layout file inside a copy, or null when the passphrase is wrong or the file is not a sync file. */
    fun layoutOf(blob: ByteArray, passphrase: String): String? =
        SyncCrypto.decrypt(blob, passphrase)?.let { runCatching { JSONObject(it.decodeToString()).optString("layout") }.getOrNull() }?.ifEmpty { null }
}

/** What this phone knows about the last sync, and what it is now. */
data class SyncLocal(
    /** The layout as a [LayoutBackupCodec] file (with its time set to 0, so the same layout always reads the same). */
    val layoutJson: String,
    val passphrase: String,
    val deviceId: String,
    val lastHash: String,
    val lastStamp: Long,
    /** The layout both phones agreed on at the last sync, or null when this phone does not have it (an older version synced last). */
    val baseJson: String? = null,
)

sealed interface SyncOutcome {
    data object Nothing : SyncOutcome

    /** This phone's layout is in the folder now; remember [hash] and [stamp]. */
    data class Uploaded(val hash: String, val stamp: Long, val keptTheOtherAs: String? = null) : SyncOutcome

    /** The folder's layout is to be put in place; remember [stamp] (and the hash of what results). */
    data class Applied(val layoutJson: String, val stamp: Long) : SyncOutcome

    /** Both phones had changed and were merged; the folder holds [layoutJson] now: put it in place and remember [stamp]. [kept] items were saved from a deletion. */
    data class Merged(val layoutJson: String, val stamp: Long, val kept: Int, val keptTheOtherAs: String? = null) : SyncOutcome

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
                val merged = local.baseJson?.let { SyncMerge.merge(it, local.layoutJson, remoteLayout!!) }
                // Without a merge, or when the same thing changed on both phones, the other phone's file is kept aside.
                val copy = if (merged == null || merged.lost) "$CONFLICT_PREFIX$remoteStamp.bin" else null
                if (copy != null && !storage.write(copy, blob!!)) return SyncOutcome.Failed
                if (merged == null) return upload(local, localHash, keptTheOtherAs = copy)
                when (val up = upload(local.copy(layoutJson = merged.json), hash(merged.json), keptTheOtherAs = copy)) {
                    is SyncOutcome.Uploaded -> SyncOutcome.Merged(merged.json, up.stamp, merged.kept, copy)
                    else -> up
                }
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
