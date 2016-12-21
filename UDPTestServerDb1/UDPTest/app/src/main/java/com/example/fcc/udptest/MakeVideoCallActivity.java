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
import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;
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

public class MakeVideoCallActivity extends Activity implements View.OnClickListener {

    private static final String LOG_TAG = "MakeVideoCall";

    private String contactIp;
    private String displayName;
    private String contactName;
    private static final int BUF_SIZE = 1024;
    private Camera camera = null;
    private Button buttonEndCall;
    private DatagramSocket mSenderSocket;
    private DatagramSocket mListenerSocket;
    private DatagramPacket mVideoPacket;
    private CameraPreview cameraPreview;
    private int mFrameWidth, mFrameHeight, mFrameLength;
    private byte[] frameData;
    private boolean isSending = false;
    private FrameLayout cameraView;
    private InetAddress address;
    private boolean shouldSendVideo = false;
    private Socket socket_sendFrameData;
    private Camera.Parameters parameters;
    private boolean LISTEN = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_make_video_call);

        Log.d(LOG_TAG, "Make video call started");

        Intent intent = getIntent();
        displayName = intent.getStringExtra(G.EXTRA_DISPLAYNAME);
        contactName = intent.getStringExtra(G.EXTRA_C_Name);
        contactIp = intent.getStringExtra(G.EXTRA_C_Ip);

        TextView textView = (TextView) findViewById(R.id.contactName);
        textView.setText("Calling: " + contactName);

        buttonEndCall = (Button) findViewById(R.id.buttonEndCall);
        buttonEndCall.setOnClickListener(this);

        cameraView = (FrameLayout) findViewById(R.id.cameraView);

        openCamera();

        startCameraPreview();
    }

    private void releaseCamera() {
        if (camera != null) {
            camera.setPreviewCallback(null);
            camera.release();
            camera = null;
        }
    }

    private Bitmap getBitmap(byte[] data) {
        YuvImage yuv = new YuvImage(data, parameters.getPreviewFormat(), mFrameWidth, mFrameHeight, null);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        yuv.compressToJpeg(new Rect(0, 0, mFrameWidth, mFrameHeight), 50, out);

        byte[] bytes = out.toByteArray();
        final Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);

        final Bitmap resizedBitmap = Bitmap.createScaledBitmap(bitmap, mFrameWidth / 2, mFrameHeight / 2, true);

        frameData = bytes;
        mFrameLength = frameData.length;
//        Logger.d(LOG_TAG, "getBitmap", "mFrameLength: " + mFrameLength);

        return resizedBitmap;
    }

    private void showBitmap(final Bitmap bitmap) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                ImageView img = (ImageView) findViewById(R.id.img);
                img.setRotation(-90);
                img.setImageBitmap(bitmap);
            }
        });

    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            releaseCamera();
        }
        return super.onKeyDown(keyCode, event);
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

    private static Camera getCameraInstance() {
        Camera c = null;

        try {
            c = Camera.open(1);
        } catch (Exception e) {
            Log.e(LOG_TAG, e.toString());
            e.printStackTrace();
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
//                        mListenerSocket.setSoTimeout(10 * 1000);
                        String data = new String(buffer, 0, packet.getLength());
                        address = packet.getAddress();
                        Log.i(LOG_TAG, "Packet received from " + address + " with contents: " + data);
                        String action = data.substring(0, 4);
                        if (action.equals("ACC:")) {

                            Log.d(LOG_TAG, "video call accepted");

//                                Send Introduce and Listen to incoming value
                            sendFrameIntroduceData();
//                            startFrameIntroduceAcceptedListener();

                        } else if (action.equals("REJ:")) {
                            // Reject notification received. End call
                            Log.d(LOG_TAG, "Ending call...");
                            endCall();
                        } else if (action.equals("END:")) {
                            Log.d(LOG_TAG, "Ending call...");
                            endCall();
                        } else if (action.equals("OKK:")) {
                            Logger.d(LOG_TAG, "startListener", "OK received");
                            shouldSendVideo = true;
                        } else {
                            Log.w(LOG_TAG, address + " sent invalid message: " + data);
                        }
                    } catch (SocketTimeoutException e) {

                        Log.i(LOG_TAG, "No reply from contact. Ending call");
                        endCall();

                    } catch (IOException e) {
                        Log.e(LOG_TAG, e.toString());
                        e.printStackTrace();
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
            mListenerSocket = new DatagramSocket(G.VIDEO_CALL_PORT);
        } catch (SocketException e) {
            e.printStackTrace();
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
        sendMessage("CAL:" + displayName, G.VIDEOCALL_SENDER_PORT);

    }

    private void endCall() {
        // Ends the chat sessions
        Log.d(LOG_TAG, "end call");

        sendMessage("END:", G.BROADCAST_PORT);

        finish();
    }

    private void stopSendingFrames() {
        isSending = false;

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
        isSending = true;

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

                    Logger.d(LOG_TAG, "bytes sent: ", byteSent + "");

//                        socket.close();

                    isSending = false;
                } catch (IOException e1) {
                    e1.printStackTrace();
                }
            }
        }).start();
    }

    /*private void startFrameIntroduceAcceptedListener() {
        new Thread(new Runnable() {

//            ServerSocket serverSocket;
//            Socket socket;

            @Override
            public void run() {
                try {

                    byte[] buf = new byte[1024];
//                    serverSocket = new ServerSocket(G.INTRODUCE_PORT);
                    mVideoPacket = new DatagramPacket(buf, buf.length);
                    mListenerSocket.receive(mVideoPacket);
//                    socket = serverSocket.accept();

//                    BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
//                    String receivedValue = reader.readLine();

//                    Logger.d(LOG_TAG, "startFrameIntroduceAcceptedListener", "ReceivedValue: " + receivedValue);
                    Logger.d(LOG_TAG, "startFrameIntroduceAcceptedListener", "ReceivedValue: " + buf.toString());

//                    socket.close();
//                    serverSocket.close();

//                    if (receivedValue.equals("OK")) {
                        if (buf.toString().equals("OK")) {

//                         Client is ready to receive the frames... so send it!
//                        initSendFrameSocket();

                        shouldSendVideo = true;
                    }

                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }).start();
    }*/

    private void initSendFrameSocket() {
        try {
            if (socket_sendFrameData == null) {
                socket_sendFrameData = new Socket(address, G.VIDEO_CALL_PORT);
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
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

        cameraPreview = new CameraPreview(MakeVideoCallActivity.this, camera, previewCb);
        cameraView.addView(cameraPreview);
    }

    private void sendFrameData(final byte[] data) {

        isSending = true;

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

                        Logger.d(LOG_TAG, "frame length sent: ", byteSent + "");
                    }

                    isSending = false;

                } catch (IOException e) {
                    e.printStackTrace();
                    isSending = false;

                    closeSendFrameSocket();
                }
            }
        }).start();
    }

    private void sendFrameDataUDP() {

        isSending = true;

        new Thread(new Runnable() {
            @Override
            public void run() {

                try {
                    Logger.d(LOG_TAG, "address: ", address + "");

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
            jsonObject.put("Width", mFrameWidth);
            jsonObject.put("Height", mFrameHeight);
            jsonObject.put("Size", mFrameLength);
        } catch (JSONException e) {
            e.printStackTrace();
        }

        return jsonObject.toString();
    }

    Camera.PreviewCallback previewCb = new Camera.PreviewCallback() {
        public void onPreviewFrame(byte[] data, Camera camera) {

            if (data != null && data.length != 0) {
                frameData = data;
                parameters = camera.getParameters();
                mFrameHeight = parameters.getPreviewSize().height;
                mFrameWidth = parameters.getPreviewSize().width;

                showBitmap(getBitmap(frameData));

            }

            if (shouldSendVideo) {

//                sendFrameData(frameData);
                sendFrameDataUDP();
            }
        }
    };

    @Override
    public void onClick(View v) {
        endCall();
    }

}

