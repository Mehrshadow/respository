package com.example.hamid.myapplication;

/**
 * Created by HaMiD on 12/13/2016.
 */

import android.app.Activity;
import android.content.IntentFilter;
import android.os.Bundle;
import android.view.Menu;
import android.view.View;
import android.widget.Toast;

public class EnableDisable extends Activity {

    UserDefinedBroadcastReceiver broadCastReceiver = new UserDefinedBroadcastReceiver();

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register_unregister);
    }



    /**
     * This method enables the Broadcast receiver for
     * "android.intent.action.TIME_TICK" intent. This intent get
     * broadcasted every minute.
     *
     * @param view
     */
    public void registerBroadcastReceiver(View view) {

        this.registerReceiver(broadCastReceiver, new IntentFilter(
                "android.intent.action.TIME_TICK"));
        Toast.makeText(this, "Registered broadcast receiver", Toast.LENGTH_SHORT)
                .show();
    }

    /**
     * This method disables the Broadcast receiver
     *
     * @param view
     */
    public void unregisterBroadcastReceiver(View view) {

        this.unregisterReceiver(broadCastReceiver);

        Toast.makeText(this, "unregistered broadcst receiver", Toast.LENGTH_SHORT)
                .show();
    }
}