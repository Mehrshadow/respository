package com.example.fcc.udptest;

import android.app.KeyguardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.hardware.Camera;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Handler;
import android.os.PowerManager;
import android.os.Vibrator;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationSet;
import android.view.animation.RotateAnimation;
import android.view.animation.ScaleAnimation;
import android.widget.Button;
import android.widget.Chronometer;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;

import classes.CameraPreview;
import classes.Logger;
import classes.Permission;
import classes.PermissionType;

public class ReceiveVideoCallActivity extends AppCompatActivity implements View.OnClickListener, AudioCall.IEndCall {

    private final static String LOG_TAG = "ReceiveVideoCall";
    private String contactIp;
    private String displayName;
    private boolean IN_VIDEO_CALL = false;
    private boolean LISTEN = false;
    private Button accept, reject, endCall;
    private ImageView mImgReceive;
    private int BUF_SIZE = 1024;
    private DatagramSocket mReceiveSocket;
    private DatagramSocket mSendSocket;
    private byte[] buffer;
    private int mSendFrameWidth, mSendFrameHeight, mSendFrameBuffSize;
    private int mReceiveFrameWidth, mReceiveFrameHeight, mReceiveFrameBuffSize;
    private boolean receiving = false;
    private FrameLayout cameraView;
    private Camera camera = null;
    private CameraPreview cameraPreview;
    private byte[] frameData;
    private Camera.Parameters parameters;
    private boolean shouldSendVideo = false;
    private InetAddress address;
    private DatagramPacket mVideoPacket;
    private AudioCall audioCall;
    private Vibrator vibrator;
    private Chronometer mChronometer;
    private MediaPlayer mediaPlayer;
    private boolean isVideoCallPermissionGranted = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_receive_video_call);
        startSockets();

        Log.d(LOG_TAG, "Receive video call created");

        initViews();

        initWakeup();

        vibrate();

        startShakeByViewAnim(accept, 1, 1.2f, 10, 500);
    }

    @Override
    protected void onResume() {
        super.onResume();

        boolean hasPermission = new Permission().checkPermission(ReceiveVideoCallActivity.this, PermissionType.VIDEOCALL);

        if (hasPermission) {
            openCamera();
            startCameraPreview();
            parsePacket();

        } else {
            Toast.makeText(ReceiveVideoCallActivity.this, R.string.permission_denied, Toast.LENGTH_SHORT).show();
            cancelVibrate();

            stopAnimation(accept);

            finish();
        }
    }

    private void initViews() {
        accept = (Button) findViewById(R.id.buttonAccept);
        reject = (Button) findViewById(R.id.buttonReject);
        endCall = (Button) findViewById(R.id.buttonEndCall);
        mImgReceive = (ImageView) findViewById(R.id.img);
        mChronometer = (Chronometer) findViewById(R.id.chronometer);

        accept.setOnClickListener(this);
        reject.setOnClickListener(this);
        endCall.setOnClickListener(this);

        Intent intent = getIntent();
        displayName = intent.getStringExtra(G.EXTRA_C_Name);
        contactIp = intent.getStringExtra(G.EXTRA_C_Ip);

        TextView textView = (TextView) findViewById(R.id.textViewIncomingCall);
        textView.setText("Incoming call: " + displayName);

        cameraView = (FrameLayout) findViewById(R.id.cameraView);

        vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);

        try {
            address = InetAddress.getByName(contactIp);
        } catch (UnknownHostException e) {
            e.printStackTrace();
        }
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

    private void startPlayingBusyTone() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {

                mediaPlayer = MediaPlayer.create(ReceiveVideoCallActivity.this, R.raw.busy);
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

    private void stopAnimation(View view) {
        view.clearAnimation();
        view.setVisibility(View.INVISIBLE);
    }

    private void initWakeup() {
        PowerManager pm = (PowerManager) getApplicationContext().getSystemService(Context.POWER_SERVICE);
        PowerManager.WakeLock wakeLock = pm.newWakeLock((PowerManager.SCREEN_BRIGHT_WAKE_LOCK | PowerManager.FULL_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP), "TAG");
        wakeLock.acquire();

        KeyguardManager keyguardManager = (KeyguardManager) getApplicationContext().getSystemService(Context.KEYGUARD_SERVICE);
        KeyguardManager.KeyguardLock keyguardLock = keyguardManager.newKeyguardLock("TAG");
        keyguardLock.disableKeyguard();
    }

    private void openCamera() {

        if (checkCameraHardware(this)) {
            try {

                camera = getCameraInstance();
//
            } catch (Exception e) {
                Log.e(LOG_TAG, "Error in camera");
                e.printStackTrace();
            }

        } else {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(ReceiveVideoCallActivity.this, "NO Camera Device found!!", Toast.LENGTH_LONG).show();
                }
            });
        }
    }

    private Camera getCameraInstance() {
        Camera c = null;

        try {
            c = Camera.open(G.FRONT_CAMERA);
        } catch (RuntimeException e) {

            try {
                c = Camera.open(G.REAR_CAMERA);

                if (c == null) {
                    Toast.makeText(ReceiveVideoCallActivity.this, R.string.cameraOpenFailure, Toast.LENGTH_LONG).show();
                    endCall();
                }
            } catch (RuntimeException e2) {
                Log.e(LOG_TAG, e2.toString());
                e.printStackTrace();
            }
        }

        return c;
    }

    private boolean checkCameraHardware(Context context) {
        if (context.getPackageManager().hasSystemFeature(
                PackageManager.FEATURE_CAMERA)) {
            // this device has a camera
            Log.d(LOG_TAG, "device has camera");
            return true;
        } else {
            // no camera on this device
            Log.d(LOG_TAG, "device does not have any camera!");
            return false;
        }
    }

    private void startCameraPreview() {
//        camera = getCameraInstance();

        cameraPreview = new CameraPreview(ReceiveVideoCallActivity.this, camera, previewCb);
        cameraView.addView(cameraPreview);
    }

    private void stopCameraPreview() {
//        camera = getCameraInstance();

        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                releaseCamera();
            }
        });
    }

    private void startSockets() {
        Logger.d("ReceiveVideoCallActivity", "socketStart", "started!");

        try {
            mReceiveSocket = new DatagramSocket(G.VIDEOCALL_LISTENER_PORT);
            mReceiveSocket.setSoTimeout(10 * 1000);
            mSendSocket = new DatagramSocket();
            Logger.d("ReceiveVideoCallActivity", "socketStart", "Connected");

        } catch (SocketException e) {
            e.printStackTrace();
        }
    }

    private void parsePacket() {

        // Create listener thread
        LISTEN = true;
        Thread listenThread = new Thread(new Runnable() {

            @Override
            public void run() {

                Logger.d(LOG_TAG, "startListener", "Listener started!");
                buffer = new byte[BUF_SIZE];
                DatagramPacket packet = new DatagramPacket(buffer, BUF_SIZE);
//                address = packet.getAddress();
                while (LISTEN) {
                    try {
                        Logger.d("ReceiveVideoCallActivity", "startListener", "Listening for packets");
                        mReceiveSocket.receive(packet);

                        String data = new String(buffer, 0, packet.getLength());
                        Logger.d("ReceiveVideoCallActivity", "startListener", "Packet received from " + packet.getAddress() + " with contents: " + data);

                        String action = data.substring(0, 4);
                        if (action.equals("END:")) {
                            showToast(getString(R.string.call_ended));
                            endCall();

                        } else if (data.startsWith("{")) {
                            introListener(data);

                        } else {
//                            Logger.d("ReceiveVideoCallActivity", "startListener", packet.getAddress() + " sent invalid message: " + data);

                            //frame received
                            shouldSendVideo = true;

                            audioCall = new AudioCall(address);
                            audioCall.startCall();
                            audioCall.setEndCallListener(ReceiveVideoCallActivity.this);

                            udpFrameListener();

                        }

                    } catch (IOException e) {
                        if (!mReceiveSocket.isClosed())
                            endCall();

                        Logger.e("ReceiveVideoCallActivity", "IOException", "IOException");
                        e.printStackTrace();
                    }
                }

                Logger.d("ReceiveVideoCallActivity", "startListener", "Listener ending");

                mReceiveSocket.disconnect();
                mReceiveSocket.close();
            }
        });
        listenThread.start();

    }

    private void sendMessage(final String message, final int port) {
        // Creates a thread for sending notifications
        Thread replyThread = new Thread(new Runnable() {

            @Override
            public void run() {

                Log.d(LOG_TAG, "sending message " + message);
                try {

                    byte[] data = message.getBytes();

                    DatagramPacket packet = new DatagramPacket(data, data.length, address, port);
                    mSendSocket.send(packet);

                    Log.i(LOG_TAG, "Sent message( " + message + " ) to " + contactIp);
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

    private void stopListener() {
        Logger.d("ReceiveVideoCallActivity", "stopListener", "Stopping listener");

        // Ends the listener thread
        LISTEN = false;
    }

    private void endCall() {

        Log.d(LOG_TAG, "end call");

        cancelVibrate();
        startPlayingBusyTone();
        stopChronometer();

        // Ends the chat sessions
        stopListener();

//        if (IN_VIDEO_CALL) {
        receiving = false;
        LISTEN = false;

        if (audioCall != null)
            audioCall.endCall();

        stopCameraPreview();

        sendMessage("END:", G.VIDEOCALL_SENDER_PORT);

        stopSockets();

        finish();
    }

    private void stopSockets() {
        mSendSocket.disconnect();
        mSendSocket.close();

        mReceiveSocket.disconnect();
        mReceiveSocket.close();
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.buttonAccept:

                cancelVibrate();
                stopAnimation(accept);

                sendMessage("ACC:", G.VIDEOCALL_SENDER_PORT);

                Logger.d("ReceiveVideoCallActivity", "onClick", "Calling " + address.toString());
                IN_VIDEO_CALL = true;

                accept.setVisibility(View.GONE);
                reject.setVisibility(View.GONE);
                endCall.setVisibility(View.VISIBLE);

                break;
            case R.id.buttonReject:
                sendMessage("REJ:", G.VIDEOCALL_SENDER_PORT);
                endCall();
                break;
            case R.id.buttonEndCall:
                endCall();
                break;
        }
    }

    private void introListener(String data) {
        try {

            JSONObject jsonObject = new JSONObject(data);
            mReceiveFrameWidth = jsonObject.getInt("Width");
            mReceiveFrameHeight = jsonObject.getInt("Height");
            mReceiveFrameBuffSize = jsonObject.getInt("Size");
            Logger.d("ReceiveVideoCallActivity", "introListener", "mFrameBuffSize >> " + mReceiveFrameBuffSize);

            Logger.d("ReceiveVideoCallActivity", "introListener", "Received Data " +
                    ">> width =" + mReceiveFrameWidth +
                    " height = " + mReceiveFrameHeight +
                    " buffSize = " + mReceiveFrameBuffSize);
            if (mReceiveFrameWidth != 0 && mReceiveFrameHeight != 0 && mReceiveFrameBuffSize != 0) {
//                sendACC();

                sendFrameIntroduceData(address);

//                udpFrameListener();

                Logger.d("ReceiveVideoCallActivity", "introListener", "mIsFrameReceived is true Sent ACC");
            } else {
                finish();
                Logger.d("ReceiveVideoCallActivity", "introListener", "mIsFrameReceived is false");
                Logger.d("ReceiveVideoCallActivity", "introListener", "finish Activity");
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    private void previewBitmap(final byte[] data, final int packetLength) {

        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {

                try {

                    final String receivedValue = new String(data, 0, packetLength);
                    int index = receivedValue.indexOf("]");
                    int bufferSize = Integer.parseInt(receivedValue.substring(0, index));
                    final byte[] bufferToSend = new byte[bufferSize];
                    System.arraycopy(data, index + 1, bufferToSend, 0, bufferSize);

                    final Bitmap bitmap = BitmapFactory.decodeByteArray(bufferToSend, 0, bufferToSend.length);
                    if (bitmap == null)
                        return;

                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            mImgReceive.setImageBitmap(bitmap);
                        }
                    });
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });
        thread.start();
    }

    private void udpFrameListener() {
        receiving = true;

        startChronometer();

        Logger.d("ReceiveVideoCallActivity", "udpReceived", " Thread Start");
        try {
            final byte[] buff = new byte[mReceiveFrameBuffSize * 10];
//            Logger.d("ReceiveVideoCallActivity", "udpReceived", "mFrameBuffSize >> " + mFrameBuffSize);
//                    datagramSocket.setSoTimeout(10000);
            DatagramPacket packet = new DatagramPacket(buff, buff.length);

            while (receiving) {

                mReceiveSocket.setSoTimeout(5 * 1000);// 5 seconds to receive next frame, else, it will close
                mReceiveSocket.receive(packet);

                Logger.d("ReceiveVideoCallActivity", "udpReceived", "buff.size()" + buff.length);
                previewBitmap(buff, packet.getLength());
            }

            showToast(getString(R.string.call_ended));

            mReceiveSocket.disconnect();
            mReceiveSocket.close();
        } catch (SocketException e) {
            Logger.e("ReceiveVideoCallActivity", "udpFrameListener", "SocketException");

            showToast(getString(R.string.call_ended));

            endCall();

            LISTEN = false;
            e.printStackTrace();
        } catch (IOException e) {
            Logger.e("ReceiveVideoCallActivity", "udpFrameListener", "IOException");
            LISTEN = false;
            e.printStackTrace();

            showToast(getString(R.string.call_ended));

            endCall();
        }
    }

    private byte[] compressCameraData(byte[] data) {

        YuvImage yuv = new YuvImage(data, parameters.getPreviewFormat(), mSendFrameWidth, mSendFrameHeight, null);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        yuv.compressToJpeg(new Rect(0, 0, mSendFrameWidth, mSendFrameHeight), 50, out);

        mSendFrameBuffSize = out.toByteArray().length;

        String size = mSendFrameBuffSize + "]";

        byte[] bufferToSend = new byte[mSendFrameBuffSize + size.getBytes().length];

        System.arraycopy(size.getBytes(), 0, bufferToSend, 0, size.getBytes().length);
        System.arraycopy(out.toByteArray(), 0, bufferToSend, size.getBytes().length, out.size());

        return bufferToSend;
//        return out.toByteArray();
    }

    private void releaseCamera() {
        if (camera != null) {
            camera.setPreviewCallback(null);
            camera.release();
            camera = null;
        }
    }

    private void sendFrameIntroduceData(final InetAddress address) {
        // Creates the thread for sending frames

        new Thread(new Runnable() {
            @Override
            public void run() {

                try {
                    int byteSent = 0;

                    String frameJSONData = getFrameJSONData();

//                    Socket socket = new Socket(address, G.INTRODUCE_PORT);

//                    OutputStream writer = socket.getOutputStream();
                    byte[] buf = frameJSONData.getBytes();

                    mVideoPacket = new DatagramPacket(buf, buf.length, address, G.VIDEOCALL_SENDER_PORT);

                    mSendSocket.send(mVideoPacket);

//                        writer.write(frameJSONData.getBytes());

                    Logger.d(LOG_TAG, "SendFrameIntroduce", frameJSONData);

                    byteSent += frameJSONData.getBytes().length;

                    Logger.d(LOG_TAG, "bytes sent: ", byteSent + "");

//                        socket.close();

                } catch (IOException e1) {
                    e1.printStackTrace();
                }
            }
        }).start();
    }

    private String getFrameJSONData() {
        JSONObject jsonObject = new JSONObject();
        try {
            jsonObject.put("Width", mSendFrameWidth);
            jsonObject.put("Height", mSendFrameHeight);
            jsonObject.put("Size", mSendFrameBuffSize);
        } catch (JSONException e) {
            e.printStackTrace();
        }

        return jsonObject.toString();
    }

    private void sendFrameDataUDP() {

        new Thread(new Runnable() {
            @Override
            public void run() {

                try {
//                    Logger.d(LOG_TAG, "address: ", address + "");

                    DatagramSocket socket = new DatagramSocket();
                    mVideoPacket = new DatagramPacket(frameData, frameData.length, address, G.VIDEOCALL_SENDER_PORT);

//                    Logger.d(LOG_TAG, "frame length sent: ", frameData.length + "");

                    socket.send(mVideoPacket);

                } catch (IOException e) {
                    releaseCamera();
                    e.printStackTrace();
                }
            }
        }).start();
    }

    Camera.PreviewCallback previewCb = new Camera.PreviewCallback() {
        public void onPreviewFrame(byte[] data, Camera camera) {

            if (data != null && data.length != 0) {
//                frameData = data;
                parameters = camera.getParameters();
                mSendFrameHeight = parameters.getPreviewSize().height;
                mSendFrameWidth = parameters.getPreviewSize().width;

                frameData = compressCameraData(data);
            }

            if (shouldSendVideo) {

                sendFrameDataUDP();
            }
        }
    };

    public void showToast(final String message) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(ReceiveVideoCallActivity.this, message, Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    public void endAudioCall() {
//        endCall();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
        switch (requestCode) {
            case Permission.MY_PERMISSIONS_REQUEST_VIDEOCALL: {
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

//    private void tcpReceived() {
//        if (!receiving) {
//            receiving = true;
//            new Thread(new Runnable() {
//                @Override
//                public void run() {
//                    try {
//                        ServerSocket serverSocket = new ServerSocket(G.VIDEO_CALL_PORT);
//                        Socket socket = serverSocket.accept();
//                        //socket.setSoTimeout(5000);
//
//                        while (receiving) {
//
//                            if (socket.isConnected()) {
//                                byte[] buff = new byte[mFrameBuffSize];
//
//                                //\\\\\\\\
//                                //BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
//                                //int lenght = bufferedReader.readLine().length();
//                                //byte[] buffer = bufferedReader.readLine().getBytes();
//                                //Logger.d("ReceiveVideoCallActivity", "receiveVideo", "bufferedReader size >> " + lenght);
//                                //\\\\\\\\\\
//
//
//                                DataInputStream inputStream1 = new DataInputStream(socket.getInputStream());
//                                Logger.d("ReceiveVideoCallActivity", "receiveVideo", "DataInputStream size >> " + inputStream1.read());
//                                inputStream1.readFully(buff, 0, mFrameBuffSize);
//
//
//                               // InputStream inputStream = socket.getInputStream();
//                               // Logger.d("ReceiveVideoCallActivity", "receiveVideo", "inputStream size >> " + inputStream.toString().length());
//                               // int count = inputStream.read(buff,0,mFrameBuffSize);
//                               // Logger.d("ReceiveVideoCallActivity", "receiveVideo", "buff size count >> " + count);
//                                //\\\\\\\\\
//
//
//                                previewBitmap(buff);
//                            }
//                        }
//                    } catch (SocketException e) {
//                        receiving = false;
//                        e.printStackTrace();
//                    } catch (IOException e) {
//                        receiving = false;
//                        e.printStackTrace();
//                    }
//                }
//            }).start();
//        }
//
//    }