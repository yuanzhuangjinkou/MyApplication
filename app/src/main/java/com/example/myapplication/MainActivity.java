package com.example.myapplication;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;

import android.os.Environment;
import android.os.Handler;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Toast;

import org.json.JSONObject;
import org.opencv.android.CameraBridgeViewBase;
import org.opencv.android.JavaCameraView;
import org.opencv.android.OpenCVLoader;
import org.opencv.android.Utils;
import org.opencv.calib3d.Calib3d;
import org.opencv.core.Core;
import org.opencv.core.DMatch;
import org.opencv.core.KeyPoint;
import org.opencv.core.Mat;
import org.opencv.core.MatOfDMatch;
import org.opencv.core.MatOfKeyPoint;
import org.opencv.core.MatOfPoint2f;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.core.Size;
import org.opencv.features2d.BFMatcher;
import org.opencv.features2d.DescriptorMatcher;
import org.opencv.features2d.Feature2D;
import org.opencv.features2d.ORB;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

public class MainActivity extends AppCompatActivity implements CameraBridgeViewBase.CvCameraViewListener2 {

    private static final int CAPTURE_INTERVAL = 1000; // 0.5 seconds in milliseconds   // 拍摄时间 CAPTURE_INTERVAL * TOTAL_FRAMES
    private static final int TOTAL_FRAMES = 5; // 获取10帧
    private int frameCount = 0;
    private boolean isCapturing = false;
    private int framesCaptured = 0;


    private JavaCameraView javaCameraView;  // OpenCV Java Camera View 组件
    private List<Mat> capturedImages = new ArrayList<>();  // 存储捕获的图像的列表
    private long lastTimestamp = 0;

    // 页面展示图片
    private ImageView panoramaView;

    @SuppressLint("MissingInflatedId")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // 1
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Log.d("tag", "onCreate");
        // 检查所需权限，如果没有则请求权限
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            checkPermission();
        }
        // 初始化Java Camera View
        javaCameraView = findViewById(R.id.java_camera_view);
        javaCameraView.setCvCameraViewListener(this);
        initOpenCV();
        Log.d("tag", "initOpenCV调用完成");
        // 初始化按钮和设置点击监听器
        // 配置和设置"捕获"按钮的监听器
        Button btnCapture = findViewById(R.id.btn_capture);
        btnCapture.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.d("tag", "捕获");
                // 如果当前没有在捕获帧
                if (!isCapturing) {
                    // 将标志设置为true，表示现在开始捕获帧
                    isCapturing = true;
                    // 重置已捕获帧的计数器
                    framesCaptured = 0;
                }
            }
        });

        panoramaView = findViewById(R.id.panorama_view);
    }

    private void displayPanorama(Mat panorama) {
        // 将Mat对象转换为Bitmap
        Bitmap bitmap = Bitmap.createBitmap(panorama.cols(), panorama.rows(), Bitmap.Config.ARGB_8888);
        Utils.matToBitmap(panorama, bitmap);

        // 显示Bitmap
        panoramaView.setImageBitmap(bitmap);
        panoramaView.setVisibility(View.VISIBLE);
    }

    private final static String[] permissions = new String[]{Manifest.permission.CAMERA, Manifest.permission.WRITE_EXTERNAL_STORAGE};

    public void checkPermission() {
        Log.d("tag", "checkPermission");
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {

            ActivityCompat.requestPermissions(this, permissions, 1);
            Log.d("tag", "未申请权限");
        } else {
            Log.d("tag", "已申请权限");
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch (requestCode) {
            case 1:
                //权限请求失败
                if (grantResults.length == this.permissions.length) {
                    for (int result : grantResults) {
                        if (result != PackageManager.PERMISSION_GRANTED) {

                            Log.d("tag", "onRequestPermissionsResult: 请求权限被拒绝");
                            break;
                        }
                    }
                } else {
                    Log.d("tag", "onRequestPermissionsResult: 已授权");
                }
                break;
        }
    }

    private void initOpenCV() {

        OpenCVLoader.initDebug();
        Log.d("tag", "OpenCV 初始化");
        // 如果OpenCV成功初始化，启动Java Camera View并设置其监听器
        javaCameraView.setCameraPermissionGranted();
        javaCameraView.enableView();
    }

    private void captureCurrentFrame(Mat frame) {
        Log.e("tag", "captureCurrentFrame帧:" + System.currentTimeMillis());
        // 捕获当前帧并将其添加到capturedImages列表中
        Mat currentFrame = frame.clone();
        capturedImages.add(currentFrame);
        // TODO: 可在此处添加逻辑，当捕获到足够数量的帧后停止捕获并进行图像拼接。
    }

    @Override
    public void onCameraViewStarted(int width, int height) {
        Log.d("tag", "onCameraViewStarted");
        // 相机视图启动时的操作
    }

    @Override
    public void onCameraViewStopped() {
        Log.d("tag", "onCameraViewStopped");
        // 相机视图停止时的操作
    }

    // 点击按钮开始执行, 捕获帧
    @Override
    public Mat onCameraFrame(CameraBridgeViewBase.CvCameraViewFrame inputFrame) {
        Log.e("tag", "onCameraFrame帧:" + System.currentTimeMillis());
        Log.d("tag", "onCameraFrame");

        // 获取当前帧的RGBA图像
        Mat currentFrame = inputFrame.rgba();

        // 获取当前时间戳
        long timestamp = System.currentTimeMillis();

        // 检查是否满足捕获帧的条件：1) isCapturing为true，2) 距离上次捕获的时间超过了CAPTURE_INTERVAL，3) 已捕获的帧数少于TOTAL_FRAMES
        if (isCapturing && (timestamp - lastTimestamp >= CAPTURE_INTERVAL) && frameCount < TOTAL_FRAMES) {
            // 捕获当前帧
            captureCurrentFrame(currentFrame);
            // 更新上次捕获帧的时间戳为当前时间戳
            lastTimestamp = timestamp;
            // 增加已捕获帧的计数
            frameCount++;
            // 如果已经捕获了TOTAL_FRAMES帧
            if (frameCount == TOTAL_FRAMES) {
                // 停止捕获
                isCapturing = false;
                // 保存
                for (int i = 0; i < capturedImages.size(); i++) {
                    String directory = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES).toString();
                    String filename = "panorama_" + i + "-" + System.currentTimeMillis() + ".jpg";
                    String fullPath = directory + File.separator + filename;
                    boolean success = Imgcodecs.imwrite(fullPath, capturedImages.get(i));
                    MediaScannerConnection.scanFile(this, new String[]{fullPath}, null, null);
                }
                // 创建全景图或执行其他操作
                createPanorama();
                // 清除已捕获的帧列表
                capturedImages.clear();
            }
        }
        // 返回当前帧，以便在屏幕上显示
        return currentFrame;
    }

    private void createPanorama() {
        Log.d("tag", "createPanorama");

        // 如果捕获的图像少于2张，不能进行拼接
        if (capturedImages.size() < 2) {
            return;
        }
        // 将捕获的图像列表中的第一张图像赋值给stitched
        Mat stitched = stitchMultipleImages(capturedImages);

        String directory = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES).toString();
        String filename = "panorama_p" + System.currentTimeMillis() + ".jpg";
        String fullPath = directory + File.separator + filename;
        boolean success = Imgcodecs.imwrite(fullPath, stitched);
        MediaScannerConnection.scanFile(this, new String[]{fullPath}, null, null);


    }


    public static Mat stitchMultipleImages(List<Mat> images) {
        List<Mat> descriptors = new ArrayList<>();
        List<MatOfKeyPoint> allKeypoints = new ArrayList<>();

        ORB detector = ORB.create();

        // Detect keypoints and compute descriptors
        for (Mat img : images) {
            MatOfKeyPoint keypoints = new MatOfKeyPoint();
            Mat descriptor = new Mat();
            detector.detectAndCompute(img, new Mat(), keypoints, descriptor);
            allKeypoints.add(keypoints);
            descriptors.add(descriptor);
        }

        BFMatcher matcher = BFMatcher.create(DescriptorMatcher.BRUTEFORCE_HAMMING);

        List<Mat> homographies = new ArrayList<>();

        // Calculate homographies
        for (int i = 1; i < images.size(); i++) {
            MatOfDMatch matches = new MatOfDMatch();
            matcher.match(descriptors.get(i - 1), descriptors.get(i), matches);

            List<Point> pts1 = new ArrayList<>();
            List<Point> pts2 = new ArrayList<>();

            for (DMatch match : matches.toList()) {
                pts1.add(allKeypoints.get(i - 1).toList().get(match.queryIdx).pt);
                pts2.add(allKeypoints.get(i).toList().get(match.trainIdx).pt);
            }

            MatOfPoint2f objPts = new MatOfPoint2f();
            MatOfPoint2f scenePts = new MatOfPoint2f();
            objPts.fromList(pts1);
            scenePts.fromList(pts2);

            Mat H = Calib3d.findHomography(objPts, scenePts, Calib3d.RANSAC, 3.0);
            homographies.add(H);
        }

        Mat panorama = images.get(0).clone();

        for (int i = 1; i < images.size(); i++) {
            Mat result = new Mat();

            Imgproc.warpPerspective(panorama, result, homographies.get(i - 1), new Size(panorama.cols() + images.get(i).cols(), panorama.rows()));

            Mat half = new Mat(result, new Rect(0, 0, images.get(i).cols(), images.get(i).rows()));
            Core.addWeighted(half, 0.5, images.get(i), 0.5, 0.0, half);

            panorama = result;
        }

        return panorama;
    }

}
