package com.example.malodybeatmapspeedchanger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.example.malodybeatmapspeedchanger.audio.WsolaTimeStretcher;

import org.junit.Test;

/**
 * WSOLA 变速器测试：验证输出时长 ≈ 输入/speed，且音调（主频）保持不变。
 */
public class WsolaTimeStretcherTest {

    private static final int SAMPLE_RATE = 44100;
    private static final double FREQ = 440.0;

    private float[] sine(double seconds) {
        int n = (int) (seconds * SAMPLE_RATE);
        float[] out = new float[n];
        for (int i = 0; i < n; i++) {
            out[i] = (float) (0.8 * Math.sin(2 * Math.PI * FREQ * i / SAMPLE_RATE));
        }
        return out;
    }

    /** 通过过零率估计主频（简单可靠） */
    private double estimateFrequency(float[] samples) {
        // 取中间 1/2 段，避免首尾过渡
        int start = samples.length / 4;
        int end = samples.length * 3 / 4;
        int zeroCrossings = 0;
        for (int i = start + 1; i < end; i++) {
            if ((samples[i - 1] < 0 && samples[i] >= 0) || (samples[i - 1] >= 0 && samples[i] < 0)) {
                zeroCrossings++;
            }
        }
        double seconds = (double) (end - start) / SAMPLE_RATE;
        return zeroCrossings / 2.0 / seconds;
    }

    private void assertStretched(float[] input, double speed) {
        long start = System.currentTimeMillis();
        float[] out = WsolaTimeStretcher.process(input, SAMPLE_RATE, speed);
        long elapsed = System.currentTimeMillis() - start;

        double expectedLen = input.length / speed;
        double ratio = out.length / expectedLen;
        assertTrue("时长偏差过大: " + ratio + " (speed=" + speed + ")", ratio > 0.93 && ratio < 1.07);

        double freq = estimateFrequency(out);
        double err = Math.abs(freq - FREQ) / FREQ;
        assertTrue("音调偏差过大: " + freq + " (speed=" + speed + ", err=" + err + ")", err < 0.02);

        System.out.println("speed=" + speed + " in=" + input.length + " out=" + out.length
                + " freq=" + freq + "Hz time=" + elapsed + "ms");
    }

    @Test
    public void speedUpPreservesPitch() {
        assertStretched(sine(2.0), 1.5);
    }

    @Test
    public void slowDownPreservesPitch() {
        assertStretched(sine(2.0), 0.5);
    }

    @Test
    public void doubleSpeedPreservesPitch() {
        assertStretched(sine(2.0), 2.0);
    }

    @Test
    public void nearNoChangeReturnsCopy() {
        float[] input = sine(0.5);
        float[] out = WsolaTimeStretcher.process(input, SAMPLE_RATE, 1.005);
        assertEquals(input.length, out.length);
    }

    @Test
    public void veryShortInputSafe() {
        float[] input = new float[]{0f, 0.1f, -0.1f, 0.2f};
        float[] out = WsolaTimeStretcher.process(input, SAMPLE_RATE, 1.5);
        assertEquals(input.length, out.length);
    }
}
