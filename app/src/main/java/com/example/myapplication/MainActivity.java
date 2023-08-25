package com.example.myapplication;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.ImageFormat;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.*;
import android.media.Image;
import android.media.ImageReader;
import android.os.*;
import android.util.Log;
import android.util.Size;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.io.*;
import java.nio.ByteBuffer;
import java.util.Arrays;

public class MainActivity extends AppCompatActivity {

    // 请求相机权限的常量
    private static final int REQUEST_CAMERA_PERMISSION = 200;
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

    // 图片保存文件夹
    private File galleryFolder;

    // 后台线程和处理程序
    private Handler backgroundHandler;
    private HandlerThread backgroundThread;

    // 相机捕获回调
    private final CameraCaptureSession.CaptureCallback captureCallback = new CameraCaptureSession.CaptureCallback() {
        @Override
        public void onCaptureCompleted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull TotalCaptureResult result) {
            super.onCaptureCompleted(session, request, result);
            showToast("图片已捕获");
            backgroundHandler.post(new Runnable() {
                @Override
                public void run() {
                    synchronized (this) {
                        captureCount++;
                    }
                }
            });
        }
    };

    private void takeContinuousPictures() {
        synchronized (this) {
            if (captureCount < 10) {
                takePicture();
                backgroundHandler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        takeContinuousPictures();
                    }
                }, 2000); //0.5 seconds delay
            }
        }
    }

    private int captureCount = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // 初始化界面元素
        textureView = findViewById(R.id.textureView);
        captureButton = findViewById(R.id.captureButton);

        // 设置按钮的点击监听器，当用户点击按钮时，调用 takePicture 方法拍摄照片
        captureButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                captureCount = 0;
                takeContinuousPictures();
            }
        });


        // 获取相机管理器
        cameraManager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);

        // 设置 TextureView 的监听器
        textureView.setSurfaceTextureListener(textureListener);

        // 创建保存图片的文件夹
        createImageGallery();

        // 启动后台线程
        startBackgroundThread();
    }

    // 创建用于保存图片的文件夹
    private void createImageGallery() {
        File storageDirectory = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES);
        galleryFolder = new File(storageDirectory, "Camera2APIDemo");
        if (!galleryFolder.exists()) {
            boolean created = galleryFolder.mkdirs();
            if (!created) {
                Log.e(TAG, "Failed to create gallery folder.");
            }
        }
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
                // 使用Toast类创建一个短暂的消息提示
                // MainActivity.this 表示在当前活动中显示Toast
                // text 是要显示的消息文本
                // Toast.LENGTH_SHORT 表示消息显示的时长（短暂）
                Toast.makeText(MainActivity.this, text, Toast.LENGTH_SHORT).show();
            }
        });
    }


    // 打开相机的方法
    private void openCamera() {
        try {
            // 获取可用的相机ID，通常取第一个相机
            String cameraId = cameraManager.getCameraIdList()[0];
            // 获取相机的特性信息
            CameraCharacteristics characteristics = cameraManager.getCameraCharacteristics(cameraId);
            // 用于存储支持的JPEG图像尺寸的数组
            Size[] jpegSizes = null;
            if (characteristics != null) {
                // 获取相机支持的JPEG图像尺寸
                jpegSizes = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP).getOutputSizes(ImageFormat.JPEG);
            }
            // 设置图像的默认宽度和高度
            int width = 640;
            int height = 480;
            if (jpegSizes != null && jpegSizes.length > 0) {
                // 如果支持JPEG尺寸，则使用第一个支持的尺寸
                width = jpegSizes[0].getWidth();
                height = jpegSizes[0].getHeight();
            }
            // 配置ImageReader以接收相机图像
            configureImageReader(width, height);
            // 检查相机权限是否已授予
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                // 打开相机，传入相机ID和相机状态回调
                cameraManager.openCamera(cameraId, stateCallback, null);
            } else {
                // 如果没有相机权限，则请求相机权限
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, REQUEST_CAMERA_PERMISSION);
            }
        } catch (CameraAccessException e) {
            e.printStackTrace();
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
                // 获取最新可用的图像
                image = reader.acquireLatestImage();
                // 获取图像的第一个平面（通常是图像数据）
                ByteBuffer buffer = image.getPlanes()[0].getBuffer();
                // 创建一个字节数组，用于存储图像数据
                byte[] bytes = new byte[buffer.capacity()];
                // 将图像数据从缓冲区复制到字节数组
                buffer.get(bytes);
                // 将图像保存到文件
                saveImage(bytes);
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                if (image != null) {
                    // 关闭图像以释放资源
                    image.close();
                }
            }
        }
    };


    // 保存图像到文件
    private void saveImage(byte[] bytes) throws IOException {
        File imageFile = new File(galleryFolder, "IMG_" + System.currentTimeMillis() + ".jpg");
        try (FileOutputStream output = new FileOutputStream(imageFile)) {
            output.write(bytes);
        }
        showToast("图片保存路径: " + imageFile.getAbsolutePath());
    }


    // 锁定焦点的方法，触发自动对焦操作
    private void lockFocus() {
        try {
            // 设置捕获请求的自动对焦触发器为开始状态
            captureRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER, CaptureRequest.CONTROL_AF_TRIGGER_START);
            // 开始捕获图像，捕获后调用 captureCallback 进行后续处理
            cameraCaptureSession.capture(captureRequestBuilder.build(), captureCallback, backgroundHandler);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    // 解锁焦点的方法，取消自动对焦触发并将其设置为闲置状态
    private void unlockFocus() {
        try {
            // 取消自动对焦触发
            captureRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER, CaptureRequest.CONTROL_AF_TRIGGER_CANCEL);
            // 捕获图像，捕获后调用 captureCallback 进行后续处理
            cameraCaptureSession.capture(captureRequestBuilder.build(), captureCallback, backgroundHandler);
            // 将自动对焦触发器设置为闲置状态
            captureRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER, CaptureRequest.CONTROL_AF_TRIGGER_IDLE);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    // 拍摄照片的方法
    private void takePicture() {
        // 检查相机设备是否为空，如果为空则退出方法
        if (cameraDevice == null) {
            return;
        }
        try {
            // 创建用于拍照的捕获请求，使用预定义的 TEMPLATE_STILL_CAPTURE 模板
            captureRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);

            // 将图像输出目标设置为ImageReader的Surface，以便保存捕获的图像
            captureRequestBuilder.addTarget(imageReader.getSurface());

            // 设置自动对焦模式为连续图片，确保照片清晰
            captureRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);

            // 使用相机捕获会话开始捕获图像，捕获后调用 captureCallback 进行后续处理
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
            // 断言 SurfaceTexture 不为空，否则抛出异常
            assert texture != null;
            // 设置默认的缓冲区大小为 640x480
            texture.setDefaultBufferSize(640, 480);
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

}
