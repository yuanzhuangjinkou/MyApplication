package com.example.myapplication;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraExtensionCharacteristics;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.media.MediaScannerConnection;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Base64;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.Size;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import org.opencv.android.OpenCVLoader;
import org.opencv.android.Utils;
import org.opencv.calib3d.Calib3d;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.DMatch;
import org.opencv.core.KeyPoint;
import org.opencv.core.Mat;
import org.opencv.core.MatOfByte;
import org.opencv.core.MatOfDMatch;
import org.opencv.core.MatOfKeyPoint;
import org.opencv.core.MatOfPoint2f;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.features2d.BFMatcher;
import org.opencv.features2d.SIFT;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class MainActivity extends Activity {

    // 请求相机权限的常量
    private static final int REQUEST_CAMERA_PERMISSION = 101;
    // 日志标签
    private static final String TAG = "tag";
    // 界面元素
    private TextureView textureView;
    private Button captureButton;
    // 相机相关
    private CameraManager cameraManager;
    private CameraDevice cameraDevice;
    private CameraCaptureSession cameraCaptureSession;
    private CaptureRequest.Builder captureRequestBuilder;
    private ImageReader imageReader;
    // 后台线程和处理程序
    private Handler backgroundHandler;
    private HandlerThread backgroundThread;
    // 捕获间隔时间
    private final int CAPTURE_INTERVAL = 1000;
    private boolean isCapturing = false;

    public int sizeWidth;
    public int sizeHeight;
    private final List<Mat> matList = new ArrayList<>();

    private ProgressDialog progressDialog;
    private Mat panorama;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        if (!OpenCVLoader.initDebug()) {
            Log.e(TAG, "OpenCV initialization failed.");
        } else {
            Log.d(TAG, "OpenCV initialization succeeded.");
        }
        // 初始化界面元素
        textureView = findViewById(R.id.textureView);
        captureButton = findViewById(R.id.captureButton);

        // 设置按钮的点击监听器，当用户点击按钮时，调用 takePicture 方法拍摄照片
        captureButton.setOnClickListener(new View.OnClickListener() {
            @SuppressLint("SetTextI18n")
            @Override
            public void onClick(View view) {
                if (!isCapturing) { // 如果没有在拍摄，则开始拍摄
                    startCapture();
                } else { // 如果已经在拍摄，则停止拍摄
                    stopCapture();
                    jointAndSave();
                    captureButton.setText("开始拍摄");
                }
            }
        });
        // 获取相机管理器
        cameraManager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        // 设置 TextureView 的监听器
        textureView.setSurfaceTextureListener(textureListener);
        // 启动后台线程
        startBackgroundThread();
    }

    // 相机捕获回调
    private final CameraCaptureSession.CaptureCallback captureCallback = new CameraCaptureSession.CaptureCallback() {
        @Override
        public void onCaptureCompleted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull TotalCaptureResult result) {
            super.onCaptureCompleted(session, request, result);
        }
    };

    private final Handler handler = new Handler();

    private void executeMethod() {
        if (isCapturing) {
            // 捕获
            takePicture();
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    executeMethod(); // 递归调用方法，实现无限执行
                }
            }, CAPTURE_INTERVAL); // 设置延迟时间为1秒（1000毫秒）
        }
    }

    private void startCapture() {
        captureButton.setText("停止拍摄");
        isCapturing = true;
        matList.clear();
        setupCaptureRequest();
        executeMethod();
    }

    private void stopCapture() {

        isCapturing = false;
    }

    private void jointAndSave() {
        progressDialog = new ProgressDialog(this);
        progressDialog.setMessage("图像生成中，请稍候...");
        progressDialog.setProgressStyle(ProgressDialog.STYLE_SPINNER); // 或者使用 ProgressDialog.STYLE_HORIZONTAL
        progressDialog.setCancelable(false); // 防止用户取消
        // 显示加载框
        progressDialog.show();

        // 创建一个新线程来执行耗时操作，以避免阻塞主线程
        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                // 保存 捕获图片
//                for (Mat mat : matList) {
//                    save(mat, "捕获图片");
//                }
//                try {
                    panorama = stitchImagesRecursive(matList);
//                } catch (Exception e) {
//                    e.printStackTrace();
//                    showToast("全景拼接失败");
//                    progressDialog.dismiss();
//                    return;
//                }

                showToast("全景图已生成");
                // 保存全景图片
                // save(panorama, "全景图片");
                for (Mat mat : matList) {
                    mat.release();
                }

                // 操作完成后，隐藏加载框
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        progressDialog.dismiss();
                        // 设置返回值并关闭当前 Activity
                        Intent resultIntent = new Intent();
                        resultIntent.putExtra("bitmap", matToBitmap(panorama));
                        panorama.release();
                        setResult(Activity.RESULT_OK, resultIntent);
                        finish();
                    }
                });
            }
        });

        // 启动线程来执行耗时操作
        thread.start();
    }

    // 将Mat转换为Bitmap
    public Bitmap matToBitmap(Mat mat) {
        Imgproc.cvtColor(mat, mat, Imgproc.COLOR_BGR2RGB);
        Bitmap bitmap = Bitmap.createBitmap(mat.cols(), mat.rows(), Bitmap.Config.ARGB_8888);
        Utils.matToBitmap(mat, bitmap, true);
        return bitmap;
    }

    // 将Bitmap转换为Mat
    public Mat bitmapToMat(Bitmap bitmap) {
        Mat mat = new Mat(bitmap.getHeight(), bitmap.getWidth(), CvType.CV_8UC4);
        Utils.bitmapToMat(bitmap, mat, true);
        return mat;
    }

    // 启动后台线程
    private void startBackgroundThread() {
        // 创建一个新的后台线程
        backgroundThread = new HandlerThread("CameraBackground");
        // 启动后台线程
        backgroundThread.start();
        // 创建后台线程的处理程序，它将与后台线程的消息队列关联
        backgroundHandler = new Handler(backgroundThread.getLooper());
    }

    // 停止后台线程
    private void stopBackgroundThread() {
        // 安全地停止后台线程，确保正在进行的任务完成
        backgroundThread.quitSafely();
        try {
            // 等待后台线程完成
            backgroundThread.join();
            // 将后台线程和处理程序设置为 null，以释放资源
            backgroundThread = null;
            backgroundHandler = null;
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    // 显示Toast消息的方法，确保在UI线程上调用
    private void showToast(final String text) {
        // 创建一个新的线程，在该线程上运行UI操作
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(MainActivity.this, text, Toast.LENGTH_SHORT).show();
            }
        });
    }

    // 打开相机的方法
    private void openCamera() {

        try {
            // 获取可用的相机ID，通常取第一个相机
            String cameraId = cameraManager.getCameraIdList()[0];
            // 获取相机支持的JPEG 最优预览分辨率
            Size size = null;
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                size = pickPreviewResolution(cameraManager, cameraId);
                if(size == null)
                    size = new Size(640, 360);
            } else {
                size = new Size(640, 360);
            }

            // 设置图像的默认宽度和高度
            sizeWidth = size.getWidth();
            sizeHeight = size.getHeight();

            // 配置ImageReader以接收相机图像 640 * 360
            configureImageReader(sizeWidth, sizeHeight);
            // 检查相机权限是否已授予
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                Log.i("tag","已申请权限");
                cameraManager.openCamera(cameraId, stateCallback, null);
                // 打开相机，传入相机ID和相机状态回调
            } else {
                // 如果没有相机权限，则请求相机权限
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, REQUEST_CAMERA_PERMISSION);
//                cameraManager.openCamera("0", stateCallback, null);
            }

        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }
    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CAMERA_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // 权限被授予，执行相机相关操作
                Log.d("TAG", "onRequestPermissionsResult: 已授权");
            } else {
                // 权限被拒绝，向用户显示相关信息
                Log.d("tag", "onRequestPermissionsResult: 请求权限被拒绝");
            }
        }
    }

    // 配置ImageReader以接收相机图像
    private void configureImageReader(int width, int height) {
        // 创建一个新的ImageReader，指定图像的宽度、高度、格式和最大图像数
        imageReader = ImageReader.newInstance(width, height, ImageFormat.JPEG, 1);
        // 设置ImageReader的图像可用监听器，以在图像可用时调用imageReaderListener
        imageReader.setOnImageAvailableListener(imageReaderListener, backgroundHandler);
    }

    // 创建一个监听ImageReader的图像可用事件的监听器
    private final ImageReader.OnImageAvailableListener imageReaderListener = new ImageReader.OnImageAvailableListener() {
        @Override
        public void onImageAvailable(ImageReader reader) {
            Image image = null;
            try {
                image = reader.acquireLatestImage();

                String directory = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES).toString();
                String filename = "panorama_" + "-" + System.currentTimeMillis() + ".jpg";
                String fullPath = directory + File.separator + filename;
                Bitmap bitmap = saveRotatedImage(image, fullPath);

                Mat mat = new Mat();
                Utils.bitmapToMat(bitmap, mat, true);
                // OpenCV通常使用BGR（蓝绿红） Bitmap通常使用RGB（红绿蓝）
                Imgproc.cvtColor(mat, mat, Imgproc.COLOR_RGB2BGR);
                matList.add(mat);
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                image.close();
            }
        }
    };

    private Bitmap saveRotatedImage(Image image, String filePath) throws CameraAccessException {
        ByteBuffer buffer = image.getPlanes()[0].getBuffer();
        byte[] bytes = new byte[buffer.remaining()];
        buffer.get(bytes);

        Bitmap originalBitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
        int rotation = getRotationFromImage(image);

        Matrix matrix = new Matrix();
        matrix.postRotate(90);

        Bitmap rotatedBitmap = Bitmap.createBitmap(originalBitmap, 0, 0, originalBitmap.getWidth(), originalBitmap.getHeight(), matrix, true);

        return rotatedBitmap;
    }

    private int getRotationFromImage(Image image) throws CameraAccessException {
        int rotation = 0;
        int rotationDegrees;
        int rotationCompensation;
        int currentOrientation = getCurrentDeviceOrientation();
        CameraCharacteristics characteristics = cameraManager.getCameraCharacteristics(cameraManager.getCameraIdList()[0]);
        Integer sensorOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);


        if (sensorOrientation != null) {
            switch (sensorOrientation) {
                case 90:
                    rotationCompensation = (currentOrientation + 45) / 90 * 90;
                    rotation = (sensorOrientation + rotationCompensation) % 360;
                    break;
                case 270:
                    rotationCompensation = (currentOrientation + 45) / 90 * 90;
                    rotation = (sensorOrientation - rotationCompensation + 360) % 360;
                    break;
            }
        }
        return rotation;
    }

    private int getCurrentDeviceOrientation() {
        int rotation = getWindowManager().getDefaultDisplay().getRotation();
        int orientation;
        Resources resources = getResources();
        Configuration config = resources.getConfiguration();
        int rotationCompensation = 0;

        switch (rotation) {
            case Surface.ROTATION_0:
                rotationCompensation = 0;
                break;
            case Surface.ROTATION_90:
                rotationCompensation = 90;
                break;
            case Surface.ROTATION_180:
                rotationCompensation = 180;
                break;
            case Surface.ROTATION_270:
                rotationCompensation = 270;
                break;
        }

        if ("portrait".equals(config.orientation)) {
            orientation = (rotationCompensation + 0) % 360;
        } else if ("landscape".equals(config.orientation)) {
            orientation = (rotationCompensation + 90) % 360;
        } else if ("reverse_portrait".equals(config.orientation)) {
            orientation = (rotationCompensation + 180) % 360;
        } else {
            orientation = (rotationCompensation + 270) % 360;
        }

        return orientation;
    }


    public String imageToBase64(Image image) {
        ByteBuffer buffer = image.getPlanes()[0].getBuffer();
        byte[] bytes = new byte[buffer.remaining()];
        buffer.get(bytes);
        return Base64.encodeToString(bytes, Base64.DEFAULT);
    }

    public Mat base64ToMat(String base64Image, int width, int height) throws IOException {
        byte[] imageBytes = Base64.decode(base64Image, Base64.DEFAULT);
        Mat imageMat = Imgcodecs.imdecode(new MatOfByte(imageBytes), Imgcodecs.IMREAD_COLOR);
        // 调整图像尺寸（可选）
//        org.opencv.core.Size newSize = new org.opencv.core.Size(640, 360);
        org.opencv.core.Size newSize = new org.opencv.core.Size(width, height);
        Imgproc.resize(imageMat, imageMat, newSize);
        return imageMat;
    }

    public String matToBase64(Mat mat) throws IOException {
        MatOfByte matOfByte = new MatOfByte();
        Imgcodecs.imencode(".jpg", mat, matOfByte);
        byte[] byteArray = matOfByte.toArray();
        return Base64.encodeToString(byteArray, Base64.DEFAULT);
    }

    private void setupCaptureRequest() {
        if (cameraDevice == null) {
            return;
        }
        try {
            captureRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            captureRequestBuilder.addTarget(imageReader.getSurface());
            // 自动对焦模式（Auto-Focus Mode）：设置为连续自动对焦模式
            captureRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
            // 图像捕获模式（Capture Mode）：设置为连续图像捕获模式
            captureRequestBuilder.set(CaptureRequest.CONTROL_CAPTURE_INTENT, CaptureRequest.CONTROL_CAPTURE_INTENT_PREVIEW);
            // 自动曝光模式（Auto-Exposure Mode）：设置为连续自动曝光模式
            captureRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void takePicture() {
        if (cameraCaptureSession == null) {
            return;
        }
        try {
            cameraCaptureSession.capture(captureRequestBuilder.build(), captureCallback, null);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    // 相机设备状态回调，用于处理相机设备的不同状态
    private final CameraDevice.StateCallback stateCallback = new CameraDevice.StateCallback() {
        @Override
        // 当相机设备成功打开时调用
        public void onOpened(@NonNull CameraDevice camera) {
            // 将打开的相机设备赋值给成员变量
            cameraDevice = camera;
            // 创建相机预览
            createCameraPreview();
        }

        @Override
        // 当相机设备断开连接时调用
        public void onDisconnected(@NonNull CameraDevice camera) {
            // 关闭相机设备并将其设为null
            cameraDevice.close();
            cameraDevice = null;
        }

        @Override
        // 当相机设备发生错误时调用
        public void onError(@NonNull CameraDevice camera, int error) {
            // 关闭相机设备并将其设为null
            cameraDevice.close();
            cameraDevice = null;
        }
    };

    // 创建相机预览的方法
    private void createCameraPreview() {
        try {
            // 获取 TextureView 的 SurfaceTexture
            SurfaceTexture texture = textureView.getSurfaceTexture();

            // 界面预览尺寸 设置默认的缓冲区大小为 640x480
            texture.setDefaultBufferSize(sizeWidth, sizeHeight);

            // 创建用于相机预览的 Surface
            Surface surface = new Surface(texture);

            // 创建用于预览的捕获请求，使用 TEMPLATE_PREVIEW 模板
            captureRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            // 将预览的 Surface 添加为捕获请求的目标
            captureRequestBuilder.addTarget(surface);

            // 创建相机捕获会话，同时将预览 Surface 和图像读取器的 Surface 作为输出目标
            cameraDevice.createCaptureSession(Arrays.asList(surface, imageReader.getSurface()), new CameraCaptureSession.StateCallback() {
                @Override
                // 当会话配置完成时调用
                public void onConfigured(@NonNull CameraCaptureSession session) {
                    // 如果相机设备已经关闭，直接返回
                    if (cameraDevice == null) {
                        return;
                    }
                    // 将会话赋值给成员变量
                    cameraCaptureSession = session;
                    // 更新相机预览
                    updatePreview();
                }

                @Override
                // 当会话配置失败时调用
                public void onConfigureFailed(@NonNull CameraCaptureSession session) {
                    // 显示Toast消息，指示无法配置相机预览
                    showToast("无法配置相机预览");
                }
            }, null);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    /**
     * 设备最优预览分辨率
     *
     * @param manager
     * @param cameraId
     * @return Size 最优分辨率
     * @throws CameraAccessException
     */
    @RequiresApi(api = Build.VERSION_CODES.S)
    private Size pickPreviewResolution(CameraManager manager, String cameraId) throws CameraAccessException {
        CameraExtensionCharacteristics extensionCharacteristics = null;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            extensionCharacteristics = manager.getCameraExtensionCharacteristics(cameraId);
        }
        int currentExtension = CameraExtensionCharacteristics.EXTENSION_AUTOMATIC;

        CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraId);
        StreamConfigurationMap map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
        Size[] textureSizes = map.getOutputSizes(ImageFormat.JPEG);

        Point displaySize = new Point();
        DisplayMetrics displayMetrics = this.getResources().getDisplayMetrics();
        displaySize.x = displayMetrics.widthPixels;
        displaySize.y = displayMetrics.heightPixels;

        if (displaySize.x < displaySize.y) {
            displaySize.x = displayMetrics.heightPixels;
            displaySize.y = displayMetrics.widthPixels;
        }

        float targetAspectRatio = 16f / 9f; // 16:9的宽高比
        ArrayList<Size> previewSizes = new ArrayList<>();
        for (int i = textureSizes.length - 1; i >= 0; i--) {
            Size sz = textureSizes[i];
            float arRatio = (float) sz.getWidth() / sz.getHeight();
            if (Math.abs(arRatio - targetAspectRatio) < 0.01f) {
                previewSizes.add(sz);
            }
        }

        List<Integer> supportedExtensions = extensionCharacteristics.getSupportedExtensions();
        boolean isExtensionSupported = supportedExtensions.contains(currentExtension);

        Size[] extensionSizes = new Size[0];
        if (isExtensionSupported) {
            List<Size> extensionSupportedSizes = extensionCharacteristics.getExtensionSupportedSizes(currentExtension, SurfaceTexture.class);
            extensionSizes = extensionSupportedSizes.toArray(new Size[0]);
        }

        // 使用过滤后的尺寸列表进行预览尺寸的选择
        Size previewSize = null;
        if (isExtensionSupported) {
            List<Size> supportedPreviewSizes = previewSizes.stream()
                    .distinct()
                    .filter(Arrays.asList(extensionSizes)::contains)
                    .collect(Collectors.toList());

            // 从支持的预览尺寸中选择最接近显示尺寸的尺寸
            int currentDistance = Integer.MAX_VALUE;
            for (Size sz : supportedPreviewSizes) {
                int distance = (int) Math.abs(sz.getWidth() * sz.getHeight() - displaySize.x * displaySize.y);
                if (currentDistance > distance) {
                    currentDistance = distance;
                    previewSize = sz;
                }
            }
        }

        // 如果扩展不支持或没有找到合适的扩展尺寸，从原始尺寸中选择
        if (previewSize == null) {
            int currentDistance = Integer.MAX_VALUE;
            for (Size sz : previewSizes) {
                int distance = (int) Math.abs(sz.getWidth() * sz.getHeight() - displaySize.x * displaySize.y);
                if (currentDistance > distance) {
                    currentDistance = distance;
                    previewSize = sz;
                }
            }
        }

        return previewSize;
    }



    // 更新相机预览
    private void updatePreview() {
        // 检查相机设备是否为空，如果为空则直接返回
        if (cameraDevice == null) {
            return;
        }
        // 设置捕获请求的控制模式为自动
        captureRequestBuilder.set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO);
        try {
            // 开始持续预览
            cameraCaptureSession.setRepeatingRequest(captureRequestBuilder.build(), null, backgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    // TextureView 的 SurfaceTexture 监听器
    private final TextureView.SurfaceTextureListener textureListener = new TextureView.SurfaceTextureListener() {
        @Override
        // 当 SurfaceTexture 可用时调用，通常在 TextureView 创建后调用
        public void onSurfaceTextureAvailable(SurfaceTexture surfaceTexture, int i, int i1) {
            // 打开相机
            openCamera();
        }

        @Override
        // 当 SurfaceTexture 大小发生变化时调用
        public void onSurfaceTextureSizeChanged(SurfaceTexture surfaceTexture, int i, int i1) {
            // 这里不执行任何操作
        }

        @Override
        // 当 SurfaceTexture 销毁时调用，通常在 TextureView 销毁前调用
        public boolean onSurfaceTextureDestroyed(SurfaceTexture surfaceTexture) {
            return false;
        }

        @Override
        // 当 SurfaceTexture 更新时调用
        public void onSurfaceTextureUpdated(SurfaceTexture surfaceTexture) {
            // 这里不执行任何操作
        }
    };

    @Override
// 当 Activity 进入前台时调用
    protected void onResume() {
        super.onResume();
        // 启动后台线程
        startBackgroundThread();
        // 如果 TextureView 已经可用，打开相机，否则设置 TextureView 的监听器
        if (textureView.isAvailable()) {
            openCamera();
        } else {
            textureView.setSurfaceTextureListener(textureListener);
        }
    }

    @Override
// 当 Activity 进入后台时调用
    protected void onPause() {
        // 关闭相机，停止后台线程
        closeCamera();
        stopBackgroundThread();
        super.onPause();
    }

    // 关闭相机
    private void closeCamera() {
        // 如果相机捕获会话不为空，关闭它
        if (cameraCaptureSession != null) {
            cameraCaptureSession.close();
            cameraCaptureSession = null;
        }
        // 如果相机设备不为空，关闭它
        if (cameraDevice != null) {
            cameraDevice.close();
            cameraDevice = null;
        }
        // 如果图像读取器不为空，关闭它
        if (imageReader != null) {
            imageReader.close();
            imageReader = null;
        }
    }

    private Mat stitchImagesRecursive(List<Mat> mats) {
        if (mats.size() == 0)
            return new Mat();
        if (mats.size() == 1) {
            return mats.get(0);
        } else if (mats.size() == 2) {
            return stitchImagesT(mats.get(0), mats.get(1));
        } else {
            int midIndex = mats.size() / 2;
            List<Mat> firstHalf = mats.subList(0, midIndex);
            List<Mat> secondHalf = mats.subList(midIndex, mats.size());
            Mat stitchedFirstHalf = stitchImagesRecursive(firstHalf);
            Mat stitchedSecondHalf = stitchImagesRecursive(secondHalf);
            return stitchImagesT(stitchedFirstHalf, stitchedSecondHalf);
        }
    }

    // 图像拼接函数
    public Mat stitchImagesT(Mat imgLeft, Mat imgRight) {

        // 检测SIFT关键点和描述子
        SIFT sift = SIFT.create(1000);
        MatOfKeyPoint keypointsLeft = new MatOfKeyPoint();
        MatOfKeyPoint keypointsRight = new MatOfKeyPoint();
        Mat descriptorsLeft = new Mat();
        Mat descriptorsRight = new Mat();
        sift.detectAndCompute(imgLeft, new Mat(), keypointsLeft, descriptorsLeft);
        sift.detectAndCompute(imgRight, new Mat(), keypointsRight, descriptorsRight);

        // 特征匹配器
        BFMatcher bfMatcher = BFMatcher.create(Core.NORM_L2, false);
        List<MatOfDMatch> knnMatches = new ArrayList<>();
        bfMatcher.knnMatch(descriptorsLeft, descriptorsRight, knnMatches, 2);

        List<DMatch> goodMatches = new ArrayList<>();
        float ratioThreshold = 1f;
        for (MatOfDMatch knnMatch : knnMatches) {
            DMatch[] matches = knnMatch.toArray();
            if (matches[0].distance < ratioThreshold * matches[1].distance) {
                goodMatches.add(matches[0]);
            }
        }
        // 筛选出较好的匹配点
        MatOfDMatch goodMatchesMat = new MatOfDMatch();
        goodMatchesMat.fromList(goodMatches);

        // 获取匹配点的关键点
        List<KeyPoint> keypointsLeftList = keypointsLeft.toList();
        List<KeyPoint> keypointsRightList = keypointsRight.toList();

        // 筛选出在水平或竖直方向上偏移过大的特征点
        double xmax = 0;
        double ymax = 0;
        List<DMatch> goodMatchesHorizontal = new ArrayList<>();
        float yThreshold = 20f;
        float xThreshold = imgLeft.cols();
        for (DMatch match : goodMatches) {
            Point pointLeft = keypointsLeftList.get(match.queryIdx).pt;
            Point pointRight = keypointsRightList.get(match.trainIdx).pt;

            if (Math.abs(pointLeft.y - pointRight.y) < yThreshold && Math.abs(pointLeft.x - pointRight.x) <= xThreshold) {
                goodMatchesHorizontal.add(match);
            }
            ymax = Math.max(pointLeft.y - pointRight.y, ymax);
            xmax = Math.max(pointLeft.x - pointRight.x, xmax);
        }

        // 构建匹配点的特征点坐标
        List<Point> pointsLeft = new ArrayList<>();
        List<Point> pointsRight = new ArrayList<>();
        for (DMatch match : goodMatchesHorizontal) {
            pointsLeft.add(keypointsLeftList.get(match.queryIdx).pt);
            pointsRight.add(keypointsRightList.get(match.trainIdx).pt);
        }

        // 将特征点坐标转换为MatOfPoint2f格式
        MatOfPoint2f srcPoints = new MatOfPoint2f();
        MatOfPoint2f dstPoints = new MatOfPoint2f();
        srcPoints.fromList(pointsLeft);
        dstPoints.fromList(pointsRight);

        MatOfDMatch goodMatchesMat1 = new MatOfDMatch();
        goodMatchesMat1.fromList(goodMatchesHorizontal);

        Mat imgResult = new Mat();
        Mat H = Calib3d.findHomography(dstPoints, srcPoints, Calib3d.RANSAC);
        //对image_right进行透视变换
        Imgproc.warpPerspective(imgRight, imgResult, H, new org.opencv.core.Size(imgRight.cols() + imgLeft.cols(), imgRight.rows()));
        Mat imageTransform1 = imgResult.clone();
        //将image_left拷贝到透视变换后的图片上，完成图像拼接
        imgLeft.copyTo(imgResult.submat(new Rect(0, 0, imgLeft.cols(), imgLeft.rows())));


        ImageStitching.calcCorners(H, imgRight);
        ImageStitching.optimizeSeam(imgLeft, imageTransform1, imgResult);
        // 优化接缝

        save(imgLeft, "left");
        save(imageTransform1, "imageTransform1");
        save(imgResult, "imgResult");

        // 拿到黑色区域范围
        Mat grayImage = new Mat();
        Imgproc.cvtColor(imgResult, grayImage, Imgproc.COLOR_BGR2GRAY);
        int lastCol = grayImage.cols() - 1;
        for (; lastCol >= 0; lastCol--) {
            Mat col = grayImage.col(lastCol);
            int i = Core.countNonZero(col);
            if (Core.countNonZero(col) >= col.rows() / 2) {
                break;
            }
        }
        imgResult = imgResult.colRange(0, lastCol + 1);

        // 裁剪黑色范围
        Mat subMat = new Mat(imgResult, new Rect(0, 0, lastCol + 1, imgResult.height()));

        return subMat;
    }

    public static void optimizeSeam(Mat img1, Mat img2, Mat dst) {
        int start = img1.cols();
        int end = img1.cols();
        int h = img1.rows();

        for (int j = start; j < end; j++) {
            for (int i = 0; i < h; i++) {
                double alpha = (double) (j - start) / (end - start); // 计算权值

                double[] pixel1 = img1.get(i, j);
                double[] pixel2 = img2.get(i, j - img1.cols() + img2.cols());

                // 使用加权平均法进行像素混合
                double[] pixel = new double[pixel1.length];
                for (int k = 0; k < pixel1.length; k++) {
                    pixel[k] = pixel1[k] * (1 - alpha) + pixel2[k] * alpha;
                }
                dst.put(i, j, pixel);
            }
        }
    }

    public boolean save(Mat mat, String name) {
        // 保存全景图片
        String directory = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES).toString();
        String filename = "panorama_" + name + "-" + System.currentTimeMillis() + ".jpg";
        String fullPath = directory + File.separator + filename;
        boolean success = Imgcodecs.imwrite(fullPath, mat);
        MediaScannerConnection.scanFile(MainActivity.this, new String[]{fullPath}, null, null);
        return success;
    }

}
