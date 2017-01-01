package com.example.fcc.udptest;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.hardware.Camera;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.util.Log;
import android.view.View;
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
import java.io.OutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;

import classes.CameraPreview;
import classes.Logger;

public class MakeVideoCallActivity extends Activity implements View.OnClickListener ,AudioCall.IEndCall {

    private static final String LOG_TAG = "MakeVideoCall";

    private String contactIp;
    private String displayName;
    private String contactName;
    private static final int BUF_SIZE = 1024;
    private Camera camera = null;
    private Button buttonEndCall;
    private Chronometer chronometer;
    private DatagramSocket mSenderSocket;
    private DatagramSocket mListenerSocket;
    private DatagramPacket mVideoPacket;
    private int mSendFrameWidth, mSendFrameHeight, mSendFrameBuffSize;
    private int mReceiveFrameWidth, mReceiveFrameHeight, mReceiverameBuffSize;
    private byte[] frameData;
    private FrameLayout cameraView;
    private InetAddress address;
    private boolean shouldSendVideo = false;
    private Socket socket_sendFrameData;
    private Camera.Parameters parameters;
    private boolean LISTEN = false;
    private boolean receiving = false;
    private AudioCall call;
    private ImageView mImgReceive;
    private AudioManager mAudioManaget;

    private MediaPlayer mediaPlayer;



    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_make_video_call);


        startPlayingWaitingTone();

        Log.d(LOG_TAG, "Make video call started");

        Intent intent = getIntent();
        displayName = intent.getStringExtra(G.EXTRA_DISPLAYNAME);
        contactName = intent.getStringExtra(G.EXTRA_C_Name);
        contactIp = intent.getStringExtra(G.EXTRA_C_Ip);

        initView();
        initSpeaker();

        openCamera();
        startCameraPreview();
    }




    private void initView() {

        TextView textView = (TextView) findViewById(R.id.contactName);
        textView.setText("Calling: " + contactName);

        buttonEndCall = (Button) findViewById(R.id.buttonEndCall);
        buttonEndCall.setOnClickListener(this);

        cameraView = (FrameLayout) findViewById(R.id.cameraView);
        chronometer = (Chronometer) findViewById(R.id.chronometer);
        mImgReceive = (ImageView) findViewById(R.id.img_receive);
    }

    private void initSpeaker() {

        mAudioManaget = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        mAudioManaget.setMode(AudioManager.MODE_IN_CALL);
        mAudioManaget.setSpeakerphoneOn(true);

    }

    private void startChronometer() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
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

    private void releaseCamera() {
        if (camera != null) {
            camera.setPreviewCallback(null);
            camera.release();
            camera = null;
        }
    }

    private void previewBitmap(final byte[] data, final int packetlength) {

        Logger.d(LOG_TAG, "previewBitmap", "Start");

        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    final String receivedValue = new String(data, 0, packetlength);
                    int index = receivedValue.indexOf("]");
                    int bufferSize = Integer.parseInt(receivedValue.substring(0, index));
                    final byte[] bufferToSend = new byte[bufferSize];
                    System.arraycopy(data, index + 1, bufferToSend, 0, bufferSize);

                    final Bitmap bitmap = BitmapFactory.decodeByteArray(bufferToSend, 0, bufferToSend.length);
                    //   final Bitmap resizeBitMap = Bitmap.createScaledBitmap(bitmap, mReceiveFrameWidth, mReceiveFrameHeight, true);
                    if (bitmap == null) {
                        return;
                    }
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
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();

        releaseCamera();
    }

    @Override
    protected void onResume() {
        super.onResume();

        openSenderSocket();
        openListenerSocket();

        startListener();
        makeVideoCall();
    }

    @Override
    protected void onStop() {
        super.onStop();

        releaseCamera();

        stopListener();

        stopSendingFrames();
    }

    private Camera getCameraInstance() {
        Camera c = null;

        try {
            c = Camera.open(G.FRONT_CAMERA);
        } catch (RuntimeException e) {

            try {
                c = Camera.open(G.REAR_CAMERA);

                if (c == null) {
                    Toast.makeText(MakeVideoCallActivity.this, R.string.cameraOpenFailure, Toast.LENGTH_LONG).show();
                    endCall();
                }
            } catch (RuntimeException e2) {
                Log.e(LOG_TAG, e2.toString());
                e.printStackTrace();
            }
        }

        return c;
    }

    private void openCamera() {

        if (checkCameraHardware(this)) {
            try {

                camera = getCameraInstance();

            } catch (Exception e) {
                Log.e(LOG_TAG, "Error in camera");
                e.printStackTrace();
            }

        } else {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(MakeVideoCallActivity.this, "NO Camera Device found!!", Toast.LENGTH_LONG).show();
                }
            });
        }
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

    private void startListener() {
        // Create listener thread
        LISTEN = true;
        Thread listenThread = new Thread(new Runnable() {

            @Override
            public void run() {

                Log.i(LOG_TAG, "Listener started!");

                byte[] buffer = new byte[BUF_SIZE];
//                mVideoPacket = new DatagramPacket(buffer, BUF_SIZE);

                DatagramPacket packet = new DatagramPacket(buffer, BUF_SIZE);

                while (LISTEN) {
                    try {

                        Log.i(LOG_TAG, "Listening for packets");
                        mListenerSocket.receive(packet);
                        mListenerSocket.setSoTimeout(10 * 1000);
//                        mListenerSocket.setSoTimeout(10 * 1000);
                        String data = new String(buffer, 0, packet.getLength());
                        address = packet.getAddress();
                        Logger.d(LOG_TAG, "startListener", "Packet received from " + address + " with contents: " + data);
                        String action = data.substring(0, 4);
                        if (action.equals("ACC:")) {

                            stopPlayingTone();

                            Logger.d(LOG_TAG, "startListener", "video call accepted");
//                                Send Introduce and Listen to incoming value
                            showToast(getString(R.string.call_accepted));
                            sendFrameIntroduceData();


                        } else if (action.equals("REJ:")) {

                            // Reject notification received. End call
                            showToast(getString(R.string.call_rejected));
                            Logger.d(LOG_TAG, "startListener", "Ending call...");
                            endCall();

                        } else if (action.equals("END:")) {

                            showToast(getString(R.string.call_ended));
                            Log.d(LOG_TAG, "Ending call...");
                            endCall();

                        } else if (data.startsWith("{")) {
                            introListener(data);
                        } else {
                            Logger.d(LOG_TAG, "startListener", address + " [else] : [FRAME]");

                            udpFrameListener();
                        }
                    } catch (SocketTimeoutException e) {

                        Log.i(LOG_TAG, "No reply from contact. Ending call");
                        endCall();

                    } catch (IOException e) {
                        Log.e(LOG_TAG, e.toString());
                        e.printStackTrace();
                        Logger.d(LOG_TAG, "startListener", e.toString());
                        endCall();
                    }
                }
                Logger.d(LOG_TAG, "startListener", "Listener ending");


                mSenderSocket.disconnect();
                mSenderSocket.close();

                Logger.d(LOG_TAG, "startListener", "Listener socket dc & close");

            }
        });
        listenThread.start();
    }

    private void openSenderSocket() {
        try {
            mSenderSocket = new DatagramSocket();
            mSenderSocket.setSoTimeout(10 * 1000);
        } catch (SocketException e) {
            Logger.d(LOG_TAG, "openVideoSocket", "socket open crashed!");
            e.printStackTrace();
        }
    }

    private void openListenerSocket() {
        try {
            mListenerSocket = new DatagramSocket(G.RECEIVEVIDEO_PORT);
        } catch (SocketException e) {
            e.printStackTrace();
        }
    }

    private void introListener(String data) {
        try {

            JSONObject jsonObject = new JSONObject(data);
            mReceiveFrameWidth = jsonObject.getInt("Width");
            mReceiveFrameHeight = jsonObject.getInt("Height");
            mReceiverameBuffSize = jsonObject.getInt("Size");
            Logger.d(LOG_TAG, "introListener", "mFrameBuffSize >> " + mReceiverameBuffSize);

            Logger.d(LOG_TAG, "introListener", "Received Data " +
                    ">> width =" + mReceiveFrameWidth +
                    " height = " + mReceiveFrameHeight +
                    " buffSize = " + mReceiverameBuffSize);
            if (mReceiveFrameHeight != 0 && mReceiveFrameWidth != 0 && mReceiverameBuffSize != 0) {

                shouldSendVideo = true;
                call = new AudioCall(address);
                call.startCall();
                call.setEndCallListener(this);

                Logger.d(LOG_TAG, "introListener", "mIsFrameReceived is true Sent ACC");
            } else {
                //finish();
                Logger.d(LOG_TAG, "introListener", "mIsFrameReceived is false");
                Logger.d(LOG_TAG, "introListener", "finish Activity");
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    private void udpFrameListener() {
        startChronometer();
        receiving = true;
        Logger.d(LOG_TAG, "udpReceived", "Start");
        try {
            final byte[] buff = new byte[mReceiverameBuffSize * 6];
            Logger.d(LOG_TAG, "udpReceived", "mFrameBuffSize >> " + mReceiverameBuffSize);
//                    datagramSocket.setSoTimeout(10000);
            DatagramPacket packet = new DatagramPacket(buff, buff.length);
            while (receiving) {

                mListenerSocket.setSoTimeout(5 * 1000);// 5 seconds to receive next frame, else, it will close
                mListenerSocket.receive(packet);

                Logger.d(LOG_TAG, "udpReceived", "buff.size()" + buff.length);
                previewBitmap(buff, packet.getLength());
            }
            mListenerSocket.disconnect();
            mListenerSocket.close();
        } catch (SocketException e) {
            Logger.e(LOG_TAG, "udpFrameListener", "SocketException");
            endCall();
            LISTEN = false;
            e.printStackTrace();
        } catch (IOException e) {
            Logger.e(LOG_TAG, "udpFrameListener", "IOException");
            LISTEN = false;
            e.printStackTrace();
            endCall();
        }
    }

    private void sendMessage(final String message, final int port) {
        // Creates a thread used for sending notifications
        Thread replyThread = new Thread(new Runnable() {

            @Override
            public void run() {

                try {

                    InetAddress address = InetAddress.getByName(contactIp);
                    byte[] data = message.getBytes();
//                    DatagramSocket socket = new DatagramSocket();
                    mVideoPacket = new DatagramPacket(data, data.length, address, port);
                    mSenderSocket.send(mVideoPacket);
                    Log.i(LOG_TAG, "Sent message( " + message + " ) to " + contactIp);
//                    mSenderSocket.disconnect();
//                    mSenderSocket.close();
                } catch (UnknownHostException e) {

                    Log.e(LOG_TAG, "Failure. UnknownHostException in sendMessage: " + contactIp);
                    e.printStackTrace();
                } catch (SocketException e) {

                    Log.e(LOG_TAG, "Failure. SocketException in sendMessage: " + e);
                    e.printStackTrace();
                } catch (IOException e) {

                    Log.e(LOG_TAG, "Failure. IOException in sendMessage: " + e);
                    e.printStackTrace();
                }
            }
        });
        replyThread.start();
    }

    private void makeVideoCall() {
        // Send a request to start a call
        sendMessage("VIDEOCALL" + displayName, G.BROADCAST_PORT);

    }

    private void endCall() {

        stopPlayingTone();
        startPlayingBusyTone();

        stopChronometer();
        if (call != null)
            call.endCall();

        // Ends the chat sessions
        Log.d(LOG_TAG, "end call");

        sendMessage("END:", G.SENDVIDEO_PORT);
        closeSockets();
        finish();
    }

    private void closeSockets() {
        mListenerSocket.disconnect();
        mListenerSocket.close();
        mSenderSocket.disconnect();
        mSenderSocket.close();
    }

    private void stopSendingFrames() {

//        closeSendFrameSocket();

//        mSenderSocket.disconnect();
//        mSenderSocket.close();

        releaseCamera();
    }

    private void stopListener() {

        Log.d(LOG_TAG, "stopping listener");
        LISTEN = false;

        mListenerSocket.disconnect();
        if (!mListenerSocket.isBound()) {
            mListenerSocket.close();
        }
    }

    private void sendFrameIntroduceData() {

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

                    mVideoPacket = new DatagramPacket(buf, buf.length, address, G.SENDVIDEO_PORT);

                    mSenderSocket.send(mVideoPacket);

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

    private void closeSendFrameSocket() {
        try {
            socket_sendFrameData.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void startCameraPreview() {
//        camera = getCameraInstance();

        CameraPreview cameraPreview = new CameraPreview(MakeVideoCallActivity.this, camera, previewCb);
        cameraView.addView(cameraPreview);
    }

    private void sendFrameDataTCP(final byte[] data) {

        //        Logger.d(LOG_TAG, "Frame rate is: ", parameters.getPreviewFrameRate() + "");

        new Thread(new Runnable() {
            @Override
            public void run() {

                try {
                    int byteSent;

                    OutputStream os = socket_sendFrameData.getOutputStream();

                    if (socket_sendFrameData.isConnected()) {

                        Logger.d(LOG_TAG, "sendFrameData ", "connected");

                        byte[] d = new byte[data.length + 1];
                        for (int i = 0; i < data.length; i++) {
                            d[i] = data[i];
                        }
                        d[data.length] = '\n';
                        os.write(d);

                        byteSent = frameData.length;

                        //  Logger.d(LOG_TAG, "frame length sent: ", byteSent + "");
                    }

                } catch (IOException e) {
                    e.printStackTrace();

                    closeSendFrameSocket();
                }
            }
        }).start();
    }

    private void sendFrameDataUDP() {

        new Thread(new Runnable() {
            @Override
            public void run() {

                try {
                    // Logger.d(LOG_TAG, "address: ", address + "");

                    DatagramSocket socket = new DatagramSocket();
                    mVideoPacket = new DatagramPacket(frameData, frameData.length, address, G.SENDVIDEO_PORT);

                    //Logger.d(LOG_TAG, "frame length sent: ", frameData.length + "");

                    socket.send(mVideoPacket);

                } catch (IOException e) {
                    releaseCamera();
                    e.printStackTrace();
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

    Camera.PreviewCallback previewCb = new Camera.PreviewCallback() {
        public void onPreviewFrame(byte[] data, Camera camera) {

            if (data != null && data.length != 0) {
                //frameData = data;
                parameters = camera.getParameters();
                mSendFrameHeight = parameters.getPreviewSize().height;
                mSendFrameWidth = parameters.getPreviewSize().width;

                frameData = compressCameraData(data);

                // getBitmap(frameData);

            }

            if (shouldSendVideo) {

//                sendFrameDataTCP(frameData);
                sendFrameDataUDP();
            }
        }
    };

    @Override
    public void onClick(View v) {
        endCall();
    }

    private void showToast(final String message) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(getApplicationContext(), message, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void startPlayingWaitingTone() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mediaPlayer = MediaPlayer.create(MakeVideoCallActivity.this, R.raw.waiting);
                mediaPlayer.setLooping(true);
                mediaPlayer.start();
            }
        });
    }

    private void startPlayingBusyTone() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {

                mediaPlayer = MediaPlayer.create(MakeVideoCallActivity.this, R.raw.busy);
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

    @Override
    public void endAudioCall() {
        endCall();
    }
}

