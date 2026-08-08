package com.example.malodybeatmapspeedchanger.generator;

import com.example.malodybeatmapspeedchanger.audio.AudioProcessor;
import com.example.malodybeatmapspeedchanger.model.Beatmap;
import com.example.malodybeatmapspeedchanger.model.ImdData;
import com.example.malodybeatmapspeedchanger.parser.ImdParser;
import com.example.malodybeatmapspeedchanger.parser.OsuParser;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;

/**
 * 谱面调速生成器。
 *
 * <p>移植自原 Web 后端 backend/beatmap_helper.py，支持三种谱面：</p>
 * <ul>
 *   <li>mc（Malody .mc JSON）：BPM × speed、offset ÷ speed、替换音频</li>
 *   <li>osu（osu! .osu 文本）：Events/TimingPoints/HitObjects 时间 ÷ speed、替换音频</li>
 *   <li>rm（节奏大师 .imd 二进制）：length/bpm/音符时间 ÷ speed，bpm × speed，替换音频</li>
 * </ul>
 *
 * <p>音频变速通过 {@link AudioProcessor} 完成；App 内默认使用纯原生实现
 * （解码 → WSOLA 变速 → AAC 编码），因此输出音频为 .m4a 而不是原后端的 .mp3。</p>
 */
public final class BeatmapGenerator {

    /** 输出音频扩展名（Android MediaCodec 仅支持 AAC 编码） */
    public static final String AUDIO_EXT = ".m4a";

    /** 音频变速处理器，可在测试中注入假实现 */
    public static volatile AudioProcessor audioProcessor = new AudioProcessor() {
        @Override
        public void process(File src, double speed, File dst) throws Exception {
            throw new IllegalStateException("audioProcessor 未初始化，请先设置 AudioSpeedChanger");
        }
    };

    private BeatmapGenerator() {
    }

    /** 对指定谱面执行一次调速生成，结果写入谱面所在目录 */
    public static void generate(Beatmap beatmap, double speed) throws Exception {
        switch (beatmap.type) {
            case "mc":
                generateMalody(beatmap.mcData, beatmap.outDir, speed);
                break;
            case "osu":
                generateOsu(beatmap.osuData, beatmap.outDir, speed);
                break;
            case "rm":
                generateRotaeno(beatmap.imdData, beatmap.outDir, speed);
                break;
            default:
                throw new IllegalArgumentException("未知谱面类型: " + beatmap.type);
        }
    }

    // ------------------------------------------------------------------ Malody

    public static void generateMalody(JSONObject json, File outDir, double speed) throws Exception {
        JSONObject tmp = new JSONObject(json.toString());
        JSONObject meta = tmp.getJSONObject("meta");

        meta.put("creator", "nladuo/malody_beatmap_speed_changer");
        meta.remove("id");
        meta.put("version", meta.optString("version", "") + "-" + formatSpeed(speed));
        meta.put("time", (int) (System.currentTimeMillis() / 1000));

        // BPM × speed
        JSONArray time = tmp.getJSONArray("time");
        for (int i = 0; i < time.length(); i++) {
            JSONObject t = time.getJSONObject(i);
            t.put("bpm", speed * t.optDouble("bpm", 0));
        }

        // 第一个带 offset 的音符 ÷ speed
        JSONArray note = tmp.getJSONArray("note");
        int offsetIndex = firstIndexOfKey(note, "offset");
        if (offsetIndex >= 0) {
            JSONObject n = note.getJSONObject(offsetIndex);
            n.put("offset", (int) (n.optInt("offset", 0) / speed));
        }

        // 变速音频
        String soundFile = firstStringValue(note, "sound", "");
        File musicSrc = new File(outDir, soundFile);
        String audioName = System.currentTimeMillis() / 1000 + "-" + formatSpeed(speed) + AUDIO_EXT;
        File audioOut = new File(outDir, audioName);
        requireAudioProcessor().process(musicSrc, speed, audioOut);

        // 替换第一个带 sound 的音符
        int soundIndex = firstIndexOfKey(note, "sound");
        if (soundIndex >= 0) {
            note.getJSONObject(soundIndex).put("sound", audioName);
        }

        // 写出 .mc
        String mcName = System.currentTimeMillis() / 1000 + "-" + formatSpeed(speed) + ".mc";
        File mcOut = new File(outDir, mcName);
        try (FileOutputStream fos = new FileOutputStream(mcOut)) {
            fos.write(tmp.toString().getBytes(StandardCharsets.UTF_8));
        }
    }

    // ------------------------------------------------------------------ osu!

    public static void generateOsu(JSONObject json, File outDir, double speed) throws Exception {
        JSONObject tmp = new JSONObject(json.toString());

        JSONObject metadata = tmp.getJSONObject("Metadata");
        metadata.put("Version", metadata.optString("Version", "") + "-" + formatSpeed(speed));
        metadata.put("Creator", "nladuo/malody_beatmap_speed_changer");
        metadata.put("Source", "https://github.com/nladuo/malody_beatmap_speed_changer");
        metadata.put("Tags", "produced by nladuo/malody_beatmap_speed_changer");
        metadata.put("BeatmapID", 0);
        metadata.put("BeatmapSetID", -1);

        JSONObject general = tmp.getJSONObject("General");
        general.put("PreviewTime", -1);

        JSONArray events = tmp.optJSONArray("Events");
        if (events != null) {
            for (int i = 0; i < events.length(); i++) {
                events.put(i, processEvent(events.getString(i), speed));
            }
        }
        JSONArray timing = tmp.optJSONArray("TimingPoints");
        if (timing != null) {
            for (int i = 0; i < timing.length(); i++) {
                timing.put(i, processTimingPoint(timing.getString(i), speed));
            }
        }
        JSONArray hits = tmp.optJSONArray("HitObjects");
        if (hits != null) {
            for (int i = 0; i < hits.length(); i++) {
                hits.put(i, processHitObject(hits.getString(i), speed));
            }
        }

        // 变速音频
        String audioSrc = general.optString("AudioFilename", "");
        File musicSrc = new File(outDir, audioSrc);
        String audioName = System.currentTimeMillis() / 1000 + "-" + formatSpeed(speed) + AUDIO_EXT;
        File audioOut = new File(outDir, audioName);
        requireAudioProcessor().process(musicSrc, speed, audioOut);
        general.put("AudioFilename", audioName);

        // 写出 .osu
        String osuName = System.currentTimeMillis() / 1000 + "-" + formatSpeed(speed) + ".osu";
        OsuParser.write(tmp, new File(outDir, osuName));
    }

    static String processEvent(String line, double speed) {
        String[] splits = line.split(",", -1);
        if (splits.length > 1) {
            String kind = splits[0];
            if (kind.equals("0") || kind.equals("1") || kind.equals("Video")) {
                splits[1] = String.valueOf((int) (parseInt(splits[1]) / speed));
            } else if (kind.equals("2")) {
                splits[1] = String.valueOf((int) (parseInt(splits[1]) / speed));
                if (splits.length > 2) {
                    splits[2] = String.valueOf((int) (parseInt(splits[2]) / speed));
                }
            }
        }
        return join(splits, ",");
    }

    static String processTimingPoint(String line, double speed) {
        String[] splits = line.split(",", -1);
        if (splits.length > 0) {
            splits[0] = String.valueOf((int) (parseInt(splits[0]) / speed));
        }
        if (splits.length > 1 && parseDouble(splits[1]) > 0) {
            splits[1] = String.valueOf(parseDouble(splits[1]) / speed);
        }
        return join(splits, ",");
    }

    static String processHitObject(String line, double speed) {
        String[] splits = line.split(",", -1);
        if (splits.length > 2) {
            splits[2] = String.valueOf((int) (parseInt(splits[2]) / speed));
        }
        String last = splits[splits.length - 1];
        String[] finalSplits = last.split(":", -1);
        if (finalSplits.length > 0) {
            finalSplits[0] = String.valueOf((int) (parseInt(finalSplits[0]) / speed));
        }
        splits[splits.length - 1] = join(finalSplits, ":");
        return join(splits, ",");
    }

    // ------------------------------------------------------------------ 节奏大师 .imd

    public static void generateRotaeno(ImdData data, File outDir, double speed) throws Exception {
        ImdData tmp = data.deepCopy();

        tmp.length = (int) (data.length / speed);

        for (int i = 0; i < tmp.count; i++) {
            tmp.bpmList[i][1] = data.bpmList[i][1] * speed;
            tmp.bpmList[i][0] = (int) (data.bpmList[i][0] / speed);
        }

        for (int i = 0; i < tmp.count2; i++) {
            tmp.notes[i][1] = (int) (data.notes[i][1] / speed);
            if (data.notes[i][0] != 0 && data.notes[i][3] > 3) {
                tmp.notes[i][3] = (int) (data.notes[i][3] / speed);
            }
        }

        // 变速音频
        File musicSrc = new File(outDir, data.songFile);
        String audioName = data.songName + "-" + formatSpeed(speed) + AUDIO_EXT;
        File audioOut = new File(outDir, audioName);
        requireAudioProcessor().process(musicSrc, speed, audioOut);

        // 写出 .imd
        String imdName = data.version + "-" + formatSpeed(speed) + ".imd";
        ImdParser.write(tmp, new File(outDir, imdName));
    }

    // ------------------------------------------------------------------ 工具


    /** 兼容 minSdk 24 的 join 实现（String.join 需要 API 26+） */
    static String join(String[] parts, String sep) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            if (i > 0) sb.append(sep);
            sb.append(parts[i]);
        }
        return sb.toString();
    }

    /** 与 Python f"{speed}" 一致的格式：1.2 → "1.2"，1.0 → "1.0" */
    public static String formatSpeed(double speed) {
        return String.valueOf(speed);
    }

    private static AudioProcessor requireAudioProcessor() {
        return audioProcessor;
    }

    private static int firstIndexOfKey(JSONArray arr, String key) {
        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.optJSONObject(i);
            if (o != null && o.has(key)) {
                return i;
            }
        }
        return -1;
    }

    private static String firstStringValue(JSONArray arr, String key, String def) {
        int i = firstIndexOfKey(arr, key);
        return i >= 0 ? arr.optJSONObject(i).optString(key, def) : def;
    }

    private static int parseInt(String s) {
        try {
            return (int) Double.parseDouble(s.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static double parseDouble(String s) {
        try {
            return Double.parseDouble(s.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
