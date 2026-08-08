package com.example.malodybeatmapspeedchanger.parser;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

/**
 * osu! 谱面 .osu 文本文件解析器。
 *
 * <p>移植自原 Web 后端 backend/osu_parser.py：</p>
 * <ul>
 *   <li>键值对 section（General/Editor/Metadata/Difficulty/Colours）解析为 JSONObject</li>
 *   <li>列表 section（Events/TimingPoints/HitObjects 等）解析为 JSONArray（保留原始行）</li>
 * </ul>
 */
public final class OsuParser {

    private static final Set<String> KEY_PAIR_SECTIONS = new HashSet<>();

    static {
        KEY_PAIR_SECTIONS.add("General");
        KEY_PAIR_SECTIONS.add("Editor");
        KEY_PAIR_SECTIONS.add("Metadata");
        KEY_PAIR_SECTIONS.add("Difficulty");
        KEY_PAIR_SECTIONS.add("Colours");
    }

    private static final String[] OUTPUT_ORDER = {
            "General", "Editor", "Metadata", "Difficulty",
            "Events", "TimingPoints", "Colours", "HitObjects"
    };

    private OsuParser() {
    }

    /** 读取 .osu 文件为 JSONObject 结构 */
    public static JSONObject read(File file) throws Exception {
        JSONObject obj = new JSONObject();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                new FileInputStream(file), StandardCharsets.UTF_8))) {
            String first = reader.readLine();
            obj.put("format", first == null ? "" : first.trim());

            String section = "";
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("//") || line.startsWith("#")) {
                    continue;
                }
                if (line.startsWith("[") && line.endsWith("]")) {
                    section = line.substring(1, line.length() - 1);
                    if (KEY_PAIR_SECTIONS.contains(section)) {
                        obj.put(section, new JSONObject());
                    } else {
                        obj.put(section, new JSONArray());
                    }
                } else if (!section.isEmpty()) {
                    if (KEY_PAIR_SECTIONS.contains(section)) {
                        int idx = line.indexOf(':');
                        if (idx > 0) {
                            String k = line.substring(0, idx).trim();
                            String v = line.substring(idx + 1).trim();
                            obj.getJSONObject(section).put(k, v);
                        }
                    } else {
                        obj.getJSONArray(section).put(line);
                    }
                }
            }
        }
        return obj;
    }

    /** 将 JSONObject 结构写回 .osu 文件 */
    public static void write(JSONObject obj, File out) throws Exception {
        StringBuilder sb = new StringBuilder();
        sb.append(obj.optString("format", "osu file format v14"));
        for (String section : OUTPUT_ORDER) {
            if (!obj.has(section)) {
                continue;
            }
            sb.append("\n\n[").append(section).append("]");
            if (KEY_PAIR_SECTIONS.contains(section)) {
                JSONObject sectionObj = obj.getJSONObject(section);
                Iterator<String> keys = sectionObj.keys();
                while (keys.hasNext()) {
                    String k = keys.next();
                    sb.append('\n').append(k).append(": ").append(sectionObj.optString(k));
                }
            } else {
                JSONArray arr = obj.getJSONArray(section);
                for (int i = 0; i < arr.length(); i++) {
                    sb.append('\n').append(arr.optString(i));
                }
            }
        }
        sb.append('\n');
        try (FileOutputStream fos = new FileOutputStream(out)) {
            fos.write(sb.toString().getBytes(StandardCharsets.UTF_8));
        }
    }
}
