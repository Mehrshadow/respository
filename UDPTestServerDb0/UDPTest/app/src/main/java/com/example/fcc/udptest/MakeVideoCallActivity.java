package com.example.fcc.udptest;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.hardware.Camera;
import android.os.Bundle;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;

import classes.CameraPreview;
import classes.Logger;

import static com.example.fcc.udptest.ContactManager.LISTEN;

public class MakeVideoCallActivity extends Activity implements SurfaceHolder.Callback, View.OnClickListener {

    private static final String LOG_TAG = "MakeVideoCall";

    private String contactIp;
    private String displayName;
    private String contactName;
    private SurfaceHolder surfaceHolder;
    private static final int BUF_SIZE = 1024;
    private Camera mCamera = null;
    private Button buttonEndCall;
    private DatagramSocket socket;
    private DatagramPacket packet;
    private CameraPreview cameraPreview;
    private int mFrameWidth, mFrameHeight, mFrameLength;
    private byte[] frameData;
    private boolean isSending = false;
    private Camera.Parameters parameters;

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

        initSurfaceView();
    }

    private void releaseCamera() {
        if (mCamera != null) {
            mCamera.setPreviewCallback(null);
            mCamera.release();
            mCamera = null;
        }
    }

    Camera.PreviewCallback previewCb = new Camera.PreviewCallback() {
        public void onPreviewFrame(byte[] data, Camera camera) {

            parameters = camera.getParameters();

            int format = parameters.getPreviewFormat();

            //YUV formats require more conversion
            if (format == ImageFormat.NV21 /*|| format == ImageFormat.YUY2 || format == ImageFormat.NV16*/) {
                mFrameWidth = parameters.getPreviewSize().width;
                mFrameHeight = parameters.getPreviewSize().height;
                // Get the YuV image
                YuvImage yuv_image = new YuvImage(data, format, mFrameWidth, mFrameHeight, null);

                // Compress and Convert YuV to Jpeg
                compress_YUVImage(yuv_image);

            }
        }
    };

    private void compress_YUVImage(YuvImage yuvImage) {
        Rect rect = new Rect(0, 0, mFrameWidth, mFrameHeight);
        ByteArrayOutputStream output_stream = new ByteArrayOutputStream();
        yuvImage.compressToJpeg(rect, 50, output_stream);
        frameData = output_stream.toByteArray();/** data is ready to send */
    }


    @Override
    public void onBackPressed() {
        super.onBackPressed();

        endCall();
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (hasCameraHardware(this)) {

            mCamera = getCameraInstance();

            startListener();
            makeVideoCall();
        } else {

            sendMessage("END:", G.BROADCAST_PORT);

            finish();
        }
    }

    @Override
    protected void onStop() {
        super.onStop();

        stopListener();
    }

    private void initSurfaceView() {
        SurfaceView cameraView = (SurfaceView) findViewById(R.id.surfaceView);
        surfaceHolder = cameraView.getHolder();
        surfaceHolder.addCallback(this);
        surfaceHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);

        Log.d(LOG_TAG, "surface holder done");
    }

    private static Camera getCameraInstance() {
        Camera c = null;

        try {
            c = Camera.open(1);
        } catch (Exception e) {
            Log.e(LOG_TAG, e.toString());
        }

        return c;
    }

    private void openCamera() {
        mCamera = getCameraInstance();
    }

    private boolean hasCameraHardware(Context context) {
        if (context.getPackageManager().hasSystemFeature(
                PackageManager.FEATURE_CAMERA)) {
            // this device has a camera
            Log.d(LOG_TAG, "device has camera");
            return true;
        } else {
            // no camera on this device
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(MakeVideoCallActivity.this, "NO Camera Device found!!", Toast.LENGTH_LONG).show();
                }
            });
            return false;
        }
    }

    private void startListener() {
        // Create listener thread
        LISTEN = true;
        Thread listenThread = new Thread(new Runnable() {

            @Override
            public void run() {

                try {

                    Log.i(LOG_TAG, "Listener started!");

                    socket = new DatagramSocket(G.BROADCAST_PORT);
                    socket.setSoTimeout(10 * 1000);
                    byte[] buffer = new byte[BUF_SIZE];
                    packet = new DatagramPacket(buffer, BUF_SIZE);

                    while (LISTEN) {
                        try {

                            Log.i(LOG_TAG, "Listening for packets");
                            socket.receive(packet);
                            String data = new String(buffer, 0, packet.getLength());
                            Log.i(LOG_TAG, "Packet received from " + packet.getAddress() + " with contents: " + data);
                            String action = data.substring(0, 4);
                            if (action.equals("ACC:")) {

                                Log.d(LOG_TAG, "video call accepted");

//                                Send Introduce and Listen to incoming value
                                sendFrameIntroduceData(socket);
                                listenForIntroduceAccept();

                            } else if (action.equals("REJ:")) {
                                // Reject notification received. End call
                                Log.d(LOG_TAG, "Ending call...");
                                endCall();
                            } else if (action.equals("END:")) {
                                Log.d(LOG_TAG, "Ending call...");
                                endCall();
                            } else {
                                Log.w(LOG_TAG, packet.getAddress() + " sent invalid message: " + data);
                            }
                        } catch (SocketTimeoutException e) {

                            Log.i(LOG_TAG, "No reply from contact. Ending call");
                            endCall();


                        } catch (IOException e) {
                            Log.e(LOG_TAG, e.toString());
                            endCall();
                        }
                    }
                    Log.i(LOG_TAG, "Listener ending");

                    socket.disconnect();
                    socket.close();
                    Log.d(LOG_TAG, "Listener socket dc & close");
                } catch (SocketException e) {

                    Log.e(LOG_TAG, e.toString());
                    endCall();
                }
            }
        });
        listenThread.start();
    }

    private void receiveVideo() {
        Log.d(LOG_TAG, "Receiving video data");
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

    private void makeVideoCall() {
        // Send a request to start a call
        sendMessage("CAL:" + displayName, G.BROADCAST_PORT);
    }

    private void endCall() {
        // Ends the chat sessions
        Log.d(LOG_TAG, "end call");

        stopListener();

        StopSendingFrames();

        sendMessage("END:", G.BROADCAST_PORT);

        finish();
    }

    private void StopSendingFrames() {
        isSending = false;

        releaseCamera();
    }

    private void stopListener() {


        Log.d(LOG_TAG, "stopping listener");
        LISTEN = false;
    }

    private void sendFrameIntroduceData(final DatagramSocket datagramSocket) {

        // Creates the thread for sending frames
        isSending = true;

        new Thread(new Runnable() {
            @Override
            public void run() {

                try {
                    int byteSent = 0;

                    String frameJSONData = getFrameJSONData();

                    Socket socket = new Socket(datagramSocket.getInetAddress(), G.INTRODUCE_PORT);
                    socket.setSoTimeout(2 * 1000);

                    DataOutputStream writer = (DataOutputStream) socket.getOutputStream();

                    if (socket.isConnected()) {

                        writer.writeBytes(frameJSONData);

                        byteSent += frameJSONData.getBytes().length;

                        Logger.d(LOG_TAG, "bytes sent: ", byteSent + "");

                        socket.close();

                        isSending = false;
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                    isSending = false;
                    socket.close();
                }
            }
        }).start();
    }

    private void listenForIntroduceAccept() {
        new Thread(new Runnable() {

            ServerSocket serverSocket;
            Socket socket;

            @Override
            public void run() {
                try {
                    serverSocket = new ServerSocket(G.INTRODUCE_PORT);

                    socket = serverSocket.accept();
                    socket.setSoTimeout(10 * 1000);

                    byte[] buf = new byte[1024];
                    int bytesRead;

                    DataInputStream is = (DataInputStream) socket.getInputStream();

                    for (int i = 0; i < buf.length; i++) {

                        bytesRead = is.read(buf, 0, buf.length);
                        Logger.d(LOG_TAG, "startFrameIntroduceAcceptedListener", "Introduce bytes read: " + bytesRead);
                    }

                    String receivedValue = new String(buf);
                    Logger.d(LOG_TAG, "startFrameIntroduceAcceptedListener", "ReceivedValue: " + receivedValue);

                    socket.close();
                    serverSocket.close();

                    if (receivedValue.equals("OK")) {

                        startCameraPreview();

                        // Client is ready to receive the frames... so send theme!
                        sendFrameData(socket.getInetAddress());
                    }

                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }).start();
    }

    private void startCameraPreview() {
        mCamera = getCameraInstance();

        cameraPreview = new CameraPreview(MakeVideoCallActivity.this, mCamera, previewCb);
    }

    private void sendFrameData(final InetAddress inetAddress) {

        isSending = true;

        Logger.d(LOG_TAG, "Frame rate is: ", parameters.getPreviewFrameRate() + "");

        new Thread(new Runnable() {
            @Override
            public void run() {

                try {
                    int byteSent = 0;

                    Socket socket = new Socket(inetAddress, G.VIDEO_CALL_PORT);
                    socket.setSoTimeout(5 * 1000);

                    BufferedOutputStream os = (BufferedOutputStream) socket.getOutputStream();

                    if (socket.isConnected()) {
                        while (isSending) {

                            os.write(frameData);

                            byteSent += mFrameLength;

                            Logger.d(LOG_TAG, "frame length sent: ", byteSent + "");
                        }
                    }

                    socket.close();
                    isSending = false;

                } catch (IOException e) {
                    e.printStackTrace();
                    isSending = false;
                    socket.close();
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

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        Log.e(LOG_TAG, "Surface destroyed");
    }

    @Override
    public void onClick(View v) {
        endCall();
    }
}

