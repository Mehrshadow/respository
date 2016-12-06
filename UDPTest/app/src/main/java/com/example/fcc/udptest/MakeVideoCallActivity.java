package com.example.fcc.udptest;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.hardware.Camera;
import android.media.CamcorderProfile;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.Button;
import android.widget.MediaController;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.VideoView;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;

public class MakeVideoCallActivity extends Activity implements SurfaceHolder.Callback, View.OnClickListener {

    private static final String LOG_TAG = "UDP:MakeVideoCall";
    private static final int SLEEP_TIME = 250;
    private String contactIp;
    private String displayName;
    private String contactName;
    private MediaRecorder mediaRecorder;
    private SurfaceHolder surfaceHolder;
    private VideoView videoView;
    private boolean recording = false;
    private boolean LISTEN = true;
    private boolean IN_CALL = false;
    private static final int BUF_SIZE = 1024;
    private final static int port_Call = 50004;
    private final static int BROADCAST_PORT = 50005;
    private final static int port_VideoCall = 60000;
    private Camera camera;
    private boolean receiving = false;
    private Button buttonEndCall;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_make_video_call);

        Log.d(LOG_TAG, "Make video call started");

//        initViewVideo();

        Intent intent = getIntent();
        displayName = intent.getStringExtra(MainActivity.EXTRA_DISPLAYNAME);
        contactName = intent.getStringExtra(MainActivity.EXTRA_CONTACT);
        contactIp = intent.getStringExtra(MainActivity.EXTRA_IP);

        TextView textView = (TextView) findViewById(R.id.contactName);
        textView.setText("Calling: " + contactName);

        buttonEndCall = (Button) findViewById(R.id.buttonEndCall);
        buttonEndCall.setOnClickListener(this);

        mediaRecorder = new MediaRecorder();
        initRecorder();

        startListener();
        makeVideoCall();
    }

    private void initViewVideo() {
        videoView = (VideoView) findViewById(R.id.videoView);
        videoView.setVideoURI(Uri.parse(G.ReceiveVideoPath));

        MediaController mc = new MediaController(this);
        videoView.setMediaController(mc);

        Log.d(LOG_TAG, "VideoView initialized");
    }

    private void initRecorder() {
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
                    Toast.makeText(MakeVideoCallActivity.this, "NO Camera Device found!!", Toast.LENGTH_LONG).show();
                }
            });
        }

        mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        mediaRecorder.setVideoSource(MediaRecorder.VideoSource.DEFAULT);
        CamcorderProfile cpHigh = CamcorderProfile.get(CamcorderProfile.QUALITY_LOW);
        mediaRecorder.setProfile(cpHigh);
        mediaRecorder.setOutputFile(G.sendVideoPath);

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
                    socket.setSoTimeout(15000);
                    byte[] buffer = new byte[BUF_SIZE];
                    DatagramPacket packet = new DatagramPacket(buffer, BUF_SIZE);

                    while (LISTEN) {

                        try {

                            Log.i(LOG_TAG, "Listening for packets");
                            socket.receive(packet);
                            String data = new String(buffer, 0, packet.getLength());
                            Log.i(LOG_TAG, "Packet received from " + packet.getAddress() + " with contents: " + data);
                            String action = data.substring(0, 4);
                            if (action.equals("ACC:")) {

//                                Accept notification received. Start VideoCall
                                sendVideo(packet.getAddress());
//                                receiveVideo();

                                IN_CALL = true;

                            } else if (action.equals("REJ:")) {
                                // Reject notification received. End call
                                Log.d(LOG_TAG, "Ending call...");
                                endCall();
                            } else if (action.equals("END:")) {
                                Log.d(LOG_TAG, "Ending call...");
                                // End call notification received. End call
                                endCall();
                            } else {
                                // Invalid notification received
                                Log.w(LOG_TAG, packet.getAddress() + " sent invalid message: " + data);
                            }
                        } catch (SocketTimeoutException e) {
                            if (!IN_CALL) {

                                Log.i(LOG_TAG, "No reply from contact. Ending call");
                                endCall();
                            }
                        } catch (IOException e) {
                            Log.e(LOG_TAG, e.toString());
                            endCall();
                        }
                    }
                    Log.i(LOG_TAG, "Listener ending");

                    socket.disconnect();
                    socket.close();
                    return;
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
        receiving = true;

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    DatagramSocket socket = new DatagramSocket(port_VideoCall);
                    socket.setSoTimeout(10000);
                    byte[] buffer = new byte[BUF_SIZE];

                    DatagramPacket packet = new DatagramPacket(buffer, BUF_SIZE);

                    videoView.start();

                    while (receiving) {
                        Log.d(LOG_TAG, "Listening for video packets");
                        socket.receive(packet);
                        Log.i(LOG_TAG, "Packet received from " + packet.getAddress());

                        saveReceivedVideoBytesToFile(buffer);// inja dare file overwrite mishe..!
                    }

                    videoView.stopPlayback();
                    socket.disconnect();
                    socket.close();
                    receiving = false;

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
        sendMessage("CAL:" + displayName, port_Call);
    }

    private void endCall() {
        // Ends the chat sessions
        Log.d(LOG_TAG, "end call");
        stopListener();
        if (IN_CALL) {
            recording = false;
            mediaRecorder.stop();
            mediaRecorder.release();

        }
        sendMessage("END:", BROADCAST_PORT);
        if (camera != null) {
//            camera.stopPreview();
            camera.release();
        }
//        finish();
    }

    private void stopListener() {
        // Ends the listener thread
        Log.d(LOG_TAG, "stopping listener");
        LISTEN = false;
    }

    private void sendVideo(final InetAddress address) {
        Log.d(LOG_TAG, "send video thread started...");

        recording = true;

        new Thread(new Runnable() {
            @Override
            public void run() {

                int bytes_read;
                int bytes_sent = 0;
                byte[] buf;

                try {

                    DatagramSocket socket = new DatagramSocket();
                    mediaRecorder.start();

                    Log.d(LOG_TAG, "Recording started");

                    File file = new File(G.sendVideoPath);
                    buf = new byte[(int) file.length()];

                    while (recording) {

                        BufferedInputStream bis = new BufferedInputStream(new FileInputStream(file));

                        bytes_read = bis.read(buf, 0, buf.length);

                        if (bytes_read != -1) {

                            Log.d(LOG_TAG, "** bytes_Read = " + bytes_read + " buffer size = " + buf.length + " **");
                            DatagramPacket packet = new DatagramPacket(buf, bytes_read, address, port_VideoCall);
                            socket.send(packet);
                            bytes_sent += bytes_read;

                            Log.i(LOG_TAG, "Total bytes sent: " + bytes_sent);
                            Thread.sleep(SLEEP_TIME, 0);

                        } else {
                            Log.d(LOG_TAG, "End of file reached");
                        }
                    }

                    Log.d(LOG_TAG, "final value: " + bytes_sent / 1000);

                    mediaRecorder.stop();
                    mediaRecorder.release();

                    socket.disconnect();
                    socket.close();

                    recording = false;

                } catch (FileNotFoundException e) {
                    Log.e(LOG_TAG, e.toString());
                    e.printStackTrace();
                    endCall();
                } catch (SocketException e) {
                    e.printStackTrace();
                } catch (IOException e) {
                    e.printStackTrace();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
//                } catch (InterruptedException e) {
//                    e.printStackTrace();
//                    recording = false;

            }
        }
        ).start();
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
        Log.d(LOG_TAG, "surface destroyed");
        if (recording) {
            mediaRecorder.stop();
            recording = false;
        }
        mediaRecorder.release();
    }

    private void prepareRecorder() {
        Log.d(LOG_TAG, "preparing recorder");
        mediaRecorder.setPreviewDisplay(surfaceHolder.getSurface());

        try {
            mediaRecorder.prepare();
        } catch (IOException e) {
            e.printStackTrace();
        }
        Log.d(LOG_TAG, "recorder prepare done");
    }

    private void saveReceivedVideoBytesToFile(byte[] data) {
        Log.d(LOG_TAG, "saving bytes to file");

        BufferedOutputStream bos = null;
        try {
            bos = new BufferedOutputStream(new FileOutputStream(G.ReceiveVideoPath));

            bos.write(data);
            bos.flush();
            bos.close();

        } catch (FileNotFoundException e) {
            e.printStackTrace();
            Log.d(LOG_TAG, "saving to file failed!!");
        } catch (IOException e) {
            e.printStackTrace();
            Log.d(LOG_TAG, "saving to file failed!!");
        }
    }

    @Override
    public void onClick(View v) {
        endCall();
    }
}

