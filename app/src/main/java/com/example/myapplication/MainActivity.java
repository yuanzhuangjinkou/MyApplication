package com.example.myapplication;

import static org.opencv.calib3d.Calib3d.findFundamentalMat;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.ImageFormat;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.TotalCaptureResult;
import android.media.Image;
import android.media.ImageReader;
import android.media.MediaScannerConnection;
import android.os.Build;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
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

import org.opencv.android.OpenCVLoader;
import org.opencv.calib3d.Calib3d;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.DMatch;
import org.opencv.core.KeyPoint;
import org.opencv.core.Mat;
import org.opencv.core.MatOfByte;
import org.opencv.core.MatOfDMatch;
import org.opencv.core.MatOfKeyPoint;
import org.opencv.core.MatOfPoint;
import org.opencv.core.MatOfPoint2f;
import org.opencv.core.MatOfPoint3f;
import org.opencv.core.Point;
import org.opencv.core.Range;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.features2d.BFMatcher;
import org.opencv.features2d.DescriptorMatcher;
import org.opencv.features2d.Features2d;
import org.opencv.features2d.ORB;
import org.opencv.features2d.SIFT;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;
import org.opencv.osgi.OpenCVNativeLoader;
import org.opencv.photo.Photo;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

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

    // 后台线程和处理程序
    private Handler backgroundHandler;
    private HandlerThread backgroundThread;

    // 捕获间隔时间
    private final int CAPTURE_INTERVAL = 1000;
    // 总秒数, 设置为60秒, 模拟取消自动结束
    private final int TOTAL_TIME = 30000;

    private boolean isCapturing = false;

    private List<Mat> matList = new ArrayList<>();
    // 相机捕获回调
    private final CameraCaptureSession.CaptureCallback captureCallback = new CameraCaptureSession.CaptureCallback() {
        @Override
        public void onCaptureCompleted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull TotalCaptureResult result) {
            super.onCaptureCompleted(session, request, result);
        }
    };

    private Handler handler = new Handler();
    private void executeMethod() {
        // 执行你的方法逻辑
        takePicture();
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                executeMethod(); // 递归调用方法，实现无限执行
            }
        }, 1000); // 设置延迟时间为1秒（1000毫秒）
    }
    private void startCapture() {
        captureButton.setText("停止拍摄");
        isCapturing = true;
        matList.clear();
        executeMethod();
    }

    private void stopCapture() {
        captureButton.setText("全景图片生成中...");
        isCapturing = false;
    }


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

    // 拼接全景图片
    public void jointAndSave() {

        for (Mat mat : matList) {
            String directory = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES).toString();
            String filename = "panorama_" + "-" + System.currentTimeMillis() + ".jpg";
            String fullPath = directory + File.separator + filename;
            boolean success = Imgcodecs.imwrite(fullPath, mat);
            MediaScannerConnection.scanFile(MainActivity.this, new String[]{fullPath}, null, null);
        }

        Mat panorama = new Mat();
//                try {
        panorama = stitchImagesRecursive(matList);
//                } catch (Exception e) {
//                    e.printStackTrace();
//                    showToast("全景拼接失败");
//                    return;
//                }

        String directory = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES).toString();
        String filename = "panorama_" + "-" + System.currentTimeMillis() + ".jpg";
        String fullPath = directory + File.separator + filename;
        boolean success = Imgcodecs.imwrite(fullPath, panorama);
        MediaScannerConnection.scanFile(MainActivity.this, new String[]{fullPath}, null, null);
        captureButton.setText("开始拍摄");
        showToast("全景图已生成");
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
                image = reader.acquireLatestImage();
                // 获取最新可用的图像
                if (image != null) {
                    String s = imageToBase64(image);
                    Mat mat = base64ToMat(s);
                    matList.add(mat);
//                    if(matList.size() == 1)
//                        pmat = mat;
//                    else
//                        pmat = stitchImagesTwo(pmat, mat);
                }
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                assert image != null;
                image.close();
            }
        }
    };

    public String imageToBase64(Image image) {
        ByteBuffer buffer = image.getPlanes()[0].getBuffer();
        byte[] bytes = new byte[buffer.remaining()];
        buffer.get(bytes);
        return java.util.Base64.getEncoder().encodeToString(bytes);
    }

    public Mat base64ToMat(String base64Image) throws IOException {
        // 解码 Base64 图片数据
        byte[] imageBytes = java.util.Base64.getDecoder().decode(base64Image);

        // 创建 ByteArrayInputStream 以读取字节数组
        ByteArrayInputStream inputStream = new ByteArrayInputStream(imageBytes);

        // 使用 OpenCV 加载图像
        Mat imageMat = Imgcodecs.imdecode(new MatOfByte(imageBytes), Imgcodecs.IMREAD_COLOR);

        // 调整图像尺寸（可选）
        org.opencv.core.Size newSize = new org.opencv.core.Size(640, 480);
        Imgproc.resize(imageMat, imageMat, newSize);

        return imageMat;
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
            // 图片顺时针旋转90度
            captureRequestBuilder.set(CaptureRequest.JPEG_ORIENTATION, 90);

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

    /**
        String tdirectory = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES).toString();
        String tfilename = "pinjie" + "-" + System.currentTimeMillis() + ".jpg";
        String tfullPath = tdirectory + File.separator + tfilename;
        boolean tsuccess = Imgcodecs.imwrite(tfullPath, stitchedImage);
        MediaScannerConnection.scanFile(MainActivity.this, new String[]{tfullPath}, null, null);
     */


        private Mat stitchImagesRecursive(List<Mat> mats) {
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

//            String tdirectory1 = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES).toString();
//            String tfilename1 = "pinjie" + "-" + System.currentTimeMillis() + ".jpg";
//            String tfullPath1 = tdirectory1 + File.separator + tfilename1;
//            boolean tsuccess1 = Imgcodecs.imwrite(tfullPath1, stitchedFirstHalf);
//            MediaScannerConnection.scanFile(MainActivity.this, new String[]{tfullPath1}, null, null);
//
//            String tdirectory2 = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES).toString();
//            String tfilename2 = "pinjie" + "-" + System.currentTimeMillis() + ".jpg";
//            String tfullPath2 = tdirectory2 + File.separator + tfilename2;
//            boolean tsuccess2 = Imgcodecs.imwrite(tfullPath2, stitchedSecondHalf);
//            MediaScannerConnection.scanFile(MainActivity.this, new String[]{tfullPath2}, null, null);

            return stitchImagesT(stitchedFirstHalf, stitchedSecondHalf);
        }
    }

    // 图像拼接函数
    public Mat stitchImagesT(Mat imgLeft, Mat imgRight) {

        // 检测SIFT关键点和描述子
        SIFT sift = SIFT.create();
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

//        // 绘制匹配结果
//        Mat outputImage = new Mat();
//        Features2d.drawMatches(imgLeft, keypointsLeft, imgRight, keypointsRight, goodMatchesMat1, outputImage);
//        // 保存绘制匹配结果
//        String directory = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES).toString();
//        String filename = "plotResult" + "-" + System.currentTimeMillis() + ".jpg";
//        String fullPath = directory + File.separator + filename;
//        boolean success = Imgcodecs.imwrite(fullPath, outputImage);
//        MediaScannerConnection.scanFile(MainActivity.this, new String[]{fullPath}, null, null);

        Mat imgResult = new Mat();
//        try {
        // 计算单应性矩阵H
        // Mat H = Calib3d.findHomography(dstPoints, srcPoints, Calib3d.RHO);
        Mat H = Calib3d.findHomography(dstPoints, srcPoints, Calib3d.RANSAC);

        //对image_right进行透视变换
        Imgproc.warpPerspective(imgRight, imgResult, H, new org.opencv.core.Size(imgRight.cols() + imgLeft.cols(), imgRight.rows()));

//        String tdirectory = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES).toString();
//        String tfilename = "toushihou" + "-" + System.currentTimeMillis() + ".jpg";
//        String tfullPath = tdirectory + File.separator + tfilename;
//        boolean tsuccess = Imgcodecs.imwrite(tfullPath, imgResult);
//        MediaScannerConnection.scanFile(MainActivity.this, new String[]{tfullPath}, null, null);

        //将image_left拷贝到透视变换后的图片上，完成图像拼接
        imgLeft.copyTo(imgResult.submat(new Rect(0, 0, imgLeft.cols(), imgLeft.rows())));
//        } catch (Exception e) {
//            e.printStackTrace();
//            throw new RuntimeException("全景拼接失败"); // 抛出另
//        }

        // 优化接缝
        int overlapWidth = imgLeft.cols() + imgRight.cols() - imgResult.cols(); // 计算重叠的最大可能宽度
        int start = imgLeft.cols() - overlapWidth;
        int end = imgLeft.cols();
        double minDiff = Double.MAX_VALUE;
        for (int i = start; i < end; i++) {
            Mat leftEdge = imgLeft.colRange(i, i + 1);
            Mat rightEdgeInResult = imgResult.colRange(i, i + 1);
            double diff = Core.norm(leftEdge, rightEdgeInResult, Core.NORM_L1);
            if (diff < minDiff) {
                minDiff = diff;
                start = i;
            }
        }
        optimizeSeam(imgLeft, imgResult.colRange(start, end), imgResult);

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
        int start = Math.max(0, img1.cols() - img2.cols());
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

}
