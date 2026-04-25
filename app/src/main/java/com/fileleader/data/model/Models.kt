package com.fileleader.data.model

import android.net.Uri
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.io.File

// ===== Junk File =====
data class JunkFile(
    val path: String,
    val name: String,
    val size: Long,
    val type: JunkType,
    var isSelected: Boolean = false
) {
    val file: File get() = File(path)
}

enum class JunkType(val label: String, val emoji: String) {
    TEMP("临时文件", "🗂"),
    LOG("日志文件", "📋"),
    CACHE("缓存文件", "💾"),
    EMPTY_FOLDER("空文件夹", "📁"),
    APK_BACKUP("APK备份", "📦"),
    THUMBNAIL("缩略图缓存", "🖼"),
    CRASH("崩溃日志", "💥"),
    DS_STORE("系统隐藏文件", "🔒");
}

// ===== Duplicate File =====
data class DuplicateGroup(
    val hash: String,
    val size: Long,             // size of ONE file
    val files: List<DuplicateFile>,
    val wastedBytes: Long = size * (files.size - 1)
)

data class DuplicateFile(
    val path: String,
    val name: String,
    val size: Long,
    val lastModified: Long,
    val mimeType: String,
    var isSelected: Boolean = false,
    val isKeepCandidate: Boolean = false   // smart: keep the newest
)

// ===== Storage Analysis =====
data class StorageInfo(
    val totalBytes: Long,
    val usedBytes: Long,
    val freeBytes: Long,
    val categories: List<StorageCategory>
)

data class StorageCategory(
    val name: String,
    val bytes: Long,
    val color: Int,
    val icon: String
)

// ===== File Organize =====
data class OrganizeRule(
    val id: String,
    val name: String,
    val extensions: List<String>,
    val targetFolder: String,
    val icon: String,
    val enabled: Boolean = true
)

data class OrganizePreview(
    val rule: OrganizeRule,
    val files: List<OrganizeFile>,
    val totalSize: Long
)

data class OrganizeFile(
    val sourcePath: String,
    val targetPath: String,
    val name: String,
    val size: Long
)

// ===== Permission Mode =====
enum class PermissionMode(val label: String, val description: String) {
    ROOT("Root 模式", "最高权限，可清理系统缓存"),
    ADB("ADB 模式", "通过 Shizuku 获得 ADB 权限"),
    NORMAL("普通模式", "仅清理用户可访问文件");
}

// ===== Scan Progress =====
data class ScanProgress(
    val phase: ScanPhase,
    val current: Int,
    val total: Int,
    val currentFile: String = "",
    val found: Int = 0
) {
    val percent: Int get() = if (total == 0) 0 else (current * 100 / total)
}

enum class ScanPhase(val label: String) {
    IDLE("空闲"),
    SCANNING_JUNK("扫描垃圾文件"),
    SCANNING_DUPLICATES("扫描重复文件"),
    SCANNING_LARGE("扫描大文件"),
    ANALYZING_STORAGE("分析存储空间"),
    DONE("扫描完成"),
    ERROR("扫描出错");
}

// ===== Room entity for scan history =====
@Entity(tableName = "scan_history")
data class ScanHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    val junkBytes: Long,
    val duplicateBytes: Long,
    val cleanedBytes: Long,
    val mode: String
)

// ===== Trash entry (for undo support) =====
@Entity(tableName = "trash_entries")
data class TrashEntry(
    @PrimaryKey val trashPath: String,
    val originalPath: String,
    val name: String,
    val size: Long,
    val deletedAt: Long,
    val type: String   // JUNK, DUPLICATE, ORGANIZE
)
