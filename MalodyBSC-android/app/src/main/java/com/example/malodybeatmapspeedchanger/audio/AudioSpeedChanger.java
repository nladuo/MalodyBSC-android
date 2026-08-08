package com.example.malodybeatmapspeedchanger.audio;

import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;

/**
 * 纯原生音频变速器：解码 → WSOLA 变速 → AAC 编码。
 *
 * <p>替代原 Web 后端的 ffmpeg，完全使用 Android MediaCodec / MediaMuxer：
 * 任意可解码格式（mp3/m4a/ogg/wav）→ PCM → {@link WsolaTimeStretcher} 变速 → .m4a。</p>
 */
public class AudioSpeedChanger implements AudioProcessor {

    private static final String TAG = "AudioSpeedChanger";
    private static final String MIME_AAC = MediaFormat.MIMETYPE_AUDIO_AAC;
    private static final int AAC_FRAME_SAMPLES = 1024;

    /** 进度回调（可选） */
    public interface ProgressListener {
        /** @param fraction 0.0 ~ 1.0，阶段细分见实现 */
        void onProgress(float fraction);
    }

    private final ProgressListener listener;

    public AudioSpeedChanger() {
        this(null);
    }

    public AudioSpeedChanger(ProgressListener listener) {
        this.listener = listener;
    }

    @Override
    public void process(File src, double speed, File dst) throws Exception {
        DecodedAudio decoded = decode(src);
        if (listener != null) {
            listener.onProgress(0.3f);
        }

        float[][] channels = toFloatChannels(decoded.pcm, decoded.channels);
        float[][] outChannels = new float[channels.length][];
        for (int c = 0; c < channels.length; c++) {
            final int channel = c;
            outChannels[c] = WsolaTimeStretcher.process(channels[c], decoded.sampleRate, speed,
                    f -> {
                        if (listener != null) {
                            // 变速阶段占 30%~80%
                            listener.onProgress(0.3f + 0.5f * (channel + f) / channels.length);
                        }
                    });
        }

        short[] interleaved = toShortInterleaved(outChannels);
        if (listener != null) {
            listener.onProgress(0.82f);
        }
        encode(dst, decoded.sampleRate, decoded.channels, interleaved);
        if (listener != null) {
            listener.onProgress(1f);
        }
    }

    // ------------------------------------------------------------------ 解码

    private static class DecodedAudio {
        final byte[] pcm;
        final int sampleRate;
        final int channels;

        DecodedAudio(byte[] pcm, int sampleRate, int channels) {
            this.pcm = pcm;
            this.sampleRate = sampleRate;
            this.channels = channels;
        }
    }

    private static DecodedAudio decode(File file) throws IOException {
        MediaExtractor extractor = new MediaExtractor();
        extractor.setDataSource(file.getAbsolutePath());

        int trackIndex = -1;
        String mime = null;
        for (int i = 0; i < extractor.getTrackCount(); i++) {
            MediaFormat f = extractor.getTrackFormat(i);
            String m = f.getString(MediaFormat.KEY_MIME);
            if (m != null && m.startsWith("audio/")) {
                trackIndex = i;
                mime = m;
                break;
            }
        }
        if (trackIndex < 0) {
            extractor.release();
            throw new IOException("文件中未找到音频轨道: " + file.getName());
        }
        extractor.selectTrack(trackIndex);
        MediaFormat format = extractor.getTrackFormat(trackIndex);
        int sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE);
        int channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT);

        MediaCodec decoder = MediaCodec.createDecoderByType(mime);
        decoder.configure(format, null, null, 0);
        decoder.start();

        ByteArrayOutputStream pcm = new ByteArrayOutputStream();
        MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
        boolean inputDone = false;
        boolean outputDone = false;
        int idleRounds = 0;

        while (!outputDone) {
            if (!inputDone) {
                int inIndex = decoder.dequeueInputBuffer(10_000);
                if (inIndex >= 0) {
                    ByteBuffer buf = decoder.getInputBuffer(inIndex);
                    int sampleSize = extractor.readSampleData(buf, 0);
                    if (sampleSize < 0) {
                        decoder.queueInputBuffer(inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                        inputDone = true;
                    } else {
                        decoder.queueInputBuffer(inIndex, 0, sampleSize, extractor.getSampleTime(), 0);
                        extractor.advance();
                    }
                }
            }

            int outIndex = decoder.dequeueOutputBuffer(info, 10_000);
            if (outIndex >= 0) {
                idleRounds = 0;
                if (info.size > 0) {
                    ByteBuffer buf = decoder.getOutputBuffer(outIndex);
                    byte[] chunk = new byte[info.size];
                    buf.position(info.offset);
                    buf.limit(info.offset + info.size);
                    buf.get(chunk);
                    pcm.write(chunk);
                }
                boolean eos = (info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0;
                decoder.releaseOutputBuffer(outIndex, false);
                if (eos) {
                    outputDone = true;
                }
            } else if (outIndex == MediaCodec.INFO_TRY_AGAIN_LATER) {
                if (inputDone) {
                    idleRounds++;
                    if (idleRounds > 50) {
                        Log.w(TAG, "解码输出超时，提前结束");
                        break;
                    }
                }
            }
        }

        decoder.stop();
        decoder.release();
        extractor.release();

        if (pcm.size() == 0) {
            throw new IOException("解码失败：未得到 PCM 数据: " + file.getName());
        }
        return new DecodedAudio(pcm.toByteArray(), sampleRate, channels);
    }

    // ------------------------------------------------------------------ PCM 转换

    private static float[][] toFloatChannels(byte[] pcm, int channels) {
        int sampleCount = pcm.length / 2;
        int frames = sampleCount / channels;
        float[][] out = new float[channels][frames];
        ByteBuffer bb = ByteBuffer.wrap(pcm).order(ByteOrder.LITTLE_ENDIAN);
        for (int f = 0; f < frames; f++) {
            for (int c = 0; c < channels; c++) {
                out[c][f] = bb.getShort() / 32768f;
            }
        }
        return out;
    }

    private static short[] toShortInterleaved(float[][] channels) {
        int n = channels.length;
        int frames = Integer.MAX_VALUE;
        for (float[] c : channels) {
            frames = Math.min(frames, c.length);
        }
        if (frames <= 0) {
            return new short[0];
        }
        short[] out = new short[frames * n];
        for (int f = 0; f < frames; f++) {
            for (int c = 0; c < n; c++) {
                float v = channels[c][f];
                if (v > 1f) v = 1f;
                if (v < -1f) v = -1f;
                out[f * n + c] = (short) (v * 32767f);
            }
        }
        return out;
    }

    // ------------------------------------------------------------------ 编码（AAC → .m4a）

    private static void encode(File dst, int sampleRate, int channels, short[] pcm) throws IOException {
        if (pcm.length == 0) {
            throw new IOException("无 PCM 数据可编码");
        }
        MediaFormat format = MediaFormat.createAudioFormat(MIME_AAC, sampleRate, channels);
        format.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC);
        int bitRate = Math.min(192_000, Math.max(96_000, sampleRate * channels * 2));
        format.setInteger(MediaFormat.KEY_BIT_RATE, bitRate);
        format.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16_384);

        MediaCodec encoder = MediaCodec.createEncoderByType(MIME_AAC);
        encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
        encoder.start();

        MediaMuxer muxer = new MediaMuxer(dst.getAbsolutePath(), MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
        MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
        boolean muxerStarted = false;
        int trackIndex = -1;
        boolean inputDone = false;
        boolean outputDone = false;
        int frameSamples = AAC_FRAME_SAMPLES * channels;
        int offset = 0;
        int idleRounds = 0;

        while (!outputDone) {
            if (!inputDone) {
                int inIndex = encoder.dequeueInputBuffer(10_000);
                if (inIndex >= 0) {
                    ByteBuffer buf = encoder.getInputBuffer(inIndex);
                    buf.clear();
                    int copy = Math.min(frameSamples, pcm.length - offset);
                    for (int i = 0; i < copy; i++) {
                        buf.putShort(pcm[offset + i]);
                    }
                    for (int i = copy; i < frameSamples; i++) {
                        buf.putShort((short) 0);
                    }
                    long ptsUs = (long) ((double) offset / sampleRate * 1_000_000);
                    encoder.queueInputBuffer(inIndex, 0, frameSamples * 2, ptsUs, 0);
                    offset += copy;
                    if (offset >= pcm.length) {
                        int eosIndex = encoder.dequeueInputBuffer(10_000);
                        if (eosIndex >= 0) {
                            encoder.queueInputBuffer(eosIndex, 0, 0, ptsUs, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                            inputDone = true;
                        }
                    }
                }
            }

            int outIndex = encoder.dequeueOutputBuffer(info, 10_000);
            if (outIndex >= 0) {
                idleRounds = 0;
                if (info.size > 0) {
                    if (!muxerStarted) {
                        MediaFormat outFormat = encoder.getOutputFormat();
                        trackIndex = muxer.addTrack(outFormat);
                        muxer.start();
                        muxerStarted = true;
                    }
                    ByteBuffer encoded = encoder.getOutputBuffer(outIndex);
                    encoded.position(info.offset);
                    encoded.limit(info.offset + info.size);
                    muxer.writeSampleData(trackIndex, encoded, info);
                }
                boolean eos = (info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0;
                encoder.releaseOutputBuffer(outIndex, false);
                if (eos) {
                    outputDone = true;
                }
            } else if (outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                // 首个输出格式变化即拿到 csd，addTrack/start 延后到首个数据帧
            } else if (outIndex == MediaCodec.INFO_TRY_AGAIN_LATER) {
                if (inputDone) {
                    idleRounds++;
                    if (idleRounds > 100) {
                        Log.w(TAG, "编码输出超时，提前结束");
                        break;
                    }
                }
            }
        }

        encoder.stop();
        encoder.release();
        if (muxerStarted) {
            muxer.stop();
        }
        muxer.release();
    }
}
