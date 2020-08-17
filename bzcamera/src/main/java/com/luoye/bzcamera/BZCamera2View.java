package com.luoye.bzcamera;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.media.Image;
import android.media.ImageReader;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.AttributeSet;
import android.util.Range;
import android.view.Surface;
import android.view.TextureView;
import android.view.WindowManager;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.core.content.ContextCompat;

import com.bzcommon.utils.BZLogUtil;
import com.luoye.bzcamera.listener.OnTransformChangeListener;
import com.luoye.bzcamera.utils.CameraCapacityCheckResult;
import com.luoye.bzcamera.utils.CameraCapacityCheckUtil;

import java.util.ArrayList;
import java.util.Arrays;

/**
 * Created by zhandalin on 2019-10-25 11:40.
 * description:
 */
@RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
public class BZCamera2View extends TextureView implements TextureView.SurfaceTextureListener, ImageReader.OnImageAvailableListener {
    private static final String TAG = "bz_BZCamera2View";
    private static final long FRAME_EXPOSURE_TIME = 30000000;
    private SurfaceTexture mSurfaceTexture;
    private int previewTargetSizeWidth = 0;
    private int previewTargetSizeHeight = 0;
    private HandlerThread mCameraHandlerThread = null;
    private Camera2Handler mCameraHandler = null;
    private int mCurrentCameraLensFacing = CameraCharacteristics.LENS_FACING_FRONT;
    private CaptureRequest.Builder captureRequestBuilder = null;
    private CaptureRequest mPreviewRequest = null;
    private CameraDevice mCameraDevice = null;
    private long previewStartTime = 0;
    private long frameCount = 0;
    private CameraCaptureSession mCameraCaptureSession = null;
    private ImageReader mImageReader = null;
    private HandlerThread mYUVHandlerThread = null;
    private Handler mYUVHandler = null;
    private OnStatusChangeListener onStatusChangeListener = null;
    private int sensorOrientation = 90;
    private int isoUpper = 1000;
    private int isoLower = 30;

    private long exposureDurationUpper = 1000 * FRAME_EXPOSURE_TIME;
    private long exposureDurationLower = FRAME_EXPOSURE_TIME;

    private boolean previewSuccessHasCallBack = false;
    private int mCurrentISO = 0;
    private OnTransformChangeListener onTransformChangeListener = null;
    private boolean checkCameraCapacity = false;
    private int lastSetIso = 0;
    private int equalAmount = 0;
    private Range<Integer>[] fpsRanges;


    public BZCamera2View(Context context) {
        this(context, null);
    }

    public BZCamera2View(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public BZCamera2View(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        setSurfaceTextureListener(this);
    }

    public void onResume() {
        BZLogUtil.d(TAG, "onResume");
        startPreview();
    }

    public void startPreview() {
        if (null != mSurfaceTexture && isAvailable()) {
            startPreview(mSurfaceTexture);
        }
    }

    public void onPause() {
        BZLogUtil.d(TAG, "onPause");
        stopPreview();
    }

    public void setPreviewTargetSize(int previewTargetSizeWidth, int previewTargetSizeHeight) {
        if (previewTargetSizeWidth <= 0 || previewTargetSizeHeight <= 0) {
            BZLogUtil.e(TAG, "setPreviewTargetSize previewTargetSizeWidth <= 0 || previewTargetSizeHeight <= 0");
            return;
        }
        this.previewTargetSizeWidth = previewTargetSizeWidth;
        this.previewTargetSizeHeight = previewTargetSizeHeight;
    }

    public int getCurrentCameraLensFacing() {
        return mCurrentCameraLensFacing;
    }

    private synchronized void startPreview(final SurfaceTexture surfaceTexture) {
        BZLogUtil.d(TAG, "startPreview mCurrentCameraLensFacing=" + mCurrentCameraLensFacing);
        if (null == surfaceTexture) {
            BZLogUtil.w(TAG, "null == surfaceTexture");
            return;
        }
        //Granted Permission
        if (ContextCompat.checkSelfPermission(getContext(), Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            BZLogUtil.d(TAG, "no permission.CAMERA");
            return;
        }
        if (checkCameraCapacity) {
            if (CameraCapacityCheckUtil.isSupportDFXSDK(getContext(), mCurrentCameraLensFacing) != CameraCapacityCheckResult.GOOD) {
                BZLogUtil.e(TAG, "CameraCapacityCheckUtil.isSupportDFXSDK(getContext(), mCurrentCameraLensFacing) != CameraCapacityCheckResult.GOOD mCurrentCameraLensFacing=" + mCurrentCameraLensFacing);
                return;
            }
        }

        int width = getWidth();
        int height = getHeight();
        if (width <= 0 || height <= 0) {
            BZLogUtil.e(TAG, "width<=0||height<=0");
            return;
        }
        stopPreview();
        mCameraHandlerThread = new HandlerThread("Camera2HandlerThread");
        mCameraHandlerThread.start();
        mCameraHandler = new Camera2Handler(mCameraHandlerThread.getLooper());

        mYUVHandlerThread = new HandlerThread("Camera2YUVHandlerThread");
        mYUVHandlerThread.start();
        mYUVHandler = new Handler(mYUVHandlerThread.getLooper());

        frameCount = 0;
        previewStartTime = 0;
        try {
            CameraManager manager = (CameraManager) getContext().getSystemService(Context.CAMERA_SERVICE);
            CameraCharacteristics characteristics = null;
            String fitCameraID = null;
            String[] cameraIdList = manager.getCameraIdList();
            for (String cameraId : cameraIdList) {
                characteristics = manager.getCameraCharacteristics(cameraId);
                fitCameraID = cameraId;
                Integer integer = characteristics.get(CameraCharacteristics.LENS_FACING);
                if (null != integer && mCurrentCameraLensFacing == integer) {
                    break;
                }
            }
            if (null == characteristics) {
                return;
            }
            fpsRanges = characteristics.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES);
            BZLogUtil.d(TAG, "fpsRanges: " + Arrays.toString(fpsRanges));
            Range<Integer> isoRange = characteristics.get(CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE);
            if (null != isoRange) {
                isoUpper = isoRange.getUpper();
                isoLower = isoRange.getLower();
                BZLogUtil.d(TAG, "isoRange: RangeUpper=" + isoUpper + " getLower=" + isoLower);
            } else {
                BZLogUtil.w("Can't get isoRange");
            }
            Range<Long> exposureDurationRange = characteristics.get(CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE);
            if (null != exposureDurationRange) {
                exposureDurationLower = exposureDurationRange.getLower();
                exposureDurationUpper = exposureDurationRange.getUpper();
                BZLogUtil.d(TAG, "exposureDurationRange: RangeUpper=" + exposureDurationUpper + " getLower=" + exposureDurationLower);
            } else {
                BZLogUtil.w("Can't get exposureDurationRange");
            }

            final Integer sensor_orientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);
            if (null != sensor_orientation) {
                Integer lens_facing = characteristics.get(CameraCharacteristics.LENS_FACING);
                if (null != lens_facing) {
                    sensorOrientation = computeSensorToViewOffset(lens_facing, sensor_orientation % 360, getDisplayOrientation(getContext()));
                }
            }
            manager.openCamera(fitCameraID, new CameraDevice.StateCallback() {
                @Override
                public void onOpened(@NonNull CameraDevice camera) {
                    try {
                        previewSuccessHasCallBack = false;
                        mCurrentISO = 0;
                        lastSetIso = 0;
                        mCameraDevice = camera;
                        captureRequestBuilder = camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
                        if (previewTargetSizeWidth > 0 && previewTargetSizeHeight > 0) {
                            if (sensorOrientation == 90 || sensorOrientation == 270) {
                                mImageReader = ImageReader.newInstance(previewTargetSizeHeight, previewTargetSizeWidth, ImageFormat.YUV_420_888, 2);
                                surfaceTexture.setDefaultBufferSize(previewTargetSizeHeight, previewTargetSizeWidth);
                            } else {
                                mImageReader = ImageReader.newInstance(previewTargetSizeWidth, previewTargetSizeHeight, ImageFormat.YUV_420_888, 2);
                                surfaceTexture.setDefaultBufferSize(previewTargetSizeWidth, previewTargetSizeHeight);
                            }
                        } else {
                            if (sensorOrientation == 90 || sensorOrientation == 270) {
                                mImageReader = ImageReader.newInstance(getHeight(), getWidth(), ImageFormat.YUV_420_888, 2);
                                surfaceTexture.setDefaultBufferSize(getHeight(), getWidth());
                            } else {
                                mImageReader = ImageReader.newInstance(getWidth(), getHeight(), ImageFormat.YUV_420_888, 2);
                                surfaceTexture.setDefaultBufferSize(getWidth(), getHeight());
                            }
                        }
                        Surface surface = new Surface(surfaceTexture);
                        captureRequestBuilder.addTarget(surface);
                        captureRequestBuilder.addTarget(mImageReader.getSurface());
                        mImageReader.setOnImageAvailableListener(BZCamera2View.this, mYUVHandler);
                        ArrayList<Surface> surfaceArrayList = new ArrayList<>();
                        surfaceArrayList.add(surface);
                        surfaceArrayList.add(mImageReader.getSurface());
                        camera.createCaptureSession(surfaceArrayList, stateCallback, mCameraHandler);
                    } catch (Exception e) {
                        BZLogUtil.e(TAG, e);
                    }
                }

                @Override
                public void onDisconnected(@NonNull CameraDevice camera) {
                    BZLogUtil.e(TAG, "onDisconnected");
                }

                @Override
                public void onError(@NonNull CameraDevice camera, int error) {
                    BZLogUtil.e(TAG, "onError error=" + error);
                }
            }, mCameraHandler);

        } catch (Exception e) {
            BZLogUtil.e(TAG, e);
        }
    }

    private CameraCaptureSession.StateCallback stateCallback = new CameraCaptureSession.StateCallback() {
        @Override
        public void onConfigured(@NonNull CameraCaptureSession session) {
            startRepeatingRequest(session);
        }

        @Override
        public void onConfigureFailed(@NonNull CameraCaptureSession session) {

        }
    };
    private CameraCaptureSession.CaptureCallback captureCallback = new CameraCaptureSession.CaptureCallback() {
        @Override
        public void onCaptureStarted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, long timestamp, long frameNumber) {
            super.onCaptureStarted(session, request, timestamp, frameNumber);
        }

        @Override
        public void onCaptureCompleted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull TotalCaptureResult result) {
            super.onCaptureCompleted(session, request, result);
            Integer sensorSensitivity = result.get(CaptureResult.SENSOR_SENSITIVITY);
            if (null != sensorSensitivity) {
                mCurrentISO = sensorSensitivity;
            }
            if (frameCount % 30 == 0) {
                Long capturedExposureDuration = result.get(CaptureResult.SENSOR_EXPOSURE_TIME);
                Long capturedFrameDuration = result.get(CaptureResult.SENSOR_FRAME_DURATION);
                BZLogUtil.v(TAG, "mCurrentISO =" + mCurrentISO + " capturedExposureDuration=" + capturedExposureDuration + " capturedFrameDuration=" + capturedFrameDuration);
            }
        }
    };

    private void startRepeatingRequest(CameraCaptureSession cameraCaptureSession) {
        if (null == cameraCaptureSession || null == captureRequestBuilder) {
            return;
        }
        mCameraCaptureSession = cameraCaptureSession;
        BZLogUtil.d(TAG, "startRepeatingRequest");
        try {
            captureRequestBuilder.set(CaptureRequest.CONTROL_AE_LOCK, false);
            captureRequestBuilder.set(CaptureRequest.CONTROL_AWB_LOCK, false);
            captureRequestBuilder.set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, getAppropriateFpsRange());
            captureRequestBuilder.set(CaptureRequest.STATISTICS_FACE_DETECT_MODE, CaptureRequest.STATISTICS_FACE_DETECT_MODE_SIMPLE);
            //captureRequestBuilder.set(CaptureRequest.NOISE_REDUCTION_MODE, CaptureRequest.NOISE_REDUCTION_MODE_OFF);//TODO investigate
            captureRequestBuilder.set(CaptureRequest.EDGE_MODE, CaptureRequest.EDGE_MODE_OFF);
            captureRequestBuilder.set(CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE, CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE_ON);
            captureRequestBuilder.set(CaptureRequest.SHADING_MODE, CaptureRequest.SHADING_MODE_OFF);
            captureRequestBuilder.set(CaptureRequest.HOT_PIXEL_MODE, CaptureRequest.HOT_PIXEL_MODE_OFF);
            //captureRequestBuilder.set(CaptureRequest.COLOR_CORRECTION_ABERRATION_MODE, CaptureRequest.COLOR_CORRECTION_ABERRATION_MODE_OFF);

            captureRequestBuilder.set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO);
            captureRequestBuilder.set(CaptureRequest.CONTROL_AWB_MODE, CaptureRequest.CONTROL_AWB_MODE_AUTO);//HUAWEI GREEN
            captureRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON);
            captureRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_AUTO);

            mPreviewRequest = captureRequestBuilder.build();
            cameraCaptureSession.setRepeatingRequest(mPreviewRequest, captureCallback, mCameraHandler);
        } catch (Exception e) {
            BZLogUtil.e(TAG, e);
        }
    }

    private Range<Integer> getAppropriateFpsRange() {
        Range<Integer> range = new Range<>(30, 30);
        if (null == fpsRanges || fpsRanges.length <= 0) {
            return range;
        }
        for (Range<Integer> fpsRange : fpsRanges) {
            if (fpsRange.getLower() >= 30 && fpsRange.getUpper() >= 30) {
                range = new Range<>(fpsRange.getLower(), fpsRange.getUpper());
                break;
            }
        }
        return range;
    }

    public synchronized void stopPreview() {
        BZLogUtil.d(TAG, "stopPreview");
        if (null != mCameraHandler) {
            mCameraHandler.post(new Runnable() {
                @Override
                public void run() {
                    try {
                        if (null != mCameraCaptureSession) {
                            mCameraCaptureSession.abortCaptures();
                            mCameraCaptureSession.close();
                            mCameraCaptureSession = null;
                        }
                    } catch (Throwable e) {
                        BZLogUtil.e(TAG, e);
                    }
                    try {
                        if (null != mCameraDevice) {
                            mCameraDevice.close();
                            mCameraDevice = null;
                        }
                    } catch (Throwable e) {
                        BZLogUtil.e(TAG, e);
                    }
                }
            });
            mCameraHandler = null;
        }
        if (null != mCameraHandlerThread) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
                    mCameraHandlerThread.quitSafely();
                } else {
                    mCameraHandlerThread.quit();
                }
                long startTime = System.currentTimeMillis();
                mCameraHandlerThread.join();
                BZLogUtil.d(TAG, "mCameraHandlerThread.join() time consuming=" + (System.currentTimeMillis() - startTime));
            } catch (Exception e) {
                BZLogUtil.e(TAG, e);
            }
            mCameraHandlerThread = null;
        }
        if (null != mYUVHandlerThread) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
                    mYUVHandlerThread.quitSafely();
                } else {
                    mYUVHandlerThread.quit();
                }
                long startTime = System.currentTimeMillis();
//                mYUVHandlerThread.join();
                BZLogUtil.d(TAG, "mYUVHandlerThread.join() time consuming=" + (System.currentTimeMillis() - startTime));
            } catch (Exception e) {
                BZLogUtil.e(TAG, e);
            }
            mYUVHandlerThread = null;
        }
        captureRequestBuilder = null;
    }

    @Override
    public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
        mSurfaceTexture = surface;
        BZLogUtil.d(TAG, "onSurfaceTextureAvailable");
        startPreview(surface);
    }

    @Override
    public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
        BZLogUtil.d(TAG, "onSurfaceTextureSizeChanged width=" + width + " height=" + height);
    }

    @Override
    public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
        return false;
    }

    @Override
    public void onSurfaceTextureUpdated(SurfaceTexture surface) {

    }


    private void calculateTransform(int width, int height) {
        final Matrix matrix = new Matrix();
        float finalWidth = getWidth();
        float finalHeight = finalWidth * height / width;
        if (finalHeight < getHeight()) {
            finalHeight = getHeight();
            finalWidth = finalHeight * width / height;
        }
        matrix.postScale(finalWidth / getWidth(), finalHeight / getHeight(), getWidth() / 2, getHeight() / 2);
        post(new Runnable() {
            @Override
            public void run() {
                setTransform(matrix);
                if (null != onTransformChangeListener) {
                    onTransformChangeListener.onTransformChange(matrix);
                }
            }
        });
    }

    public void setOnTransformChangeListener(OnTransformChangeListener onTransformChangeListener) {
        this.onTransformChangeListener = onTransformChangeListener;
    }

    private static int computeSensorToViewOffset(int cameraId, int cameraOrientation, int displayOrientation) {
        return cameraId == CameraCharacteristics.LENS_FACING_FRONT ? (360 - (cameraOrientation + displayOrientation) % 360) % 360 : (cameraOrientation - displayOrientation + 360) % 360;
    }

    public static int getDisplayOrientation(Context context) {
        WindowManager windowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        int rotation = windowManager.getDefaultDisplay().getRotation();
        short degrees = 0;
        switch (rotation) {
            case Surface.ROTATION_0:
                degrees = 0;
                break;
            case Surface.ROTATION_90:
                degrees = 90;
                break;
            case Surface.ROTATION_180:
                degrees = 180;
                break;
            case Surface.ROTATION_270:
                degrees = 270;
        }

        return degrees;
    }

    public synchronized void switchCamera() {
        if (mCurrentCameraLensFacing == CameraCharacteristics.LENS_FACING_FRONT) {
            mCurrentCameraLensFacing = CameraCharacteristics.LENS_FACING_BACK;
        } else if (mCurrentCameraLensFacing == CameraCharacteristics.LENS_FACING_BACK) {
            mCurrentCameraLensFacing = CameraCharacteristics.LENS_FACING_FRONT;
        }
        switchCamera2Lens(mCurrentCameraLensFacing);
    }

    public synchronized void switchCamera2Lens(int cameraLensFacing) {
        mCurrentCameraLensFacing = cameraLensFacing;
        startPreview();
    }


    @Override
    public void onImageAvailable(ImageReader reader) {
        Image image = reader.acquireLatestImage();

        if (image == null) {
            return;
        }
        if (previewStartTime <= 0) {
            previewStartTime = System.currentTimeMillis();
        }
        frameCount++;
        long time = System.currentTimeMillis() - previewStartTime;
        float fps = frameCount / (time / 1000.f);
        int width = image.getWidth();
        int height = image.getHeight();
        if (null != onStatusChangeListener) {
            if (!previewSuccessHasCallBack) {
                if (sensorOrientation == 90 || sensorOrientation == 270) {
                    onStatusChangeListener.onPreviewSuccess(mCameraDevice, height, width);
                    calculateTransform(height, width);
                } else {
                    onStatusChangeListener.onPreviewSuccess(mCameraDevice, width, height);
                    calculateTransform(width, height);
                }
            }
            previewSuccessHasCallBack = true;
            onStatusChangeListener.onImageAvailable(image, sensorOrientation, fps);
        }
        if (frameCount % 150 == 0) {
            frameCount = 30;
            previewStartTime = System.currentTimeMillis() - 1000;
        }
        image.close();
        if (frameCount % 30 == 0) {
            BZLogUtil.v(TAG, "onPreviewDataUpdate width=" + width + " height=" + height + " fps=" + fps);
        }
//        if (fps < 29) {
//            repeatingRequest();
//        }
    }

    public int getIsoUpper() {
        return isoUpper;
    }

    public int getIsoLower() {
        return isoLower;
    }

    public int getGetIsoIntervalValue() {
        return isoUpper - isoLower;
    }


    public void setOnStatusChangeListener(OnStatusChangeListener onStatusChangeListener) {
        this.onStatusChangeListener = onStatusChangeListener;
    }

    public synchronized void setISO(int iso) {
        if (null == captureRequestBuilder || null == mPreviewRequest || null == mCameraCaptureSession) {
            return;
        }
        if (iso == lastSetIso) {
            equalAmount++;
            if (equalAmount >= 2) {
                BZLogUtil.w(TAG, "iso==lastSetIso");
                return;
            }
        } else {
            equalAmount = 0;
        }
        lastSetIso = iso;
        if (iso < isoLower || iso > isoUpper) {
            BZLogUtil.w(TAG, "iso < isoLower || iso > isoUpper");
            return;
        }
        captureRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_OFF);
        captureRequestBuilder.set(CaptureRequest.SENSOR_FRAME_DURATION, FRAME_EXPOSURE_TIME);
        captureRequestBuilder.set(CaptureRequest.SENSOR_EXPOSURE_TIME, FRAME_EXPOSURE_TIME);
        captureRequestBuilder.set(CaptureRequest.SENSOR_SENSITIVITY, iso);
        mPreviewRequest = captureRequestBuilder.build();
        BZLogUtil.d(TAG, "Setting setISO " + iso);
        try {
            mCameraCaptureSession.setRepeatingRequest(mPreviewRequest, captureCallback, mCameraHandler);
        } catch (Exception e) {
            BZLogUtil.e(TAG, e);
        }

    }

    public synchronized void repeatingRequest() {
        if (null == captureRequestBuilder || null == mPreviewRequest || null == mCameraCaptureSession) {
            return;
        }
        try {
            mCameraCaptureSession.setRepeatingRequest(mPreviewRequest, captureCallback, mCameraHandler);
        } catch (Exception e) {
            BZLogUtil.e(TAG, e);
        }
    }

    public synchronized void setISOPercent(float iso) {
        if (null == captureRequestBuilder || null == mPreviewRequest || null == mCameraCaptureSession) {
            return;
        }
        int finalIso = (int) ((isoUpper - isoLower) * iso + isoLower);
        setISO(finalIso);
    }

    public synchronized void setExposureTimePercent(float exposureTime) {
        if (null == captureRequestBuilder || null == mPreviewRequest || null == mCameraCaptureSession) {
            return;
        }
        long finalExposureTime = (long) ((exposureDurationUpper - exposureDurationLower) * exposureTime + exposureDurationLower);
        setExposureTime(finalExposureTime);
    }

    public synchronized void setExposureTime(long exposureDuration) {
        if (null == captureRequestBuilder || null == mPreviewRequest || null == mCameraCaptureSession) {
            return;
        }
        if (exposureDuration < exposureDurationLower || exposureDuration > exposureDurationUpper) {
            BZLogUtil.w(TAG, "exposureDuration < exposureDurationLower || exposureDuration > exposureDurationUpper");
            return;
        }
        captureRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_OFF);
        captureRequestBuilder.set(CaptureRequest.SENSOR_EXPOSURE_TIME, exposureDuration);
        mPreviewRequest = captureRequestBuilder.build();
        BZLogUtil.d(TAG, "Setting setExposureTime=" + exposureDuration);
        try {
            mCameraCaptureSession.setRepeatingRequest(mPreviewRequest, captureCallback, mCameraHandler);
        } catch (Exception e) {
            BZLogUtil.e(TAG, e);
        }
    }

    public synchronized void lockFocus() {
        BZLogUtil.d(TAG, "lockFocus");
        if (null == captureRequestBuilder || null == mPreviewRequest || null == mCameraCaptureSession) {
            BZLogUtil.e(TAG, "null == captureRequestBuilder || null == mPreviewRequest || null == mCameraCaptureSession");
            return;
        }
        try {
            captureRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_OFF);
            captureRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER,
                    CaptureRequest.CONTROL_AF_TRIGGER_START);
            mPreviewRequest = captureRequestBuilder.build();
            mCameraCaptureSession.setRepeatingRequest(mPreviewRequest, captureCallback, mCameraHandler);
        } catch (Exception e) {
            BZLogUtil.e(TAG, e);
        }
    }

    public synchronized void unLockFocus() {
        BZLogUtil.d(TAG, "unLockFocus");
        if (null == captureRequestBuilder || null == mPreviewRequest || null == mCameraCaptureSession) {
            BZLogUtil.e(TAG, "null == captureRequestBuilder || null == mPreviewRequest || null == mCameraCaptureSession");
            return;
        }
        try {
            // Reset the auto-focus trigger
            captureRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_AUTO);
            mPreviewRequest = captureRequestBuilder.build();
            mCameraCaptureSession.setRepeatingRequest(mPreviewRequest, captureCallback, mCameraHandler);
        } catch (Exception e) {
            BZLogUtil.e(TAG, e);
        }
    }

    public synchronized void lockAWB() {
        BZLogUtil.d(TAG, "lockAWB");
        if (null == captureRequestBuilder || null == mPreviewRequest || null == mCameraCaptureSession) {
            BZLogUtil.e(TAG, "null == captureRequestBuilder || null == mPreviewRequest || null == mCameraCaptureSession");
            return;
        }
        try {
            captureRequestBuilder.set(CaptureRequest.CONTROL_AWB_LOCK, true);
            mPreviewRequest = captureRequestBuilder.build();
            mCameraCaptureSession.setRepeatingRequest(mPreviewRequest, captureCallback, mCameraHandler);
        } catch (Exception e) {
            BZLogUtil.e(TAG, e);
        }
    }

    public synchronized void unLockAWB() {
        BZLogUtil.d(TAG, "unLockAWB");
        if (null == captureRequestBuilder || null == mPreviewRequest || null == mCameraCaptureSession) {
            BZLogUtil.e(TAG, "null == captureRequestBuilder || null == mPreviewRequest || null == mCameraCaptureSession");
            return;
        }
        try {
            captureRequestBuilder.set(CaptureRequest.CONTROL_AWB_LOCK, false);
            mPreviewRequest = captureRequestBuilder.build();
            mCameraCaptureSession.setRepeatingRequest(mPreviewRequest, captureCallback, mCameraHandler);
        } catch (Exception e) {
            BZLogUtil.e(TAG, e);
        }
    }

    public int getCurrentISO() {
        return mCurrentISO;
    }

    public int getLastSetIso() {
        return lastSetIso <= 0 ? mCurrentISO : lastSetIso;
    }

    public void setCheckCameraCapacity(boolean checkCameraCapacity) {
        this.checkCameraCapacity = checkCameraCapacity;
    }

    public interface OnStatusChangeListener {
        void onPreviewSuccess(CameraDevice mCameraDevice, int width, int height);

        void onImageAvailable(Image image, int displayOrientation, float fps);
    }
}
