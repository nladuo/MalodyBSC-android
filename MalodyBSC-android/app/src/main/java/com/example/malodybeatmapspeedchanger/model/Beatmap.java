package com.example.malodybeatmapspeedchanger.model;

import org.json.JSONObject;

import java.io.File;

/**
 * 谱面条目：档案（.mcz/.osz/.zip）中解析出的单个谱面。
 *
 * <p>对应原 Web 后端 get_beatmaps() 返回的 beatmap 字典。</p>
 */
public class Beatmap {

    /** 谱面类型：mc = Malody，osu = osu!，rm = 节奏大师（.imd） */
    public final String type;

    /** 显示用的版本/难度名 */
    public final String version;

    /** 谱面文件所在目录（生成结果也输出到此目录） */
    public final File outDir;

    /** 当 type == "mc" 时的 Malody JSON 数据 */
    public final JSONObject mcData;

    /** 当 type == "osu" 时的 osu 数据 */
    public final JSONObject osuData;

    /** 当 type == "rm" 时的 imd 二进制数据 */
    public final ImdData imdData;

    public Beatmap(String type, String version, File outDir,
                   JSONObject mcData, JSONObject osuData, ImdData imdData) {
        this.type = type;
        this.version = version;
        this.outDir = outDir;
        this.mcData = mcData;
        this.osuData = osuData;
        this.imdData = imdData;
    }

    /** 生成结果归档的后缀：mc→mcz，osu→osz，rm→zip */
    public String outputExtension() {
        switch (type) {
            case "mc":
                return "mcz";
            case "osu":
                return "osz";
            default:
                return "zip";
        }
    }
}
