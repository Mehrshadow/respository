/*
 * Barebones implementation of displaying camera preview.
 *
 * Created by lisah0 on 2012-02-24
 */
package classes;

import android.content.Context;
import android.hardware.Camera;
import android.hardware.Camera.PreviewCallback;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import java.io.IOException;
import java.util.List;

/**
 * A basic Camera preview class
 */
public class CameraPreview extends SurfaceView implements SurfaceHolder.Callback {
    private SurfaceHolder mHolder;
    private Camera mCamera;
    private PreviewCallback previewCallback;
    private Camera.Parameters parameters;

    public CameraPreview(Context context, Camera camera,
                         PreviewCallback previewCb) {
        super(context);

        Logger.d("CameraPreview", "SurfaceChanged", "started...");

        mCamera = camera;
        previewCallback = previewCb;
        parameters = camera.getParameters();
        List<Camera.Size> sizes = parameters.getSupportedPictureSizes();
//        parameters.setPreviewFrameRate(1);
        parameters.setPreviewSize(sizes.get(sizes.size() - 1).width, sizes.get(sizes.size() - 1).height);
        parameters.setPictureSize(sizes.get(sizes.size() - 1).width, sizes.get(sizes.size() - 1).height);
        camera.setParameters(parameters);

        mHolder = getHolder();
        mHolder.addCallback(this);

        // deprecated setting, but required on Android versions prior to 3.0
        mHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
    }

    public void surfaceCreated(SurfaceHolder holder) {
        // The Surface has been created, now tell the camera where to draw the preview.
        try {
            mCamera.setPreviewDisplay(holder);
        } catch (IOException e) {
            Log.d("DBG", "Error setting camera preview: " + e.getMessage());
        }
    }

    public void surfaceDestroyed(SurfaceHolder holder) {
        // Camera preview released in activity
    }

    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {

        Logger.d("CameraPreview", "SurfaceChanged", "surface changed, width: " + width + ", height: " + height);

        /*
         * If your preview can change or rotate, take care of those events here.
         * Make sure to stop the preview before resizing or reformatting it.
         */
        if (mHolder.getSurface() == null) {
            // preview surface does not exist
            return;
        }

        // stop preview before making changes
        try {
            mCamera.stopPreview();
        } catch (Exception e) {
            // ignore: tried to stop a non-existent preview
        }

        try {
            // Hard code camera surface rotation 90 degs to match Activity view in portrait
            mCamera.setDisplayOrientation(90);

            mCamera.setPreviewDisplay(mHolder);
            mCamera.setPreviewCallback(previewCallback);
            mCamera.startPreview();
//            mCamera.autoFocus(autoFocusCallback);
        } catch (Exception e) {
            Log.d("DBG", "Error starting camera preview: " + e.getMessage());
        }
    }
}
