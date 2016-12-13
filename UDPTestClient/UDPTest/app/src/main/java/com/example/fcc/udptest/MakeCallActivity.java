package com.example.fcc.udptest;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;

public class MakeCallActivity extends Activity implements CompoundButton.OnCheckedChangeListener {

    private static final String LOG_TAG = "MakeCall";
    private static final int BROADCAST_PORT = 50002;
    private static final int BUF_SIZE = 1024;
    private String displayName;
    private String contactName;
    private String contactIp;
    private boolean LISTEN = true;
    private AudioCall call;


    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_make_call);

        Log.i(LOG_TAG, "MakeCallActivity started!");

        Intent intent = getIntent();
        displayName = intent.getStringExtra(MainActivity.EXTRA_DISPLAYNAME);
        contactName = intent.getStringExtra(MainActivity.EXTRA_CONTACT);
        contactIp = intent.getStringExtra(MainActivity.EXTRA_IP);

        TextView textView = (TextView) findViewById(R.id.textViewCalling);
        ToggleButton btnSwtich = (ToggleButton) findViewById(R.id.toggleButton2);
        btnSwtich.setOnCheckedChangeListener(this);
        textView.setText("Calling: " + contactName);

        startListener();
        makeCall();

        Button endButton = (Button) findViewById(R.id.buttonEndCall);
        endButton.setOnClickListener(new OnClickListener() {

            @Override
            public void onClick(View v) {
                // Button to end the call has been pressed
                endCall();
            }
        });
    }

    private void makeCall() {
        // Send a request to start a call
        sendMessage("CAL:" + displayName, 50003);
    }

    private void endCall() {
        // Ends the chat sessions
        stopListener();
        if (G.IN_CALL) {

            call.endCall();
            G.IN_CALL = false;
        }
        sendMessage("END:", BROADCAST_PORT);
        finish();
    }

    private void startListener() {
        // Create listener thread
        LISTEN = true;
        Thread listenThread = new Thread(new Runnable() {

            @Override
            public void run() {

                try {

                    Log.i(LOG_TAG, "Listener started!");
                    DatagramSocket socket = new DatagramSocket(BROADCAST_PORT);
                    socket.setSoTimeout(10000);
                    byte[] buffer = new byte[BUF_SIZE];
                    DatagramPacket packet = new DatagramPacket(buffer, BUF_SIZE);
                    while (LISTEN) {

                        try {

                            Log.i(LOG_TAG, "Listening for packets");
                            socket.receive(packet);
                            String data = new String(buffer, 0, packet.getLength());
                            Log.i(LOG_TAG, "Packet received from " + packet.getAddress() + " with contents: " + data);
                            String action = data.substring(0, 4);
                            if (action.equals("ACC:")) {

                                runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        Toast.makeText(MakeCallActivity.this, R.string.call_accepted, Toast.LENGTH_LONG).show();
                                    }
                                });
                                // Accept notification received. Start call
                                call = new AudioCall(packet.getAddress());
                                call.startCall();

                                G.IN_CALL = true;
                            } else if (action.equals("REJ:")) {

                                runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        Toast.makeText(MakeCallActivity.this, R.string.call_rejected, Toast.LENGTH_LONG).show();
                                    }
                                });

                                // Reject notification received. End call
                                endCall();
                            } else if (action.equals("END:")) {
                                // End call notification received. End call

                                runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        Toast.makeText(MakeCallActivity.this, R.string.call_ended, Toast.LENGTH_LONG).show();
                                    }
                                });
                                endCall();
                            } else {
                                // Invalid notification received
                                Log.w(LOG_TAG, packet.getAddress() + " sent invalid message: " + data);
                            }
                        } catch (SocketTimeoutException e) {
                            if (!G.IN_CALL) {

                                Log.i(LOG_TAG, "No reply from contact. Ending call");

                                runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        Toast.makeText(MakeCallActivity.this, R.string.server_not_reachable, Toast.LENGTH_LONG).show();
                                    }
                                });
                                endCall();
                            }
                        } catch (IOException e) {

                        }
                    }
                    Log.i(LOG_TAG, "Listener ending");
                    socket.disconnect();
                    socket.close();
                    return;
                } catch (SocketException e) {

                    Log.e(LOG_TAG, "SocketException in Listener");
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

    private void sendMessage(final String message, final int port) {
        // Creates a thread used for sending notifications
        Thread replyThread = new Thread(new Runnable() {

            @Override
            public void run() {

                try {

                    InetAddress address = InetAddress.getByName(contactIp);
                    byte[] data = message.getBytes();
                    DatagramSocket socket = new DatagramSocket();
                    DatagramPacket packet = new DatagramPacket(data, data.length, address, port);
                    socket.send(packet);
                    Log.i(LOG_TAG, "Sent message( " + message + " ) to " + contactIp);
                    socket.disconnect();
                    socket.close();
                } catch (UnknownHostException e) {

                    Log.e(LOG_TAG, "Failure. UnknownHostException in sendMessage: " + contactIp);
                } catch (SocketException e) {

                    Log.e(LOG_TAG, "Failure. SocketException in sendMessage: " + e);
                } catch (IOException e) {

                    Log.e(LOG_TAG, "Failure. IOException in sendMessage: " + e);
                }
            }
        });
        replyThread.start();
    }

    @Override
    public void onBackPressed() {
        if (G.IN_CALL) {
            return;
        }

        sendMessage("END:", BROADCAST_PORT);

        super.onBackPressed();
    }

    @Override
    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
        Log.d(LOG_TAG, "IN CALL: " + G.IN_CALL);
        if (G.IN_CALL) {

            AudioManager m_amAudioManager;
            m_amAudioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);

            if (isChecked) {

                m_amAudioManager.setMode(AudioManager.MODE_IN_CALL);
                m_amAudioManager.setSpeakerphoneOn(false);

            } else {
                m_amAudioManager.setMode(AudioManager.MODE_NORMAL);
                m_amAudioManager.setSpeakerphoneOn(true);

            }

            Log.d(LOG_TAG, "Speaker changed" + " & switchStatus is: " + isChecked);
        }
    }
}
