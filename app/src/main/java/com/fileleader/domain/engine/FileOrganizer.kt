package com.fileleader.domain.engine

import android.os.Environment
import com.fileleader.data.model.OrganizeFile
import com.fileleader.data.model.OrganizePreview
import com.fileleader.data.model.OrganizeRule
import com.fileleader.data.model.PermissionMode
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

@Singleton
class FileOrganizer @Inject constructor(
    private val permissionManager: PermissionManager
) {
    private val rootDir = Environment.getExternalStorageDirectory()

    // Default organize rules
    val defaultRules: List<OrganizeRule> = listOf(
        OrganizeRule("images",    "图片",   listOf("jpg","jpeg","png","gif","webp","heic","heif","bmp","svg"), "Pictures",   "🖼"),
        OrganizeRule("videos",    "视频",   listOf("mp4","mkv","avi","mov","wmv","flv","ts","m4v","3gp"),      "Movies",     "🎬"),
        OrganizeRule("audio",     "音乐",   listOf("mp3","flac","aac","ogg","wav","m4a","opus","wma"),         "Music",      "🎵"),
        OrganizeRule("documents", "文档",   listOf("pdf","doc","docx","xls","xlsx","ppt","pptx","txt","csv","epub"), "Documents", "📄"),
        OrganizeRule("archives",  "压缩包", listOf("zip","rar","7z","tar","gz","bz2","xz"),                   "Archives",   "📦"),
        OrganizeRule("apks",      "APK",    listOf("apk","apks","xapk"),                                      "APKs",       "📱"),
        OrganizeRule("images_raw","RAW图片",listOf("raw","cr2","nef","arw","dng","orf","rw2"),                 "Pictures/RAW","📷"),
    )

    /**
     * Preview what would be moved, without actually moving anything
     */
    suspend fun preview(
        scanDir: File = File(rootDir, "Download"),
        rules: List<OrganizeRule> = defaultRules
    ): List<OrganizePreview> = withContext(Dispatchers.IO) {
        val previews = mutableMapOf<String, MutableList<OrganizeFile>>()

        // Build extension → rule map
        val extToRule = mutableMapOf<String, OrganizeRule>()
        rules.filter { it.enabled }.forEach { rule ->
            rule.extensions.forEach { ext -> extToRule[ext.lowercase()] = rule }
        }

        // Walk all accessible locations
        val searchDirs = listOf(
            scanDir,
            File(rootDir, "Downloads"),
            File(rootDir, "DCIM"),
            File(rootDir, "Documents"),
            File(rootDir, "Bluetooth")
        ).filter { it.exists() }

        searchDirs.forEach { dir ->
            dir.walkTopDown()
                .filter { it.isFile && it.canRead() }
                .forEach { file ->
                    val ext = file.extension.lowercase()
                    val rule = extToRule[ext] ?: return@forEach

                    val targetDir = File(rootDir, rule.targetFolder)
                    val targetPath = File(targetDir, file.name).absolutePath

                    // Skip if already in correct place
                    if (file.parentFile?.absolutePath == targetDir.absolutePath) return@forEach
                    // Skip if target == source
                    if (file.absolutePath == targetPath) return@forEach

                    previews.getOrPut(rule.id) { mutableListOf() }.add(
                        OrganizeFile(
                            sourcePath = file.absolutePath,
                            targetPath = targetPath,
                            name = file.name,
                            size = file.length()
                        )
                    )
                }
        }

        rules.mapNotNull { rule ->
            val files = previews[rule.id] ?: return@mapNotNull null
            if (files.isEmpty()) return@mapNotNull null
            OrganizePreview(rule, files, files.sumOf { it.size })
        }
    }

    /**
     * Execute organization for a given list of OrganizeFile entries
     */
    fun execute(files: List<OrganizeFile>): Flow<Pair<Int, Int>> = flow {
        var done = 0
        val total = files.size

        files.forEach { item ->
            val src = File(item.sourcePath)
            val dst = File(item.targetPath)

            val ok = moveFile(src, dst)
            if (ok) done++
            emit(done to total)
        }
    }.flowOn(Dispatchers.IO)

    private fun moveFile(src: File, dst: File): Boolean {
        if (!src.exists()) return false
        dst.parentFile?.mkdirs()

        // Resolve name conflict
        val finalDst = resolveConflict(dst)

        return when (permissionManager.getCurrentMode()) {
            PermissionMode.ROOT -> {
                Shell.cmd("mv \"${src.absolutePath}\" \"${finalDst.absolutePath}\"")
                    .exec().isSuccess
            }
            else -> {
                try {
                    src.copyTo(finalDst, overwrite = false)
                    src.delete()
                    true
                } catch (e: Exception) { false }
            }
        }
    }

    private fun resolveConflict(file: File): File {
        if (!file.exists()) return file
        var counter = 1
        val name = file.nameWithoutExtension
        val ext = file.extension
        var candidate = File(file.parent, "${name}_$counter.$ext")
        while (candidate.exists()) {
            counter++
            candidate = File(file.parent, "${name}_$counter.$ext")
        }
        return candidate
    }
}
