package com.example.malodybeatmapspeedchanger.util;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;

import com.example.malodybeatmapspeedchanger.generator.BeatmapGenerator;
import com.example.malodybeatmapspeedchanger.model.Beatmap;
import com.example.malodybeatmapspeedchanger.model.ImdData;
import com.example.malodybeatmapspeedchanger.parser.ImdParser;
import com.example.malodybeatmapspeedchanger.parser.OsuParser;

import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/**
 * 谱面档案（.mcz/.msz/.osz/.zip）的解析与生成。
 *
 * <p>移植自原 Web 后端 backend/beatmap_helper.py 的
 * get_beatmaps / create_tmp_dir / generate_beatmaps / write_file_recursively：</p>
 * <ol>
 *   <li>将用户选择的谱面文件解压到缓存工作目录</li>
 *   <li>遍历 zip 条目，识别 .mc / .osu / .imd 并解析为 {@link Beatmap} 列表</li>
 *   <li>调速生成（写入各谱面所在目录）</li>
 *   <li>把整个工作目录重新打包为 .mcz/.osz/.zip 输出</li>
 * </ol>
 */
public class BeatmapArchive {

    private final Context context;
    private File workDir;
    private String originFileName;
    private final List<Beatmap> beatmaps = new ArrayList<>();

    private BeatmapArchive(Context context) {
        this.context = context.getApplicationContext();
    }

    public File getWorkDir() {
        return workDir;
    }

    public String getOriginFileName() {
        return originFileName;
    }

    public List<Beatmap> getBeatmaps() {
        return beatmaps;
    }

    /** 从 SAF content Uri 导入并解析 */
    public static BeatmapArchive open(Context context, Uri uri) throws Exception {
        String name = queryDisplayName(context, uri);
        File src = copyToCache(context, uri, name);
        return open(context, src);
    }

    /** 从本地文件导入并解析 */
    public static BeatmapArchive open(Context context, File file) throws Exception {
        BeatmapArchive archive = new BeatmapArchive(context);
        archive.originFileName = file.getName();
        archive.workDir = new File(context.getCacheDir(), "work/" + UUID.randomUUID());
        if (!archive.workDir.mkdirs() && !archive.workDir.isDirectory()) {
            throw new IOException("无法创建工作目录: " + archive.workDir);
        }
        try (ZipFile zf = new ZipFile(file)) {
            archive.discoverAndExtract(zf);
        }
        return archive;
    }

    /** 解析 zip 内的谱面并解压全部内容（音频/图片等也会用到） */
    private void discoverAndExtract(ZipFile zf) throws Exception {
        Enumeration<? extends ZipEntry> entries = zf.entries();
        while (entries.hasMoreElements()) {
            ZipEntry entry = entries.nextElement();
            if (entry.isDirectory()) {
                continue;
            }
            String name = entry.getName();
            File outFile = safeResolve(workDir, name);
            extractEntry(zf, entry, outFile);

            if (name.contains(".mc")) {
                JSONObject json = new JSONObject(readText(outFile));
                String version = json.optJSONObject("meta").optString("version", name);
                beatmaps.add(new Beatmap("mc", version, outFile.getParentFile(), json, null, null));
            } else if (name.contains(".osu")) {
                JSONObject json = OsuParser.read(outFile);
                JSONObject meta = json.optJSONObject("Metadata");
                String version = meta != null ? meta.optString("Version", name) : name;
                beatmaps.add(new Beatmap("osu", version, outFile.getParentFile(), null, json, null));
            } else if (name.contains(".imd")) {
                ImdData imd = ImdParser.read(outFile);
                beatmaps.add(new Beatmap("rm", imd.version, outFile.getParentFile(), null, null, imd));
            }
        }
    }

    /** 对指定谱面执行调速，并将工作目录打包为输出档案 */
    public File generate(int index, double speed) throws Exception {
        Beatmap beatmap = beatmaps.get(index);
        BeatmapGenerator.generate(beatmap, speed);
        return pack(beatmap.outputExtension());
    }

    /** 递归打包工作目录（保持相对路径），对应 write_file_recursively */
    private File pack(String ext) throws IOException {
        File outDir = new File(context.getCacheDir(), "out");
        if (!outDir.exists() && !outDir.mkdirs()) {
            throw new IOException("无法创建输出目录: " + outDir);
        }
        File out = new File(outDir, UUID.randomUUID() + "." + ext);
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(out))) {
            addRecursively(zos, workDir, "");
        }
        return out;
    }

    private void addRecursively(ZipOutputStream zos, File dir, String relative) throws IOException {
        File[] files = dir.listFiles();
        if (files == null) {
            return;
        }
        for (File f : files) {
            String path = relative.isEmpty() ? f.getName() : relative + "/" + f.getName();
            if (f.isDirectory()) {
                addRecursively(zos, f, path);
            } else {
                ZipEntry entry = new ZipEntry(path);
                zos.putNextEntry(entry);
                try (InputStream in = new java.io.FileInputStream(f)) {
                    byte[] buffer = new byte[8192];
                    int n;
                    while ((n = in.read(buffer)) > 0) {
                        zos.write(buffer, 0, n);
                    }
                }
                zos.closeEntry();
            }
        }
    }

    // ------------------------------------------------------------------ 工具

    private static File safeResolve(File base, String name) throws IOException {
        File f = new File(base, name);
        String basePath = base.getCanonicalPath();
        String filePath = f.getCanonicalPath();
        if (!filePath.startsWith(basePath + File.separator) && !filePath.equals(basePath)) {
            throw new IOException("非法 zip 路径: " + name);
        }
        File parent = f.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IOException("无法创建目录: " + parent);
        }
        return f;
    }

    private static void extractEntry(ZipFile zf, ZipEntry entry, File outFile) throws IOException {
        try (InputStream in = zf.getInputStream(entry);
             OutputStream out = new FileOutputStream(outFile)) {
            byte[] buffer = new byte[8192];
            int n;
            while ((n = in.read(buffer)) > 0) {
                out.write(buffer, 0, n);
            }
        }
    }

    private static String readText(File file) throws IOException {
        byte[] bytes;
        try (InputStream in = new java.io.FileInputStream(file)) {
            java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int n;
            while ((n = in.read(buffer)) > 0) {
                bos.write(buffer, 0, n);
            }
            bytes = bos.toByteArray();
        }
        return new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
    }

    private static File copyToCache(Context context, Uri uri, String name) throws IOException {
        File dir = new File(context.getCacheDir(), "input");
        if (!dir.exists() && !dir.mkdirs()) {
            throw new IOException("无法创建输入目录: " + dir);
        }
        File target = new File(dir, name);
        try (InputStream in = context.getContentResolver().openInputStream(uri);
             OutputStream out = new FileOutputStream(target)) {
            if (in == null) {
                throw new IOException("无法读取所选文件");
            }
            byte[] buffer = new byte[8192];
            int n;
            while ((n = in.read(buffer)) > 0) {
                out.write(buffer, 0, n);
            }
        }
        return target;
    }

    private static String queryDisplayName(Context context, Uri uri) {
        String name = null;
        try (Cursor cursor = context.getContentResolver().query(uri, null, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (idx >= 0) {
                    name = cursor.getString(idx);
                }
            }
        } catch (Exception ignored) {
        }
        if (name == null || name.isEmpty()) {
            name = uri.getLastPathSegment();
        }
        if (name == null || name.isEmpty()) {
            name = "beatmap.zip";
        }
        return name;
    }

    /** 小写扩展名，例如 "mcz" */
    public static String extensionOf(String fileName) {
        int idx = fileName.lastIndexOf('.');
        if (idx < 0 || idx == fileName.length() - 1) {
            return "";
        }
        return fileName.substring(idx + 1).toLowerCase(Locale.ROOT);
    }
}
