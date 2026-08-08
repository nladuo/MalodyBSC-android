# MalodyBSC-android

Android 版 **Malody 谱面调速器**（纯本地实现）。

> 本项目原为「Android 壳 + Web 后端（Vue3 + Flask + ffmpeg）」架构，已完整重构为
> **不依赖网络、不依赖 WebView、不依赖任何第三方库**的纯原生 App。

## 功能

- 支持谱面包：`.mcz` / `.msz`（Malody）、`.osz`（osu!）、`.zip`（含 `.imd` 节奏大师谱面）
- 自动解析包内所有谱面（`.mc` / `.osu` / `.imd`）
- 调速范围 **0.50x ~ 2.00x**，自动修改 BPM / 偏移 / 时间轴
- 音频变速使用**纯本地实现**：MediaCodec 解码 → WSOLA 算法变速 → AAC 编码（`.m4a`）
- 生成结果自动重新打包为 `.mcz` / `.osz` / `.zip`，可**保存**（SAF）或**分享**
- 支持从文件管理器「打开方式」直接进入解析
- 无需申请任何运行时权限

## 使用

1. 安装 App，打开后点击「选择谱面文件」，或直接在文件管理器中用本 App 打开谱面包；
2. 从下拉列表选择一个谱面（显示 `Malody｜难度名` / `osu!｜难度名` / `节奏大师｜版本`）；
3. 拖动滑杆设置速度（0.50x ~ 2.00x，默认 1.20x）；
4. 点击「生成谱面」，等待音频变速完成；
5. 生成完成后点击「保存到...」选择位置，或「分享」发送给他人。

> 生成结果中的音频为 `.m4a`（AAC）。由于 Android 系统编解码器不支持 MP3 编码，
> 原 Web 后端输出的 `.mp3` 在纯本地实现中改为 `.m4a`，Malody / osu! 均可正常读取。

## 构建

要求：JDK 17+、Android SDK（compileSdk 33）。

```bash
cd MalodyBSC-android
./gradlew :app:assembleDebug          # 打包 Debug APK
./gradlew :app:testDebugUnitTest      # 运行 JVM 单元测试（解析/生成/WSOLA）
```

生成的 APK 位于 `app/build/outputs/apk/debug/`。

## 项目结构

```
MalodyBSC-android/
├── app/src/main/java/com/example/malodybeatmapspeedchanger/
│   ├── MainActivity.java        # 主界面（选文件→解析→调速→保存/分享）
│   ├── model/                   # Beatmap、ImdData 数据模型
│   ├── parser/                  # OsuParser、ImdParser（谱面解析/写出）
│   ├── generator/               # BeatmapGenerator（mc/osu/rm 调速生成）
│   ├── audio/                   # AudioSpeedChanger、WsolaTimeStretcher（纯本地变速）
│   └── util/                    # BeatmapArchive（zip 解压/发现/重打包）
└── docs/ARCHITECTURE.md         # 架构设计与移植说明
```

## 与旧版（Web）的对应关系

| 旧 Web 后端（已删除） | 新纯本地实现 |
| --- | --- |
| `backend/beatmap_helper.py` | `generator/BeatmapGenerator.java`、`util/BeatmapArchive.java` |
| `backend/osu_parser.py` | `parser/OsuParser.java` |
| `backend/imd_parser.py` | `parser/ImdParser.java` |
| `backend/music_helper.py`（ffmpeg atempo） | `audio/AudioSpeedChanger.java` + `audio/WsolaTimeStretcher.java` |
| `main_backend.py` / Vue3 前端 | `MainActivity.java` |
| 激活码 `vid` 校验 | 已移除（本地 App 无需校验） |

## LICENSE

MIT
