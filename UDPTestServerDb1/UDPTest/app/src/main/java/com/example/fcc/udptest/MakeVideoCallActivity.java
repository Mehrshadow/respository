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
import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.util.Log;
import android.view.View;
import android.widget.Button;
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
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;

import classes.CameraPreview;
import classes.Logger;

public class MakeVideoCallActivity extends Activity implements View.OnClickListener {

    private static final String LOG_TAG = "MakeVideoCall";

    private String contactIp;
    private String displayName;
    private static final int BUF_SIZE = 1024;
    private Camera camera = null;
    private DatagramSocket mSenderSocket, mListenerSocket;
    private DatagramPacket mVideoPacket;
    private int mSenderWidth, mSenderHeight, mSendFrameBuffSize;
    private int mReceiveFrameBuffSize;
    private byte[] frameData;
    private FrameLayout cameraView;
    private InetAddress address;
    private boolean shouldSendVideo = false;
    private boolean LISTEN = false;
    private boolean receiving = false;
    private ImageView img;
    private AudioCall audioCall;
    private MediaPlayer mediaPlayer;
    private Camera.Parameters parameters;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_make_video_call);

        Log.d(LOG_TAG, "Make video call started");

        initViews();

        openCamera();

        startCameraPreview();
    }

    private void initViews() {
        Intent intent = getIntent();
        displayName = intent.getStringExtra(G.EXTRA_DISPLAYNAME);
        String contactName = intent.getStringExtra(G.EXTRA_C_Name);
        contactIp = intent.getStringExtra(G.EXTRA_C_Ip);

        TextView textView = (TextView) findViewById(R.id.contactName);
        textView.setText("Calling: " + contactName);

        img = (ImageView) findViewById(R.id.img);

        Button buttonEndCall = (Button) findViewById(R.id.buttonEndCall);
        buttonEndCall.setOnClickListener(this);

        try {
            address = InetAddress.getByName(contactIp);
        } catch (UnknownHostException e) {
            e.printStackTrace();
        }

        cameraView = (FrameLayout) findViewById(R.id.cameraView);
    }

    private void releaseCamera() {
        if (camera != null) {
            camera.setPreviewCallback(null);
            camera.release();
            camera = null;
        }
    }

    private void previewBitmap(final byte[] data, final int packetLength) {

        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {

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
                        img.setImageBitmap(bitmap);
                    }
                });
            }
        });
        thread.start();
    }

    private byte[] compressCameraData(byte[] data) {

        YuvImage yuv = new YuvImage(data, parameters.getPreviewFormat(), mSenderWidth, mSenderHeight, null);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        yuv.compressToJpeg(new Rect(0, 0, mSenderWidth, mSenderHeight), 100, out);

        mSendFrameBuffSize = out.toByteArray().length;

        String size = mSendFrameBuffSize + "]";

        byte[] bufferToSend = new byte[mSendFrameBuffSize + size.getBytes().length];

        System.arraycopy(size.getBytes(), 0, bufferToSend, 0, size.getBytes().length);
        System.arraycopy(out.toByteArray(), 0, bufferToSend, size.getBytes().length, out.size());

        return bufferToSend;
//        return out.toByteArray();
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
    protected void onPause() {
        super.onPause();

    }

    @Override
    protected void onStop() {
        super.onStop();

//        releaseCamera();

        stopListener();

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

    private void startCameraPreview() {

        CameraPreview cameraPreview = new CameraPreview(MakeVideoCallActivity.this, camera, previewCb);
        cameraView.addView(cameraPreview);
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

        startPlayingWaitingTone();

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
//                        mListenerSocket.setSoTimeout(10 * 1000);

//                        address = packet.getAddress();

                        String data = new String(buffer, 0, packet.getLength());
                        Log.i(LOG_TAG, "Packet received from " + address + " with contents: " + data);
                        String action = data.substring(0, 4);
                        if (action.equals("ACC:")) {

                            stopPlayingTone();

                            showToast(getString(R.string.call_accpeted));

                            Log.d(LOG_TAG, "video call accepted");

//                                Send Introduce and Listen to incoming value
                            sendFrameIntroduceData();
//                            startFrameIntroduceAcceptedListener();

                        } else if (action.equals("REJ:")) {

                            stopPlayingTone();

                            showToast(getString(R.string.call_rejected));
                            // Reject notification received. End call
                            Log.d(LOG_TAG, "Ending call...");
                            endCall();
                        } else if (action.equals("END:")) {

                            stopPlayingTone();

                            showToast(getString(R.string.call_ended));
                            Log.d(LOG_TAG, "Ending call...");
                            endCall();
                        } else if (data.startsWith("{")) {

                            introListener(data);
                        } else {
                            Log.w(LOG_TAG, address + " [else] : [FRAME]");
                            udpFrameListener();
                        }
                    } catch (SocketTimeoutException e) {

                        Log.i(LOG_TAG, "No reply from contact. Ending call");

                        stopPlayingTone();

                        endCall();

                    } catch (IOException e) {
                        Log.e(LOG_TAG, e.toString());
                        e.printStackTrace();

                        stopPlayingTone();

                        endCall();
                    }
                }
                Log.i(LOG_TAG, "Listener ending");

                mSenderSocket.disconnect();
                mSenderSocket.close();

                Log.d(LOG_TAG, "Listener socket dc & close");
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
            mListenerSocket = new DatagramSocket(G.VIDEOCALL_LISTENER_PORT);
            mListenerSocket.setSoTimeout(10 * 1000);
        } catch (SocketException e) {
            e.printStackTrace();
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

                    mVideoPacket = new DatagramPacket(buf, buf.length, address, G.VIDEOCALL_SENDER_PORT);

                    mSenderSocket.send(mVideoPacket);

//                        writer.write(frameJSONData.getBytes());

                    Logger.d(LOG_TAG, "SendFrameIntroduce", frameJSONData);

                    byteSent += frameJSONData.getBytes().length;

//                    Logger.d(LOG_TAG, "bytes sent: ", byteSent + "");

//                        socket.close();

                } catch (IOException e1) {
                    e1.printStackTrace();
                }
            }
        }).start();
    }

    private void sendMessage(final String message, final int port) {
        // Creates a thread used for sending notifications
        Thread replyThread = new Thread(new Runnable() {

            @Override
            public void run() {

                try {

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

//    private void sendFrameDataTCP(final byte[] data) {
//
//        //        Logger.d(LOG_TAG, "Frame rate is: ", parameters.getPreviewFrameRate() + "");
//
//        new Thread(new Runnable() {
//            @Override
//            public void run() {
//
//                try {
//                    int byteSent;
//
//                    OutputStream os = socket_sendFrameData.getOutputStream();
//
//                    if (socket_sendFrameData.isConnected()) {
//
//                        Logger.d(LOG_TAG, "sendFrameData ", "connected");
//
//                        byte[] d = new byte[data.length + 1];
//                        for (int i = 0; i < data.length; i++) {
//                            d[i] = data[i];
//                        }
//                        d[data.length] = '\n';
//                        os.write(d);
//
//                        byteSent = frameData.length;
//
////                        Logger.d(LOG_TAG, "frame length sent: ", byteSent + "");
//                    }
//
//                } catch (IOException e) {
//                    e.printStackTrace();
//
//                    closeSendFrameSocket();
//                }
//            }
//        }).start();
//    }

    private void makeVideoCall() {
        // Send a request to start a call
        sendMessage("VIDEOCALL" + displayName, G.BROADCAST_PORT);

    }

    private void endCall() {
        // Ends the chat sessions
        Log.d(LOG_TAG, "end call");

        stopPlayingTone();
        startPlayingBusyTone();

        LISTEN = false;
        receiving = false;

        if (audioCall != null)
            audioCall.endCall();

        sendMessage("END:", G.VIDEOCALL_SENDER_PORT);

        closeSockets();

        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                releaseCamera();
            }
        });

        finish();
    }

    private void closeSockets() {

//        closeSendFrameSocket();

        mSenderSocket.disconnect();
        mSenderSocket.close();

        mListenerSocket.disconnect();
        mListenerSocket.close();


    }

    private void udpFrameListener() {
        receiving = true;
        Logger.d(LOG_TAG, "udpFrameListener", "Receiving Thread Start");
        try {
            final byte[] buff = new byte[mReceiveFrameBuffSize * 10];
            Logger.d(LOG_TAG, "udpFrameListener", "mFrameBuffSize >> " + mReceiveFrameBuffSize);
//                    datagramSocket.setSoTimeout(10000);
            DatagramPacket packet = new DatagramPacket(buff, buff.length);
            while (receiving) {

                mListenerSocket.setSoTimeout(2 * 1000);// 2 seconds to receive next frame, else, it will close
                mListenerSocket.receive(packet);

                Logger.d(LOG_TAG, "udpReceived", "buff.size()" + buff.length);
                previewBitmap(buff, packet.getLength());

            }
            mListenerSocket.disconnect();
            mListenerSocket.close();
        } catch (SocketException e) {
//            Logger.e(LOG_TAG, "udpFrameListener", "SocketException");
            endCall();
            LISTEN = false;
//            e.printStackTrace();
        } catch (IOException e) {
            Logger.e(LOG_TAG, "udpFrameListener", "IOException");
            LISTEN = false;
            e.printStackTrace();
            endCall();
        }
    }

    private void introListener(String data) {
        try {

            JSONObject jsonObject = new JSONObject(data);
            int mReceiveFrameWidth = jsonObject.getInt("Width");
            int mReceiveFrameHeight = jsonObject.getInt("Height");
            mReceiveFrameBuffSize = jsonObject.getInt("Size");
            Logger.d(LOG_TAG, "introListener", "mFrameBuffSize >> " + mReceiveFrameBuffSize);

            Logger.d(LOG_TAG, "introListener", "Received Data " +
                    ">> width =" + mReceiveFrameWidth +
                    " height = " + mReceiveFrameHeight +
                    " buffSize = " + mReceiveFrameBuffSize);
            if (mReceiveFrameHeight != 0 && mReceiveFrameWidth != 0 && mReceiveFrameBuffSize != 0) {

                shouldSendVideo = true;

                audioCall = new AudioCall(address);
                audioCall.startCall();

                Logger.d(LOG_TAG, "introListener", "OK Sent");
            } else {
                Logger.d("ReceiveVideoCallActivity", "introListener", "mIsFrameReceived is false");
                finish();
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    private void stopListener() {

        Log.d(LOG_TAG, "stopping listener");
        LISTEN = false;

        mListenerSocket.disconnect();
        mListenerSocket.close();
    }

    private void sendFrameDataUDP() {

        new Thread(new Runnable() {
            @Override
            public void run() {

                try {
//                    Logger.d(LOG_TAG, "address: ", address + "");

                    DatagramSocket socket = new DatagramSocket();
                    mVideoPacket = new DatagramPacket(frameData, frameData.length, address, G.VIDEOCALL_SENDER_PORT);

                    Logger.d(LOG_TAG, "frame length sent: ", frameData.length + "");

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
            jsonObject.put("Width", mSenderWidth);
            jsonObject.put("Height", mSenderHeight);
            jsonObject.put("Size", mSendFrameBuffSize);
        } catch (JSONException e) {
            e.printStackTrace();
        }

        return jsonObject.toString();
    }

    Camera.PreviewCallback previewCb = new Camera.PreviewCallback() {
        public void onPreviewFrame(byte[] data, Camera camera) {

            if (data != null && data.length != 0) {

                parameters = camera.getParameters();
                mSenderHeight = parameters.getPreviewSize().height;
                mSenderWidth = parameters.getPreviewSize().width;

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
                Toast.makeText(MakeVideoCallActivity.this, message, Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    public void onClick(View v) {
        endCall();
    }
}

