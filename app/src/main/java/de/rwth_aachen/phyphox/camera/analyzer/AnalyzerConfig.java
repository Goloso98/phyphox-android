package de.rwth_aachen.phyphox.camera.analyzer;

import android.opengl.EGLConfig;
import android.opengl.EGLContext;
import android.opengl.EGLDisplay;

public record AnalyzerConfig(int width,
                             int height,
                             EGLContext eglContext,
                             EGLDisplay eglDisplay,
                             EGLConfig eglConfig,
                             int cameraTexture) {
}
