package de.rwth_aachen.phyphox.camera.analyzer;

import android.graphics.RectF;
import android.opengl.EGL14;
import android.opengl.EGLSurface;
import android.opengl.GLES20;
import android.os.Build;

import androidx.annotation.CallSuper;
import androidx.annotation.RequiresApi;

import de.rwth_aachen.phyphox.camera.model.CameraSettingState;

@RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
public abstract class AnalyzingModule {

    AnalyzerConfig analyzerConfig;
    EGLSurface analyzingSurface = null;

    int nDownsampleSteps = 3; // Must be <= 4 (analyzing modules are designed with this limit in mind)
    // 3 downsampling steps seem to be a good trade-off between fixed costs of each step and reducing CPU load.
    // However, this was only tested on a Nexus 5x which had its optimum at 3 and a Pixel 6 where 3 and 4 steps were
    // nearly indistinguishable. Note, that this probably also heavily depends on the resolution that the video
    // stream gets on this device. Both devices used a 1600x1200 stream, but older devices with lower resolutions
    // might reduce the preview stream resolution, hopefully evening out the lower performance of such devices.

    EGLSurface[] downsampleSurfaces = new EGLSurface[nDownsampleSteps];
    int[] wDownsampleStep = new int[nDownsampleSteps];
    int[] hDownsampleStep = new int[nDownsampleSteps];

    int[] downsamplingTextures = new int[nDownsampleSteps];

    public AnalyzingModule(){}

    protected void setupGL(AnalyzerConfig analyzerConfig) {
        this.analyzerConfig = analyzerConfig;

        analyzingSurface = createPbufferSurface(analyzerConfig.width(), analyzerConfig.height());

        configureDownSampling();

        downsampleSurfaces = new EGLSurface[nDownsampleSteps];
        wDownsampleStep = new int[nDownsampleSteps];
        hDownsampleStep = new int[nDownsampleSteps];
        downsamplingTextures = new int[nDownsampleSteps];

        GLES20.glGenTextures(nDownsampleSteps, downsamplingTextures, 0);
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);

        for (int i = 0; i < nDownsampleSteps; i++) {
            calculateStepDimensions(i);
            downsampleSurfaces[i] = createPbufferSurface( wDownsampleStep[i], hDownsampleStep[i]);
        }

    }

    protected void configureDownSampling(){
        nDownsampleSteps = 3;
    }

    protected void calculateStepDimensions(int i){
        int prevW = (i == 0) ? analyzerConfig.width() : wDownsampleStep[i - 1];
        int prevH = (i == 0) ? analyzerConfig.height() : hDownsampleStep[i - 1];

        wDownsampleStep[i] = (prevW + 3) / 4;
        hDownsampleStep[i] = (prevH + 3) / 4;
    }

    @CallSuper
     protected void release() {
         if (analyzingSurface != null) {
             EGL14.eglDestroySurface(analyzerConfig.eglDisplay(), analyzingSurface);
         }
         if (downsampleSurfaces != null) {
             for (EGLSurface surface : downsampleSurfaces) {
                 if (surface != null) EGL14.eglDestroySurface(analyzerConfig.eglDisplay(), surface);
             }
         }
         if (downsamplingTextures != null) {
             GLES20.glDeleteTextures(nDownsampleSteps, downsamplingTextures, 0);
         }
    }

    private EGLSurface createPbufferSurface(int w, int h) {
        int[] surfaceAttr = {
                EGL14.EGL_WIDTH, w,
                EGL14.EGL_HEIGHT, h,
                EGL14.EGL_TEXTURE_FORMAT, EGL14.EGL_TEXTURE_RGBA,
                EGL14.EGL_TEXTURE_TARGET, EGL14.EGL_TEXTURE_2D,
                EGL14.EGL_MIPMAP_TEXTURE, EGL14.EGL_FALSE,
                EGL14.EGL_NONE
        };
        return EGL14.eglCreatePbufferSurface(analyzerConfig.eglDisplay(), analyzerConfig.eglConfig(), surfaceAttr, 0);
    }

    public abstract void prepare();
    public abstract void analyze(float[] camMatrix, RectF passepartout);
    public abstract void writeToBuffers(CameraSettingState state);
    public void destroy() {

    }


}
