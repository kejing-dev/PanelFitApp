package io.embodit.panelfit.data

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Environment
import android.provider.MediaStore
import io.embodit.panelfit.domain.usecase.ImportFile
import io.embodit.panelfit.platform.PfLog
import java.io.File

/** Where a save ended up (S09). */
sealed class SaveResult {
    data class Public(val path: String) : SaveResult()

    data class Private(val dir: String, val path: String) : SaveResult()

    data object None : SaveResult()
}

/**
 * Spec card files and imported pictures (design `rules.export`, `content.templates` T8).
 * Saving: public Download/PanelFit/ through MediaStore (no permission for the app's own files), else the app's own
 * Android/data/.../files/Documents/PanelFit/. Importing: only the app's own files/import/ (scoped storage).
 */
class PfFiles(private val context: Context) {
    /** Test hook (debug `saveFail`): "public" fails the public folder, "all" fails both. */
    @Volatile var failMode: String? = null

    val importDir: File
        get() = File(context.getExternalFilesDir(null), "import").also { runCatching { it.mkdirs() } }

    private val privateDir: File
        get() = File(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), "PanelFit")

    /** Writes [files] (name -> text). Names that exist already get -2, -3 (the caller passes a base without extension). */
    fun save(base: String, json: String, md: String): SaveResult {
        val fail = failMode
        if (fail != "public" && fail != "all") {
            val ok = runCatching { savePublic(base, json, md) }.onFailure { PfLog.w("PF-SAVE public failed: $it") }.getOrNull()
            if (ok != null) return SaveResult.Public(ok)
        }
        if (fail != "all") {
            val ok = runCatching { savePrivate(base, json, md) }.onFailure { PfLog.w("PF-SAVE private failed: $it") }.getOrNull()
            if (ok != null) return SaveResult.Private(privateDir.absolutePath, ok)
        }
        return SaveResult.None
    }

    private fun savePublic(base: String, json: String, md: String): String {
        val resolver = context.contentResolver
        val rel = Environment.DIRECTORY_DOWNLOADS + "/PanelFit/"
        val name = uniqueName(base) { n -> existsPublic(rel, "$n.json") }
        for ((ext, mime, text) in listOf(Triple("json", "application/json", json), Triple("md", "text/markdown", md))) {
            val values =
                ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, "$name.$ext")
                    put(MediaStore.MediaColumns.MIME_TYPE, mime)
                    put(MediaStore.MediaColumns.RELATIVE_PATH, rel)
                }
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: error("insert returned null")
            resolver.openOutputStream(uri)?.use { it.write(text.toByteArray(Charsets.UTF_8)) } ?: error("no output stream")
        }
        return "Download/PanelFit/$name.json"
    }

    private fun existsPublic(rel: String, display: String): Boolean =
        runCatching {
            context.contentResolver.query(
                MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                arrayOf(MediaStore.MediaColumns._ID),
                "${MediaStore.MediaColumns.RELATIVE_PATH}=? AND ${MediaStore.MediaColumns.DISPLAY_NAME}=?",
                arrayOf(rel, display),
                null,
            )?.use { it.count > 0 } ?: false
        }.getOrDefault(false)

    private fun savePrivate(base: String, json: String, md: String): String {
        val dir = privateDir
        if (!dir.exists() && !dir.mkdirs()) error("mkdirs failed: $dir")
        val name = uniqueName(base) { n -> File(dir, "$n.json").exists() }
        File(dir, "$name.json").writeText(json, Charsets.UTF_8)
        File(dir, "$name.md").writeText(md, Charsets.UTF_8)
        return File(dir, "$name.json").absolutePath
    }

    private fun uniqueName(base: String, exists: (String) -> Boolean): String {
        if (!exists(base)) return base
        var n = 2
        while (exists("$base-$n") && n < 100) n++
        return "$base-$n"
    }

    /** Pictures in files/import/ with their pixel sizes (bounds only; a file that does not decode is listed as bad). */
    fun listImports(): List<ImportFile> {
        val files = importDir.listFiles()?.filter { it.isFile && it.extension.lowercase() in setOf("png", "jpg", "jpeg", "webp") }?.sortedBy { it.name } ?: return emptyList()
        return files.map { f -> bounds(f) }
    }

    fun bounds(f: File): ImportFile {
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        runCatching { BitmapFactory.decodeFile(f.absolutePath, o) }
        val ok = o.outWidth > 0 && o.outHeight > 0 && o.outWidth <= MAX_SIDE_PX && o.outHeight <= MAX_SIDE_PX
        return ImportFile(f.name, o.outWidth.coerceAtLeast(0), o.outHeight.coerceAtLeast(0), ok)
    }

    /** Decodes a picture for a specimen, downsampled so its longer side stays within [maxPx]. */
    fun decode(name: String, maxPx: Int = 1600): Bitmap? {
        val f = File(importDir, name)
        if (!f.isFile) return null
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(f.absolutePath, o)
        if (o.outWidth <= 0 || o.outHeight <= 0) return null
        var sample = 1
        while (maxOf(o.outWidth, o.outHeight) / sample > maxPx) sample *= 2
        return runCatching { BitmapFactory.decodeFile(f.absolutePath, BitmapFactory.Options().apply { inSampleSize = sample }) }.getOrNull()
    }

    private companion object {
        const val MAX_SIDE_PX = 8000
    }
}
