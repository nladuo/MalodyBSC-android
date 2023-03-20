package com.example.malodybeatmapspeedchanger;


import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import androidx.appcompat.app.AppCompatActivity;

import android.content.Context;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.TimeUnit;


public class ImportActivity extends AppCompatActivity {
    public static final String SCHEME_CONTENT = "content";
    private Uri mImportingUri;
    private TextView textView;

    private Handler handler = new Handler(){
        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            switch (msg.what) {      //判断标志位
                case 1: // 成功
                    /**
                     获取数据，更新UI
                     */
                    textView.setText("已导入MalodyBSC路径");
                    break;
                case 2:  // 失败
                    textView.setText("导入失败");
                    break;
            }
        }
    };

    public static void startActivity(Context context) {
        Intent intent = new Intent();
        intent.setClass(context, ImportActivity.class);
        context.startActivity(intent);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_import);

        textView = findViewById(R.id.import_tv);
        textView.setText("导入中。。。请稍后。。。");

        Intent intent = this.getIntent();
        int flags = intent.getFlags();
        if ((flags & Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY) == 0) {
            if (intent.getAction() != null && Intent.ACTION_VIEW.equals(intent.getAction())) {
                if (SCHEME_CONTENT.equals(intent.getScheme())) {

                    String i_type = getIntent().getType();
                    intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    mImportingUri = intent.getData();
                    System.out.println("====mImportingUri=" + mImportingUri);

                    InputStream is = null;
                    try {
                        is = getContentResolver().openInputStream(mImportingUri);
                    } catch (Exception e) {
                        System.out.println("====e=" + e);
                    }

                    System.out.println(mImportingUri.getScheme());
                    System.out.println("start copy file");

                    System.out.println(getName(mImportingUri));


                    if (mImportingUri != null && SCHEME_CONTENT.equalsIgnoreCase(mImportingUri.getScheme())) {
                        if (!startToCopyFile(is)){
                            Toast.makeText(ImportActivity.this, "文件格式有误", Toast.LENGTH_LONG).show();
                        }
                    }

                }
            }
        }
    }

    private String getName(Uri uri) {
        String str = uri.toString();
        int index = str.lastIndexOf(File.separator);
        String name = str.substring(index, str.length());
        return name;
    }

    private boolean startToCopyFile(InputStream is) {
        String path = Environment.getExternalStorageDirectory() + "/MalodyBSC";
        //创建文件夹
        File bscDirFile = new File(path);
        bscDirFile.mkdirs();

        //复制文件
        String saveFileName = "";
        String importFileName = getName(mImportingUri);
        if (importFileName.endsWith("mcz")) {
            saveFileName = Environment.getExternalStorageDirectory() + "/MalodyBSC" + importFileName;
        } else if (importFileName.endsWith("msz")){
            saveFileName = Environment.getExternalStorageDirectory() + "/MalodyBSC" + importFileName;
        } else if (importFileName.endsWith("osz")){
            saveFileName = Environment.getExternalStorageDirectory() + "/MalodyBSC" + importFileName;
        } else {
            return false;
        }

        File toFile = new File(saveFileName);
        CopyThread mCopyThread = new CopyThread(is, toFile);
        new Thread(mCopyThread).start();
        return true;
    }

    private class CopyThread implements Runnable {

        private File toFile;
        private InputStream fosfrom = null;


        public CopyThread(InputStream fosfrom, File toFile) {
            this.fosfrom = fosfrom;
            this.toFile = toFile;
        }

        @Override
        public void run() {
            try {
                TimeUnit.MILLISECONDS.sleep(800);

                FileInputStream fosfrom = null;
                if (this.fosfrom != null) {
                    fosfrom = (FileInputStream) this.fosfrom;
                }
                FileOutputStream fosto = new FileOutputStream(toFile);
                byte bt[] = new byte[1024];
                int c;
                int time = 0;
                while ((c = fosfrom.read(bt)) > 0) {
                    fosto.write(bt, 0, c);
                }
                if (fosfrom != null) {
                    fosfrom.close();
                }
                fosto.close();

                Message msg =Message.obtain();
                msg.what=1;   //标志消息的标志
                handler.sendMessage(msg);

            } catch (Exception e) {
                Message msg =Message.obtain();
                msg.what = 2;   //标志消息的标志
                handler.sendMessage(msg);
                return;
            } finally {
                try {
                    if (this.fosfrom != null) {
                        this.fosfrom.close();
                    }
                } catch (IOException e) {
                }
            }
        }
    }

}
