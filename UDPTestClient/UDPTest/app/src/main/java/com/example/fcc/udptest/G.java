package com.example.fcc.udptest;

import android.app.Application;
import android.os.Environment;

/**
 * Created by FCC on 12/4/2016.
 */

public class G extends Application {
    public static final String sendVideoPath = Environment.getExternalStorageDirectory() + "/Send_videoTest.flv";
    public static final String ReceiveVideoPath = Environment.getExternalStorageDirectory() + "/Receive_videoTest.flv";
    public static boolean IN_CALL = false;
    public static final int BROADCAST_PORT = 50002;
    public static final int CONTACTSYNC_PORT = 50001;
    public static String ServerIp = "";
    public static String UserName = "";
    public static boolean isIPChanged = false;
}
