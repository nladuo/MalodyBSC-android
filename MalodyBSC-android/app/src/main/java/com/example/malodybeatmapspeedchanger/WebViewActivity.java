package com.example.malodybeatmapspeedchanger;

import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import android.webkit.DownloadListener;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

public class WebViewActivity extends AppCompatActivity {


    public static final int REQUEST_SELECT_FILE = 1000;
    private ValueCallback<Uri[]> uploadCallBack = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_webview);
        WebView wv = (WebView)findViewById(R.id.web_view);
        WebSettings settings = wv.getSettings(); // webView 配置项
        settings.setUseWideViewPort(true); // 是否启用对视口元标记的支持
        settings.setJavaScriptEnabled(true); // 是否启用 JavaScript
        settings.setDomStorageEnabled(true); // 是否启用本地存储（允许使用 localStorage 等）
        settings.setAllowFileAccess(true); // 是否启用文件访问
        settings.setAllowContentAccess(true); // 是否启用内容 URL 访问
        settings.setJavaScriptCanOpenWindowsAutomatically(true); // 是否允许 JS 弹窗
        settings.setMediaPlaybackRequiresUserGesture(false); // 是否需要用户手势来播放媒体

        settings.setLoadWithOverviewMode(true); // 是否以概览模式加载页面，即按宽度缩小内容以适应屏幕
        settings.setBuiltInZoomControls(true); // 是否应使用其内置的缩放机制
        settings.setDisplayZoomControls(false); // 是否应显示屏幕缩放控件

        settings.setAllowFileAccessFromFileURLs(true); // 是否应允许在文件方案 URL 上下文中运行的 JavaScript 访问来自其他文件方案 URL 的内容
        settings.setAllowUniversalAccessFromFileURLs(true); // 是否应允许在文件方案URL上下文中运行的 JavaScript 访问任何来源的内容
        wv.setDownloadListener(new DownloadListener() {
            @Override
            public void onDownloadStart(String url, String s1, String s2, String s3, long l) {
                Uri uri = Uri.parse(url);
                Intent intent = new Intent(Intent.ACTION_VIEW, uri);
                startActivity(intent);
            }
        });

        String vid = getSharedPreferences("user",MODE_PRIVATE).getString("vid", "");
        wv.loadUrl("http://119.45.124.108:4776/bsc?vid="+vid);
        wv.setDrawingCacheEnabled(true); // 启用或禁用图形缓存
        wv.setWebViewClient(new WebViewClient()); // 帮助 WebView 处理各种通知、请求事件
        wv.setWebChromeClient(new WebChromeClient(){
            @Override
            public boolean onShowFileChooser(WebView webView, ValueCallback<Uri[]> filePathCallback, FileChooserParams fileChooserParams) {

                uploadCallBack = filePathCallback;
                Intent contentIntent  =new  Intent(Intent.ACTION_GET_CONTENT);
                contentIntent.setType("*/*");

                contentIntent.addCategory(Intent.CATEGORY_OPENABLE);

                Intent chooserIntent = fileChooserParams.createIntent();
                chooserIntent.putExtra(Intent.EXTRA_INTENT, contentIntent);

                startActivityForResult(chooserIntent, REQUEST_SELECT_FILE);

                return true;
            }
        });
    }


    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_SELECT_FILE) {
            if (uploadCallBack == null)
                return;
            Uri result = (data == null || resultCode != RESULT_OK) ? null : data.getData();
            if (result != null) {
                uploadCallBack.onReceiveValue(new Uri[]{result});
            } else {
                uploadCallBack.onReceiveValue(new Uri[]{});
            }
            uploadCallBack = null;
        }

    }


}
