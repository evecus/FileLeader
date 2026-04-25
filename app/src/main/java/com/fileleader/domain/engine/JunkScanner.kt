package com.fileleader.domain.engine

import android.os.Environment
import com.fileleader.data.model.JunkFile
import com.fileleader.data.model.JunkType
import com.fileleader.data.model.ScanPhase
import com.fileleader.data.model.ScanProgress
import com.fileleader.util.FileUtils
import com.fileleader.util.PermissionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.currentCoroutineContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class JunkScanner @Inject constructor(
    private val permissionManager: PermissionManager
) {

    private val rootDir: File = Environment.getExternalStorageDirectory()

    // Extensions considered junk
    private val junkExtensions = setOf(
        "tmp", "temp", "bak", "bk", "old",
        "log", "dmp", "crdownload", "part"
    )

    // Folder names that are junk candidates
    private val junkFolderNames = setOf(
        ".thumbnails", "thumbnails", ".cache", "cache",
        "temp", "tmp", "log", "logs", "crash",
        "tombstones", "anr", "bugreports"
    )

    // System paths to skip (never touch these in normal mode)
    private val skipPaths = setOf(
        "/proc", "/sys", "/dev", "/acct",
        "/data/data", "/data/app", "/data/system",
        "/data/dalvik-cache", "/system", "/vendor", "/oem"
    )

    fun scan(): Flow<Pair<ScanProgress, List<JunkFile>>> = flow {
        val found = mutableListOf<JunkFile>()

        emit(ScanProgress(ScanPhase.SCANNING_JUNK, 0, 1, "准备扫描…") to emptyList())

        val dirs = getSearchDirs()
        var scanned = 0
        val total = dirs.size

        for (dir in dirs) {
            if (!currentCoroutineContext().isActive) break
            emit(
                ScanProgress(ScanPhase.SCANNING_JUNK, scanned, total, dir.path, found.size)
                to found.toList()
            )
            scanDirectory(dir, found)
            scanned++
        }

        // Emit final
        emit(ScanProgress(ScanPhase.DONE, total, total, "", found.size) to found.toList())
    }.flowOn(Dispatchers.IO)

    private fun getSearchDirs(): List<File> {
        val dirs = mutableListOf<File>()

        // Android/data - app caches accessible in normal mode
        val androidData = File(rootDir, "Android/data")
        if (androidData.exists()) {
            androidData.listFiles()?.forEach { appDir ->
                File(appDir, "cache").takeIf { it.exists() }?.let { dirs.add(it) }
                File(appDir, "files/cache").takeIf { it.exists() }?.let { dirs.add(it) }
            }
        }

        // Common junk directories
        listOf("DCIM/.thumbnails", ".thumbnails", "Download", "Bluetooth").forEach { rel ->
            File(rootDir, rel).takeIf { it.exists() }?.let { dirs.add(it) }
        }

        // Root-accessible locations
        if (permissionManager.isRootAvailable()) {
            listOf(
                "/data/tombstones",
                "/data/anr",
                "/data/misc/logd",
                "/cache"
            ).map { File(it) }.filter { it.exists() }.forEach { dirs.add(it) }
        }

        return dirs
    }

    private fun scanDirectory(dir: File, results: MutableList<JunkFile>) {
        if (!dir.canRead()) return

        dir.walkTopDown()
            .onEnter { f ->
                // Skip protected system paths
                skipPaths.none { f.absolutePath.startsWith(it) }
            }
            .forEach { file ->
                when {
                    file.isDirectory && FileUtils.isEmptyDir(file) -> {
                        results.add(
                            JunkFile(file.absolutePath, file.name, 0L, JunkType.EMPTY_FOLDER)
                        )
                    }
                    file.isFile -> {
                        classifyFile(file)?.let { results.add(it) }
                    }
                }
            }
    }

    private fun classifyFile(file: File): JunkFile? {
        val name = file.name
        val ext = name.substringAfterLast('.', "").lowercase()
        val size = file.length()

        return when {
            ext in junkExtensions ->
                JunkFile(file.absolutePath, name, size, JunkType.TEMP)

            ext == "log" || name.endsWith(".log.gz") ->
                JunkFile(file.absolutePath, name, size, JunkType.LOG)

            name == ".DS_Store" || name == "Thumbs.db" || name == "desktop.ini" ->
                JunkFile(file.absolutePath, name, size, JunkType.DS_STORE)

            file.parentFile?.name == ".thumbnails" || file.parentFile?.name == "thumbnails" ->
                JunkFile(file.absolutePath, name, size, JunkType.THUMBNAIL)

            ext == "apk" && file.parentFile?.absolutePath?.contains("backup", true) == true ->
                JunkFile(file.absolutePath, name, size, JunkType.APK_BACKUP)

            name.endsWith(".crash") || file.parentFile?.name == "tombstones" ->
                JunkFile(file.absolutePath, name, size, JunkType.CRASH)

            else -> null
        }
    }
}
