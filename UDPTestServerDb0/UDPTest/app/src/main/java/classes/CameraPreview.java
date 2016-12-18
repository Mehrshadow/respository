package classes;


import android.content.Context;
import android.hardware.Camera;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import java.io.IOException;


public class CameraPreview extends SurfaceView implements SurfaceHolder.Callback {

    private SurfaceHolder mHolder;
    private Camera mCamera;
    private Camera.PreviewCallback previewCallback;
//    private Camera.AutoFocusCallback autoFocusCallback;
    private String LOG_TAG = "Camera Preview";

    public CameraPreview(Context context, Camera camera,
                         Camera.PreviewCallback previewCb) {
        super(context);
        mCamera = camera;
        previewCallback = previewCb;

        // Install a SurfaceHolder.Callback so we get notified when the
        // underlying surface is created and destroyed.
        mHolder = getHolder();
        mHolder.addCallback(this);

        // deprecated setting, but required on Android versions prior to 3.0
        mHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {

        try {
            mCamera.setPreviewDisplay(holder);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
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
//            mediaRecorder.setPreviewDisplay(mHolder.getSurface());
            mCamera.setPreviewCallback(previewCallback);
            mCamera.startPreview();
//            mCamera.autoFocus(autoFocusCallback);


//            Camera.Parameters params = mCamera.getParameters();
//            params.setPreviewSize(100, 100);
//            mCamera.setParameters(params);
//            Log.d("Camera preview", "width: " + width + ", height: " + height);


        } catch (Exception e) {
            Log.d("DBG", "Error starting camera preview: " + e.getMessage());
        }
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {

        Log.d(LOG_TAG, "surface destroyed");
        mCamera.release();
    }

    public SurfaceHolder getmHolder() {
        return mHolder;
    }
}
