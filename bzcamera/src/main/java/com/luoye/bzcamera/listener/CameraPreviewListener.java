package com.luoye.bzcamera.listener;


import android.hardware.Camera;

/**
 * Created by zhandalin on 2019-08-29 13:57.
 * 说明:
 */
public interface CameraPreviewListener {
    void onPreviewSuccess(Camera camera, int width, int height);
}
