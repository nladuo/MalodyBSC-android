package com.example.malodybeatmapspeedchanger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.example.malodybeatmapspeedchanger.audio.AudioProcessor;
import com.example.malodybeatmapspeedchanger.generator.BeatmapGenerator;
import com.example.malodybeatmapspeedchanger.model.ImdData;
import com.example.malodybeatmapspeedchanger.parser.OsuParser;

import org.json.JSONObject;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/**
 * 生成器单元测试：用假音频处理器（复制文件）验证谱面数据修改逻辑。
 */
public class BeatmapGeneratorTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    @Before
    public void setUp() {
        BeatmapGenerator.audioProcessor = new AudioProcessor() {
            @Override
            public void process(File src, double speed, File dst) throws Exception {
                Files.copy(src.toPath(), dst.toPath());
            }
        };
    }

    // ---------------------------------------------------------------- Malody

    private static final String MC_JSON =
            "{\"meta\":{\"version\":\"Hard\",\"creator\":\"me\",\"id\":123,\"time\":1}," +
            "\"time\":[{\"bpm\":120}],\"note\":[" +
            "{\"sound\":\"song.mp3\"},{\"offset\":48000}]}";

    @Test
    public void malodyGenerationModifiesJson() throws Exception {
        File dir = tmp.newFolder("mc");
        Files.write(new File(dir, "song.mp3").toPath(), new byte[]{1, 2, 3});
        JSONObject json = new JSONObject(MC_JSON);

        BeatmapGenerator.generateMalody(json, dir, 1.5);

        File[] mcs = dir.listFiles((d, n) -> n.endsWith(".mc"));
        assertNotNull(mcs);
        assertEquals(1, mcs.length);
        JSONObject out = new JSONObject(Files.readString(mcs[0].toPath(), StandardCharsets.UTF_8));

        JSONObject meta = out.getJSONObject("meta");
        assertTrue(meta.getString("version").endsWith("-1.5"));
        assertFalse(meta.has("id"));
        assertEquals("nladuo/malody_beatmap_speed_changer", meta.getString("creator"));

        // BPM × 1.5
        assertEquals(180.0, out.getJSONArray("time").getJSONObject(0).getDouble("bpm"), 1e-9);

        // offset ÷ 1.5
        int offset = out.getJSONArray("note").getJSONObject(1).getInt("offset");
        assertEquals(32000, offset);

        // 音频被替换为 .m4a
        String sound = out.getJSONArray("note").getJSONObject(0).getString("sound");
        assertTrue(sound.endsWith(".m4a"));
        assertTrue(new File(dir, sound).exists());
    }

    // ---------------------------------------------------------------- osu!

    @Test
    public void osuGenerationModifiesSections() throws Exception {
        File dir = tmp.newFolder("osu");
        Files.write(new File(dir, "song.mp3").toPath(), new byte[]{1, 2, 3});
        String osu =
                "osu file format v14\n\n" +
                "[General]\nAudioFilename: song.mp3\nPreviewTime: 5000\n\n" +
                "[Metadata]\nTitle:T\nArtist:A\nVersion:Normal\n\n" +
                "[Events]\n0,0,\"bg.jpg\"\n2,1000,2000\n\n" +
                "[TimingPoints]\n0,500,4,2,0,60,1,0\n\n" +
                "[HitObjects]\n100,200,1000,1,0,0:0:0:0:\n300,200,5000,128,0,2000:0:0:0:";
        File osuFile = tmp.newFile("test.osu");
        Files.write(osuFile.toPath(), osu.getBytes(StandardCharsets.UTF_8));

        BeatmapGenerator.generateOsu(OsuParser.read(osuFile), dir, 2.0);

        File[] osus = dir.listFiles((d, n) -> n.endsWith(".osu"));
        assertNotNull(osus);
        assertEquals(1, osus.length);
        String text = Files.readString(osus[0].toPath(), StandardCharsets.UTF_8);

        assertTrue(text.contains("Version: Normal-2.0"));
        assertTrue(text.contains("AudioFilename:"));
        assertTrue(text.contains(".m4a"));
        assertTrue(text.contains("PreviewTime: -1"));

        // Events: 2,1000,2000 → 2,500,1000
        assertTrue(text.contains("2,500,1000"));
        // TimingPoints: 0,500 → 0,250（Python 风格 float 除法输出 250.0）
        assertTrue(text.contains("0,250.0,4,2,0,60,1,0"));
        // HitObjects: 1000 → 500；endTime 2000 → 1000
        assertTrue(text.contains("100,200,500,1,0,0:0:0:0:"));
        assertTrue(text.contains("300,200,2500,128,0,1000:0:0:0:"));
    }

    // ---------------------------------------------------------------- 节奏大师 .imd

    @Test
    public void rotaenoGenerationModifiesBinary() throws Exception {
        File dir = tmp.newFolder("imd");
        Files.write(new File(dir, "song.mp3").toPath(), new byte[]{1, 2, 3});

        ImdData data = new ImdData();
        data.version = "song_4k_Hard";
        data.songName = "song";
        data.songFile = "song.mp3";
        data.length = 180000;
        data.count = 1;
        data.bpmList = new double[][]{{0, 120.0}};
        data.flag = 0;
        data.count2 = 2;
        data.notes = new int[][]{{1, 10000, 0, 4}, {2, 20000, 1, 2}};

        BeatmapGenerator.generateRotaeno(data, dir, 1.2);

        File[] imds = dir.listFiles((d, n) -> n.endsWith(".imd"));
        assertNotNull(imds);
        assertEquals(1, imds.length);

        // 用解析器读回并验证
        ImdData out = com.example.malodybeatmapspeedchanger.parser.ImdParser.read(imds[0]);
        assertEquals(150000, out.length);                       // 180000 / 1.2
        assertEquals(144.0, out.bpmList[0][1], 1e-9);           // 120 * 1.2
        assertEquals(0, out.bpmList[0][0], 0.0);                // 0 / 1.2
        assertEquals(8333, out.notes[0][1]);                    // 10000 / 1.2
        assertEquals(3, out.notes[0][3]);                       // 4 / 1.2 → int
        assertEquals(16666, out.notes[1][1]);                   // 20000 / 1.2
        assertEquals(2, out.notes[1][3]);                       // param=2 ≤ 3 不修改

        // 音频已生成
        File[] audios = dir.listFiles((d, n) -> n.endsWith(".m4a"));
        assertNotNull(audios);
        assertEquals(1, audios.length);
    }
}
