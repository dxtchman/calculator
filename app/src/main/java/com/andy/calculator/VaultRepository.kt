package com.andy.calculator

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.util.Base64
import android.util.LruCache
import android.util.Size
import android.webkit.MimeTypeMap
import androidx.compose.runtime.mutableStateListOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.UUID
import javax.crypto.SecretKey
import kotlin.math.max

data class VaultItem(
    val id: String,
    val name: String,
    val mime: String,
    val size: Long,
    val added: Long,
    val kind: String, // "media" or "file"
    val hasThumb: Boolean,
) {
    val isMedia get() = kind == "media"
    val isVideo get() = mime.startsWith("video/")
    val isImage get() = mime.startsWith("image/")
}

data class Note(val id: String, val title: String, val body: String, val updated: Long)

/**
 * Everything lives in the app's private storage, encrypted. State lists are only
 * changed on the main thread; heavy work runs on Dispatchers.IO.
 */
class VaultRepository(private val ctx: Context) {
    private val prefs = ctx.getSharedPreferences("vault", Context.MODE_PRIVATE)
    private val root = File(ctx.filesDir, "vault")
    private val itemDir = File(root, "items")
    private val thumbDir = File(root, "thumbs")
    private val indexFile = File(root, "index.bin")
    private val openDir = File(ctx.cacheDir, "open")
    private val thumbCache = LruCache<String, Bitmap>(120)

    @Volatile
    private var key: SecretKey? = null

    val items = mutableStateListOf<VaultItem>()
    val notes = mutableStateListOf<Note>()

    val isSetUp: Boolean get() = prefs.contains("wrapped")
    val usesPin: Boolean get() = prefs.getBoolean("pin", false)
    val isUnlocked: Boolean get() = key != null

    private fun b64(b: ByteArray) = Base64.encodeToString(b, Base64.NO_WRAP)
    private fun unb64(s: String) = Base64.decode(s, Base64.NO_WRAP)

    private fun wrapMasterKey(master: ByteArray, password: String, pin: Boolean) {
        val salt = VaultCrypto.random(16)
        val kek = VaultCrypto.deriveKey(password, salt)
        val wrapped = VaultCrypto.seal(kek, master)
        prefs.edit()
            .putString("salt", b64(salt))
            .putString("wrapped", b64(wrapped))
            .putBoolean("pin", pin)
            .commit()
    }

    suspend fun setup(password: String, pin: Boolean) {
        val master = withContext(Dispatchers.IO) {
            itemDir.mkdirs()
            thumbDir.mkdirs()
            val m = VaultCrypto.random(32)
            wrapMasterKey(m, password, pin)
            m
        }
        key = VaultCrypto.keyFrom(master)
        items.clear()
        notes.clear()
        saveIndex()
    }

    suspend fun unlock(password: String): Boolean {
        val k = withContext(Dispatchers.IO) {
            try {
                val salt = unb64(prefs.getString("salt", null)!!)
                val wrapped = unb64(prefs.getString("wrapped", null)!!)
                val kek = VaultCrypto.deriveKey(password, salt)
                VaultCrypto.keyFrom(VaultCrypto.open(kek, wrapped))
            } catch (e: Exception) {
                null
            }
        } ?: return false
        val loaded = withContext(Dispatchers.IO) { loadIndex(k) }
        key = k
        items.clear()
        items.addAll(loaded.first)
        notes.clear()
        notes.addAll(loaded.second)
        return true
    }

    suspend fun changePassword(newPassword: String, pin: Boolean): Boolean {
        val k = key ?: return false
        withContext(Dispatchers.IO) { wrapMasterKey(k.encoded, newPassword, pin) }
        return true
    }

    fun lock() {
        key = null
        items.clear()
        notes.clear()
        thumbCache.evictAll()
        openDir.deleteRecursively()
    }

    fun wipe() {
        lock()
        root.deleteRecursively()
        prefs.edit().clear().commit()
    }

    // ------------------------------------------------------------ index

    private fun loadIndex(k: SecretKey): Pair<List<VaultItem>, List<Note>> {
        if (!indexFile.exists()) return emptyList<VaultItem>() to emptyList()
        val json = JSONObject(String(VaultCrypto.open(k, indexFile.readBytes()), Charsets.UTF_8))
        val its = ArrayList<VaultItem>()
        val ia = json.optJSONArray("items") ?: JSONArray()
        for (i in 0 until ia.length()) {
            val o = ia.getJSONObject(i)
            its.add(
                VaultItem(
                    o.getString("id"), o.getString("name"), o.getString("mime"),
                    o.getLong("size"), o.getLong("added"), o.getString("kind"), o.getBoolean("thumb")
                )
            )
        }
        val ns = ArrayList<Note>()
        val na = json.optJSONArray("notes") ?: JSONArray()
        for (i in 0 until na.length()) {
            val o = na.getJSONObject(i)
            ns.add(Note(o.getString("id"), o.getString("title"), o.getString("body"), o.getLong("updated")))
        }
        return its to ns
    }

    /** Returns false if the folder got locked before the index could be written. */
    suspend fun saveIndex(): Boolean {
        val k = key ?: return false
        val ia = JSONArray()
        for (it in items.toList()) {
            ia.put(
                JSONObject().put("id", it.id).put("name", it.name).put("mime", it.mime)
                    .put("size", it.size).put("added", it.added).put("kind", it.kind).put("thumb", it.hasThumb)
            )
        }
        val na = JSONArray()
        for (n in notes.toList()) {
            na.put(JSONObject().put("id", n.id).put("title", n.title).put("body", n.body).put("updated", n.updated))
        }
        val data = JSONObject().put("items", ia).put("notes", na).toString().toByteArray(Charsets.UTF_8)
        return withContext(Dispatchers.IO) {
            try {
                root.mkdirs()
                val tmp = File(root, "index.tmp")
                tmp.writeBytes(VaultCrypto.seal(k, data))
                tmp.renameTo(indexFile)
            } catch (e: Exception) {
                false
            }
        }
    }

    // ------------------------------------------------------------ import

    suspend fun importUri(uri: Uri, kind: String): VaultItem? = withContext(Dispatchers.IO) {
        val k = key ?: return@withContext null
        val cr = ctx.contentResolver
        var name = "file"
        cr.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst() && !c.isNull(0)) name = c.getString(0)
        }
        val mime = cr.getType(uri) ?: guessMime(name)
        val id = UUID.randomUUID().toString()
        val out = File(itemDir, "$id.bin")
        try {
            itemDir.mkdirs()
            thumbDir.mkdirs()
            val input = cr.openInputStream(uri) ?: return@withContext null
            input.use { i -> FileOutputStream(out).use { o -> VaultCrypto.encrypt(k, i, o) } }
            var hasThumb = false
            if (mime.startsWith("image/") || mime.startsWith("video/")) {
                makeThumb(uri, mime)?.let { bmp ->
                    val bos = ByteArrayOutputStream()
                    bmp.compress(Bitmap.CompressFormat.JPEG, 85, bos)
                    File(thumbDir, "$id.bin").writeBytes(VaultCrypto.seal(k, bos.toByteArray()))
                    thumbCache.put(id, bmp)
                    hasThumb = true
                }
            }
            if (key == null) throw IllegalStateException("locked")
            VaultItem(id, name, mime, out.length() - 16, System.currentTimeMillis(), kind, hasThumb)
        } catch (e: Exception) {
            out.delete()
            File(thumbDir, "$id.bin").delete()
            null
        }
    }

    private fun guessMime(name: String): String {
        val ext = name.substringAfterLast('.', "").lowercase()
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "application/octet-stream"
    }

    private fun makeThumb(uri: Uri, mime: String): Bitmap? {
        try {
            return ctx.contentResolver.loadThumbnail(uri, Size(384, 384), null)
        } catch (_: Exception) {
        }
        if (mime.startsWith("image/")) {
            try {
                return ImageDecoder.decodeBitmap(ImageDecoder.createSource(ctx.contentResolver, uri)) { d, info, _ ->
                    d.setTargetSampleSize(max(1, max(info.size.width, info.size.height) / 384))
                    d.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                }
            } catch (_: Exception) {
            }
        }
        if (mime.startsWith("video/")) {
            val r = MediaMetadataRetriever()
            try {
                r.setDataSource(ctx, uri)
                val frame = r.getFrameAtTime(1_000_000) ?: return null
                val scale = 384f / max(frame.width, frame.height)
                return if (scale < 1f) {
                    Bitmap.createScaledBitmap(frame, (frame.width * scale).toInt(), (frame.height * scale).toInt(), true)
                } else frame
            } catch (_: Exception) {
            } finally {
                try { r.release() } catch (_: Exception) {}
            }
        }
        return null
    }

    // ------------------------------------------------------------ reading

    suspend fun thumbnail(item: VaultItem): Bitmap? {
        if (!item.hasThumb) return null
        thumbCache.get(item.id)?.let { return it }
        return withContext(Dispatchers.IO) {
            val k = key ?: return@withContext null
            try {
                val bytes = VaultCrypto.open(k, File(thumbDir, "${item.id}.bin").readBytes())
                android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.also { thumbCache.put(item.id, it) }
            } catch (e: Exception) {
                null
            }
        }
    }

    /** Decrypts into the app's cache so a viewer can open it. The cache is wiped on lock. */
    suspend fun decryptToCache(item: VaultItem): File? = withContext(Dispatchers.IO) {
        val k = key ?: return@withContext null
        val dir = File(openDir, item.id)
        val f = File(dir, item.name.replace('/', '_').ifBlank { "file" })
        if (f.exists() && f.length() == item.size) return@withContext f
        try {
            dir.mkdirs()
            FileInputStream(File(itemDir, "${item.id}.bin")).use { i ->
                FileOutputStream(f).use { o -> VaultCrypto.decrypt(k, i, o) }
            }
            f
        } catch (e: Exception) {
            f.delete()
            null
        }
    }

    suspend fun loadImage(item: VaultItem, maxDim: Int = 2560): Bitmap? {
        val f = decryptToCache(item) ?: return null
        return withContext(Dispatchers.IO) {
            try {
                ImageDecoder.decodeBitmap(ImageDecoder.createSource(f)) { d, info, _ ->
                    val m = max(info.size.width, info.size.height)
                    var s = 1
                    while (m / s > maxDim) s *= 2
                    d.setTargetSampleSize(s)
                }
            } catch (e: Exception) {
                null
            }
        }
    }

    // ------------------------------------------------------------ taking things out

    /** Copies an item back to Pictures/Movies/Download. */
    suspend fun export(item: VaultItem): Boolean = withContext(Dispatchers.IO) {
        val k = key ?: return@withContext false
        val cr = ctx.contentResolver
        val (collection, dir) = when {
            item.isImage -> MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY) to
                Environment.DIRECTORY_PICTURES + "/Restored"
            item.isVideo -> MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY) to
                Environment.DIRECTORY_MOVIES + "/Restored"
            else -> MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY) to
                Environment.DIRECTORY_DOWNLOADS
        }
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, item.name)
            put(MediaStore.MediaColumns.MIME_TYPE, item.mime)
            put(MediaStore.MediaColumns.RELATIVE_PATH, dir)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = try { cr.insert(collection, values) } catch (e: Exception) { null } ?: return@withContext false
        try {
            val out = cr.openOutputStream(uri) ?: throw IllegalStateException("no stream")
            out.use { o ->
                FileInputStream(File(itemDir, "${item.id}.bin")).use { i -> VaultCrypto.decrypt(k, i, o) }
            }
            values.clear()
            values.put(MediaStore.MediaColumns.IS_PENDING, 0)
            cr.update(uri, values, null, null)
            true
        } catch (e: Exception) {
            try { cr.delete(uri, null, null) } catch (_: Exception) {}
            false
        }
    }

    suspend fun deleteFiles(item: VaultItem) = withContext(Dispatchers.IO) {
        File(itemDir, "${item.id}.bin").delete()
        File(thumbDir, "${item.id}.bin").delete()
        File(openDir, item.id).deleteRecursively()
        thumbCache.remove(item.id)
    }
}
