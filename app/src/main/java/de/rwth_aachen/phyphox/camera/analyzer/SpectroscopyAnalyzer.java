package de.rwth_aachen.phyphox.camera.analyzer;

import static android.opengl.GLES11Ext.GL_TEXTURE_EXTERNAL_OES;
import static de.rwth_aachen.phyphox.camera.analyzer.LuminanceAnalyzer.lumaFragmentShader;
import static de.rwth_aachen.phyphox.camera.analyzer.LuminanceAnalyzer.luminanceFragmentShader;
import static de.rwth_aachen.phyphox.camera.analyzer.OpenGLHelper.buildProgram;
import static de.rwth_aachen.phyphox.camera.analyzer.OpenGLHelper.checkGLError;
import static de.rwth_aachen.phyphox.camera.analyzer.OpenGLHelper.fullScreenVboTexCoordinates;
import static de.rwth_aachen.phyphox.camera.analyzer.OpenGLHelper.fullScreenVboVertices;
import static de.rwth_aachen.phyphox.camera.analyzer.OpenGLHelper.fullScreenVertexShader;
import static de.rwth_aachen.phyphox.camera.analyzer.OpenGLHelper.interpolatingHeightFullScreenVertexShader;

import android.graphics.RectF;
import android.opengl.EGL14;
import android.opengl.EGLSurface;
import android.opengl.GLES20;
import android.util.Log;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;

import de.rwth_aachen.phyphox.DataBuffer;
import de.rwth_aachen.phyphox.camera.model.CameraSettingState;

public class SpectroscopyAnalyzer extends AnalyzingModule{

    final static String verticalReductionFragmentShader =
            "precision highp float;" +
                    "uniform sampler2D texture;" +
                    "varying vec2 texPosition1;" +
                    "varying vec2 texPosition2;" +
                    "varying vec2 texPosition3;" +
                    "varying vec2 texPosition4;" +
                    "void main () {" +
                    "   vec4 result = texture2D(texture, texPosition1);" +
                    "   if (texPosition2.y <= 1.0)" +
                    "       result += texture2D(texture, texPosition2);" +
                    "   if (texPosition3.y <= 1.0)" +
                    "       result += texture2D(texture, texPosition3);" +
                    "   if (texPosition4.y <= 1.0)" +
                    "       result += texture2D(texture, texPosition4);" +
                    "   float overflow = floor(result.g);" +
                    "   result.g = result.g - overflow;" +
                    "   result.r = result.r + overflow / 255.0;" +
                    "   result.b = result.b / 4.0;" +
                    "   gl_FragColor = result;" +
                    "}";


    boolean linear = false;
    DataBuffer out;
    DataBuffer pixelPosition;

    int spectroscopyProgram, verticalReductionProgram;
    int spectroscopyProgramVerticesHandle, spectroscopyProgramTexCoordinatesHandle;
    int spectroscopyProgramCamMatrixHandle, spectroscopyProgramTextureHandle;
    int spectroscopyProgramPassepartoutMinHandle, spectroscopyProgramPassepartoutMaxHandle;

    int reductionProgramVerticesHandle, reductionProgramTexCoordinatesHandle;
    int reductionProgramTextureHandle, reductionResSourceHandle, reductionResTargetHandle;

    double[] latestResult = null;
    int outputWidth = 0;

    ByteBuffer resultBuffer = null;
    int resultBufferSize = 0;

    public SpectroscopyAnalyzer(DataBuffer out, DataBuffer pixelPosition, boolean linear){
        this.linear = linear;
        this.out = out;
        this.pixelPosition = pixelPosition;
    }

    @Override
    public void prepare() {
        // Prepare spectroscopy conversion program
        spectroscopyProgram = buildProgram(fullScreenVertexShader, linear ? luminanceFragmentShader : lumaFragmentShader);
        spectroscopyProgramVerticesHandle = GLES20.glGetAttribLocation(spectroscopyProgram, "vertices");
        spectroscopyProgramTexCoordinatesHandle = GLES20.glGetAttribLocation(spectroscopyProgram, "texCoordinates");
        spectroscopyProgramCamMatrixHandle = GLES20.glGetUniformLocation(spectroscopyProgram, "camMatrix");
        spectroscopyProgramTextureHandle = GLES20.glGetUniformLocation(spectroscopyProgram, "texture");
        spectroscopyProgramPassepartoutMinHandle = GLES20.glGetUniformLocation(spectroscopyProgram, "passepartoutMin");
        spectroscopyProgramPassepartoutMaxHandle = GLES20.glGetUniformLocation(spectroscopyProgram, "passepartoutMax");

        verticalReductionProgram = buildProgram(interpolatingHeightFullScreenVertexShader, verticalReductionFragmentShader);
        reductionProgramVerticesHandle = GLES20.glGetAttribLocation(verticalReductionProgram, "vertices");
        reductionProgramTexCoordinatesHandle = GLES20.glGetAttribLocation(verticalReductionProgram, "texCoordinates");
        reductionProgramTextureHandle = GLES20.glGetUniformLocation(verticalReductionProgram, "texture");
        reductionResSourceHandle = GLES20.glGetUniformLocation(verticalReductionProgram, "resSource");
        reductionResTargetHandle = GLES20.glGetUniformLocation(verticalReductionProgram, "resTarget");

        checkGLError("SpectroscopyAnalyzer: prepare");
    }

    int passepartoutWidth = 0;
    @Override
    public void analyze(float[] camMatrix, RectF passepartout) {
        // --- Phase 1: OpenGL Drawing/Downsampling ---
        drawLuminance(camMatrix, passepartout);

        for(int i = 0; i < nDownsampleSteps; i++){
            drawVerticalReduction(i, camMatrix);
        }

        // --- Phase 2: Setup and GL Read ---

        int outW = wDownsampleStep[nDownsampleSteps -1];
        int outH = hDownsampleStep[nDownsampleSteps -1];

        if (resultBuffer == null || resultBufferSize != outW * outH) {
            resultBufferSize = outW * outH;
            resultBuffer = ByteBuffer.allocateDirect(resultBufferSize * 4).order(ByteOrder.nativeOrder());
        }
        resultBuffer.rewind();

        // --- Phase 3: Processing ---

        // Read pixels from the OpenGL framebuffer
        GLES20.glReadPixels(0, 0, outW, outH, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, resultBuffer);
        resultBuffer.rewind();

        double[] columnSums = new double[outW];

        byte[] bytes = new byte[resultBuffer.remaining()];
        resultBuffer.get(bytes);

        // The normalization factor is applied to all sums
        final double normalizationFactor = (double) (outH * Math.pow(4, nDownsampleSteps));

        for (int pixelIndex = 0; pixelIndex < bytes.length / 4 ; pixelIndex++) {
            int byteIndex = pixelIndex * 4;
            int r = bytes[byteIndex] & 0xff;
            int g = bytes[byteIndex+1] & 0xff;
            long luminance  = (r << 8) + g;

            int columnIndex = pixelIndex % outW;

            columnSums[columnIndex] += (double) luminance;
        }

        // Normalize the final aggregated sums by the factor
        for (int i = 0; i < outW; i++) {
            columnSums[i] /= normalizationFactor;
        }

        // Calculate the normalized passepartout boundaries
        final float normalizedYMin = 1.0f - Math.min(passepartout.top, passepartout.bottom);
        final float normalizedYMax = 1.0f - Math.max(passepartout.top, passepartout.bottom);

        // Calculate the region of interest indices
        int roiStartIndex = (int) (normalizedYMax * outW);
        int roiEndIndex = (int) (normalizedYMin * outW);
        passepartoutWidth = roiEndIndex - roiStartIndex;

        latestResult = Arrays.copyOfRange(columnSums, roiStartIndex, roiEndIndex);

    }

    @Override
    public void writeToBuffers(CameraSettingState state) {
        double exposureFactor = linear ?
                Math.pow(2.0, state.getCurrentApertureValue())/2.0 * 100.0/state.getCurrentIsoValue() *
                        (1.0e9/60.0) / state.getCurrentShutterValue() : 1.0;

        out.clear(true);
        int count = 0;

        if (latestResult != null) {
            for (double v : latestResult) {
                pixelPosition.append(++count);
                out.append(v * exposureFactor);
            }
        }

        latestResult = null;
    }

    public void makeCurrent(EGLSurface eglSurface, int w, int h) {
        if (!EGL14.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext)) {
            throw new RuntimeException("Camera preview: eglMakeCurrent failed");
        }
        GLES20.glViewport(0, 0, w, h);
    }

    void drawLuminance(float[] camMatrix, RectF passepartout) {
        makeCurrent(analyzingSurface, w, h);

        GLES20.glClearColor(0.0f, 0.0f, 0.0f, 1.0f);
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);

        // Calculate scissor rect
        int scissorX = (int)Math.floor(w*(1.0-Math.max(passepartout.top, passepartout.bottom)));
        int scissorY = (int)Math.floor(h*(1.0-Math.max(passepartout.left, passepartout.right)));
        int scissorW = (int)Math.ceil(w*Math.abs(passepartout.height()));
        int scissorH = (int)Math.ceil(h*Math.abs(passepartout.width()));

        GLES20.glEnable(GLES20.GL_SCISSOR_TEST);
        GLES20.glScissor(scissorX, scissorY, scissorW, scissorH);

        GLES20.glUseProgram(spectroscopyProgram);

        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, fullScreenVboVertices);
        GLES20.glEnableVertexAttribArray(spectroscopyProgramVerticesHandle);
        GLES20.glVertexAttribPointer(spectroscopyProgramVerticesHandle, 2, GLES20.GL_FLOAT, false, 0, 0);

        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, fullScreenVboTexCoordinates);
        GLES20.glEnableVertexAttribArray(spectroscopyProgramTexCoordinatesHandle);
        GLES20.glVertexAttribPointer(spectroscopyProgramTexCoordinatesHandle, 2, GLES20.GL_FLOAT, false, 0, 0);

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GL_TEXTURE_EXTERNAL_OES, cameraTexture);
        GLES20.glTexParameteri(GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_NEAREST);
        GLES20.glTexParameteri(GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_NEAREST);
        GLES20.glUniform1i(spectroscopyProgramTextureHandle, 0);

        GLES20.glUniform2f(spectroscopyProgramPassepartoutMinHandle, passepartout.left, passepartout.top);
        GLES20.glUniform2f(spectroscopyProgramPassepartoutMaxHandle, passepartout.right, passepartout.bottom);

        GLES20.glUniformMatrix4fv(spectroscopyProgramCamMatrixHandle, 1, false, camMatrix, 0);

        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);

        GLES20.glBindTexture(GL_TEXTURE_EXTERNAL_OES, 0);
        GLES20.glDisableVertexAttribArray(spectroscopyProgramVerticesHandle);
        GLES20.glDisableVertexAttribArray(spectroscopyProgramTexCoordinatesHandle);

        GLES20.glDisable(GLES20.GL_SCISSOR_TEST);

        checkGLError("draw luminance");
    }

    void drawVerticalReduction(int step, float[] camMatrix) {
        makeCurrent(downsampleSurfaces[step], wDownsampleStep[step], hDownsampleStep[step]);

        GLES20.glUseProgram(verticalReductionProgram);

        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, fullScreenVboVertices);
        GLES20.glEnableVertexAttribArray(reductionProgramVerticesHandle);
        GLES20.glVertexAttribPointer(reductionProgramVerticesHandle, 2, GLES20.GL_FLOAT, false, 0, 0);

        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, fullScreenVboTexCoordinates);
        GLES20.glEnableVertexAttribArray(reductionProgramTexCoordinatesHandle);
        GLES20.glVertexAttribPointer(reductionProgramTexCoordinatesHandle, 2, GLES20.GL_FLOAT, false, 0, 0);

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, downsamplingTextures[step]);
        EGL14.eglBindTexImage(eglDisplay, (step == 0) ? analyzingSurface : downsampleSurfaces[step-1], EGL14.EGL_BACK_BUFFER);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_NEAREST);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_NEAREST);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glUniform1i(reductionProgramTextureHandle, 0);

        GLES20.glUniform2f(reductionResSourceHandle, step == 0 ? w : wDownsampleStep[step-1], step == 0 ? h : hDownsampleStep[step-1]);
        GLES20.glUniform2f(reductionResTargetHandle, wDownsampleStep[step], hDownsampleStep[step]);

        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);

        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);
        EGL14.eglReleaseTexImage(eglDisplay, (step == 0) ? analyzingSurface : downsampleSurfaces[step-1], EGL14.EGL_BACK_BUFFER);
        GLES20.glDisableVertexAttribArray(reductionProgramVerticesHandle);
        GLES20.glDisableVertexAttribArray(reductionProgramTexCoordinatesHandle);

        checkGLError("vertical reduction");
    }
}
