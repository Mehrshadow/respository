package com.example.fcc.udptest;


import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.Chronometer;
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

import classes.Permission;
import classes.PermissionType;

public class MakeCallActivity extends Activity implements CompoundButton.OnCheckedChangeListener, OnClickListener, AudioCall.IEndCall {

    private static final String LOG_TAG = "MakeCall";
    private static final int BUF_SIZE = 1024;
    private String displayName;
    private String contactName;
    private String contactIp;
    private boolean LISTEN = true;
    private AudioCall call;
    private Button endButton;
    private AudioManager mAudioManager;
    private MediaPlayer mediaPlayer;
    private Chronometer mChronometer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_make_call);

        Log.i(LOG_TAG, "MakeCallActivity started!");

        initViews();

        initSpeaker();
    }

    @Override
    protected void onResume() {
        super.onResume();

        boolean hasPermission = new Permission().checkPermission(MakeCallActivity.this, PermissionType.VIDEOCALL);

        if (hasPermission) {

            startListener();
            makeCall();

        } else {
            Toast.makeText(MakeCallActivity.this, R.string.permission_denied, Toast.LENGTH_SHORT).show();

            finish();
        }
    }

    private void initViews() {
        Intent intent = getIntent();
        displayName = intent.getStringExtra(G.EXTRA_DISPLAYNAME);
        contactName = intent.getStringExtra(G.EXTRA_C_Name);
        contactIp = intent.getStringExtra(G.EXTRA_C_Ip);

        TextView textView = (TextView) findViewById(R.id.textViewCalling);
        ToggleButton btnSwitch = (ToggleButton) findViewById(R.id.toggleButton2);
        btnSwitch.setOnCheckedChangeListener(this);
        textView.setText(String.format(getString(R.string.calling_lbl), contactName));

        mChronometer = (Chronometer) findViewById(R.id.chronometer);

        endButton = (Button) findViewById(R.id.buttonEndCall);
        endButton.setOnClickListener(this);
    }

    private void initSpeaker() {
        mAudioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        mAudioManager.setMode(AudioManager.MODE_IN_CALL);
        mAudioManager.setSpeakerphoneOn(false);
    }

    private void makeCall() {
        // Send a request to start a call
        sendMessage("VOICECALL" + displayName, G.BROADCAST_PORT);
    }

    private void endCall() {
        // Ends the chat sessions

        stopPlayingTone();
        startPlayingBusyTone();

        stopChronometer();

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
                    listenerSocket.setSoTimeout(15000);
                    byte[] buffer = new byte[BUF_SIZE];
                    DatagramPacket packet = new DatagramPacket(buffer, BUF_SIZE);

                    startPlayingWaitingTone();

                    while (LISTEN) {

                        try {

                            Log.d(LOG_TAG, "Listening for packets");
                            listenerSocket.receive(packet);
                            String data = new String(buffer, 0, packet.getLength());
                            Log.d(LOG_TAG, "Packet received from " + packet.getAddress() + " with contents: " + data);
                            String action = data.substring(0, 4);
                            if (action.equals("ACC:")) {

                                stopPlayingTone();

                                startChronometer();

                                showToast(getString(R.string.call_accpeted));

                                // Accept notification received. Start call
                                call = new AudioCall(packet.getAddress());
                                call.startCall();

                                call.setEndCallListener(MakeCallActivity.this);

                                G.IN_CALL = true;
                            } else if (action.equals("REJ:")) {

                                showToast(getString(R.string.call_rejected));
                                // Reject notification received. End call
                                endCall();
                            } else if (action.equals("END:")) {

                                showToast(getString(R.string.call_ended));
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

    private void startPlayingWaitingTone() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mediaPlayer = MediaPlayer.create(MakeCallActivity.this, R.raw.waiting);
                mediaPlayer.setLooping(true);
                mediaPlayer.start();
            }
        });
    }

    private void startPlayingBusyTone() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {

                mediaPlayer = MediaPlayer.create(MakeCallActivity.this, R.raw.busy);
                mediaPlayer.start();

                CountDownTimer timer = new CountDownTimer(3000, 1000) {
                    //
                    @Override
                    public void onTick(long millisUntilFinished) {
                    }

                    @Override
                    public void onFinish() {
                        stopPlayingTone();
                    }
                };
                timer.start();

            }
        });
    }

    private void stopPlayingTone() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {

                if (mediaPlayer.isPlaying()) {
                    mediaPlayer.stop();
//                    mediaPlayer.release();
                }
            }
        });
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
    public void onCheckedChanged(CompoundButton buttonView, final boolean isChecked) {

        new Thread(new Runnable() {
            @Override
            public void run() {
                Log.d(LOG_TAG, "IN CALL: " + G.IN_CALL);
                if (isChecked) {

                    mAudioManager.setMode(AudioManager.MODE_IN_CALL);
                    mAudioManager.setSpeakerphoneOn(true);
//                    mediaPlayer.setAudioStreamType(AudioManager.STREAM_VOICE_CALL);

                } else {
                    mAudioManager.setMode(AudioManager.MODE_IN_CALL);
                    mAudioManager.setSpeakerphoneOn(false);
                }
            }
        }).start();

    }

    public void showToast(final String message) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(MakeCallActivity.this, message, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void startChronometer() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mChronometer.start();
            }
        });
    }

    private void stopChronometer() {

        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mChronometer.stop();
            }
        });
    }

    @Override
    public void onClick(View v) {
        endCall();
    }

    @Override
    public void endAudioCall() {
        endCall();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
        switch (requestCode) {
            case Permission.MY_PERMISSIONS_REQUEST_RECORD_AUDIO: {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {

                } else {

                    finish();
                }
                return;
            }

            // other 'case' lines to check for other
            // permissions this app might request
        }
    }
}
