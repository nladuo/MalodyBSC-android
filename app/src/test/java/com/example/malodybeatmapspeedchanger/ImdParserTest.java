package com.example.malodybeatmapspeedchanger;

import static org.junit.Assert.assertEquals;

import com.example.malodybeatmapspeedchanger.model.ImdData;
import com.example.malodybeatmapspeedchanger.parser.ImdParser;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;

public class ImdParserTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    @Test
    public void binaryRoundTrip() throws Exception {
        ImdData data = new ImdData();
        data.version = "song_4k_HD";
        data.songName = "song";
        data.songFile = "song.mp3";
        data.length = 180000;
        data.count = 2;
        data.bpmList = new double[][]{{0, 120.0}, {90000, 60.5}};
        data.flag = 0;
        data.count2 = 3;
        data.notes = new int[][]{
                {1, 1000, 0, 4},
                {2, 2000, 1, 2},
                {3, 3000, 2, 5},
        };

        File out = tmp.newFile("song_4k_HD.imd");
        ImdParser.write(data, out);

        ImdData read = ImdParser.read(out);
        assertEquals("song_4k_HD", read.version);
        assertEquals("song", read.songName);
        assertEquals("song.mp3", read.songFile);
        assertEquals(180000, read.length);
        assertEquals(2, read.count);
        assertEquals(0.0, read.bpmList[0][0], 0.0);
        assertEquals(120.0, read.bpmList[0][1], 1e-9);
        assertEquals(90000.0, read.bpmList[1][0], 0.0);
        assertEquals(60.5, read.bpmList[1][1], 1e-9);
        assertEquals(0, read.flag);
        assertEquals(3, read.count2);
        assertEquals(1, read.notes[0][0]);
        assertEquals(1000, read.notes[0][1]);
        assertEquals(0, read.notes[0][2]);
        assertEquals(4, read.notes[0][3]);
        assertEquals(3000, read.notes[2][1]);
        assertEquals(5, read.notes[2][3]);
    }

    @Test
    public void headerDerivation() {
        ImdData data = ImdParser.createHeader("dir/song_4k_Hard.imd");
        assertEquals("song_4k_Hard", data.version);
        assertEquals("song", data.songName);
        assertEquals("song.mp3", data.songFile);
    }
}
