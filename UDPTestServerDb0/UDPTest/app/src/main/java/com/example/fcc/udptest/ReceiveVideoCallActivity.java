package com.example.fcc.udptest;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.hardware.Camera;
import android.media.CamcorderProfile;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
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

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.Socket;
import java.net.SocketException;
import java.net.UnknownHostException;

public class ReceiveVideoCallActivity extends AppCompatActivity implements View.OnClickListener, SurfaceHolder.Callback {

    private final static String LOG_TAG = "UDP:ReceiveVideoCall";
    private static final int SLEEP_TIME = 100;
    private String contactIp;
    private String displayName;
    private final static int port_Call = 50004;
    private final static int BROADCAST_PORT = 50005;
    private final static int port_VideoCall = 60000;
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
        displayName = intent.getStringExtra(G.EXTRA_C_Name);
        contactIp = intent.getStringExtra(G.EXTRA_C_Ip);

        TextView textView = (TextView) findViewById(R.id.textViewIncomingCall);
        textView.setText("Incoming call: " + displayName);

//        initViewVideo();

        mediaRecorder = new MediaRecorder();

        startListener();

        receiveVideo();
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

                    Log.i(LOG_TAG, "Listener started!");
                    DatagramSocket socket = new DatagramSocket(BROADCAST_PORT);
                    socket.setSoTimeout(5000);
                    byte[] buffer = new byte[BUF_SIZE];
                    DatagramPacket packet = new DatagramPacket(buffer, BUF_SIZE);

                    while (LISTEN) {

                        try {

                            Log.i(LOG_TAG, "Listening for packets");
                            socket.receive(packet);
                            String data = new String(buffer, 0, packet.getLength());
                            Log.i(LOG_TAG, "Packet received from " + packet.getAddress() + " with contents: " + data);
                            String action = data.substring(0, 4);
                            if (action.equals("END:")) {
                                endCall();

                            } else {
                                // Invalid notification received
                                Log.w(LOG_TAG, packet.getAddress() + " sent invalid message: " + data);
                            }

                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                    Log.i(LOG_TAG, "Listener ending");
                    socket.disconnect();
                    socket.close();
                    return;
                } catch (SocketException e) {

                    Log.e(LOG_TAG, "SocketException in Listener");
                    endCall();
                }
            }
        });
        listenThread.start();
    }

    private void makeVideoCall() {
        // Send a request to start a call
        sendMessage("ACC:" + displayName, port_Call);
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
        Log.d(LOG_TAG, "send video thread started...");

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

                        Log.d(LOG_TAG, "Recording started");

                        while (recording) {
                            bytes_read = fis.read(buf, 0, BUF_SIZE);
                            DatagramPacket packet = new DatagramPacket(buf, bytes_read, address, port_VideoCall);
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
        Log.d(LOG_TAG, "**********Receive video");

        if (!receiving) {
            receiving = true;

//            createReceiveVideoFileOrRecreateExiting();

            new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
//                        DatagramSocket socket = new DatagramSocket(port_VideoCall);
//                        socket.setSoTimeout(5000);

                        Thread.sleep(2000);

                        while (receiving) {

                        Log.d(LOG_TAG, "***********trying to connect to server");
                        InetAddress address = InetAddress.getByName(contactIp);

                        Socket socket = new Socket(address, port_VideoCall);

                        if (socket.isConnected()) {
                            Log.d(LOG_TAG, "************Receiver socket connected");
                        }

//                        byte[] buffer = new byte[BUF_SIZE];
//                        int bytesSaved = 0;
//
//                        videoView.start();

//                        Log.d(LOG_TAG, "Listening for video packets");

//                            DatagramPacket packet = new DatagramPacket(buffer, BUF_SIZE);
//                            socket.receive(packet);
//                            Log.i(LOG_TAG, "Packet received from " + packet.getAddress());

//                            saveReceivedVideoBytesToFile(packet.getData());

//                            playReceivedVideo(socket, packet.getLength());
//
//                            bytesSaved += buffer.length;
//
//                            Log.d(LOG_TAG, bytesSaved + " bytes saved");
                        }

//                        videoView.stopPlayback();
//                        socket.disconnect();
//                        socket.close();
                        receiving = false;

                    } catch (SocketException e) {
                        receiving = false;
                        e.printStackTrace();
                    } catch (IOException e) {
                        receiving = false;
                        e.printStackTrace();
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }).start();
        }
    }

    private void stopListener() {
        Log.d(LOG_TAG, "Stopping listener");
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
        sendMessage("END:", BROADCAST_PORT);

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
                sendMessage("ACC:", BROADCAST_PORT);

                InetAddress address = null;
                try {
                    address = InetAddress.getByName(contactIp);

                    Log.i(LOG_TAG, "Calling " + address.toString());
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
                sendMessage("REJ:", BROADCAST_PORT);
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

}
