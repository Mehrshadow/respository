package com.example.fcc.udptest;

import android.app.Application;
import android.os.Environment;

import java.util.ArrayList;
import java.util.List;

import classes.Contacts;

/**
 * Created by FCC on 12/4/2016.
 */

public class G extends Application {
    public static final String sendVideoPath = Environment.getExternalStorageDirectory() + "/Send_videoTest.flv";
    public static final String ReceiveVideoPath = Environment.getExternalStorageDirectory() + "/Receive_videoTest.flv";
    public static List<Contacts> contactsList = new ArrayList<>();
    public static final int BROADCAST_PORT = 50002;
    public static final int CONTACTSYNC_PORT = 50001; // Socket on which packets are sent/received
    public static boolean IN_CALL = false;

    public final static String EXTRA_C_Name = "CNAME";
    public final static String EXTRA_C_Ip = "CIP";
    public final static String EXTRA_DISPLAYNAME = "DISPNAME";
}
