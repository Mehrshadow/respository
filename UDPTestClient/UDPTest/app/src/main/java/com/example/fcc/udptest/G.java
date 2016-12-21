package com.example.fcc.udptest;

import android.app.Application;

/**
 * Created by FCC on 12/4/2016.
 */

public class G extends Application {
    public static boolean IN_CALL = false;

    public static final int BROADCAST_PORT = 30000;

    public static final int CONTACTSYNC_PORT = 40000;

    public static final int CALL_SENDER_PORT = 50000;
    public static final int CALL_LISTENER_PORT = 50001;

    public static final int SENDVIDEO_PORT = 60000;
    public static final int RECEIVEVIDEO_PORT = 60001;

    public static final int CHECK_ONLINE_MOBILES_PORT = 70000;

    public final static String EXTRA_C_Name = "CNAME";
    public final static String EXTRA_C_Ip = "CIP";
    public final static String EXTRA_DISPLAYNAME = "DISPNAME";

    public static String ServerIp = "";
    public static String UserName = "";
    public static boolean isIPChanged = false;
    public static String BROADCAST_WIFI_STATUS = "android.net.conn.CONNECTIVITY_CHANGE";

}
