package com.example.fcc.udptest;

import android.app.Activity;
import android.app.KeyguardManager;
import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Handler;
import android.os.PowerManager;
import android.os.Vibrator;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.animation.Animation;
import android.view.animation.AnimationSet;
import android.view.animation.RotateAnimation;
import android.view.animation.ScaleAnimation;
import android.widget.Button;
import android.widget.Chronometer;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;

import classes.Logger;

public class ReceiveCallActivity extends Activity implements OnClickListener, AudioCall.IEndCall {

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
    private Vibrator mVibrator;
    private MediaPlayer mMediaPlayer;
    private Chronometer chronometer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_receive_call);
        audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        audioManager.setMode(AudioManager.MODE_IN_CALL);
        audioManager.setSpeakerphoneOn(false);

        Intent intent = getIntent();
        contactName = intent.getStringExtra(G.EXTRA_C_Name);
        contactIp = intent.getStringExtra(G.EXTRA_C_Ip);


        initView();
        initWakeup();
        startListener();

    }

    private void startShakeByViewAnim(View view, float scaleSmall, float scaleLarge, float shakeDegrees, long duration) {
        if (view == null) {
            return;
        }

        Animation scaleAnim = new ScaleAnimation(scaleSmall, scaleLarge, scaleSmall, scaleLarge);
        final Animation scaleAnim2 = new ScaleAnimation(scaleSmall, 1 / scaleLarge, scaleSmall, 1 / scaleLarge);
        Animation rotateAnim = new RotateAnimation(-shakeDegrees, shakeDegrees, Animation.RELATIVE_TO_SELF, 0.5f, Animation.RELATIVE_TO_SELF, 0.5f);

        scaleAnim.setDuration(duration);
        scaleAnim.setRepeatMode(Animation.REVERSE);
        scaleAnim.setRepeatCount(Animation.INFINITE);

        scaleAnim2.setDuration(duration);
        scaleAnim2.setRepeatMode(Animation.REVERSE);
        scaleAnim2.setRepeatCount(Animation.INFINITE);


        rotateAnim.setDuration(duration / 10);
        rotateAnim.setRepeatMode(Animation.REVERSE);
        rotateAnim.setRepeatCount(Animation.INFINITE);

        final AnimationSet smallAnimationSet = new AnimationSet(false);
        smallAnimationSet.addAnimation(scaleAnim);
        smallAnimationSet.addAnimation(rotateAnim);


        view.startAnimation(smallAnimationSet);

        Handler handler = new Handler();
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                smallAnimationSet.addAnimation(scaleAnim2);
            }
        }, 500);
    }

    private void initView() {

        chronometer = (Chronometer) findViewById(R.id.chronometer);
        Tgl_Speaker = (ToggleButton) findViewById(R.id.tgl_speaker);
        Tgl_Speaker.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {

                if (isChecked) {

                    Logger.d("ReceiveCallActivity", "onCreate", "Toggle isChecked)");
                    audioManager.setMode(AudioManager.MODE_IN_CALL);
                    audioManager.setSpeakerphoneOn(true);

                } else {
                    Logger.d(LOG_TAG, "onCreate", "Toggle is not Checked)");
                    audioManager.setMode(AudioManager.MODE_IN_CALL);
                    audioManager.setSpeakerphoneOn(false);

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
        startVibrator();
        startShakeByViewAnim(acceptButton, 1, 1.2f, 10, 500);
    }

    private void startChronometer() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                //  chronometer.setBase(SystemClock.elapsedRealtime());
                chronometer.start();
            }
        });
    }

    private void stopChronometer() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                chronometer.stop();
            }
        });
    }

    private void initWakeup() {
        PowerManager pm = (PowerManager) getApplicationContext().getSystemService(Context.POWER_SERVICE);
        PowerManager.WakeLock wakeLock = pm.newWakeLock((PowerManager.SCREEN_BRIGHT_WAKE_LOCK | PowerManager.FULL_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP), "TAG");
        wakeLock.acquire();

        KeyguardManager keyguardManager = (KeyguardManager) getApplicationContext().getSystemService(Context.KEYGUARD_SERVICE);
        KeyguardManager.KeyguardLock keyguardLock = keyguardManager.newKeyguardLock("TAG");
        keyguardLock.disableKeyguard();
    }

    private void endCall() {
        stopChronometer();
        startPlayingBusyTone();

        stopVibrator();
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
                                showToast(getString(R.string.call_ended));
                                // End call notification received. End call
                                endCall();
                            } else {
                                // Invalid notification received.
                                Logger.e(LOG_TAG, "startListener", "Packet received from " + packet.getAddress() + " with contents: " + data);

                            }
                        } catch (IOException e) {
                            Logger.e(LOG_TAG, "startListener", "IOException in Listener " + e);
                            e.printStackTrace();
                        }
                    }
                    Logger.e(LOG_TAG, "startListener", "Listener ending");

                    socket.disconnect();
                    socket.close();
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
                    DatagramPacket packet = new DatagramPacket(data, data.length, address, G.CALL_SENDER_PORT);
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

                    startChronometer();
                    stopVibrator();
                    mLinearLayout.setVisibility(View.INVISIBLE);
                    // Accepting call. Send a notification and start the call
                    sendMessage("ACC:");
                    InetAddress address = InetAddress.getByName(contactIp);
                    Logger.d(LOG_TAG, "onCreate", "Calling " + address.toString());

                    G.IN_CALL = true;

                    call = new AudioCall(address);
                    call.startCall();
                    call.setEndCallListener(ReceiveCallActivity.this);

                    //call.setEndCallListener(ReceiveCallActivity.this);
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

    private void showToast(final String message) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(getApplicationContext(), message, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void startVibrator() {
        mVibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        long[] patern = {0, 500, 1000};
        mVibrator.vibrate(patern, 0);
    }

    private void stopVibrator() {
        mVibrator.cancel();
    }

    @Override
    public void endAudioCall() {
        endCall();
    }

    private void startPlayingBusyTone() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {

                mMediaPlayer = MediaPlayer.create(ReceiveCallActivity.this, R.raw.busy);
                mMediaPlayer.start();

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

                if (mMediaPlayer.isPlaying()) {
                    mMediaPlayer.stop();
//                    mMediaPlayer.release();
                }
            }
        });
    }
}
