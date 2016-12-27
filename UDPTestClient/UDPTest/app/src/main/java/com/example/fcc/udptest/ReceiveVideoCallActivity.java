package com.example.fcc.udptest;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.hardware.Camera;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.Vibrator;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
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
import java.net.UnknownHostException;

import classes.CameraPreview;
import classes.Logger;

public class ReceiveVideoCallActivity extends AppCompatActivity implements View.OnClickListener {

    private final static String LOG_TAG = "ReceiveVideoCall";
    private String contactIp;
    private String displayName;
    private boolean IN_VIDEO_CALL = false;
    private boolean LISTEN = false;
    private Button accept, reject, endCall;
    private ImageView mImgReceive;
    private int BUF_SIZE = 1024;
    private int mFrameBuffSize;
    DatagramSocket mReceiveSocket;
    DatagramSocket mSendSocket;
    byte[] buffer;
    private int mFrameWidth, mFrameHeight;
    private boolean receiving = false;
    private FrameLayout cameraView;
    private Camera camera = null;
    private CameraPreview cameraPreview;
    private byte[] frameData;
    private Camera.Parameters parameters;
    private boolean shouldSendVideo = false;
    private InetAddress address;
    private DatagramPacket mVideoPacket;
    AudioManager audioManager;
    private AudioCall call;
    private Vibrator mVibrator;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_receive_video_call);


        audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        Log.d(LOG_TAG, "Receive video call created");


        Intent intent = getIntent();
        displayName = intent.getStringExtra(G.EXTRA_C_Name);
        contactIp = intent.getStringExtra(G.EXTRA_C_Ip);
        try {
            address = InetAddress.getByName(contactIp);
        } catch (UnknownHostException e) {
            e.printStackTrace();
        }

        initView();

        openCamera();

        startCameraPreview();

    }

    @Override
    protected void onResume() {
        super.onResume();
        startSockets();
        parsePacket();
        startVibrator();
        turnOnScreen();

    }

    private void turnOnScreen(){
        WindowManager.LayoutParams params = getWindow().getAttributes();
        params.screenBrightness = 1;
        getWindow().setAttributes(params);
    }

    private void initView() {

        accept = (Button) findViewById(R.id.buttonAccept);
        reject = (Button) findViewById(R.id.buttonReject);
        endCall = (Button) findViewById(R.id.buttonEndCall);
        mImgReceive = (ImageView) findViewById(R.id.img_receive);
        accept.setOnClickListener(this);
        reject.setOnClickListener(this);
        endCall.setOnClickListener(this);

        TextView textView = (TextView) findViewById(R.id.textViewIncomingCall);
        textView.setText("Incoming call: " + displayName);

        cameraView = (FrameLayout) findViewById(R.id.cameraView);

    }

    @Override
    protected void onStop() {
        super.onStop();
//        showToast(getString(R.string.call_ended));

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

    private static Camera getCameraInstance() {
        Camera c = null;

        try {
            c = Camera.open(G.BACK_CAMERA
            );
        } catch (Exception e) {
            Log.e(LOG_TAG, e.toString());
            e.printStackTrace();
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

    private void startSockets() {
        Logger.d("ReceiveVideoCallActivity", "socketStart", "started!");

        try {
            mReceiveSocket = new DatagramSocket(G.RECEIVEVIDEO_PORT);
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
                            shouldSendVideo = true;
                            call = new AudioCall(address);
                            call.startCall();
                            //previewBitmap(buffer);
                            Logger.d("ReceiveVideoCallActivity", "startListener", packet.getAddress() + " sent invalid message: " + data);
                            udpFrameListener();
                        }

                    } catch (IOException e) {
                        receiving = false;
                        LISTEN = false;
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

        showToast(getString(R.string.call_ended));

        stopVibrator();

        if (call != null)
            call.endCall();
        Log.d(LOG_TAG, "end call");
        // Ends the chat sessions
        stopListener();

        if (IN_VIDEO_CALL) {
            receiving = false;

            stopCameraPreview();
        }

        sendMessage("END:", G.SENDVIDEO_PORT);

        closeSockets();

        finish();
    }
    private void closeSockets(){
        mReceiveSocket.disconnect();
        mReceiveSocket.close();
        mSendSocket.disconnect();
        mSendSocket.close();
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.buttonAccept:
                stopVibrator();
                sendMessage("ACC:", G.SENDVIDEO_PORT);
                //sendFrameIntroduceData();


                //\\\\\\\\\\
                //call = new AudioCall(address);
                // call.startCall();
                //\\\\\\\\\\




               /* introReceveid = true;

                while (introReceveid) {

                    Logger.d("ReceiveVideoCallActivity", "onClick", "mFrameBuffSize >> " + mFrameBuffSize);
                }
                receiveVideo();*/


                Logger.d(LOG_TAG, "onClick", "Calling " + address.toString());
                IN_VIDEO_CALL = true;

//                    sendVideo(address);
//                    receiveVideo();


                // Hide the buttons as they're not longer required
                accept.setVisibility(View.GONE);
                reject.setVisibility(View.GONE);
                endCall.setVisibility(View.VISIBLE);


                break;
            case R.id.buttonReject:
                sendMessage("REJ:", G.SENDVIDEO_PORT);
                endCall();
                break;
            case R.id.buttonEndCall:
                endCall();
                break;
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
            jsonObject.put("Width", mFrameWidth);
            jsonObject.put("Height", mFrameHeight);
            jsonObject.put("Size", mFrameBuffSize);
        } catch (JSONException e) {
            e.printStackTrace();
        }

        return jsonObject.toString();
    }

    private void introListener(String data) {
        try {

            JSONObject jsonObject = new JSONObject(data);
            mFrameWidth = jsonObject.getInt("Width");
            mFrameHeight = jsonObject.getInt("Height");
            mFrameBuffSize = jsonObject.getInt("Size");
            Logger.d(LOG_TAG, "introListener", "mFrameBuffSize >> " + mFrameBuffSize);

            Logger.d(LOG_TAG, "introListener", "Received Data " +
                    ">> width =" + mFrameWidth +
                    " height = " + mFrameHeight +
                    " buffSize = " + mFrameBuffSize);
            if (mFrameHeight != 0 && mFrameWidth != 0 && mFrameBuffSize != 0) {
                sendFrameIntroduceData();
//                udpFrameListener();

                Logger.d(LOG_TAG, "introListener", "mIsFrameReceived is true Sent ACC");
            } else {
                // finish();
                Logger.d(LOG_TAG, "introListener", "mIsFrameReceived is false");
                Logger.d(LOG_TAG, "introListener", "finish Activity");
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    private void sendACC() {
        Logger.d(LOG_TAG, "sendACC", "Start");

        try {
            String message = "OKK:";
            byte[] data = message.getBytes();
            DatagramPacket packet = new DatagramPacket(data, data.length, address, G.SENDVIDEO_PORT);
            mSendSocket.send(packet);


        } catch (UnknownHostException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private byte[] compressCameraData(byte[] data) {

        Logger.d(LOG_TAG, "compressCameraData", "actual camera size: " + data.length);

        YuvImage yuv = new YuvImage(data, parameters.getPreviewFormat(), mFrameWidth, mFrameHeight, null);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        yuv.compressToJpeg(new Rect(0, 0, mFrameWidth, mFrameHeight), 20, out);

        Logger.d(LOG_TAG, "compressCameraData", "compressed size: " + out.toByteArray().length);

        mFrameBuffSize = out.toByteArray().length;

        return out.toByteArray();
    }

    private void previewBitmap(final byte[] data) {
        Logger.d(LOG_TAG, "previewBitmap", "Start");
        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                final Bitmap bitmap = BitmapFactory.decodeByteArray(data, 0, data.length);
                final Bitmap resizeBitMap = Bitmap.createScaledBitmap(bitmap, mFrameWidth, mFrameHeight, true);

                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        mImgReceive.setImageBitmap(resizeBitMap);
                    }
                });
            }
        });
        thread.start();


    }

    private void udpFrameListener() {
        receiving = true;
        Logger.d(LOG_TAG, "udpFrameListener", "Start");
        Logger.d(LOG_TAG, "udpFrameListener", " Thread Start");
        try {
            final byte[] buff = new byte[mFrameBuffSize * 10];
            Logger.d(LOG_TAG, "udpFrameListener", "mFrameBuffSize >> " + mFrameBuffSize);
//                    datagramSocket.setSoTimeout(10000);
            DatagramPacket packet = new DatagramPacket(buff, mFrameBuffSize);
            mReceiveSocket.setSoTimeout(1 * 1000);// 5 seconds to receive next frame, else, it will close

            while (receiving) {

                mReceiveSocket.receive(packet);

                Logger.d(LOG_TAG, "udpFrameListener", "buff.size()" + buff.length);
                previewBitmap(buff);
            }
            showToast(getString(R.string.call_ended));

            mReceiveSocket.disconnect();
            mReceiveSocket.close();
        } catch (SocketException e) {
            Logger.e(LOG_TAG, "udpFrameListener", "SocketException");
            showToast(getString(R.string.call_ended));
            endCall();
            LISTEN = false;
            e.printStackTrace();
        } catch (IOException e) {
            showToast(getString(R.string.call_ended));
            Logger.e(LOG_TAG, "udpFrameListener", "IOException");
            LISTEN = false;
            e.printStackTrace();
            endCall();
        }
    }

    private Bitmap getBitmap(byte[] data) {
        YuvImage yuv = new YuvImage(data, parameters.getPreviewFormat(), mFrameWidth, mFrameHeight, null);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        yuv.compressToJpeg(new Rect(0, 0, mFrameWidth, mFrameHeight), 20, out);

        byte[] bytes = out.toByteArray();
        final Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);

        final Bitmap resizedBitmap = Bitmap.createScaledBitmap(bitmap, mFrameWidth / 2, mFrameHeight / 2, true);

        frameData = bytes;
        mFrameBuffSize = frameData.length;
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

    private void releaseCamera() {
        if (camera != null) {
            camera.setPreviewCallback(null);
            camera.release();
            camera = null;
        }
    }

    private void stopCameraPreview() {

        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                releaseCamera();
            }
        });
    }

    private void sendFrameDataUDP() {

        new Thread(new Runnable() {
            @Override
            public void run() {

                try {
                    Logger.d(LOG_TAG, "address: ", address + "");

                    DatagramSocket socket = new DatagramSocket();
                    mVideoPacket = new DatagramPacket(frameData, frameData.length, address, G.SENDVIDEO_PORT);

                    Logger.d(LOG_TAG, "frame length sent: ", frameData.length + "");

                    socket.send(mVideoPacket);

                } catch (IOException e) {
                    releaseCamera();
                    e.printStackTrace();
                }
            }
        }).start();
    }

    private void showToast(final String message) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(getApplicationContext(), message, Toast.LENGTH_SHORT).show();
            }
        });
    }

    Camera.PreviewCallback previewCb = new Camera.PreviewCallback() {
        public void onPreviewFrame(byte[] data, Camera camera) {

            if (data != null && data.length != 0) {
                //frameData = data;
                parameters = camera.getParameters();
                mFrameHeight = parameters.getPreviewSize().height;
                mFrameWidth = parameters.getPreviewSize().width;
                frameData = compressCameraData(data);

//                showBitmap(getBitmap(frameData));

            }

            if (shouldSendVideo) {

//                sendFrameDataTCP(frameData);
                sendFrameDataUDP();
            }
        }
    };

    private void startVibrator() {
        mVibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        long[] patern = {0, 500, 1000};
        mVibrator.vibrate(patern, 0);
    }

    private void stopVibrator() {
        mVibrator.cancel();
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
