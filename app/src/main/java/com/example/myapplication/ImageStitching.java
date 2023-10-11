package com.example.myapplication;

/**
 * c++ 拼接修改 java
 */
import org.opencv.core.*;
import org.opencv.core.Scalar;
import org.opencv.core.CvType;
import org.opencv.core.Core;
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

//    public static void calcCorners(Mat H, Mat src) {
//        double[] v2 = {0, 0, 1}; // 左上角
//        double[] v1 = new double[3]; // 变换后的坐标值
//        Mat V2 = new Mat(3, 1, CvType.CV_64FC1, new Scalar(v2)); // 列向量
//        Mat V1 = new Mat(3, 1, CvType.CV_64FC1, new Scalar(v1)); // 列向量
//
//        Core.gemm(H, V2, 1.0, new Mat(), 0.0, V1);
//
////        v1 = V1.get(0, 0);
////        if(v1[2] != 0)
////            corners.leftTop = new Point(v1[0] / v1[2], v1[1] / v1[2]);
////        else
//            corners.leftTop = new Point(0, 0);
//
//        // 左下角(0,src.rows,1)
//        v2[0] = 0;
//        v2[1] = src.rows();
//        v2[2] = 1;
//        V2.put(0, 0, v2);
//        Core.gemm(H, V2, 1, new Mat(), 0, V1);
//        v1 = V1.get(0, 0);
//        corners.leftBottom = new Point(v1[0] / v1[2], v1[1] / v1[2]);
//
//        // 右上角(src.cols,0,1)
//        v2[0] = src.cols();
//        v2[1] = 0;
//        v2[2] = 1;
//        V2.put(0, 0, v2);
//        Core.gemm(H, V2, 1, new Mat(), 0, V1);
//        v1 = V1.get(0, 0);
//        corners.rightTop = new Point(v1[0] / v1[2], v1[1] / v1[2]);
//
//        // 右下角(src.cols,src.rows,1)
//        v2[0] = src.cols();
//        v2[1] = src.rows();
//        v2[2] = 1;
//        V2.put(0, 0, v2);
//        Core.gemm(H, V2, 1, new Mat(), 0, V1);
//        v1 = V1.get(0, 0);
//        corners.rightBottom = new Point(v1[0] / v1[2], v1[1] / v1[2]);
//    }

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
//        corners.rightBottom = new Point(V1.get(0, 0)[0] / V1.get(2, 0)[0], V1.get(1, 0)[0] / V1.get(2, 0)[0]);
    }

    public static void optimizeSeam(Mat img1, Mat trans, Mat dst) {
        double start = Math.min(corners.leftTop.x, corners.leftBottom.x); // 左上角和左下角的x坐标的最小值

        if(start < 0) {
            System.out.println("sdfaf");
        }
        int processWidth = img1.cols() - (int) start;
        int rows = dst.rows();
        int cols = img1.cols();
        double alpha = 1.0;

        for (int i = 0; i < rows; i++) {
            byte[] p = new byte[cols * img1.channels()];
            byte[] t = new byte[cols * trans.channels()];
            byte[] d = new byte[cols * dst.channels()];

            img1.get(i, 0, p);
            trans.get(i, 0, t);
            dst.get(i, 0, d);

            for (int j = (int) start; j < cols; j++) {
                if (t[j * 3] == 0 && t[j * 3 + 1] == 0 && t[j * 3 + 2] == 0) {
                    alpha = 1.0;
                } else {
                    alpha = (processWidth - (j - start)) / processWidth;
                }

                d[j * 3] = (byte) (p[j * 3] * alpha + t[j * 3] * (1 - alpha));
                d[j * 3 + 1] = (byte) (p[j * 3 + 1] * alpha + t[j * 3 + 1] * (1 - alpha));
                d[j * 3 + 2] = (byte) (p[j * 3 + 2] * alpha + t[j * 3 + 2] * (1 - alpha));
            }

            dst.put(i, 0, d);
        }
    }
}
