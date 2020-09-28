package com.luoye.bzcameraview;

import android.hardware.camera2.CameraDevice;
import android.media.Image;
import android.os.Build;
import android.os.Bundle;
import android.view.View;

import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;

import com.luoye.bzcamera.BZCamera2View;

@RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
public class Camera2Activity extends AppCompatActivity {

    private BZCamera2View bz_camera2_view;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camera2);
        bz_camera2_view = findViewById(R.id.bz_camera2_view);
//        bz_camera2_view.setDisplayOrientation(90);
//        bz_camera2_view.setPreviewTargetSize(480,640);
        bz_camera2_view.setOnStatusChangeListener(new BZCamera2View.OnStatusChangeListener() {
            @Override
            public void onPreviewSuccess(CameraDevice mCameraDevice, int width, int height) {

            }

            @Override
            public void onImageAvailable(Image image, int displayOrientation, float fps) {

            }
        });
    }

    @Override
    protected void onPause() {
        super.onPause();
        bz_camera2_view.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        bz_camera2_view.onResume();
    }
}