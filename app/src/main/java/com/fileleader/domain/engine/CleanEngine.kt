package com.fileleader.domain.engine

import android.os.Environment
import com.fileleader.data.db.TrashDao
import com.fileleader.data.model.JunkFile
import com.fileleader.data.model.PermissionMode
import com.fileleader.data.model.TrashEntry
import com.fileleader.util.FileUtils
import com.fileleader.util.PermissionManager
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

data class CleanResult(
    val success: Int,
    val failed: Int,
    val freedBytes: Long,
    val errors: List<String>
)

@Singleton
class CleanEngine @Inject constructor(
    private val permissionManager: PermissionManager,
    private val trashDao: TrashDao
) {
    private val trashDir: File by lazy {
        File(Environment.getExternalStorageDirectory(), ".Trash").also { it.mkdirs() }
    }

    fun cleanJunkFiles(files: List<JunkFile>): Flow<Pair<Int, CleanResult>> = flow {
        var success = 0
        var failed = 0
        var freed = 0L
        val errors = mutableListOf<String>()
        val trashEntries = mutableListOf<TrashEntry>()

        files.forEachIndexed { index, junk ->
            val file = File(junk.path)
            val result = when (permissionManager.getCurrentMode()) {
                PermissionMode.ROOT -> deleteWithRoot(file)
                PermissionMode.ADB -> deleteWithAdb(file)
                PermissionMode.NORMAL -> moveToTrash(file)
            }

            if (result) {
                success++
                freed += junk.size
                if (permissionManager.getCurrentMode() == PermissionMode.NORMAL) {
                    val trashPath = File(trashDir, "${System.currentTimeMillis()}_${file.name}")
                    trashEntries.add(
                        TrashEntry(
                            trashPath = trashPath.absolutePath,
                            originalPath = junk.path,
                            name = junk.name,
                            size = junk.size,
                            deletedAt = System.currentTimeMillis(),
                            type = "JUNK"
                        )
                    )
                }
            } else {
                failed++
                errors.add(junk.name)
            }

            emit(index + 1 to CleanResult(success, failed, freed, errors.toList()))
        }

        if (trashEntries.isNotEmpty()) {
            trashDao.insertAll(trashEntries)
        }
    }.flowOn(Dispatchers.IO)

    fun cleanDuplicateFiles(paths: List<String>): Flow<Pair<Int, CleanResult>> = flow {
        var success = 0; var failed = 0; var freed = 0L
        val errors = mutableListOf<String>()
        val trashEntries = mutableListOf<TrashEntry>()

        paths.forEachIndexed { index, path ->
            val file = File(path)
            val size = file.length()
            val result = when (permissionManager.getCurrentMode()) {
                PermissionMode.ROOT -> deleteWithRoot(file)
                PermissionMode.ADB  -> deleteWithAdb(file)
                PermissionMode.NORMAL -> moveToTrash(file)
            }
            if (result) {
                success++; freed += size
                if (permissionManager.getCurrentMode() == PermissionMode.NORMAL) {
                    val tp = File(trashDir, "${System.currentTimeMillis()}_${file.name}")
                    trashEntries.add(TrashEntry(tp.absolutePath, path, file.name, size, System.currentTimeMillis(), "DUPLICATE"))
                }
            } else {
                failed++; errors.add(file.name)
            }
            emit(index + 1 to CleanResult(success, failed, freed, errors.toList()))
        }

        if (trashEntries.isNotEmpty()) trashDao.insertAll(trashEntries)
    }.flowOn(Dispatchers.IO)

    suspend fun restoreFromTrash(entry: TrashEntry): Boolean = withContext(Dispatchers.IO) {
        try {
            val trashFile = File(entry.trashPath)
            val originalFile = File(entry.originalPath)
            originalFile.parentFile?.mkdirs()
            val ok = trashFile.renameTo(originalFile)
            if (ok) trashDao.delete(entry)
            ok
        } catch (e: Exception) {
            false
        }
    }

    suspend fun emptyTrash(): Long = withContext(Dispatchers.IO) {
        var freed = 0L
        trashDir.listFiles()?.forEach { f ->
            freed += f.length()
            f.deleteRecursively()
        }
        trashDao.deleteExpired(0L) // clear all
        freed
    }

    // ===== Root clean (system caches, dalvik, etc.) =====
    suspend fun cleanWithRoot(): CleanResult = withContext(Dispatchers.IO) {
        if (!permissionManager.isRootAvailable()) {
            return@withContext CleanResult(0, 0, 0, listOf("Root 未授权"))
        }

        val commands = listOf(
            "find /data/tombstones -type f -delete 2>/dev/null",
            "find /data/anr -type f -delete 2>/dev/null",
            "find /data/misc/logd -type f -delete 2>/dev/null",
            "rm -rf /cache/* 2>/dev/null",
            "find /data/local/tmp -type f -delete 2>/dev/null"
        )

        var freed = 0L
        val errors = mutableListOf<String>()
        var success = 0; var failed = 0

        commands.forEach { cmd ->
            val result = Shell.cmd(cmd).exec()
            if (result.isSuccess) success++ else { failed++; errors.add(cmd) }
        }

        // Estimate freed space (rough)
        freed = 0L
        CleanResult(success, failed, freed, errors)
    }

    // ===== Private helpers =====
    private fun moveToTrash(file: File): Boolean {
        return try {
            val dest = File(trashDir, "${System.currentTimeMillis()}_${file.name}")
            file.renameTo(dest)
        } catch (e: Exception) { false }
    }

    private fun deleteWithRoot(file: File): Boolean {
        return try {
            val result = Shell.cmd("rm -rf \"${file.absolutePath}\"").exec()
            result.isSuccess
        } catch (e: Exception) { false }
    }

    private fun deleteWithAdb(file: File): Boolean {
        // Shizuku gives us a process with ADB-level permission
        // For files we can access, use Java delete; for others, fall back to normal
        return try {
            if (file.canWrite()) moveToTrash(file)
            else {
                // ADB can delete via shell
                val pb = ProcessBuilder("sh", "-c", "rm -rf \"${file.absolutePath}\"")
                pb.start().waitFor() == 0
            }
        } catch (e: Exception) { false }
    }
}
