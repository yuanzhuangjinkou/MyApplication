#include <jni.h>
#include <stdlib.h>
#include <string>
#include <android/log.h>
#include "opencv2/opencv.hpp"
#include "opencv2/imgproc/types_c.h"
#include "opencv2/opencv_modules.hpp"

const char *TAG = "fly-native";

#define LOG __android_log_print

void LOGI(const char *info, std::string s) {
    LOG(ANDROID_LOG_INFO, TAG, info, s.c_str());
}

void LOGI(const char *info, int num) {
    LOGI(info, std::to_string(num));
}

using namespace cv;

extern "C"
JNIEXPORT jstring JNICALL
Java_my_opencv_MainActivity_myOpenCv(JNIEnv *env, jobject thiz) {
    int v_major = cv::getVersionMajor();
    int v_minor = cv::getVersionMinor();
    LOGI("major %s", v_major);
    LOGI("minor %s", v_minor);
    std::string vs = cv::getVersionString();
    return env->NewStringUTF(vs.c_str());
}