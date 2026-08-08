# 架构设计文档

本文档说明纯本地版 Malody 谱面调速器的架构、数据流，以及与旧 Web 实现的移植对应关系。

## 1. 背景

旧版本架构：

- **Android App**：仅是一个壳。`MainActivity` 输入激活码 `vid`，
  `WebViewActivity` 加载远程 `http://119.45.124.108:4776/bsc?vid=xxx`，
  `ImportActivity` 把收到的文件复制到 `/MalodyBSC` 目录。
- **Web 后端**（`MalodyBSC-web`，已删除）：Vue3 前端 + Flask 后端。
  后端接收上传的谱面包 → 解压 → 解析谱面 → 用 ffmpeg `atempo` 变速音频 →
  修改谱面数据 → 重新打包 → 前端下载。

新版本：**全部逻辑在 App 内本地完成**，无网络、无 WebView、无第三方库依赖。

## 2. 总体架构

```
┌─────────────────────────────────────────────────────────────┐
│                        MainActivity                         │
│  选择文件(SAF) / 外部打开(VIEW)                                │
│        │                                                     │
│        ▼                                                     │
│  BeatmapArchive.open(uri)   zip 解压到 cache/work/{uuid}     │
│        │  遍历条目：.mc/.osu/.imd → 解析为 Beatmap 列表       │
│        ▼                                                     │
│  Spinner 选择谱面 + SeekBar 速度 0.5~2.0x                     │
│        │                                                     │
│        ▼                                                     │
│  BeatmapArchive.generate(index, speed)                       │
│        ├─ BeatmapGenerator.generate(beatmap, speed)          │
│        │     ├─ mc : BPM×speed、offset÷speed、替换音频        │
│        │     ├─ osu: Events/TimingPoints/HitObjects ÷speed   │
│        │     └─ rm : length/bpm/notes ÷speed、bpm×speed       │
│        │     └─ AudioSpeedChanger.process(...)               │
│        │           MediaExtractor+MediaCodec 解码 → PCM      │
│        │           WsolaTimeStretcher 变速（保持音调）        │
│        │           MediaCodec(AAC)+MediaMuxer 编码 → .m4a    │
│        └─ 把工作目录重新打包为 .mcz/.osz/.zip                 │
│        ▼                                                     │
│  保存(ACTION_CREATE_DOCUMENT) / 分享(FileProvider+ACTION_SEND)│
└─────────────────────────────────────────────────────────────┘
```

## 3. 数据流与关键类

### 3.1 谱面档案解析 —— `util/BeatmapArchive`

对应旧 `backend/beatmap_helper.py` 的 `get_beatmaps()`：

1. 把用户选择的 `content://` Uri 复制到 `cache/input/`；
2. 用 `java.util.zip.ZipFile` 打开，遍历条目：
   - 条目名包含 `.mc` → `org.json` 解析 → `Beatmap(type="mc")`；
   - 条目名包含 `.osu` → `OsuParser.read()` → `Beatmap(type="osu")`；
   - 条目名包含 `.imd` → `ImdParser.read()` → `Beatmap(type="rm")`；
3. 全部条目解压到 `cache/work/{uuid}/`（音频、图片等原样保留）；
4. `Beatmap.outDir` 指向该谱面文件所在目录，生成结果输出到同目录。

### 3.2 Malody（.mc）—— `generator/BeatmapGenerator.generateMalody`

对应旧 `generate_beatmap_malody()`：

- `meta.creator` 固定为原项目署名，删除 `meta.id`；
- `meta.version += "-{speed}"`、`meta.time = 当前unix秒`；
- `time[].bpm ×= speed`；
- 第一个含 `offset` 的音符 `offset ÷= speed`（整数）；
- 找到第一个含 `sound` 的音符作为音频来源，变速后把 `sound` 指向新 `.m4a`；
- 写出 `{time}-{speed}.mc`。

### 3.3 osu!（.osu）—— `BeatmapGenerator.generateOsu`

对应旧 `generate_beatmap_osu()`：

- `Metadata.Version += "-{speed}"`；`Metadata` 写入原作者信息；`BeatmapID=0`、`BeatmapSetID=-1`；
- `General.PreviewTime = -1`；
- `Events`：`0/1/Video` 行时间 ÷speed；`2`（背景/视频）两个时间都 ÷speed；
- `TimingPoints`：首列 ÷speed；BPM（第二列 >0 时）÷speed（浮点除法，与 Python 一致）；
- `HitObjects`：`x` 之外第 3 列（时间）÷speed，冒号段的第一个数字（时长/结束时间）÷speed；
- `AudioFilename` 指向新的 `.m4a`。

### 3.4 节奏大师（.imd）—— `BeatmapGenerator.generateRotaeno` + `parser/ImdParser`

对应旧 `imd_parser.py` + `generate_beatmap_rm()`：

- 二进制小端布局：`int length`、`int count`、`count×(int t + double bpm)`、
  `short flag`、`int count2`、`count2×(short action + int time + byte track + int param)`；
- 生成规则：`length ÷= speed`；`bpm_list[].bpm ×= speed`、`t ÷= speed`；
  `notes[].time ÷= speed`；当 `action != 0 && param > 3` 时 `param ÷= speed`；
- 音频文件名由 `.imd` 文件名推导（`version.split("_")[0].split("-")[0] + ".mp3"`），与旧实现一致。

### 3.5 重新打包 —— `BeatmapArchive.pack`

对应旧 `write_file_recursively()`：把 `cache/work/{uuid}/` 递归压成 zip，
后缀按谱面类型：`mc→.mcz`、`osu→.osz`、`rm→.zip`。

## 4. 音频变速（纯本地实现）

旧实现调用 `ffmpeg -filter:a atempo={speed}`。新实现完全使用 Android 原生 API：

1. **解码**：`MediaExtractor` 选取音频轨道 + `MediaCodec` 解码为 16bit PCM，
   支持 mp3 / m4a / ogg / wav 等系统可解码格式；
2. **变速**：`WsolaTimeStretcher`（纯 Java）对每个声道独立做
   **WSOLA（波形相似叠加）** 时间拉伸：
   - 分析帧约 60ms，50% 重叠；
   - 理想分析起点按 `analysisHop = synHop × speed` 步进；
   - 在理想位置附近两阶段搜索（粗搜步长 8 + 细搜 ±8），
     用归一化互相关选择与输出重叠区最相似的位置，交叉淡化叠加；
   - 输出长度 ≈ 输入长度 / speed，**音调保持不变**；
3. **编码**：`MediaCodec` AAC 编码器（LC profile）+ `MediaMuxer` 封装为 `.m4a`。

> 差异说明：Android `MediaCodec` 不支持 MP3 编码，故输出音频为 `.m4a`（AAC），
> 与旧版 `.mp3` 不同；Malody / osu! 均支持 `.m4a`。

单元测试 `WsolaTimeStretcherTest` 验证了 0.5x / 1.5x / 2.0x 下：
输出时长与 `输入/speed` 偏差 < 7%，440Hz 正弦主频误差 < 2%。

## 5. UI 与权限

- **单 Activity**（`MainActivity`）：SAF 选文件 → Spinner 选谱面 → SeekBar 调速度 →
  生成 → 保存 / 分享。
- **零权限**：读写全部通过 SAF（`ACTION_OPEN_DOCUMENT` / `ACTION_CREATE_DOCUMENT`），
  分享通过 `FileProvider`（`xml/file_paths.xml` 暴露 `cache/files` 目录）。
- **打开方式**：`AndroidManifest.xml` 中 `MainActivity` 注册 `VIEW` intent-filter，
  支持 `content://` 与 `file://` 的 `.msz/.mcz/.osz/.zip/.imd`。

## 6. 测试

- `app/src/test/java/...`：JVM 单元测试（不依赖 Android 运行时）：
  - `OsuParserTest`：.osu 解析与回写；
  - `ImdParserTest`：.imd 二进制读写往返与文件名推导；
  - `BeatmapGeneratorTest`：三类谱面生成规则（注入假音频处理器验证数据修改）；
  - `WsolaTimeStretcherTest`：变速时长与音调保持。
- `BeatmapGenerator.audioProcessor` 为可注入接口，测试中替换为文件复制，
  使纯 JVM 环境也能验证谱面数据逻辑。

## 7. 移植对照表

| 旧 Web 后端文件 | 新实现 |
| --- | --- |
| `backend/beatmap_helper.py`（get_beatmaps / generate_* / write_file_recursively） | `util/BeatmapArchive.java`、`generator/BeatmapGenerator.java` |
| `backend/osu_parser.py` | `parser/OsuParser.java` |
| `backend/imd_parser.py` | `parser/ImdParser.java` |
| `backend/music_helper.py`（ffmpeg） | `audio/AudioSpeedChanger.java`、`audio/WsolaTimeStretcher.java` |
| `backend/utils.py`（allowed_file） | SAF 选文件 + zip 内容识别，无需白名单 |
| `main_backend.py`（路由/vid 校验/redis 锁） | 全部移除 |
| `cleanup_worker.py` | 移除（缓存目录由系统管理） |
| Vue3 前端（Home.vue 等） | `MainActivity.java` + `activity_main.xml` |
