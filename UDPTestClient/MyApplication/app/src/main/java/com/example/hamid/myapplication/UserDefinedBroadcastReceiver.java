package com.example.hamid.myapplication;

/**
 * Created by HaMiD on 12/13/2016.
 */

import java.text.Format;
import java.text.SimpleDateFormat;
import java.util.Date;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.widget.Toast;

public class UserDefinedBroadcastReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {

        //You can do the processing here update the widget/remote views.
        StringBuilder msgStr = new StringBuilder("Current time : ");
        Format formatter = new SimpleDateFormat("hh:mm:ss a");
        msgStr.append(formatter.format(new Date()));
        Toast.makeText(context, msgStr, Toast.LENGTH_SHORT).show();
    }
}
