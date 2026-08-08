package com.example.malodybeatmapspeedchanger;

import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

/**
 * 主界面。
 *
 * <p>重构进行中：将原「Android 壳 + Web 后端」架构重构为纯本地 App，
 * 不再依赖 WebView 与远程服务。</p>
 */
public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
    }
}
