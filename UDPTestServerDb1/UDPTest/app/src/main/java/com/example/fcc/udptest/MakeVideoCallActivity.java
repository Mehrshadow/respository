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
import android.renderscript.Allocation;
import android.renderscript.Element;
import android.renderscript.RenderScript;
import android.renderscript.ScriptIntrinsicYuvToRGB;
import android.renderscript.Type;
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

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
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

public class MakeVideoCallActivity extends Activity implements View.OnClickListener {

    private static final String LOG_TAG = "MakeVideoCall";

    private String contactIp;
    private String displayName;
    private String contactName;
    private static final int BUF_SIZE = 1024;
    private Camera camera = null;
    private Button buttonEndCall;
    private DatagramSocket socket;
    private DatagramPacket packet;
    private CameraPreview cameraPreview;
    private int mFrameWidth = 100, mFrameHeight = 200, mFrameLength;
    private byte[] frameData;
    private boolean isSending = false;
    private FrameLayout cameraView;
    private InetAddress address;
    private boolean shouldSendVideo = false;
    private Socket socket_sendFrameData;
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

    Camera.PreviewCallback previewCb = new Camera.PreviewCallback() {
        public void onPreviewFrame(byte[] data, Camera camera) {

            final Bitmap bitmap = getBitmap(data);

            runOnUiThread(new Runnable() {

                @Override
                public void run() {
                    ((ImageView) findViewById(R.id.img)).setImageBitmap(bitmap);
                }
            });

//
            /*if (shouldSendVideo) {

                Logger.d(LOG_TAG, "OnPreviewFrame", "sending frames started...");

                sendFrameData();
            }*/
        }
    };

    private Bitmap getBitmap(byte[] data) {
        YuvImage yuv = new YuvImage(data, parameters.getPreviewFormat(), mFrameWidth, mFrameHeight, null);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        yuv.compressToJpeg(new Rect(0, 0, mFrameWidth, mFrameHeight), 50, out);

        byte[] bytes = out.toByteArray();
        final Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);

        return bitmap;
    }



    /*byte[] resizeImage(byte[] input, int width, int height) {

        Bitmap original = previewBitmap(input);

//        Bitmap original = BitmapFactory.decodeByteArray(input, 0, input.length);

        Bitmap resized = Bitmap.createScaledBitmap(original, width / 10, height / 10, false);

        ByteArrayOutputStream os = new ByteArrayOutputStream();
        resized.compress(Bitmap.CompressFormat.JPEG, 50, os);

        mFrameWidth = resized.getWidth();
        mFrameHeight = resized.getHeight();

        frameData = os.toByteArray();/** data is ready to send

        mFrameLength = frameData.length;

        return os.toByteArray();
    }*/

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

        startListener();
        makeVideoCall();
    }

    @Override
    protected void onStop() {
        super.onStop();

        stopListener();
    }

    /*private void initSurfaceView() {
        SurfaceView cameraView = (SurfaceView) findViewById(R.id.surfaceView);
        surfaceHolder = cameraView.getHolder();
        surfaceHolder.addCallback(this);
        surfaceHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);

        Log.d(LOG_TAG, "surface holder done");
    }*/

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
                            address = packet.getAddress();
                            Log.i(LOG_TAG, "Packet received from " + address + " with contents: " + data);
                            String action = data.substring(0, 4);
                            if (action.equals("ACC:")) {

                                Log.d(LOG_TAG, "video call accepted");

//                                Send Introduce and Listen to incoming value
                                sendFrameIntroduceData();
                                startFrameIntroduceAcceptedListener();

                            } else if (action.equals("REJ:")) {
                                // Reject notification received. End call
                                Log.d(LOG_TAG, "Ending call...");
                                endCall();
                            } else if (action.equals("END:")) {
                                Log.d(LOG_TAG, "Ending call...");
                                endCall();
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

                    socket.disconnect();
                    socket.close();
                    Log.d(LOG_TAG, "Listener socket dc & close");
                } catch (SocketException e) {

                    Log.e(LOG_TAG, e.toString());
                    e.printStackTrace();
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

        closeSendFrameSocket();

        releaseCamera();
    }

    private void stopListener() {

        Log.d(LOG_TAG, "stopping listener");
        LISTEN = false;
    }

    private void sendFrameIntroduceData() {

        // Creates the thread for sending frames
        isSending = true;

        new Thread(new Runnable() {
            @Override
            public void run() {

                try {
                    int byteSent = 0;

                    String frameGSONData = getFrameGSONData();

                    Socket socket = new Socket(address, G.INTRODUCE_PORT);
                    socket.setSoTimeout(2 * 1000);

                    OutputStream writer = socket.getOutputStream();
//                    DataOutputStream writer = (DataOutputStream) socket.getOutputStream();

                    if (socket.isConnected()) {

                        writer.write(frameGSONData.getBytes());

                        Logger.d(LOG_TAG, "SendFrameIntroduce", frameGSONData.toString());

                        byteSent += frameGSONData.getBytes().length;

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

//                    DataInputStream is = (DataInputStream) socket.getInputStream();
                    BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                    String receivedValue = reader.readLine();

                    Logger.d(LOG_TAG, "startFrameIntroduceAcceptedListener", "ReceivedValue: " + receivedValue);

                    socket.close();
                    serverSocket.close();

                    if (receivedValue.equals("OK")) {

                        // Client is ready to receive the frames... so send it!

                        initSendFrameSocket();

                        shouldSendVideo = true;
                    }

                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }).start();
    }

    private void initSendFrameSocket() {
        try {
            if (socket_sendFrameData == null) {
                socket_sendFrameData = new Socket(address, G.VIDEO_CALL_PORT);
//                socket_sendFrameData.setSoTimeout(5 * 1000);
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

    private void sendFrameData() {

        isSending = true;

//        Logger.d(LOG_TAG, "Frame rate is: ", parameters.getPreviewFrameRate() + "");

        new Thread(new Runnable() {
            @Override
            public void run() {

                try {
                    int byteSent = 0;

                    OutputStream os = socket_sendFrameData.getOutputStream();

                    if (socket_sendFrameData.isConnected()) {

                        os.write(frameData);

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

    private String getFrameGSONData() {
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

    private Bitmap previewBitmap(byte[] data) {
        Logger.d("ReceiveVideoCallActivity", "previewBitmap", "Start");

        final Bitmap bitmap = Bitmap.createBitmap(mFrameWidth, mFrameHeight, Bitmap.Config.ARGB_8888);

        Allocation bmData = renderScriptNV21ToRGBA888(
                getApplicationContext(),
                mFrameWidth,
                mFrameHeight,
                data);
        bmData.copyTo(bitmap);

        runOnUiThread(new Runnable() {
            @Override
            public void run() {
//                ImageView img = (ImageView) findViewById(R.id.img);
//                img.setImageBitmap(bitmap);
            }
        });


        return bitmap;
    }

    public Allocation renderScriptNV21ToRGBA888(Context context, int width, int height, byte[] nv21) {
        RenderScript rs = RenderScript.create(context);
        ScriptIntrinsicYuvToRGB yuvToRgbIntrinsic = ScriptIntrinsicYuvToRGB.create(rs, Element.U8_4(rs));

        Type.Builder yuvType = new Type.Builder(rs, Element.U8(rs)).setX(nv21.length);
        Allocation in = Allocation.createTyped(rs, yuvType.create(), Allocation.USAGE_SCRIPT);

        Type.Builder rgbaType = new Type.Builder(rs, Element.RGBA_8888(rs)).setX(width).setY(height);
        Allocation out = Allocation.createTyped(rs, rgbaType.create(), Allocation.USAGE_SCRIPT);

        in.copyFrom(nv21);

        yuvToRgbIntrinsic.setInput(in);
        yuvToRgbIntrinsic.forEach(out);
        return out;
    }

    @Override
    public void onClick(View v) {
        endCall();
    }
}

