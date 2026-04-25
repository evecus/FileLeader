package com.fileleader.util

import java.io.File
import java.security.MessageDigest
import java.text.DecimalFormat

object FileUtils {

    // ===== Junk extensions =====
    val JUNK_EXTENSIONS = setOf(
        ".tmp", ".temp", ".bak", ".bk", ".old",
        ".log", ".logs", ".dmp",
        ".cache", ".crdownload", ".part",
        ".DS_Store", ".nomedia"
    )

    val JUNK_FOLDER_NAMES = setOf(
        ".thumbnails", "thumbnails", "cache", ".cache",
        "temp", "tmp", "log", "logs", ".trash", "Trash",
        "lost+found", ".lost+found"
    )

    // ===== Size formatting =====
    fun formatSize(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt()
        return DecimalFormat("#,##0.##").format(
            bytes / Math.pow(1024.0, digitGroups.toDouble())
        ) + " " + units[digitGroups.coerceAtMost(units.size - 1)]
    }

    // ===== MD5 hash =====
    fun md5(file: File): String {
        val digest = MessageDigest.getInstance("MD5")
        file.inputStream().use { stream ->
            val buffer = ByteArray(8192)
            var bytes = stream.read(buffer)
            while (bytes > 0) {
                digest.update(buffer, 0, bytes)
                bytes = stream.read(buffer)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    // Fast partial hash (first + last 64KB)
    fun partialHash(file: File): String {
        val digest = MessageDigest.getInstance("MD5")
        val chunkSize = 65536 // 64KB
        file.inputStream().use { stream ->
            val buffer = ByteArray(chunkSize)
            val read = stream.read(buffer)
            if (read > 0) digest.update(buffer, 0, read)
        }
        if (file.length() > chunkSize * 2) {
            file.inputStream().use { stream ->
                val skip = file.length() - chunkSize
                stream.skip(skip)
                val buffer = ByteArray(chunkSize)
                val read = stream.read(buffer)
                if (read > 0) digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    // ===== Directory scanning =====
    fun isSystemPath(path: String): Boolean {
        val systemPaths = listOf(
            "/proc", "/sys", "/dev", "/acct", "/charger",
            "/data/app", "/data/data", "/data/dalvik-cache",
            "/data/system", "/data/misc",
            "/vendor", "/system", "/oem"
        )
        return systemPaths.any { path.startsWith(it) }
    }

    fun isEmptyDir(file: File): Boolean {
        if (!file.isDirectory) return false
        val children = file.listFiles() ?: return true
        return children.isEmpty()
    }

    fun getDirSize(dir: File): Long {
        var size = 0L
        dir.walkTopDown().forEach { file ->
            if (file.isFile) size += file.length()
        }
        return size
    }

    // ===== Trash =====
    fun getTrashDir(externalStoragePath: String): File {
        return File(externalStoragePath, ".Trash").also { it.mkdirs() }
    }

    fun moveToTrash(file: File, trashDir: File): File? {
        return try {
            val trashFile = File(trashDir, "${System.currentTimeMillis()}_${file.name}")
            if (file.renameTo(trashFile)) trashFile else null
        } catch (e: Exception) {
            null
        }
    }

    // ===== MIME type detection =====
    fun getMimeType(path: String): String {
        val ext = path.substringAfterLast('.', "").lowercase()
        return when (ext) {
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "gif" -> "image/gif"
            "webp" -> "image/webp"
            "heic", "heif" -> "image/heic"
            "mp4", "m4v" -> "video/mp4"
            "mkv" -> "video/x-matroska"
            "avi" -> "video/avi"
            "mov" -> "video/quicktime"
            "mp3" -> "audio/mpeg"
            "flac" -> "audio/flac"
            "aac" -> "audio/aac"
            "ogg" -> "audio/ogg"
            "wav" -> "audio/wav"
            "pdf" -> "application/pdf"
            "doc", "docx" -> "application/msword"
            "xls", "xlsx" -> "application/vnd.ms-excel"
            "ppt", "pptx" -> "application/vnd.ms-powerpoint"
            "zip" -> "application/zip"
            "rar" -> "application/x-rar-compressed"
            "7z" -> "application/x-7z-compressed"
            "apk" -> "application/vnd.android.package-archive"
            "txt" -> "text/plain"
            else -> "application/octet-stream"
        }
    }
}
