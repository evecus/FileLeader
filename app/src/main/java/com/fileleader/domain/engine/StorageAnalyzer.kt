package com.fileleader.domain.engine

import android.content.Context
import android.graphics.Color
import android.os.Environment
import android.os.StatFs
import com.fileleader.data.model.StorageCategory
import com.fileleader.data.model.StorageInfo
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StorageAnalyzer @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val rootDir = Environment.getExternalStorageDirectory()

    suspend fun analyze(): StorageInfo = withContext(Dispatchers.IO) {
        val stat = StatFs(rootDir.absolutePath)
        val total = stat.totalBytes
        val free  = stat.availableBytes
        val used  = total - free

        val categories = mutableListOf<StorageCategory>()

        // Walk and categorize
        val typeBytes = mutableMapOf<String, Long>()
        rootDir.walkTopDown()
            .onEnter { it.canRead() }
            .filter { it.isFile }
            .forEach { file ->
                val type = getCategory(file.extension.lowercase())
                typeBytes[type] = (typeBytes[type] ?: 0L) + file.length()
            }

        val colors = mapOf(
            "图片"   to Color.parseColor("#4CAF50"),
            "视频"   to Color.parseColor("#2196F3"),
            "音频"   to Color.parseColor("#9C27B0"),
            "文档"   to Color.parseColor("#FF9800"),
            "APK"    to Color.parseColor("#F44336"),
            "压缩包" to Color.parseColor("#00BCD4"),
            "其他"   to Color.parseColor("#9E9E9E")
        )
        val icons = mapOf(
            "图片" to "🖼", "视频" to "🎬", "音频" to "🎵",
            "文档" to "📄", "APK" to "📱", "压缩包" to "📦", "其他" to "📂"
        )

        typeBytes.entries
            .sortedByDescending { it.value }
            .forEach { (type, bytes) ->
                categories.add(
                    StorageCategory(
                        name = type,
                        bytes = bytes,
                        color = colors[type] ?: Color.GRAY,
                        icon = icons[type] ?: "📂"
                    )
                )
            }

        StorageInfo(
            totalBytes = total,
            usedBytes = used,
            freeBytes = free,
            categories = categories
        )
    }

    /**
     * Get top N largest files
     */
    suspend fun getLargeFiles(limit: Int = 50): List<Pair<File, Long>> = withContext(Dispatchers.IO) {
        rootDir.walkTopDown()
            .onEnter { it.canRead() && !it.name.startsWith(".") }
            .filter { it.isFile }
            .map { it to it.length() }
            .sortedByDescending { it.second }
            .take(limit)
            .toList()
    }

    private fun getCategory(ext: String): String = when (ext) {
        "jpg","jpeg","png","gif","webp","heic","heif","bmp","raw","cr2","nef" -> "图片"
        "mp4","mkv","avi","mov","wmv","flv","ts","m4v","3gp" -> "视频"
        "mp3","flac","aac","ogg","wav","m4a","opus","wma" -> "音频"
        "pdf","doc","docx","xls","xlsx","ppt","pptx","txt","csv","epub" -> "文档"
        "apk","apks","xapk" -> "APK"
        "zip","rar","7z","tar","gz","bz2","xz" -> "压缩包"
        else -> "其他"
    }
}
