package com.example.myapplication;

/**
 * c++ 拼接修改 java
 */

import org.opencv.core.*;
import org.opencv.core.Scalar;
import org.opencv.core.CvType;
import org.opencv.core.Core;

import java.nio.ByteBuffer;

public class ImageStitching {
    static {
//        System.loadLibrary(Core.NATIVE_LIBRARY_NAME);
    }

    static class FourCorners {
        Point leftTop;
        Point leftBottom;
        Point rightTop;
        Point rightBottom;
    }

    static FourCorners corners = new FourCorners();

    public static void calcCorners(Mat H, Mat src) {
        double[] v2 = {0, 0, 1}; // 左上角
        double[] v1 = new double[3]; // 变换后的坐标值
        Mat V2 = new Mat(3, 1, CvType.CV_64FC1);
        Mat V1 = new Mat(3, 1, CvType.CV_64FC1);

        V2.put(0, 0, v2);
        Core.gemm(H, V2, 1, new Mat(), 0, V1);
        System.out.println("V2: " + V2.dump());
        System.out.println("V1: " + V1.dump());
        double x1 = V1.get(0, 0)[0] / V1.get(2, 0)[0];
        double y1 = V1.get(1, 0)[0] / V1.get(2, 0)[0];
        corners.leftTop = new Point(Math.max(0, Math.min(x1, src.cols() - 1)), Math.max(0, Math.min(y1, src.rows() - 1)));


        v2[0] = 0;
        v2[1] = src.rows();
        v2[2] = 1;
        V2.put(0, 0, v2);
        Core.gemm(H, V2, 1, new Mat(), 0, V1);
        x1 = V1.get(0, 0)[0] / V1.get(2, 0)[0];
        y1 = V1.get(1, 0)[0] / V1.get(2, 0)[0];
        corners.leftBottom = new Point(Math.max(0, Math.min(x1, src.cols() - 1)), Math.max(0, Math.min(y1, src.rows() - 1)));

        v2[0] = src.cols();
        v2[1] = 0;
        v2[2] = 1;
        V2.put(0, 0, v2);
        Core.gemm(H, V2, 1, new Mat(), 0, V1);
        x1 = V1.get(0, 0)[0] / V1.get(2, 0)[0];
        y1 = V1.get(1, 0)[0] / V1.get(2, 0)[0];
        corners.rightTop = new Point(Math.max(0, Math.min(x1, src.cols() - 1)), Math.max(0, Math.min(y1, src.rows() - 1)));
//        corners.rightTop = new Point(V1.get(0, 0)[0] / V1.get(2, 0)[0], V1.get(1, 0)[0] / V1.get(2, 0)[0]);


        v2[0] = src.cols();
        v2[1] = src.rows();
        v2[2] = 1;
        V2.put(0, 0, v2);
        Core.gemm(H, V2, 1, new Mat(), 0, V1);
        x1 = V1.get(0, 0)[0] / V1.get(2, 0)[0];
        y1 = V1.get(1, 0)[0] / V1.get(2, 0)[0];
        corners.rightBottom = new Point(Math.max(0, Math.min(x1, src.cols() - 1)), Math.max(0, Math.min(y1, src.rows() - 1)));
    }

    // 1902行, 1080列
    public static void optimizeSeam(Mat img1, Mat trans, Mat dst) {
        int start = (int) Math.min(corners.leftTop.x, corners.leftBottom.x);

        double processWidth = img1.cols() - start; // Width of the overlapping region
        int rows = dst.rows();
        int cols = img1.cols() + 5; // Note: it's the number of columns * number of channels
        double alpha = 1; // Weight of pixels in img1
        for (int i = 0; i < rows; i++) {
            byte[] p = doubleArrayToByteArray(img1.get(i, 0)); // Get the address of the first element in the i-th row
            byte[] t = doubleArrayToByteArray(trans.get(i, 0));
            byte[] d = doubleArrayToByteArray(dst.get(i, 0));
            for (int j = start; j < cols && j * 3 + 2 < t.length; j++) {
                // If encountering black pixels with no intensity in the trans image, completely copy the data from img1
                if (t[j * 3] == 0 && t[j * 3 + 1] == 0 && t[j * 3 + 2] == 0) {
                    alpha = 1;
                } else {
                    // Weight of pixels in img1, proportional to the distance from the current processing point to the left boundary of the overlapping region
                    alpha = (processWidth - (j - start)) / processWidth;
                }

                d[j * 3] = (byte) (p[j * 3] * alpha + t[j * 3] * (1 - alpha));
                d[j * 3 + 1] = (byte) (p[j * 3 + 1] * alpha + t[j * 3 + 1] * (1 - alpha));
                d[j * 3 + 2] = (byte) (p[j * 3 + 2] * alpha + t[j * 3 + 2] * (1 - alpha));
            }
        }
    }

    public static byte[] doubleArrayToByteArray(double[] doubles) {
        ByteBuffer buffer = ByteBuffer.allocate(doubles.length * Double.BYTES);
        buffer.asDoubleBuffer().put(doubles);
        return buffer.array();
    }
}
