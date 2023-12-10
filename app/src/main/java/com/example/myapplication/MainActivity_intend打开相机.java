//package com.example.myapplication;
//
//import android.Manifest;
//import android.content.Intent;
//import android.content.pm.PackageManager;
//import android.os.Bundle;
//import android.provider.MediaStore;
//
//import androidx.appcompat.app.AppCompatActivity;
//import androidx.core.app.ActivityCompat;
//import androidx.core.content.ContextCompat;
//
//public class MainActivity extends AppCompatActivity {
//
//    static final int REQUEST_IMAGE_CAPTURE = 1;
//    static final int REQUEST_VIDEO_CAPTURE = 2;
//    private static final int PERMISSION_REQUEST_CAMERA = 101;
//
//    @Override
//    protected void onCreate(Bundle savedInstanceState) {
//        super.onCreate(savedInstanceState);
//        // setContentView(R.layout.activity_main);
//
//        // 检查相机权限
//        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
//            // 请求相机权限
//            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, PERMISSION_REQUEST_CAMERA);
//        } else {
//            // 如果已有权限，则直接进行操作
//            dispatchTakePictureIntent();
//        }
//    }
//
//    private void dispatchTakePictureIntent() {
//        Intent takeVideoIntent = new Intent(MediaStore.ACTION_VIDEO_CAPTURE);
//        if (takeVideoIntent.resolveActivity(getPackageManager()) != null) {
//            startActivityForResult(takeVideoIntent, REQUEST_VIDEO_CAPTURE);
//        } else {
//            // 显示错误信息给用户
//        }
//    }
//
//    @Override
//    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
//        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
//        if (requestCode == PERMISSION_REQUEST_CAMERA) {
//            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
//                // 权限被授予，执行相机相关操作
//                dispatchTakePictureIntent();
//            } else {
//                // 权限被拒绝，向用户显示相关信息
//            }
//        }
//    }
//}
