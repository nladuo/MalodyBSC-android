package com.example.malodybeatmapspeedchanger;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;

import com.example.malodybeatmapspeedchanger.audio.AudioSpeedChanger;
import com.example.malodybeatmapspeedchanger.generator.BeatmapGenerator;
import com.example.malodybeatmapspeedchanger.model.Beatmap;
import com.example.malodybeatmapspeedchanger.util.BeatmapArchive;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 主界面：纯本地谱面调速器。
 *
 * <p>流程：选择/打开谱面档案（.mcz/.msz/.osz/.zip/.imd）→ 解析出谱面列表 →
 * 选择谱面与速度（0.50x~2.00x）→ 生成调速谱面 → 保存（SAF）或分享。</p>
 *
 * <p>不再依赖 WebView 或远程后端，全部在本机完成。</p>
 */
public class MainActivity extends AppCompatActivity {

    private static final int REQ_PICK_FILE = 1001;
    private static final int REQ_SAVE_FILE = 1002;
    private static final int REQ_STORAGE = 1003;

    private final Handler handler = new Handler(Looper.getMainLooper());

    private TextView tvFile;
    private TextView tvSpeed;
    private TextView tvStatus;
    private TextView tvResult;
    private Spinner spinnerBeatmap;
    private SeekBar seekSpeed;
    private Button btnPick;
    private Button btnGenerate;
    private Button btnSave;
    private Button btnSaveLocal;
    private Button btnShare;
    private ProgressBar progressBar;
    private LinearLayout beatmapPanel;
    private LinearLayout resultPanel;

    private BeatmapArchive archive;
    private File generatedFile;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        tvFile = findViewById(R.id.tv_file);
        tvSpeed = findViewById(R.id.tv_speed);
        tvStatus = findViewById(R.id.tv_status);
        tvResult = findViewById(R.id.tv_result);
        spinnerBeatmap = findViewById(R.id.spinner_beatmap);
        seekSpeed = findViewById(R.id.seek_speed);
        btnPick = findViewById(R.id.btn_pick);
        btnGenerate = findViewById(R.id.btn_generate);
        btnSave = findViewById(R.id.btn_save);
        btnSaveLocal = findViewById(R.id.btn_save_local);
        btnShare = findViewById(R.id.btn_share);
        progressBar = findViewById(R.id.progress_bar);
        beatmapPanel = findViewById(R.id.beatmap_panel);
        resultPanel = findViewById(R.id.result_panel);

        seekSpeed.setMax(200);
        seekSpeed.setProgress(120);
        updateSpeedLabel();

        seekSpeed.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                updateSpeedLabel();
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });

        btnPick.setOnClickListener(v -> pickFile());

        btnGenerate.setOnClickListener(v -> generate());

        btnSave.setOnClickListener(v -> saveTo());

        btnSaveLocal.setOnClickListener(v -> saveToMalodyBsc());

        btnShare.setOnClickListener(v -> share());

        // 通过文件管理器“打开方式”进入时直接解析
        Intent intent = getIntent();
        if (intent != null && Intent.ACTION_VIEW.equals(intent.getAction()) && intent.getData() != null) {
            processFile(intent.getData());
        }
    }

    // ------------------------------------------------------------------ 选择文件

    private void pickFile() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        startActivityForResult(intent, REQ_PICK_FILE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_PICK_FILE && resultCode == RESULT_OK && data != null && data.getData() != null) {
            processFile(data.getData());
        } else if (requestCode == REQ_SAVE_FILE && resultCode == RESULT_OK && data != null && data.getData() != null) {
            saveResultTo(data.getData());
        }
    }

    private void processFile(Uri uri) {
        resultPanel.setVisibility(View.GONE);
        setBusy(true);
        setStatus("正在解析谱面档案...");
        tvFile.setText("正在读取...");

        new Thread(() -> {
            try {
                BeatmapArchive a = BeatmapArchive.open(MainActivity.this, uri);
                handler.post(() -> {
                    archive = a;
                    setBusy(false);
                    showBeatmaps(a);
                    setStatus("解析完成：共 " + a.getBeatmaps().size() + " 个谱面");
                });
            } catch (Exception e) {
                handler.post(() -> {
                    setBusy(false);
                    setStatus("解析失败：" + e.getMessage());
                    Toast.makeText(MainActivity.this, "解析失败，请确认文件为 .mcz/.msz/.osz/.zip 谱面包", Toast.LENGTH_LONG).show();
                });
            }
        }).start();
    }

    private void showBeatmaps(BeatmapArchive a) {
        List<Beatmap> beatmaps = a.getBeatmaps();
        if (beatmaps.isEmpty()) {
            setStatus("未在文件中找到谱面（.mc/.osu/.imd）");
            beatmapPanel.setVisibility(View.GONE);
            return;
        }
        List<String> labels = new ArrayList<>();
        for (Beatmap b : beatmaps) {
            labels.add(typeName(b.type) + "｜" + b.version);
        }
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, labels);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerBeatmap.setAdapter(adapter);
        beatmapPanel.setVisibility(View.VISIBLE);
        tvFile.setText(a.getOriginFileName());
    }

    // ------------------------------------------------------------------ 生成

    private void generate() {
        if (archive == null || archive.getBeatmaps().isEmpty()) {
            Toast.makeText(this, "请先选择谱面文件", Toast.LENGTH_SHORT).show();
            return;
        }
        int index = spinnerBeatmap.getSelectedItemPosition();
        if (index < 0 || index >= archive.getBeatmaps().size()) {
            Toast.makeText(this, "请先选择谱面", Toast.LENGTH_SHORT).show();
            return;
        }
        final int selectedIndex = index;
        final double speed = seekSpeed.getProgress() / 100.0;

        setBusy(true);
        setStatus("正在生成：" + BeatmapGenerator.formatSpeed(speed) + "x（音频变速中，请稍候...）");
        new Thread(() -> {
            try {
                File out = archive.generate(selectedIndex, speed);
                handler.post(() -> {
                    generatedFile = out;
                    setBusy(false);
                    resultPanel.setVisibility(View.VISIBLE);
                    tvResult.setText("生成完成：" + out.getName() + "\n" + out.length() + " 字节");
                    setStatus("生成完成，请保存或分享");
                });
            } catch (Exception e) {
                handler.post(() -> {
                    setBusy(false);
                    setStatus("生成失败：" + e.getMessage());
                    Toast.makeText(MainActivity.this, "生成失败：" + e.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        }).start();
    }

    // ------------------------------------------------------------------ 保存 / 分享

    private void saveTo() {
        if (generatedFile == null) {
            return;
        }
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        intent.putExtra(Intent.EXTRA_TITLE, suggestOutputName());
        startActivityForResult(intent, REQ_SAVE_FILE);
    }

    private void saveResultTo(Uri uri) {
        try (InputStream in = new FileInputStream(generatedFile);
             OutputStream out = getContentResolver().openOutputStream(uri)) {
            if (out == null) {
                throw new Exception("无法写入目标位置");
            }
            byte[] buffer = new byte[8192];
            int n;
            while ((n = in.read(buffer)) > 0) {
                out.write(buffer, 0, n);
            }
            Toast.makeText(this, "已保存", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this, "保存失败：" + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    /** 直接保存到 /storage/emulated/0/MalodyBSC（需要“所有文件访问权限”） */
    private void saveToMalodyBsc() {
        if (generatedFile == null) {
            return;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                Toast.makeText(this, "需要开启「所有文件访问权限」才能直接保存到 /MalodyBSC", Toast.LENGTH_LONG).show();
                Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                        Uri.parse("package:" + getPackageName()));
                try {
                    startActivity(intent);
                } catch (Exception e) {
                    startActivity(new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION));
                }
                return;
            }
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQ_STORAGE);
                return;
            }
        }
        saveToMalodyBscInternal();
    }

    private void saveToMalodyBscInternal() {
        try {
            File dir = new File(Environment.getExternalStorageDirectory(), "MalodyBSC");
            if (!dir.exists() && !dir.mkdirs()) {
                throw new Exception("无法创建目录: " + dir.getAbsolutePath());
            }
            File target = new File(dir, suggestOutputName());
            try (InputStream in = new FileInputStream(generatedFile);
                 OutputStream out = new FileOutputStream(target)) {
                byte[] buffer = new byte[8192];
                int n;
                while ((n = in.read(buffer)) > 0) {
                    out.write(buffer, 0, n);
                }
            }
            Toast.makeText(this, "已保存到 " + target.getAbsolutePath(), Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Toast.makeText(this, "保存失败：" + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_STORAGE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                saveToMalodyBscInternal();
            } else {
                Toast.makeText(this, "未授予存储权限，无法保存到 /MalodyBSC", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void share() {
        if (generatedFile == null) {
            return;
        }
        Uri fileUri = FileProvider.getUriForFile(this,
                getPackageName() + ".fileprovider", generatedFile);
        Intent share = new Intent(Intent.ACTION_SEND);
        share.setType("*/*");
        share.putExtra(Intent.EXTRA_STREAM, fileUri);
        share.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivity(Intent.createChooser(share, "分享谱面"));
    }

    /** 建议输出文件名：原名-速度.后缀 */
    private String suggestOutputName() {
        if (archive == null || generatedFile == null) {
            return "beatmap.mcz";
        }
        String origin = archive.getOriginFileName();
        String base = origin;
        int idx = origin.lastIndexOf('.');
        if (idx > 0) {
            base = origin.substring(0, idx);
        }
        String ext = generatedFile.getName();
        int dot = ext.lastIndexOf('.');
        if (dot >= 0) {
            ext = ext.substring(dot + 1);
        }
        double speed = seekSpeed.getProgress() / 100.0;
        return base + "-" + BeatmapGenerator.formatSpeed(speed) + "." + ext;
    }

    // ------------------------------------------------------------------ UI 工具

    private void setBusy(boolean busy) {
        btnPick.setEnabled(!busy);
        btnGenerate.setEnabled(!busy);
        progressBar.setVisibility(busy ? View.VISIBLE : View.GONE);
    }

    private void setStatus(String text) {
        tvStatus.setText(text);
    }

    private void updateSpeedLabel() {
        double speed = seekSpeed.getProgress() / 100.0;
        tvSpeed.setText(String.format(Locale.ROOT, "速度：%.2fx", speed));
    }

    private static String typeName(String type) {
        switch (type) {
            case "mc":
                return "Malody";
            case "osu":
                return "osu!";
            case "rm":
                return "节奏大师";
            default:
                return type;
        }
    }
}
