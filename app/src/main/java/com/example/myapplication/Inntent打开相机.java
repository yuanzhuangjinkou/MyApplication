//package com.example.myapplication;
//
//import android.content.Context;
//import android.content.Intent;
//import android.hardware.camera2.CameraAccessException;
//import android.hardware.camera2.CameraCharacteristics;
//import android.hardware.camera2.CameraDevice;
//import android.hardware.camera2.CameraExtensionCharacteristics;
//import android.hardware.camera2.CameraExtensionSession;
//import android.hardware.camera2.CameraManager;
//import android.hardware.camera2.CaptureRequest;
//import android.hardware.camera2.params.ExtensionSessionConfiguration;
//import android.hardware.camera2.params.OutputConfiguration;
//import android.os.Build;
//import android.os.Bundle;
//import android.provider.MediaStore;
//import android.util.Log;
//
//import androidx.annotation.NonNull;
//import androidx.annotation.Nullable;
//import androidx.appcompat.app.AppCompatActivity;
//
//import com.google.android.material.snackbar.Snackbar;
//
//import java.util.ArrayList;
//import java.util.Arrays;
//import java.util.Collections;
//import java.util.List;
//import java.util.stream.Collectors;
//import java.util.stream.IntStream;
//
//public class MainActivity extends AppCompatActivity {
//
//    static final int REQUEST_IMAGE_CAPTURE = 1;
//
//    @Override
//    protected void onCreate(Bundle savedInstanceState) {
//        super.onCreate(savedInstanceState);
//        // setContentView(R.layout.activity_main); // 不需要设置ContentView，因为我们不显示任何东西
//        try {
//            dispatchTakePictureIntent();
//        } catch (CameraAccessException e) {
//            throw new RuntimeException(e);
//        }
//    }
//
//    private void dispatchTakePictureIntent() throws CameraAccessException {
//        Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
//        if (takePictureIntent.resolveActivity(getPackageManager()) != null) {
//            startActivityForResult(takePictureIntent, REQUEST_IMAGE_CAPTURE);
//        }
//        CameraManager cameraManager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
//
//    }
//
//
//}
//
