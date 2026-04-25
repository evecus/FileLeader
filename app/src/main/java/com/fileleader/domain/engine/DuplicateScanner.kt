package com.fileleader.domain.engine

import android.content.ContentResolver
import android.content.Context
import android.database.Cursor
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.fileleader.data.model.DuplicateFile
import com.fileleader.data.model.DuplicateGroup
import com.fileleader.data.model.ScanPhase
import com.fileleader.data.model.ScanProgress
import com.fileleader.util.FileUtils
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DuplicateScanner @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val contentResolver: ContentResolver = context.contentResolver

    // Min file size to consider (ignore tiny files < 10KB to reduce noise)
    private val minFileSize = 10 * 1024L

    fun scan(): Flow<Pair<ScanProgress, List<DuplicateGroup>>> = flow {
        emit(ScanProgress(ScanPhase.SCANNING_DUPLICATES, 0, 100, "收集文件列表…") to emptyList())

        // Step 1: Collect all files via MediaStore
        val allFiles = collectFilesFromMediaStore()
        if (allFiles.isEmpty()) {
            emit(ScanProgress(ScanPhase.DONE, 100, 100, "", 0) to emptyList())
            return@flow
        }

        emit(ScanProgress(ScanPhase.SCANNING_DUPLICATES, 10, 100, "按大小分组…") to emptyList())

        // Step 2: Group by size (first pass - fast, no IO)
        val sizeGroups = allFiles
            .filter { it.second >= minFileSize }
            .groupBy { it.second }
            .filter { it.value.size >= 2 }

        val candidates = sizeGroups.values.flatten()
        val total = candidates.size
        if (total == 0) {
            emit(ScanProgress(ScanPhase.DONE, 100, 100, "", 0) to emptyList())
            return@flow
        }

        emit(ScanProgress(ScanPhase.SCANNING_DUPLICATES, 20, 100, "计算文件指纹…", 0) to emptyList())

        // Step 3: Partial hash per size-group (second pass)
        var processed = 0
        val partialHashGroups = mutableMapOf<String, MutableList<Pair<String, Long>>>()

        coroutineScope {
            sizeGroups.values.map { group ->
                async(Dispatchers.IO) {
                    group.mapNotNull { (path, size) ->
                        if (!currentCoroutineContext().isActive) return@mapNotNull null
                        try {
                            val f = File(path)
                            if (!f.exists() || !f.canRead()) return@mapNotNull null
                            val hash = FileUtils.partialHash(f)
                            Triple(hash, path, size)
                        } catch (e: Exception) {
                            null
                        }
                    }
                }
            }.awaitAll().flatten().forEach { (hash, path, size) ->
                partialHashGroups.getOrPut(hash) { mutableListOf() }.add(path to size)
                processed++
            }
        }

        // Step 4: Full hash only on partial-hash duplicates
        val partialDupCandidates = partialHashGroups.filter { it.value.size >= 2 }
        val fullHashGroups = mutableMapOf<String, MutableList<Pair<String, Long>>>()
        var step4Done = 0
        val step4Total = partialDupCandidates.values.sumOf { it.size }

        emit(ScanProgress(ScanPhase.SCANNING_DUPLICATES, 60, 100, "验证重复文件…", 0) to emptyList())

        coroutineScope {
            partialDupCandidates.values.map { group ->
                async(Dispatchers.IO) {
                    group.mapNotNull { (path, size) ->
                        if (!currentCoroutineContext().isActive) return@mapNotNull null
                        try {
                            val f = File(path)
                            if (!f.exists() || !f.canRead()) return@mapNotNull null
                            val hash = FileUtils.md5(f)
                            Triple(hash, path, size)
                        } catch (e: Exception) {
                            null
                        }
                    }
                }
            }.awaitAll().flatten().forEach { (hash, path, size) ->
                fullHashGroups.getOrPut(hash) { mutableListOf() }.add(path to size)
                step4Done++
                if (step4Done % 10 == 0) {
                    val pct = 60 + (step4Done * 35 / step4Total.coerceAtLeast(1))
                    // Emit progress (approximate)
                }
            }
        }

        // Step 5: Build DuplicateGroup list
        val groups = fullHashGroups
            .filter { it.value.size >= 2 }
            .map { (hash, pathSizes) ->
                val files = pathSizes.map { (path, size) ->
                    val f = File(path)
                    DuplicateFile(
                        path = path,
                        name = f.name,
                        size = size,
                        lastModified = f.lastModified(),
                        mimeType = FileUtils.getMimeType(path),
                        isSelected = false,
                        isKeepCandidate = false
                    )
                }.sortedByDescending { it.lastModified }

                // Mark the newest file as "keep candidate"
                val filesWithKeep = files.mapIndexed { idx, file ->
                    file.copy(isKeepCandidate = idx == 0)
                }

                DuplicateGroup(
                    hash = hash,
                    size = pathSizes.first().second,
                    files = filesWithKeep,
                    wastedBytes = pathSizes.first().second * (pathSizes.size - 1)
                )
            }
            .sortedByDescending { it.wastedBytes }

        emit(ScanProgress(ScanPhase.DONE, 100, 100, "", groups.size) to groups)
    }.flowOn(Dispatchers.IO)

    /**
     * Collect all user files from MediaStore (images, video, audio, downloads)
     * Returns list of (path, size)
     */
    private fun collectFilesFromMediaStore(): List<Pair<String, Long>> {
        val results = mutableListOf<Pair<String, Long>>()

        val uris = mutableListOf(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            uris.add(MediaStore.Downloads.EXTERNAL_CONTENT_URI)
        }

        val projection = arrayOf(
            MediaStore.MediaColumns.DATA,
            MediaStore.MediaColumns.SIZE
        )

        uris.forEach { uri ->
            try {
                val cursor: Cursor? = contentResolver.query(
                    uri, projection, null, null, null
                )
                cursor?.use {
                    val dataCol = it.getColumnIndexOrThrow(MediaStore.MediaColumns.DATA)
                    val sizeCol = it.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
                    while (it.moveToNext()) {
                        val path = it.getString(dataCol) ?: continue
                        val size = it.getLong(sizeCol)
                        if (size >= minFileSize) results.add(path to size)
                    }
                }
            } catch (e: Exception) {
                // Ignore inaccessible URIs
            }
        }

        // Also walk external storage directly for non-media files
        val extRoot = Environment.getExternalStorageDirectory()
        extRoot.walkTopDown()
            .onEnter { it.canRead() && !it.name.startsWith(".") }
            .filter { it.isFile && it.length() >= minFileSize }
            .filter { f ->
                // Only add files not already covered by MediaStore
                val ext = f.extension.lowercase()
                ext in setOf("pdf", "doc", "docx", "xls", "xlsx", "zip", "rar", "7z", "apk")
            }
            .forEach { results.add(it.absolutePath to it.length()) }

        return results.distinctBy { it.first }
    }
}
