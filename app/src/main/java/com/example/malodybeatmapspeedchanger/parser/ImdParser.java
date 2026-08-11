package com.example.malodybeatmapspeedchanger.parser;

import com.example.malodybeatmapspeedchanger.model.ImdData;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * 节奏大师二进制谱面 .imd 解析器。
 *
 * <p>移植自原 Web 后端 backend/imd_parser.py。文件为小端序：</p>
 * <ul>
 *   <li>int  length</li>
 *   <li>int  count</li>
 *   <li>count 组：int t + double bpm</li>
 *   <li>short flag + int count2</li>
 *   <li>count2 组：short action + int time + byte track + int param</li>
 * </ul>
 */
public final class ImdParser {

    private ImdParser() {
    }

    /** 由 .imd 文件名推导 version / song_name / song_file（与 Python 版本一致） */
    public static ImdData createHeader(String fileName) {
        String name = fileName;
        int slash = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
        if (slash >= 0) {
            name = name.substring(slash + 1);
        }
        if (name.endsWith(".imd")) {
            name = name.substring(0, name.length() - 4);
        }
        ImdData data = new ImdData();
        data.version = name;
        String base = name.split("_")[0].split("-")[0].split("\\.")[0];
        data.songName = base;
        data.songFile = base + ".mp3";
        return data;
    }

    /** 读取 .imd 二进制文件 */
    public static ImdData read(File file) throws IOException {
        ImdData data = createHeader(file.getName());
        byte[] bytes;
        try (FileInputStream fis = new FileInputStream(file)) {
            bytes = readAll(fis);
        }
        ByteBuffer buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        data.length = buf.getInt();
        data.count = buf.getInt();
        data.bpmList = new double[data.count][2];
        for (int i = 0; i < data.count; i++) {
            data.bpmList[i][0] = buf.getInt();
            data.bpmList[i][1] = buf.getDouble();
        }
        data.flag = buf.getShort();
        data.count2 = buf.getInt();
        data.notes = new int[data.count2][4];
        for (int i = 0; i < data.count2; i++) {
            data.notes[i][0] = buf.getShort();
            data.notes[i][1] = buf.getInt();
            data.notes[i][2] = buf.get();
            data.notes[i][3] = buf.getInt();
        }
        return data;
    }

    /** 写出 .imd 二进制文件 */
    public static void write(ImdData data, File out) throws IOException {
        int size = 4 + 4 + data.count * 12 + 2 + 4 + data.count2 * 11;
        ByteBuffer buf = ByteBuffer.allocate(size).order(ByteOrder.LITTLE_ENDIAN);
        buf.putInt(data.length);
        buf.putInt(data.count);
        for (int i = 0; i < data.count; i++) {
            buf.putInt((int) data.bpmList[i][0]);
            buf.putDouble(data.bpmList[i][1]);
        }
        buf.putShort(data.flag);
        buf.putInt(data.count2);
        for (int i = 0; i < data.count2; i++) {
            buf.putShort((short) data.notes[i][0]);
            buf.putInt(data.notes[i][1]);
            buf.put((byte) data.notes[i][2]);
            buf.putInt(data.notes[i][3]);
        }
        try (FileOutputStream fos = new FileOutputStream(out)) {
            fos.write(buf.array());
        }
    }

    private static byte[] readAll(InputStream in) throws IOException {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int n;
        while ((n = in.read(buffer)) > 0) {
            out.write(buffer, 0, n);
        }
        return out.toByteArray();
    }
}
