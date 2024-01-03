package com.luoye.bzcameraview;

import androidx.appcompat.app.AppCompatActivity;

import android.Manifest;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import com.bzcommon.utils.BZLogUtil;
import com.bzcommon.utils.BZPermissionUtil;

import java.util.ArrayList;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "bz_MainActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        BZPermissionUtil.requestCommonTestPermission(this);
    }

    public void Camera1Activity(View view) {
        if (!BZPermissionUtil.isPermissionGranted(this, Manifest.permission.CAMERA)) {
            Toast.makeText(this, "Please give App sufficient permissions", Toast.LENGTH_LONG).show();
            return;
        }
        startActivity(new Intent(this, Camera1Activity.class));
    }

    public void Camera2Activity(View view) {
        if (!BZPermissionUtil.isPermissionGranted(this, Manifest.permission.CAMERA)) {
            Toast.makeText(this, "Please give App sufficient permissions", Toast.LENGTH_LONG).show();
            return;
        }
        startActivity(new Intent(this, Camera2Activity.class));
    }
}