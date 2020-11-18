package com.luoye.bzcamera.listener;

import android.hardware.Camera;

/**
 * Created by zhandalin on 2019-08-30 16:36.
 * Function:
 */
public interface OnCameraStateListener {
    void onPreviewSuccess(Camera camera, int width, int height);

    void onPreviewFail(String message);

    void onPreviewDataUpdate(byte[] data, int width, int height, int displayOrientation, int cameraId);

    void onCameraClose();
}
