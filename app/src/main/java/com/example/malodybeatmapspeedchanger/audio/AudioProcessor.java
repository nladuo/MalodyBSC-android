package com.example.malodybeatmapspeedchanger.audio;

import java.io.File;

/**
 * 音频变速处理器接口。
 *
 * <p>App 内默认使用 {@link AudioSpeedChanger}（MediaCodec + WSOLA）；
 * 单元测试中可注入假实现，避免依赖 Android 编解码器。</p>
 */
public interface AudioProcessor {

    /**
     * 将音频 src 变速为 speed 倍（保持音调），写出到 dst。
     *
     * @param src   源音频文件（mp3/m4a/ogg/wav 等）
     * @param speed 播放速度倍率，如 1.2 表示变快 1.2 倍
     * @param dst   输出文件
     */
    void process(File src, double speed, File dst) throws Exception;
}
