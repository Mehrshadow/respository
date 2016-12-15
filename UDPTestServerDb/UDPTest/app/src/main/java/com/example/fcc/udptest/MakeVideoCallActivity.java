package com.example.fcc.udptest;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.hardware.Camera;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.util.Log;
import android.view.KeyEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.Button;
import android.widget.MediaController;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.VideoView;

import org.json.JSONException;
import org.json.JSONObject;

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
    private MediaRecorder mediaRecorder;
    private SurfaceHolder surfaceHolder;
    private VideoView videoView;
    private static final int BUF_SIZE = 1024;
    private Camera camera = null;
    private Button buttonEndCall;
    private ParcelFileDescriptor writeFD;
    private DatagramSocket socket;
    private DatagramPacket packet;
    private CameraPreview cameraPreview;
    private int frameWidth, frameHeight, frameLength;
    private byte[] frameData;
    private boolean isSending = false;

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

        camera = getCameraInstance();

        cameraPreview = new CameraPreview(MakeVideoCallActivity.this, camera, mediaRecorder, previewCb);

        mediaRecorder = new MediaRecorder();

        initSurfaceView();

    }

    private void initViewVideo() {
        videoView = (VideoView) findViewById(R.id.videoView);
        videoView.setVideoURI(Uri.parse(G.ReceiveVideoPath));

        MediaController mc = new MediaController(this);
        videoView.setMediaController(mc);

        Log.d(LOG_TAG, "VideoView initialized");
    }

    private void releaseCamera() {
        if (camera != null) {
            camera.setPreviewCallback(null);
            camera.release();
            camera = null;
        }
    }

    Camera.PreviewCallback previewCb = new Camera.PreviewCallback() {
        public void onPreviewFrame(byte[] data, Camera camera) {

            Camera.Parameters parameters = camera.getParameters();
            int format = parameters.getPreviewFormat();

            //YUV formats require more conversion
            if (format == ImageFormat.NV21 /*|| format == ImageFormat.YUY2 || format == ImageFormat.NV16*/) {
                frameWidth = parameters.getPreviewSize().width;
                frameHeight = parameters.getPreviewSize().height;
                // Get the YuV image
                YuvImage yuv_image = new YuvImage(data, format, frameWidth, frameHeight, null);

                // Compress and Convert YuV to Jpeg
                compress_YUVImage(yuv_image);

            }
        }
    };

    private void compress_YUVImage(YuvImage yuvImage) {
        Rect rect = new Rect(0, 0, frameWidth, frameHeight);
        ByteArrayOutputStream output_stream = new ByteArrayOutputStream();
        yuvImage.compressToJpeg(rect, 50, output_stream);
        frameData = output_stream.toByteArray();/** data is ready to send */
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            releaseCamera();
            stopRecorder();
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    protected void onResume() {
        super.onResume();

        openCamera();

        startListener();
        makeVideoCall();
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
            c = Camera.open();
        } catch (Exception e) {
            Log.e(LOG_TAG, e.toString());
        }

        return c;
    }

    private void openCamera() {

        if (checkCameraHardware(this)) {
            try {

                camera = getCameraInstance();

                mediaRecorder.setCamera(camera);
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

    private void stopRecorder() {
        try {
            releaseCamera();

            writeFD.close();
            mediaRecorder.stop();
            mediaRecorder.release();
        } catch (Exception e) {
            Log.e(LOG_TAG, "Error in Stop Recorder");
            e.printStackTrace();
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

                try {

                    Log.i(LOG_TAG, "Listener started!");

                    socket = new DatagramSocket(G.BROADCAST_PORT);
                    socket.setSoTimeout(10000);
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
                                sendFrameIntoduceData(socket);
                                startFrameIntroduceAcceptedListener();

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

        stopRecorder();

        stopListener();

        sendMessage("END:", G.BROADCAST_PORT);


        finish();
    }

    private void stopListener() {


        Log.d(LOG_TAG, "stopping listener");
        LISTEN = false;
    }

    private void sendFrameIntoduceData(final DatagramSocket datagramSocket) {

        // Creates the thread for sending frames
        isSending = true;

        new Thread(new Runnable() {
            @Override
            public void run() {

                try {
                    int byteSent = 0;

                    String frameGSONData = getFrameGSONData();

                    Socket socket = new Socket(datagramSocket.getInetAddress(), G.INTRODUCE_PORT);
                    socket.setSoTimeout(10 * 1000);

                    DataOutputStream writer = (DataOutputStream) socket.getOutputStream();

                    if (socket.isConnected()) {

                        writer.writeBytes(frameGSONData);

                        byteSent += frameLength;

                        Logger.d(LOG_TAG, "frame length sent: ", byteSent + "");

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

    private void startFrameIntroduceAcceptedListener() {
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

                    String recievedValue = new String(buf);
                    Logger.d(LOG_TAG, "startFrameIntroduceAcceptedListener", "ReceivedValue: " + recievedValue);

                    socket.close();
                    serverSocket.close();

                    if (recievedValue.equals("OK")) {
                        sendFrameData();
                    }

                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }).start();
    }

    private void sendFrameData() {

        isSending = true;

        new Thread(new Runnable() {
            @Override
            public void run() {

                try {
                    int byteSent = 0;

                    String frameGSONData = getFrameGSONData();

                    Socket socket = new Socket(datagramSocket.getInetAddress(), G.INTRODUCE_PORT);
                    socket.setSoTimeout(10 * 1000);

                    DataOutputStream writer = (DataOutputStream) socket.getOutputStream();

                    if (socket.isConnected()) {

                        writer.writeBytes(frameGSONData);

                        byteSent += frameLength;

                        Logger.d(LOG_TAG, "frame length sent: ", byteSent + "");

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

    private String getFrameGSONData() {
        JSONObject jsonObject = new JSONObject();
        try {
            jsonObject.put("Width", frameWidth);
            jsonObject.put("Height", frameHeight);
            jsonObject.put("Size", frameLength);
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
        Log.d(LOG_TAG, "surface destroyed");
        try {

//            if (recording) {
////                mediaRecorder.stop();
//                recording = false;
//            }

            mediaRecorder.release();
        } catch (Exception e) {
            Log.e(LOG_TAG, "Surface destroyed");
            e.printStackTrace();
        }
    }

    @Override
    public void onClick(View v) {
        endCall();
    }
}

