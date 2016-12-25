package com.example.fcc.udptest;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.ToggleButton;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.concurrent.TransferQueue;

import classes.Logger;

public class ReceiveCallActivity extends Activity implements OnClickListener {

    private static final String LOG_TAG = "ReceiveCallActivity";
    private static final int BUF_SIZE = 1024;
    private String contactIp;
    private String contactName;
    private boolean LISTEN = true;
    private AudioCall call;
    AudioManager audioManager;

    private LinearLayout mLinearLayout;
    private Button acceptButton;
    private Button rejectButton;
    private Button endButton;
    private ToggleButton Tgl_Speaker;
    private TextView txtIncomingCall;

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_receive_call);
        audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);

        Intent intent = getIntent();
        contactName = intent.getStringExtra(G.EXTRA_C_Name);
        contactIp = intent.getStringExtra(G.EXTRA_C_Ip);

        initView();
        startListener();

    }

    private void initView() {
        Tgl_Speaker = (ToggleButton) findViewById(R.id.tgl_speaker);
        Tgl_Speaker.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {

                if (isChecked) {

                    Logger.d("ReceiveCallActivity", "onCreate", "Toggle isChecked)");
                    audioManager.setMode(AudioManager.MODE_IN_CALL);
                    audioManager.setSpeakerphoneOn(false);

                } else {
                    Logger.d(LOG_TAG, "onCreate", "Toggle is not Checked)");
                    audioManager.setMode(AudioManager.MODE_NORMAL);
                    audioManager.setSpeakerphoneOn(true);

                }
            }
        });

        mLinearLayout = (LinearLayout) findViewById(R.id.layout_call);

        txtIncomingCall = (TextView) findViewById(R.id.textViewIncomingCall);
        txtIncomingCall.setText("Incoming call: " + contactName);

        endButton = (Button) findViewById(R.id.buttonEndCall1);
        endButton.setVisibility(View.INVISIBLE);

        // ACCEPT BUTTON
        acceptButton = (Button) findViewById(R.id.buttonAccept);
        acceptButton.setOnClickListener(this);

        // REJECT BUTTON
        rejectButton = (Button) findViewById(R.id.buttonReject);
        rejectButton.setOnClickListener(this);

        // END BUTTON
        endButton.setOnClickListener(this);
    }

    private void endCall() {
        // End the call and send a notification
        stopListener();
        if (G.IN_CALL) {

            call.endCall();
            G.IN_CALL = false;
        }
        sendMessage("END:");
        finish();
    }

    private void startListener() {
        // Creates the listener thread
        LISTEN = true;
        Thread listenThread = new Thread(new Runnable() {

            @Override
            public void run() {

                try {

                    Logger.e(LOG_TAG, "startListener", "Listener started!");
                    DatagramSocket socket = new DatagramSocket(G.BROADCAST_PORT);
                    socket.setSoTimeout(15000);
                    byte[] buffer = new byte[BUF_SIZE];
                    DatagramPacket packet = new DatagramPacket(buffer, BUF_SIZE);

                    while (LISTEN) {
                        try {

                            Logger.e(LOG_TAG, "startListener", "Listening for packets");

                            socket.receive(packet);
                            String data = new String(buffer, 0, packet.getLength());
                            Logger.e(LOG_TAG, "startListener", "Packet received from " + packet.getAddress() + " with contents: " + data);

                            String action = data.substring(0, 4);
                            if (action.equals("END:")) {
                                // End call notification received. End call
                                endCall();
                            } else {
                                // Invalid notification received.
                                Logger.e(LOG_TAG, "startListener", "Packet received from " + packet.getAddress() + " with contents: " + data);

                            }
                        } catch (IOException e) {
                            Logger.e(LOG_TAG, "startListener", "IOException in Listener " + e);
                        }
                    }
                    Logger.e(LOG_TAG, "startListener", "Listener ending");

//                    socket.disconnect();
//                    socket.close();
                    return;
                } catch (SocketException e) {
                    Logger.e(LOG_TAG, "startListener", "SocketException in Listener " + e);
                    endCall();
                }
            }
        });
        listenThread.start();
    }

    private void stopListener() {
        // Ends the listener thread
        LISTEN = false;
    }

    private void sendMessage(final String message) {
        // Creates a thread for sending notifications
        Thread replyThread = new Thread(new Runnable() {

            @Override
            public void run() {

                try {

                    InetAddress address = InetAddress.getByName(contactIp);
                    byte[] data = message.getBytes();
                    DatagramSocket socket = new DatagramSocket();
                    DatagramPacket packet = new DatagramPacket(data, data.length, address, G.BROADCAST_PORT);
                    socket.send(packet);
                    Logger.e(LOG_TAG, "sendMessage", "Sent message( " + message + " ) to " + contactIp);

                    socket.disconnect();
                    socket.close();
                } catch (UnknownHostException e) {
                    Logger.e(LOG_TAG, "sendMessage", "Failure. UnknownHostException in sendMessage: " + contactIp);

                } catch (SocketException e) {
                    Logger.e(LOG_TAG, "sendMessage", "Failure. SocketException in sendMessage: " + e);

                } catch (IOException e) {

                    Log.e(LOG_TAG, "Failure. IOException in sendMessage: " + e);
                }
            }
        });
        replyThread.start();
    }

    @Override
    public void onClick(View v) {


        switch (v.getId()) {

            case R.id.buttonAccept:
                try {
                    mLinearLayout.setVisibility(View.INVISIBLE);
                    // Accepting call. Send a notification and start the call
                    sendMessage("ACC:");
                    InetAddress address = InetAddress.getByName(contactIp);
                    Logger.d(LOG_TAG, "onCreate", "Calling " + address.toString());

                    G.IN_CALL = true;

                    call = new AudioCall(address);
                    call.startCall();
                    // Hide the buttons as they're not longer required
                    Button accept = (Button) findViewById(R.id.buttonAccept);
                    accept.setEnabled(false);

                    Button reject = (Button) findViewById(R.id.buttonReject);
                    reject.setEnabled(false);

                    endButton.setVisibility(View.VISIBLE);
                } catch (UnknownHostException e) {

                    Logger.e(LOG_TAG, "onCreate", "UnknownHostException in acceptButton: " + e);

                } catch (Exception e) {
                    Logger.e(LOG_TAG, "onCreate", "Exception in acceptButton: " + e);
                }
                break;


            case R.id.buttonReject:
                sendMessage("REJ:");
                endCall();
                break;

            case R.id.buttonEndCall1:
                endCall();
                break;

        }

    }
}
