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
<<<<<<< HEAD
    public static List<Contacts> contactsList = new ArrayList<>();
=======

    public static long cameraDataSize = 0;
>>>>>>> udpBranch
}
