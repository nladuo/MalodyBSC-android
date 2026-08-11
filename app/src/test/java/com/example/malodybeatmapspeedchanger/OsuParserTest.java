package com.example.malodybeatmapspeedchanger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.example.malodybeatmapspeedchanger.parser.OsuParser;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

public class OsuParserTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    private static final String SAMPLE =
            "osu file format v14\n" +
            "\n" +
            "[General]\n" +
            "AudioFilename: song.mp3\n" +
            "AudioLeadIn: 0\n" +
            "PreviewTime: 5000\n" +
            "Mode: 3\n" +
            "\n" +
            "[Metadata]\n" +
            "Title:Test Song\n" +
            "Artist:Someone\n" +
            "Version:Normal\n" +
            "\n" +
            "[Difficulty]\n" +
            "HPDrainRate:5\n" +
            "OverallDifficulty:5\n" +
            "\n" +
            "[Events]\n" +
            "0,0,\"bg.jpg\"\n" +
            "2,1000,2000\n" +
            "\n" +
            "[TimingPoints]\n" +
            "0,500,4,2,0,60,1,0\n" +
            "5000,500,4,2,0,60,1,0\n" +
            "\n" +
            "[HitObjects]\n" +
            "100,200,1000,1,0,0:0:0:0:\n" +
            "300,200,5000,128,0,2000:0:0:0:";

    @Test
    public void readParsesSections() throws Exception {
        File f = tmp.newFile("test.osu");
        Files.write(f.toPath(), SAMPLE.getBytes(StandardCharsets.UTF_8));
        JSONObject obj = OsuParser.read(f);

        assertEquals("osu file format v14", obj.getString("format"));
        assertEquals("song.mp3", obj.getJSONObject("General").getString("AudioFilename"));
        assertEquals("Normal", obj.getJSONObject("Metadata").getString("Version"));
        assertEquals("5", obj.getJSONObject("Difficulty").getString("OverallDifficulty"));

        JSONArray events = obj.getJSONArray("Events");
        assertEquals(2, events.length());
        assertEquals("0,0,\"bg.jpg\"", events.getString(0));

        JSONArray timing = obj.getJSONArray("TimingPoints");
        assertEquals(2, timing.length());

        JSONArray hits = obj.getJSONArray("HitObjects");
        assertEquals(2, hits.length());
    }

    @Test
    public void writeRoundTrip() throws Exception {
        File f = tmp.newFile("in.osu");
        Files.write(f.toPath(), SAMPLE.getBytes(StandardCharsets.UTF_8));
        JSONObject obj = OsuParser.read(f);

        File out = tmp.newFile("out.osu");
        OsuParser.write(obj, out);

        JSONObject reRead = OsuParser.read(out);
        assertEquals("Normal", reRead.getJSONObject("Metadata").getString("Version"));
        assertEquals("song.mp3", reRead.getJSONObject("General").getString("AudioFilename"));
        assertTrue(reRead.getJSONArray("Events").length() >= 2);
        assertTrue(reRead.getJSONArray("TimingPoints").length() >= 2);
    }
}
