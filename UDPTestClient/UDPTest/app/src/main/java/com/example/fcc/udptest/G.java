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
    public static final int VIDEOCALL_LISTENER_PORT = 50004;
    public static final int CheckStatus_PORT = 50006;
    public static final int CALL_LISTENER_PORT = 50003;

    public final static String EXTRA_C_Name = "CNAME";
    public final static String EXTRA_C_Ip = "CIP";
    public final static String EXTRA_DISPLAYNAME = "DISPNAME";

    public static final int CONTACTSYNC_PORT = 50001; // Socket on which packets are sent/received
    public static final int RECEIVEVIDEO_PORT = 60001; // Socket on which frame dimension and sizes are sent
    public static final int SENDVIDEO_PORT = 60000;
    public static String ServerIp = "";
    public static String UserName = "";
    public static boolean isIPChanged = false;
    public static String BROADCAST_WIFI_STATUS = "android.net.conn.CONNECTIVITY_CHANGE";


}
