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
import classes.Permission;
import classes.PermissionType;

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
    private AudioManager m_amAudioManager;
    private Vibrator vibrator;
    private MediaPlayer mediaPlayer;
    private Chronometer mChronometer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_receive_call);
        audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);

        initView();

        initSpeaker();

        initWakeup();
    }

    @Override
    protected void onResume() {
        super.onResume();

        boolean hasPermission = new Permission().checkPermission(ReceiveCallActivity.this, PermissionType.VIDEOCALL);

        if (hasPermission) {

            startListener();

        } else {
            Toast.makeText(ReceiveCallActivity.this, R.string.permission_denied, Toast.LENGTH_SHORT).show();
            cancelVibrate();
            finish();
        }
    }

    private void initSpeaker() {
        m_amAudioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        m_amAudioManager.setMode(AudioManager.MODE_IN_CALL);
        m_amAudioManager.setSpeakerphoneOn(false);
    }

    private void initView() {

        Intent intent = getIntent();
        contactName = intent.getStringExtra(G.EXTRA_C_Name);
        contactIp = intent.getStringExtra(G.EXTRA_C_Ip);

        vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);

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

        mChronometer = (Chronometer) findViewById(R.id.chronometer);

        vibrate();

        startShakeByViewAnim(acceptButton, 1, 1.2f, 10, 500);
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
        // End the call and send a notification
        stopListener();

        stopChronometer();

        if (G.IN_CALL) {

            call.endCall();
            G.IN_CALL = false;
        }
        sendMessage("END:");

        cancelVibrate();
        startPlayingBusyTone();

        finish();
    }

    private void startListener() {
        // Creates the listener thread
        LISTEN = true;
        Thread listenThread = new Thread(new Runnable() {

            @Override
            public void run() {

                try {

                    Logger.d(LOG_TAG, "startListener", "Listener started!");
                    DatagramSocket socket = new DatagramSocket(G.BROADCAST_PORT);
                    socket.setSoTimeout(15000);
                    byte[] buffer = new byte[BUF_SIZE];
                    DatagramPacket packet = new DatagramPacket(buffer, BUF_SIZE);

                    while (LISTEN) {
                        try {

                            Logger.d(LOG_TAG, "startListener", "Listening for packets");

                            socket.receive(packet);
                            String data = new String(buffer, 0, packet.getLength());
                            Logger.d(LOG_TAG, "startListener", "Packet received from " + packet.getAddress() + " with contents: " + data);

                            String action = data.substring(0, 4);
                            if (action.equals("END:")) {

                                showToast(getString(R.string.call_ended));

                                // End call notification received. End call
                                endCall();
                            } else {
                                // Invalid notification received.
                                Logger.d(LOG_TAG, "startListener", "Packet received from " + packet.getAddress() + " with contents: " + data);

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
                    DatagramPacket packet = new DatagramPacket(data, data.length, address, G.CALL_LISTENER_PORT);
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

    public void showToast(final String message) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(ReceiveCallActivity.this, message, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void vibrate() {
        vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);

        // Start without a delay
        // Vibrate for 100 milliseconds
        // Sleep for 1000 milliseconds
        long[] pattern = {0, 500, 1000};

        // The '0' here means to repeat indefinitely
        // '0' is actually the index at which the pattern keeps repeating from (the start)
        // To repeat the pattern from any other point, you could increase the index, e.g. '1'
        vibrator.vibrate(pattern, 0);
    }

    private void cancelVibrate() {
        vibrator.cancel();
    }

    private void startPlayingBusyTone() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {

                mediaPlayer = MediaPlayer.create(ReceiveCallActivity.this, R.raw.busy);
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


        switch (v.getId()) {

            case R.id.buttonAccept:
                try {

                    cancelVibrate();

                    showToast(getString(R.string.call_accpeted));

                    mLinearLayout.setVisibility(View.INVISIBLE);
                    // Accepting call. Send a notification and start the call
                    sendMessage("ACC:");

                    startChronometer();

                    cancelVibrate();

                    InetAddress address = InetAddress.getByName(contactIp);
                    Logger.d(LOG_TAG, "onCreate", "Calling " + address.toString());

                    G.IN_CALL = true;

                    call = new AudioCall(address);
                    call.startCall();
                    call.setEndCallListener(ReceiveCallActivity.this);
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

    @Override
    public void endAudioCall() {
        endCall();
    }
}
