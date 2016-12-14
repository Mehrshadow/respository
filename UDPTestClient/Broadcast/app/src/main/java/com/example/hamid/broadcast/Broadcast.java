package com.example.hamid.broadcast;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.widget.Toast;

/**
 * Created by HaMiD on 12/13/2016.
 */

public class Broadcast extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        Intent intent1 = new Intent(context,MainActivity.class);
        Toast.makeText(context,"dddddddddd",Toast.LENGTH_LONG).show();
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_DOCUMENT);
        context.startActivity(intent1);
    }
}
