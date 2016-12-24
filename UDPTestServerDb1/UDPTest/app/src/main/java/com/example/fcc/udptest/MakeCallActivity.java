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
import android.widget.TextView;
import android.widget.ToggleButton;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;

public class MakeCallActivity extends Activity implements CompoundButton.OnCheckedChangeListener, OnClickListener {

    private static final String LOG_TAG = "MakeCall";
    private static final int BUF_SIZE = 1024;
    private String displayName;
    private String contactName;
    private String contactIp;
    private boolean LISTEN = true;
    private AudioCall call;
    private Button endButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_make_call);

        Log.i(LOG_TAG, "MakeCallActivity started!");

        Intent intent = getIntent();
        displayName = intent.getStringExtra(G.EXTRA_DISPLAYNAME);
        contactName = intent.getStringExtra(G.EXTRA_C_Name);
        contactIp = intent.getStringExtra(G.EXTRA_C_Ip);

        TextView textView = (TextView) findViewById(R.id.textViewCalling);
        ToggleButton btnSwtich = (ToggleButton) findViewById(R.id.toggleButton2);
        btnSwtich.setOnCheckedChangeListener(this);
        displayName = String.format(getString(R.string.calling_lbl), contactName);

        endButton = (Button) findViewById(R.id.buttonEndCall);
        endButton.setOnClickListener(this);

        startListener();
        makeCall();
    }

    private void makeCall() {
        // Send a request to start a call
        sendMessage("VOICECALL" + displayName, G.BROADCAST_PORT);
    }

    private void endCall() {
        // Ends the chat sessions
        stopListener();
        if (G.IN_CALL) {

            call.endCall();
            G.IN_CALL = false;
        }
        sendMessage("END:", G.BROADCAST_PORT);
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

                    DatagramSocket listenerSocket = new DatagramSocket(G.CALL_LISTENER_PORT);
                    DatagramSocket senderSocket = new DatagramSocket();
                    listenerSocket.setSoTimeout(15000);
                    byte[] buffer = new byte[BUF_SIZE];
                    DatagramPacket packet = new DatagramPacket(buffer, BUF_SIZE);
                    while (LISTEN) {

                        try {

                            Log.i(LOG_TAG, "Listening for packets");
                            listenerSocket.receive(packet);
                            String data = new String(buffer, 0, packet.getLength());
                            Log.i(LOG_TAG, "Packet received from " + packet.getAddress() + " with contents: " + data);
                            String action = data.substring(0, 4);
                            if (action.equals("ACC:")) {
                                // Accept notification received. Start call
                                call = new AudioCall(senderSocket,listenerSocket ,packet.getAddress());
                                call.startCall();

                                G.IN_CALL = true;
                            } else if (action.equals("REJ:")) {
                                // Reject notification received. End call
                                endCall();
                            } else if (action.equals("END:")) {
                                // End call notification received. End call
                                endCall();
                            } else {
                                // Invalid notification received
                                Log.w(LOG_TAG, packet.getAddress() + " sent invalid message: " + data);
                            }
                        } catch (SocketTimeoutException e) {
                            if (!G.IN_CALL) {

                                Log.i(LOG_TAG, "No reply from contact. Ending call");
                                endCall();
                            }
                        } catch (IOException e) {

                        }
                    }
                    Log.i(LOG_TAG, "Listener ending");
                    listenerSocket.disconnect();
                    listenerSocket.close();
                } catch (SocketException e) {

                    Log.e(LOG_TAG, "SocketException in Listener");
                    e.printStackTrace();
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
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.make_call, menu);
        return true;
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

    @Override
    public void onClick(View v) {
        endCall();
    }
}
