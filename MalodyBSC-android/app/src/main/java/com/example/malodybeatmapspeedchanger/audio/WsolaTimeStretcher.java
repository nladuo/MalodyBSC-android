package com.example.malodybeatmapspeedchanger.audio;

import java.util.Arrays;

/**
 * 纯 Java 实现的 WSOLA（Waveform Similarity Overlap-Add）音频变速器。
 *
 * <p>替代原 Web 后端使用的 ffmpeg {@code atempo} 滤镜，保持音调不变地改变播放速度。
 * 适用于 0.5x ~ 2.0x 的调速范围（与原前端 50%~200% 滑杆一致）。</p>
 *
 * <p>算法要点：</p>
 * <ul>
 *   <li>分析帧长约 60ms，50% 重叠（synHop = frame/2）</li>
 *   <li>理想分析起点按 analysisHop = synHop × speed 步进</li>
 *   <li>在理想位置附近搜索与输出重叠区互相关最大的位置，进行交叉淡化叠加</li>
 * </ul>
 */
public final class WsolaTimeStretcher {

    /** 进度回调 */
    public interface ProgressListener {
        /** @param fraction 0.0 ~ 1.0 */
        void onProgress(float fraction);
    }

    private WsolaTimeStretcher() {
    }

    /**
     * 对单声道音频做变速（保持音调）。
     *
     * @param input      输入采样（-1.0 ~ 1.0）
     * @param sampleRate 采样率
     * @param speed      播放倍率：1.2 表示变快 1.2 倍（时长变为 1/1.2）
     * @return 变速后的采样
     */
    public static float[] process(float[] input, int sampleRate, double speed) {
        return process(input, sampleRate, speed, null);
    }

    public static float[] process(float[] input, int sampleRate, double speed, ProgressListener listener) {
        if (input == null || input.length < sampleRate / 20) {
            // 音频太短，直接返回，避免越界
            return input;
        }
        if (Math.abs(speed - 1.0) < 0.01) {
            return Arrays.copyOf(input, input.length);
        }

        int frame = Math.max(256, (int) (sampleRate * 0.06)); // 60ms
        int overlap = frame / 2;
        int synHop = frame - overlap; // == overlap（50%）
        double analysisHop = synHop * speed;

        // 搜索半径：至少 10ms，随速度差增大而增大
        int searchRadius = (int) Math.max(sampleRate * 0.01,
                synHop * Math.abs(speed - 1.0)) + 1;
        searchRadius = Math.min(searchRadius, frame);

        int expectedLen = (int) (input.length / speed) + frame + 1;
        float[] out = new float[Math.max(expectedLen, frame)];
        int outLen;

        // 第一帧直接复制
        int firstLen = Math.min(frame, input.length);
        System.arraycopy(input, 0, out, 0, firstLen);
        outLen = firstLen;

        int synPos = synHop;
        int anPos = (int) Math.round(analysisHop);
        long frames = 0;
        long totalFrames = Math.max(1, (input.length - frame) / Math.max(1, synHop));

        while (anPos + frame <= input.length && synPos + frame <= out.length) {
            int maxHi = input.length - overlap;
            int lo = Math.max(0, Math.min(anPos - searchRadius, maxHi));
            int hi = Math.max(lo, Math.min(maxHi, anPos + searchRadius));
            int best = searchBest(input, lo, hi, out, synPos, overlap);

            // 交叉淡化叠加
            for (int k = 0; k < overlap; k++) {
                float w = k / (float) (overlap - 1);
                out[synPos + k] = out[synPos + k] * (1f - w) + input[best + k] * w;
            }
            for (int k = overlap; k < frame; k++) {
                out[synPos + k] = input[best + k];
            }
            outLen = synPos + frame;

            anPos = best + (int) Math.round(analysisHop);
            synPos += synHop;
            frames++;
            if (listener != null && (frames % 64 == 0 || frames == totalFrames)) {
                listener.onProgress(Math.min(1f, frames / (float) totalFrames));
            }
        }

        // 尾部：交叉淡化剩余输入
        if (anPos < input.length && outLen < out.length) {
            int remain = input.length - anPos;
            int space = out.length - outLen;
            int cross = Math.min(overlap, Math.min(remain, space));
            for (int k = 0; k < cross; k++) {
                float w = k / (float) Math.max(1, cross - 1);
                out[outLen + k] = out[outLen + k] * (1f - w) + input[anPos + k] * w;
            }
            int copy = Math.min(remain, space) - cross;
            if (copy > 0) {
                System.arraycopy(input, anPos + cross, out, outLen + cross, copy);
            }
            outLen = Math.min(out.length, outLen + Math.min(remain, space));
        }

        if (listener != null) {
            listener.onProgress(1f);
        }
        return Arrays.copyOf(out, Math.max(outLen, 1));
    }

    /** 两阶段搜索：先粗搜（步长 8），再在最优附近细搜 */
    private static int searchBest(float[] input, int lo, int hi,
                                  float[] out, int outPos, int overlap) {
        int best = lo;
        double bestScore = -1.0;
        for (int p = lo; p <= hi; p += 8) {
            double s = correlation(input, p, out, outPos, overlap);
            if (s > bestScore) {
                bestScore = s;
                best = p;
            }
        }
        int fineLo = Math.max(lo, best - 8);
        int fineHi = Math.min(hi, best + 8);
        for (int p = fineLo; p <= fineHi; p++) {
            double s = correlation(input, p, out, outPos, overlap);
            if (s > bestScore) {
                bestScore = s;
                best = p;
            }
        }
        return best;
    }

    /** 归一化互相关 */
    private static double correlation(float[] a, int aPos, float[] b, int bPos, int n) {
        double dot = 0.0, ea = 0.0, eb = 0.0;
        for (int k = 0; k < n; k++) {
            float x = a[aPos + k];
            float y = b[bPos + k];
            dot += x * y;
            ea += x * x;
            eb += y * y;
        }
        if (ea < 1e-9 || eb < 1e-9) {
            return 0.0;
        }
        return dot / Math.sqrt(ea * eb);
    }
}
