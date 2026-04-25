# 文件方领

> 一款面向 Android (arm64) 的文件清理与整理工具，支持 Root / ADB / 普通三种权限模式。

<p align="center">
  <img src="docs/icon.png" width="120" alt="文件方领图标"/>
</p>

---

## 功能

| 模块 | 说明 |
|------|------|
| 🗑 垃圾清理 | 扫描临时文件、日志、空文件夹、缩略图缓存、崩溃日志 |
| 📋 重复文件 | 二阶段哈希（局部 → 完整 MD5）检测重复，智能全选保留最新副本 |
| 📁 文件整理 | 按类型将文件归类到 Pictures / Movies / Music / Documents 等 |
| 📊 存储分析 | 饼图可视化 + TOP 20 大文件列表 |
| 🔐 权限层级 | 自动检测：Root → ADB(Shizuku) → 普通，逐级降级 |
| ♻️ 回收站 | 删除文件移入 `.Trash/` 文件夹，支持恢复 |

---

## 权限模式

```
启动时自动检测：
  1. Root（检测到 su 并授权）  → 可清理 /cache /data/tombstones 等系统目录
  2. ADB via Shizuku           → 可执行 pm clear 等 ADB 指令
  3. 普通模式                   → 仅操作用户可访问的外部存储
```

---

## 构建

### 本地构建

```bash
git clone https://github.com/YOUR_USERNAME/FileLeader.git
cd FileLeader

# Debug APK
./gradlew assembleDebug

# Release APK（需要 keystore.properties）
./gradlew assembleRelease
```

**keystore.properties 格式**（不要提交到 Git！）

```properties
storeFile=/absolute/path/to/your.jks
storePassword=yourStorePassword
keyAlias=yourKeyAlias
keyPassword=yourKeyPassword
```

### GitHub Actions 发布

1. 在仓库 **Settings → Secrets and variables → Actions** 中添加：

| Secret | 说明 |
|--------|------|
| `KEYSTORE_BASE64` | `base64 -i release.jks` 的输出 |
| `KEYSTORE_PASSWORD` | keystore 密码 |
| `KEY_ALIAS` | key alias |
| `KEY_PASSWORD` | key 密码 |

2. 进入 **Actions → Release Build → Run workflow**，填写：
   - **版本 Tag**：`v1.0.0`（必须符合 `vX.Y.Z` 格式）
   - **更新说明**：本次更新的内容

3. 构建完成后自动创建 GitHub Release，包含：
   - 签名的 arm64 APK
   - SHA256 校验文件

---

## 生成 Keystore

```bash
keytool -genkey -v \
  -keystore release.jks \
  -keyalg RSA \
  -keysize 2048 \
  -validity 10000 \
  -alias fileleader

# 编码为 base64（用于 GitHub Secret）
base64 -i release.jks | pbcopy   # macOS，直接复制到剪贴板
base64 -i release.jks            # Linux，手动复制输出
```

---

## 技术栈

- **语言**：Kotlin 1.9
- **架构**：MVVM + Repository + Hilt DI
- **异步**：Coroutines + Flow
- **数据库**：Room（扫描历史 + 回收站记录）
- **导航**：Navigation Component
- **Root**：libsu 5.x
- **ADB**：Shizuku API 13
- **图表**：MPAndroidChart
- **构建**：Gradle 8.6 + Version Catalog

---

## 目录结构

```
app/src/main/java/com/fileleader/
├── data/
│   ├── model/       # 数据模型（JunkFile, DuplicateGroup 等）
│   ├── db/          # Room DAO + Database
│   └── repository/  # （扩展用）
├── domain/
│   └── engine/      # 核心引擎：JunkScanner, DuplicateScanner,
│                    #           CleanEngine, FileOrganizer, StorageAnalyzer
├── ui/
│   ├── home/        # 主页 Fragment + ViewModel
│   ├── clean/       # 垃圾清理
│   ├── duplicates/  # 重复文件
│   ├── organize/    # 文件整理
│   ├── analyze/     # 存储分析
│   └── widget/      # 自定义 View（RingProgressView）
├── util/            # FileUtils, PermissionManager
├── di/              # Hilt AppModule
└── service/         # ScanService（前台服务）
```

---

## 注意事项

- 本 App 会请求 `MANAGE_EXTERNAL_STORAGE`（Android 11+），在 Google Play 上架需要额外审核，建议通过 GitHub Releases 或私有渠道分发。
- Root 和 ADB 功能仅在设备支持时可用，普通模式下完全不依赖特权权限。
- `.Trash/` 文件夹默认不会自动清空，可在设置中手动清空。

---

## License

MIT License
