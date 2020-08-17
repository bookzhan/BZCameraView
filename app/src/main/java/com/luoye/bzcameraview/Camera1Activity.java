package com.luoye.bzcameraview;

import android.os.Bundle;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;

import com.luoye.bzcamera.BZCameraView;

public class Camera1Activity extends AppCompatActivity {

    private BZCameraView bz_camera_view;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camera1);
        bz_camera_view = findViewById(R.id.bz_camera_view);
    }

    @Override
    protected void onResume() {
        super.onResume();
        bz_camera_view.onResume();
    }


    @Override
    protected void onPause() {
        super.onPause();
        bz_camera_view.onPause();
    }
}