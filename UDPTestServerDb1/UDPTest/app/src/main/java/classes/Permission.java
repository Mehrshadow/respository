package classes;

import android.Manifest;
import android.annotation.TargetApi;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.os.Build;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;

/**
 * Created by FCC on 1/4/2017.
 */

public class Permission {

    public static final int MY_PERMISSIONS_REQUEST_VIDEOCALL = 1;
    public static final int MY_PERMISSIONS_REQUEST_RECORD_AUDIO = 2;


    public boolean checkPermission(Activity activity, String permissionType) {

        boolean hasPermission = false;

        switch (permissionType) {
            case PermissionType.VIDEOCALL:
                hasPermission = checkVideoCallPermission(activity);
                break;

            case PermissionType.RECORD_AUDIO:
                hasPermission = checkRecordAudioPermission(activity);
                break;
        }

        return hasPermission;
    }

    @TargetApi(Build.VERSION_CODES.M)
    private boolean checkVideoCallPermission(Activity activity) {

        boolean hasPermission = true;

        if (ContextCompat.checkSelfPermission(activity, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED
                ||
                ContextCompat.checkSelfPermission(activity, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {

            hasPermission = false;

            // Should we show an explanation?
            if (ActivityCompat.shouldShowRequestPermissionRationale(activity,
                    Manifest.permission.CAMERA)
                    &&
                    ActivityCompat.shouldShowRequestPermissionRationale(activity,
                            Manifest.permission.RECORD_AUDIO)) {

                // Show an explanation to the user *asynchronously* -- don't block
                // this thread waiting for the user's response! After the user
                // sees the explanation, try again to request the permission.

            } else {

                // No explanation needed, we can request the permission.

                ActivityCompat.requestPermissions(activity,
                        new String[]{Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO},
                        MY_PERMISSIONS_REQUEST_VIDEOCALL);

                // MY_PERMISSIONS_REQUEST_READ_CONTACTS is an
                // app-defined int constant. The callback method gets the
                // result of the request.

            }
        }

        return hasPermission;
    }

    @TargetApi(Build.VERSION_CODES.M)
    private boolean checkRecordAudioPermission(Activity activity) {

        boolean hasPermission = true;

        if (ContextCompat.checkSelfPermission(activity,
                Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {

            hasPermission = false;

            if (ActivityCompat.shouldShowRequestPermissionRationale(activity,
                    Manifest.permission.RECORD_AUDIO)) {

            } else {

                ActivityCompat.requestPermissions(activity,
                        new String[]{Manifest.permission.RECORD_AUDIO},
                        MY_PERMISSIONS_REQUEST_RECORD_AUDIO);
            }
        }
        return hasPermission;
    }
}
