package com.example.malodybeatmapspeedchanger.model;

import java.util.Arrays;

/**
 * 节奏大师（Rotaeno 系）二进制谱面 .imd 的数据模型。
 *
 * <p>对应原 Web 后端 backend/imd_parser.py 中解析出的字典结构。</p>
 *
 * <p>二进制布局（全部小端）：</p>
 * <ul>
 *   <li>int  length：谱面总时长</li>
 *   <li>int  count：BPM 段数量</li>
 *   <li>count 组：int t + double bpm</li>
 *   <li>short flag</li>
 *   <li>int  count2：音符数量</li>
 *   <li>count2 组：short action + int time + byte track + int param</li>
 * </ul>
 */
public class ImdData {

    /** 谱面版本号（由 .imd 文件名推导） */
    public String version = "";

    /** 音频文件名（由文件名推导，例如 song.mp3） */
    public String songFile = "";

    /** 歌曲名（由文件名推导） */
    public String songName = "";

    public int length;
    public int count;

    /** bpm 段：每行 [t, bpm] */
    public double[][] bpmList;

    public short flag;
    public int count2;

    /** 音符：每行 [action, time, track, param] */
    public int[][] notes;

    public ImdData deepCopy() {
        ImdData copy = new ImdData();
        copy.version = version;
        copy.songFile = songFile;
        copy.songName = songName;
        copy.length = length;
        copy.count = count;
        copy.flag = flag;
        copy.count2 = count2;
        if (bpmList != null) {
            copy.bpmList = new double[bpmList.length][];
            for (int i = 0; i < bpmList.length; i++) {
                copy.bpmList[i] = Arrays.copyOf(bpmList[i], bpmList[i].length);
            }
        }
        if (notes != null) {
            copy.notes = new int[notes.length][];
            for (int i = 0; i < notes.length; i++) {
                copy.notes[i] = Arrays.copyOf(notes[i], notes[i].length);
            }
        }
        return copy;
    }
}
