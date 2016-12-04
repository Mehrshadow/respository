package com.example.fcc.udptest;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Color;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.TextView;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.HashMap;

public class MainActivity extends Activity implements ContactManager.IUpdateContact, OnClickListener {

    static final String LOG_TAG = "UDPchat";
    private static final int CALL_LISTENER_PORT = 50003;
    private static final int VIDEOCALL_LISTENER_PORT = 50004;
    private static final int BUF_SIZE = 1024;
    private ContactManager contactManager;
    private String displayName;
    private boolean STARTED = false;
    private boolean IN_CALL = false;
    private boolean IN_VIDEO_CALL = false;
    private boolean LISTEN = false;
    private boolean LISTEN_Video = true;

    public final static String EXTRA_CONTACT = "hw.dt83.udpchat.CONTACT";
    public final static String EXTRA_IP = "hw.dt83.udpchat.IP";
    public final static String EXTRA_DISPLAYNAME = "hw.dt83.udpchat.DISPLAYNAME";
    private String contact;
    private InetAddress ip;

    private Button startButton, callButton, videoCallButton;
    private ScrollView scrollView;
    private RadioGroup radioGroup;

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initViews();
    }

    private void initViews() {
        startButton = (Button) findViewById(R.id.buttonStart);
        callButton = (Button) findViewById(R.id.buttonCall);
        videoCallButton = (Button) findViewById(R.id.btnVideoCall);
        scrollView = (ScrollView) findViewById(R.id.scrollView);

        callButton.setOnClickListener(this);
        videoCallButton.setOnClickListener(this);
        startButton.setOnClickListener(this);
    }

    private void updateContactList() {
        // Create a copy of the HashMap used by the ContactManager
        HashMap<String, InetAddress> contacts = contactManager.getContacts();
        // Create a radio button for each contact in the HashMap
        RadioGroup radioGroup = (RadioGroup) findViewById(R.id.contactList);
        radioGroup.removeAllViews();

        for (String name : contacts.keySet()) {

            RadioButton radioButton = new RadioButton(getBaseContext());
            radioButton.setText(name);
            radioButton.setTextColor(Color.BLACK);
            radioGroup.addView(radioButton);
        }

        radioGroup.clearCheck();
    }

    private InetAddress getBroadcastIp() {
        // Function to return the broadcast address, based on the IP address of the device
        try {

            WifiManager wifiManager = (WifiManager) getSystemService(WIFI_SERVICE);
            WifiInfo wifiInfo = wifiManager.getConnectionInfo();
            int ipAddress = wifiInfo.getIpAddress();
            String addressString = toBroadcastIp(ipAddress);
            InetAddress broadcastAddress = InetAddress.getByName(addressString);
            return broadcastAddress;
        } catch (UnknownHostException e) {

            Log.e(LOG_TAG, "UnknownHostException in getBroadcastIP: " + e);
            return null;
        }

    }

    private String toBroadcastIp(int ip) {
        // Returns converts an IP address in int format to a formatted string
        return (ip & 0xFF) + "." +
                ((ip >> 8) & 0xFF) + "." +
                ((ip >> 16) & 0xFF) + "." +
                "255";
    }

    private void startCallListener() {
        // Creates the listener thread
        LISTEN = true;
        Thread listener = new Thread(new Runnable() {

            @Override
            public void run() {

                try {
                    // Set up the socket and packet to receive
                    Log.i(LOG_TAG, "Incoming call listener started");
                    DatagramSocket socket = new DatagramSocket(CALL_LISTENER_PORT);
                    socket.setSoTimeout(1000);
                    byte[] buffer = new byte[BUF_SIZE];
                    DatagramPacket packet = new DatagramPacket(buffer, BUF_SIZE);
                    while (LISTEN) {
                        // Listen for incoming call requests
                        try {
                            Log.i(LOG_TAG, "Listening for incoming calls");
                            socket.receive(packet);
                            String data = new String(buffer, 0, packet.getLength());
                            Log.i(LOG_TAG, "Packet received from " + packet.getAddress() + " with contents: " + data);
                            String action = data.substring(0, 4);
                            if (action.equals("CAL:")) {
                                // Received a call request. Start the ReceiveCallActivity
                                String address = packet.getAddress().toString();
                                String name = data.substring(4, packet.getLength());

                                Intent intent = new Intent(MainActivity.this, ReceiveCallActivity.class);
                                intent.putExtra(EXTRA_CONTACT, name);
                                intent.putExtra(EXTRA_IP, address.substring(1, address.length()));
                                IN_CALL = true;
                                //LISTEN = false;
                                //stopCallListener();
                                startActivity(intent);
                            } else {
                                // Received an invalid request
                                Log.w(LOG_TAG, packet.getAddress() + " sent invalid message: " + data);
                            }
                        } catch (Exception e) {
                        }
                    }
                    Log.i(LOG_TAG, "Call Listener ending");
                    socket.disconnect();
                    socket.close();
                } catch (SocketException e) {

                    Log.e(LOG_TAG, "SocketException in listener " + e);
                }
            }
        });
        listener.start();
    }

    private void stopCallListener() {
        // Ends the listener thread
        LISTEN = false;
    }

    private void startVideoCallListener() {
        // Creates the listener thread
        LISTEN_Video = true;
        Thread listener = new Thread(new Runnable() {

            @Override
            public void run() {

                try {
                    // Set up the socket and packet to receive
                    Log.i(LOG_TAG, "Incoming video call listener started");
                    DatagramSocket socket = new DatagramSocket(VIDEOCALL_LISTENER_PORT);
                    socket.setSoTimeout(1000);
                    byte[] buffer = new byte[BUF_SIZE];
                    DatagramPacket packet = new DatagramPacket(buffer, BUF_SIZE);
                    while (LISTEN_Video) {
                        // Listen for incoming call requests
                        try {
                            Log.i(LOG_TAG, "Listening for incoming video calls");
                            socket.receive(packet);
                            String data = new String(buffer, 0, packet.getLength());
                            Log.i(LOG_TAG, "Packet received from " + packet.getAddress() + " with contents: " + data);
                            String action = data.substring(0, 4);
                            if (action.equals("CAL:")) {
                                // Received a call request. Start the ReceiveCallActivity
                                String address = packet.getAddress().toString();
                                String name = data.substring(4, packet.getLength());

                                Intent intent = new Intent(MainActivity.this, ReceiveVideoCallActivity.class);
                                intent.putExtra(EXTRA_CONTACT, name);
                                intent.putExtra(EXTRA_IP, address.substring(1, address.length()));
                                IN_VIDEO_CALL = true;

                                startActivity(intent);
                            } else {
                                // Received an invalid request
                                Log.w(LOG_TAG, packet.getAddress() + " sent invalid message: " + data);
                            }
                        } catch (Exception e) {
                        }
                    }
                    Log.i(LOG_TAG, "Video Call Listener ending");
                    socket.disconnect();
                    socket.close();
                } catch (SocketException e) {

                    Log.e(LOG_TAG, "SocketException in listener " + e);
                }
            }
        });
        listener.start();
    }

    private void stopVideoCallListener() {
        // Ends the listener thread
        LISTEN_Video = false;
    }

    @Override
    public void onPause() {

        super.onPause();
        if (STARTED) {

            contactManager.bye(displayName);
            contactManager.stopBroadcasting();
            contactManager.stopListening();
            //STARTED = false;
        }
        stopCallListener();
        stopVideoCallListener();
        Log.i(LOG_TAG, "App paused!");
    }

    @Override
    public void onStop() {

        super.onStop();
        Log.i(LOG_TAG, "App stopped!");
        stopCallListener();
        stopVideoCallListener();
        contactManager.stopBroadcasting();
        contactManager.stopListening();

        if (!IN_CALL && !IN_VIDEO_CALL) {

            finish();
        }
    }

    @Override
    public void onRestart() {

        super.onRestart();
        Log.i(LOG_TAG, "App restarted!");
        IN_CALL = false;
        IN_VIDEO_CALL = false;
        STARTED = true;
        contactManager = new ContactManager(displayName, getBroadcastIp());
        contactManager.setUpdateListener(MainActivity.this);
        startCallListener();
        startVideoCallListener();
    }

    @Override
    public void onReceive() {
        Log.d(LOG_TAG, "OnReceive1");
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                updateContactList();
            }
        });
        Log.d(LOG_TAG, "OnReceive2");
    }

    private boolean isContactSelected() {
        radioGroup = (RadioGroup) findViewById(R.id.contactList);
        int selectedButton = radioGroup.getCheckedRadioButtonId();
        if (selectedButton == -1) {
            // If no device was selected, present an error message to the user
            Log.w(LOG_TAG, "Warning: no contact selected");
            final AlertDialog alert = new AlertDialog.Builder(MainActivity.this).create();
            alert.setTitle("Oops");
            alert.setMessage("You must select a contact first");
            alert.setButton(-1, "OK", new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int which) {

                    alert.dismiss();
                }
            });
            alert.show();

            return false;

        } else {
            // Collect details about the selected contact
            RadioButton radioButton = (RadioButton) findViewById(selectedButton);
            contact = radioButton.getText().toString();
            ip = contactManager.getContacts().get(contact);

            return true;
        }
    }

    @Override
    public void onClick(View v) {

        switch (v.getId()) {

            case R.id.buttonStart:

                Log.i(LOG_TAG, "Start button pressed");
                STARTED = true;

                EditText displayNameText = (EditText) findViewById(R.id.editTextDisplayName);
                displayName = displayNameText.getText().toString();

                displayNameText.setEnabled(false);
                startButton.setEnabled(false);

                TextView text = (TextView) findViewById(R.id.textViewSelectContact);
                text.setVisibility(View.VISIBLE);

                callButton.setVisibility(View.VISIBLE);
                videoCallButton.setVisibility(View.VISIBLE);

                scrollView.setVisibility(View.VISIBLE);

                contactManager = new ContactManager(displayName, getBroadcastIp());
                contactManager.setUpdateListener(MainActivity.this);

                startCallListener();
                startVideoCallListener();
                break;

            case R.id.buttonCall:

                IN_CALL = true;

                if (!isContactSelected())
                    return;

                // Send this information to the MakeCallActivity and start that activity
                Intent intent = new Intent(MainActivity.this, MakeCallActivity.class);
                intent.putExtra(EXTRA_CONTACT, contact);
                String address = ip.toString();
                address = address.substring(1, address.length());
                intent.putExtra(EXTRA_IP, address);
                intent.putExtra(EXTRA_DISPLAYNAME, displayName);
                startActivity(intent);

                Log.d("btn call", "remove all views");
                radioGroup.removeAllViews();

                break;


            case R.id.btnVideoCall:

                if (!isContactSelected())
                    return;

                IN_VIDEO_CALL = true;

                Intent i = new Intent(MainActivity.this, MakeVideoCallActivity.class);
                i.putExtra(EXTRA_CONTACT, contact);
                String adr = ip.toString();
                adr = adr.substring(1, adr.length());
                i.putExtra(EXTRA_IP, adr);
                i.putExtra(EXTRA_DISPLAYNAME, displayName);
                startActivity(i);

                Log.d("btn video call", "remove all views");
                radioGroup.removeAllViews();

                break;
        }
    }
}
