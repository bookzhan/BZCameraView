package com.luoye.bzcameraview;

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