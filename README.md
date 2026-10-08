# 扫码关联

> 基于 Android + Kotlin + ZXing 的扫码 / 批量生成二维码 / 条形码工具。

## 功能

- **扫码识别**：相机扫描二维码（QR）和常见一维条形码（EAN-13/8、UPC-A/E、Code-128/39/93、ITF）
- **批量生成**：根据内容批量生成 QR / 条形码图片，**主项 + 关联子项** 自动合成到一张大图
- **关联子项**：长按主项打开详情页，添加 / 扫码关联子项
- **重复检测**：子项有重复时自动用半透明底色标记，不同重复组用不同颜色区分
- **左滑删除**：主项支持左滑显示红色"删除"按钮
- **保存到相册**：批量生成的图片自动写入系统相册 `Pictures/QRBatch/`

## 截图

（暂无）

## 界面预览

- **首页**：扫码按钮 + 手动输入 + 列表展示
- **详情页**：编辑主项 / 手动添加子项 / 扫码关联子项
- **列表展开**：点击主项展开查看子项；长按编辑

## 技术栈

- **语言**：Kotlin 1.9.24
- **构建**：Gradle 8.10.2 + AGP 8.7.2
- **目标 SDK**：34 (Android 14)
- **最低 SDK**：24 (Android 7.0)
- **核心依赖**：
  - [ZXing](https://github.com/journeyapps/zxing-android-embedded) 4.3.0 — 扫码与码图生成
  - [AndroidX](https://developer.android.com/jetpack) — `core-ktx`, `appcompat`, `material`, `recyclerview`, `constraintlayout`

## 项目结构

```
.
├── app/
│   ├── build.gradle.kts
│   ├── proguard-rules.pro
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── java/com/example/qrbatch/
│       │   ├── MainActivity.kt            # 主页
│       │   ├── ItemDetailActivity.kt      # 关联子项详情页
│       │   ├── CaptureManager.kt          # 扫码封装（多码制）
│       │   ├── QrItem.kt                  # 数据模型（含 format）
│       │   ├── ItemStore.kt               # 跨 Activity 共享数据
│       │   ├── RowItem.kt                 # 列表行模型
│       │   ├── CodeBitmapFactory.kt       # QR / 条形码生成
│       │   ├── QrComposer.kt              # 合成大图（多行自适应）
│       │   ├── BatchQrSaver.kt            # 批量保存到相册
│       │   ├── DuplicateDetector.kt       # 子项重复检测
│       │   └── ...
│       └── res/
│           ├── drawable/bg_swipe_delete.xml
│           ├── layout/                    # activity_*.xml + row_*.xml
│           ├── mipmap-*/ic_launcher.png   # 应用图标（5 个 density）
│           ├── mipmap-anydpi-v26/         # 自适应图标
│           └── values/{strings,colors,themes}.xml
├── build.gradle.kts
├── settings.gradle.kts
├── gradle.properties
├── gradle/wrapper/
├── gradlew / gradlew.bat
└── .gitignore
```

## 构建

### 前置要求

- JDK 17 或更高
- Android SDK Platform 34 + Build-Tools 34.0.0
- Gradle 8.10.2（或使用 `./gradlew` 自动下载）

### 命令

```bash
# 调试包
./gradlew :app:assembleDebug

# 发布包（需要签名配置）
./gradlew :app:assembleRelease

# 输出位置
# app/build/outputs/apk/debug/app-debug.apk
# app/build/outputs/apk/release/app-release.apk
```

### 安装到设备

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n com.example.qrbatch/.MainActivity
```

## 权限说明

| 权限 | 用途 |
|------|------|
| `CAMERA` | 摄像头扫码 |
| `WRITE_EXTERNAL_STORAGE` (API ≤ 28) | Android 9 及以下保存到相册 |
| `READ_MEDIA_IMAGES` (API ≥ 33) | Android 13+ 读取相册（MediaStore 写入自带） |
| `READ_EXTERNAL_STORAGE` (API ≤ 32) | 历史兼容 |

## 应用图标

应用图标位于 `app/src/main/res/mipmap-*/`：
- 紫色渐变圆角背景
- 白色扫码框 4 角
- 中央两个交叠环形（象征"关联"）

## 已知问题

- 在某些设备/系统版本上 ZXing-Android-Embedded 的 `IntentIntegrator` 已被标记 `@Deprecated`，但功能仍正常
- 长按主项进入详情页：详情页不直接支持修改主项文本（只能添加/删除/扫码关联子项），如需改主项文本需要重建条目

## 路线图

- [ ] 长按主项菜单改为主项改名（替代当前"长按进详情页"）
- [ ] iOS 版（评估中）
- [ ] 列表拖拽排序
- [ ] 历史记录 / 草稿自动保存

## 许可证

MIT
