package com.example.fcc.udptest;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.hardware.Camera;
import android.media.CamcorderProfile;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.renderscript.Allocation;
import android.renderscript.Element;
import android.renderscript.RenderScript;
import android.renderscript.ScriptIntrinsicYuvToRGB;
import android.renderscript.Type;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.Button;
import android.widget.MediaController;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.VideoView;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.UnknownHostException;

import classes.Logger;

import static android.R.attr.data;

public class ReceiveVideoCallActivity extends AppCompatActivity implements View.OnClickListener, SurfaceHolder.Callback {

    private final static String LOG_TAG = "UDP:ReceiveVideoCall";
    private static final int SLEEP_TIME = 100;
    private String contactIp;
    private String displayName;
    private final static int port_Call = 50004;
    private boolean IN_VIDEO_CALL = false;
    private boolean LISTEN = false;
    private boolean recording = false;
    private boolean IN_CALL = false;
    private boolean receiving = false;
    private Button accept, reject, endCall;
    private int BUF_SIZE = 1024;
    private MediaRecorder mediaRecorder;
    private SurfaceHolder surfaceHolder;
    private Camera camera;
    private VideoView videoView;
    private BufferedReader bufferedReader;
    private int mFrameWidth;
    private int mFrameHeight;
    private int mFrameBuffSize;
    private boolean mIsFrameReceived = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_receive_video_call);

        Log.d(LOG_TAG, "Receive video call created");

        accept = (Button) findViewById(R.id.buttonAccept);
        reject = (Button) findViewById(R.id.buttonReject);
        endCall = (Button) findViewById(R.id.buttonEndCall);
        accept.setOnClickListener(this);
        reject.setOnClickListener(this);
        endCall.setOnClickListener(this);

        Intent intent = getIntent();
        displayName = intent.getStringExtra(MainActivity.EXTRA_CONTACT);
        contactIp = intent.getStringExtra(MainActivity.EXTRA_IP);

        TextView textView = (TextView) findViewById(R.id.textViewIncomingCall);
        textView.setText("Incoming call: " + displayName);

//        initViewVideo();

        //mediaRecorder = new MediaRecorder();

        startListener();

        //receiveVideo();
    }

    private void initViewVideo() {

        videoView = (VideoView) findViewById(R.id.videoView);

        createReceiveVideoFileOrRecreateExiting();

        videoView.setVideoURI(Uri.parse(G.ReceiveVideoPath));

        MediaController mc = new MediaController(this);
        videoView.setMediaController(mc);

        Log.d(LOG_TAG, "video view initialized");
    }

    private void createReceiveVideoFileOrRecreateExiting() {
        try {
            File f = new File(G.ReceiveVideoPath);
            if (f.exists()) {

                f.delete();
                f.createNewFile();

            } else {

                f.createNewFile();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void prepareRecorder() {
        SurfaceView cameraView = (SurfaceView) findViewById(R.id.surfaceView);
        surfaceHolder = cameraView.getHolder();
        surfaceHolder.addCallback(this);
        surfaceHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);

        Log.d(LOG_TAG, "surface holder done");

        if (checkCameraHardware(this)) {
            try {
                camera = Camera.open(1);// 1 = front camera, 0 = rear camera}
                camera.unlock();
                mediaRecorder.setCamera(camera);
            } catch (Exception e) {
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

        mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        mediaRecorder.setVideoSource(MediaRecorder.VideoSource.DEFAULT);

        CamcorderProfile cpHigh = CamcorderProfile.get(CamcorderProfile.QUALITY_LOW);
        mediaRecorder.setProfile(cpHigh);
        mediaRecorder.setOutputFile(G.sendVideoPath);
//        mediaRecorder.setMaxDuration();
//        mediaRecorder.setMaxFileSize();

        Log.d(LOG_TAG, "media recorder done");
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
                    Logger.d("ReceiveVideoCallActivity", "startListener", "Listener started!");
                    DatagramSocket socket = new DatagramSocket(G.VIDEOCALL_LISTENER_PORT);
                    socket.setSoTimeout(5000);
                    byte[] buffer = new byte[BUF_SIZE];
                    DatagramPacket packet = new DatagramPacket(buffer, BUF_SIZE);

                    while (LISTEN) {

                        try {
                            Logger.d("ReceiveVideoCallActivity", "startListener", "Listening for packets");

                            socket.receive(packet);
                            String data = new String(buffer, 0, packet.getLength());
                            Logger.d("ReceiveVideoCallActivity", "startListener", "Packet received from " + packet.getAddress() + " with contents: " + data);

                            String action = data.substring(0, 4);
                            if (action.equals("END:")) {
                                endCall();

                            } else {
                                // Invalid notification received
                                Logger.d("ReceiveVideoCallActivity", "startListener", packet.getAddress() + " sent invalid message: " + data);

                            }

                        } catch (IOException e) {
                            Logger.e("ReceiveVideoCallActivity", "IOException", "IOException");
                            e.printStackTrace();
                        }
                    }
                    Logger.d("ReceiveVideoCallActivity", "startListener", "Listener ending");

                    socket.disconnect();
                    socket.close();
                    return;
                } catch (SocketException e) {

                    Logger.e("ReceiveVideoCallActivity", "startListener", "SocketException in Listener");
                    e.printStackTrace();
                    endCall();
                }
            }
        });
        listenThread.start();
    }

    private void makeVideoCall() {
        // Send a request to start a call
        sendMessage("ACC:" + displayName, G.VIDEO_CALL_PORT);
    }

    private void sendMessage(final String message, final int port) {
        // Creates a thread for sending notifications
        Thread replyThread = new Thread(new Runnable() {

            @Override
            public void run() {

                Log.d(LOG_TAG, "sending message " + message);
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

    private void sendVideo(final InetAddress address) {
        Logger.d("ReceiveVideoCallActivity", "sendVideo", "send video thread started...");


        if (!recording) {
            recording = true;

            new Thread(new Runnable() {
                @Override
                public void run() {

                    int bytes_read;
                    int bytes_sent = 0;
                    byte[] buf = new byte[BUF_SIZE];

                    try {
                        FileInputStream fis = new FileInputStream(G.sendVideoPath);
                        DatagramSocket socket = new DatagramSocket();
                        mediaRecorder.start();

                        Logger.d("ReceiveVideoCallActivity", "sendVideo", "Recording started");


                        while (recording) {
                            bytes_read = fis.read(buf, 0, BUF_SIZE);
                            DatagramPacket packet = new DatagramPacket(buf, bytes_read, address, G.VIDEO_CALL_PORT);
                            socket.send(packet);
                            bytes_sent += bytes_read;

                            Log.i(LOG_TAG, "Total bytes sent: " + bytes_sent);
//                            Thread.sleep(SLEEP_TIME, 0);
                        }

                        mediaRecorder.stop();
                        mediaRecorder.release();

                        socket.disconnect();
                        socket.close();

                        recording = false;

                    } catch (FileNotFoundException e) {
                        e.printStackTrace();
                        recording = false;
                    } catch (IOException e) {
                        e.printStackTrace();
                        recording = false;
//                    } catch (InterruptedException e) {
//                        e.printStackTrace();
//                        recording = false;
                    }

                }
            }).start();
        }
    }

    private void receiveVideo() {
        Logger.d("ReceiveVideoCallActivity", "receiveVideo", "**********Receive video");

        if (!receiving) {
            receiving = true;
            new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        ServerSocket serverSocket = new ServerSocket(G.VIDEO_CALL_PORT);

                        //socket.setSoTimeout(5000);

                        while (receiving) {
                            Socket socket = serverSocket.accept();
                            if (socket.isConnected()) {
                                byte[] buff = new byte[mFrameBuffSize];
                                InputStream inputStream = socket.getInputStream();
                                int count = inputStream.read(buff);
                                Logger.d("ReceiveVideoCallActivity", "receiveVideo", "buff size >> " + count);
                                previewBitmap(buff);
                            }
                        }
                    } catch (SocketException e) {
                        receiving = false;
                        e.printStackTrace();
                    } catch (IOException e) {
                        receiving = false;
                        e.printStackTrace();
                    }
                }
            }).start();
        }
    }

    private void stopListener() {
        Logger.d("ReceiveVideoCallActivity", "stopListener", "Stopping listener");

        // Ends the listener thread
        LISTEN = false;
    }

    private void endCall() {
        Log.d(LOG_TAG, "end call");
        // Ends the chat sessions
        stopListener();
//        if (camera != null) {
////            camera.stopPreview();
//            camera.release();
//        }

        if (IN_VIDEO_CALL) {
            receiving = false;
//            mediaRecorder.stop();
//            mediaRecorder.release();
        }
        sendMessage("END:", G.BROADCAST_PORT);

        finish();
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        prepareRecorder();
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {

    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        if (recording) {
            mediaRecorder.stop();
            recording = false;
        }
        mediaRecorder.release();
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.buttonAccept:
                sendMessage("ACC:", G.BROADCAST_PORT);
                introListener();
                receiveVideo();
                InetAddress address = null;
                try {
                    address = InetAddress.getByName(contactIp);

                    Logger.d("ReceiveVideoCallActivity", "onClick", "Calling " + address.toString());
                    IN_VIDEO_CALL = true;

//                    sendVideo(address);
//                    receiveVideo();


                    // Hide the buttons as they're not longer required
                    accept.setVisibility(View.INVISIBLE);
                    reject.setVisibility(View.INVISIBLE);
                    endCall.setVisibility(View.VISIBLE);

                } catch (UnknownHostException e) {
                    e.printStackTrace();
                }
                break;
            case R.id.buttonReject:
                sendMessage("REJ:", G.BROADCAST_PORT);
                endCall();
                break;
            case R.id.buttonEndCall:
                endCall();
                break;
        }
    }

    private void saveReceivedVideoBytesToFile(byte[] data) {
        Log.d(LOG_TAG, "saving to file...");
        try {
            File f = new File(G.ReceiveVideoPath);
            BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(f, true));

//            FileOutputStream os = new FileOutputStream(G.ReceiveVideoPath, true);
            bos.write(data);
            bos.close();

        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void playReceivedVideo(DatagramSocket socket, int packetLength) {
        try {
            MediaPlayer mediaPlayer = new MediaPlayer();
            ParcelFileDescriptor pfd = ParcelFileDescriptor.fromDatagramSocket(socket);
            Log.d(LOG_TAG, "parcel size: " + pfd.getStatSize());

            mediaPlayer.setDataSource(pfd.getFileDescriptor(), 0, packetLength);

            mediaPlayer.setDisplay(surfaceHolder);
//            mediaPlayer.prepare();
            mediaPlayer.prepareAsync();
            mediaPlayer.start();
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    private void introListener() {
        Logger.d("ReceiveVideoCallActivity", "introListener", "Start");

        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                Socket socket = null;
                try {
                    ServerSocket serverSocket = new ServerSocket(G.INTRODUCE_PORT);
                    Logger.d("ReceiveVideoCallActivity", "introListener", "Listening . . .");
                    socket = serverSocket.accept();
                    Logger.d("ReceiveVideoCallActivity", "introListener", "Socket Connected");
                    socket.setSoTimeout(2000);
                    bufferedReader = new BufferedReader(new InputStreamReader(socket.getInputStream(), "UTF-8"));
                    String read = bufferedReader.readLine();
                    JSONObject jsonObject = new JSONObject(read);
                    mFrameWidth = jsonObject.getInt("Width");
                    mFrameHeight = jsonObject.getInt("Height");
                    mFrameBuffSize = jsonObject.getInt("Size");
                    Logger.d("ReceiveVideoCallActivity", "introListener", "Received Data " +
                            ">> width =" + mFrameWidth +
                            " height = " + mFrameHeight +
                            " buffSize = " + mFrameBuffSize);
                    socket.close();

                    if (mFrameHeight != 0) {
                        sendACC();
                        Logger.d("ReceiveVideoCallActivity", "introListener", "mIsFrameReceived is true Sent ACC");
                    } else {
                        Logger.d("ReceiveVideoCallActivity", "introListener", "mIsFrameReceived is false");
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
        });
        thread.start();

    }

    private void sendACC() {
        Logger.d("ReceiveVideoCallActivity", "sendACC", "Start");

        try {
            JSONObject jsonObject = new JSONObject();


            jsonObject.put("ACC", "OK");
            String data = "OK";
            InetAddress address = InetAddress.getByName(contactIp);
            Logger.d("ReceiveVideoCallActivity", "sendACC", "Try To Connect");
            Socket socket = new Socket(address, G.INTRODUCE_PORT);
            Logger.d("ReceiveVideoCallActivity", "sendACC", "Connected To >> " + address + " : " + G.INTRODUCE_PORT);
            socket.setSoTimeout(2000);
            DataOutputStream outputStream = new DataOutputStream(socket.getOutputStream());


            outputStream.write(data.getBytes());
            outputStream.flush();
            socket.close();
            Logger.d("ReceiveVideoCallActivity", "sendACC", " ACC Sent To >> " + address + " : " + G.INTRODUCE_PORT);
        } catch (UnknownHostException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
            Logger.d("ReceiveVideoCallActivity", "sendACC", "Socket Ex >> " + e.toString());
        } catch (JSONException e) {
            e.printStackTrace();
        }


    }

    private void previewBitmap(byte[] data) {
        Logger.d("ReceiveVideoCallActivity", "previewBitmap", "Start");

        Bitmap bitmap = Bitmap.createBitmap(mFrameWidth, mFrameHeight, Bitmap.Config.ARGB_8888);

        Allocation bmData = renderScriptNV21ToRGBA888(
                getApplicationContext(),
                mFrameWidth,
                mFrameHeight,
                data);
        bmData.copyTo(bitmap);

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

}
