package com.example.fcc.udptest;

import android.app.Activity;
import android.content.Intent;
import android.hardware.Camera;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.MediaController;
import android.widget.TextView;
import android.widget.VideoView;

import java.io.BufferedOutputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;

public class MakeVideoCallActivity extends Activity implements View.OnClickListener {

    private static final String LOG_TAG = "UDP:MakeVideoCall";
    private String contactIp;
    private String displayName;
    private String contactName;
    private MediaRecorder mediaRecorder;
    private VideoView videoView;
    private boolean recording = false;
    private boolean LISTEN = true;
    private boolean IN_CALL = false;
    private boolean cameraPrepared = true;
    private static int BUF_SIZE = 1024;
    private static int BUF_SIZE_CAMERA = 1024;
    private final static int port_Call = 50004;
    private final static int BROADCAST_PORT = 50005;
    private final static int port_VideoCall = 60000;
    private Camera camera = null;
    private boolean receiving = false;
    private Button buttonEndCall;
    private ParcelFileDescriptor writeFD;
    private Socket socket;
    private CameraPreview cameraPreview;
    private byte[] cameraData;

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

        camera = getCameraInstance();

        cameraPreview = new CameraPreview(MakeVideoCallActivity.this, camera, mediaRecorder, previewCb);

        initCameraView();

        camera.startPreview();

    }

    private void startMediaRecorder() {
        try {
            mediaRecorder.prepare();
            mediaRecorder.start();

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void stopMediaRecorder() {
        if (mediaRecorder != null) {
//            mediaRecorder.stop();
//            mediaRecorder.release();
        }
    }

    Camera.PreviewCallback previewCb = new Camera.PreviewCallback() {
        public void onPreviewFrame(byte[] data, Camera camera) {
            if (cameraPrepared) {
                Log.d(LOG_TAG, "******We made a video call*****");
                startListener();
                makeVideoCall();
                cameraPrepared = false;
            }
            cameraData = data;
            BUF_SIZE_CAMERA = data.length;
            Log.d(LOG_TAG, BUF_SIZE_CAMERA + "");
        }
    };

    public static Camera getCameraInstance() {
        Camera c = null;
        try {
            c = Camera.open(1);
        } catch (Exception e) {
        }
        return c;
    }

    private void releaseCamera() {
        if (camera != null) {
//            previewing = false;
            camera.setPreviewCallback(null);
            camera.release();
            camera = null;
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            releaseCamera();
            stopMediaRecorder();
        }
        return super.onKeyDown(keyCode, event);
    }

    private void initCameraView() {
        FrameLayout cameraView = (FrameLayout) findViewById(R.id.cameraView);
        cameraView.addView(cameraPreview);

        Log.d(LOG_TAG, "surface holder done");
    }

    private void initViewVideo() {
        videoView = (VideoView) findViewById(R.id.videoView);
        videoView.setVideoURI(Uri.parse(G.ReceiveVideoPath));

        MediaController mc = new MediaController(this);
        videoView.setMediaController(mc);

        Log.d(LOG_TAG, "VideoView initialized");
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
//                    socket.setSoTimeout(5000);
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

                                Log.d(LOG_TAG, "video call accepted");
//                                Accept notification received. Start VideoCall
                                sendVideo(packet.getAddress());
//
// receiveVideo();

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
                    LISTEN = false;
                    Log.d(LOG_TAG, "Listener socket dc & close");

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
        sendMessage("CAL:" + displayName + "___" + BUF_SIZE, port_Call);
        G.cameraDataSize = BUF_SIZE;
    }

    private void endCall() {
        // Ends the chat sessions
        Log.d(LOG_TAG, "end call");
        stopListener();

//        if (camera != null) {
//            camera.unlock();
//            camera.stopPreview();
//            camera.release();
//        }
        if (IN_CALL) {
            recording = false;
            stopMediaRecorder();
            releaseCamera();

        }
        sendMessage("END:", BROADCAST_PORT);

        finish();
    }

    private void stopListener() {
        // Ends the listener thread
        Log.d(LOG_TAG, "stopping listener");
        LISTEN = false;
    }

    private void sendVideo(final InetAddress address) {
        Log.d(LOG_TAG, "***********send video thread started...");

        recording = true;

        new Thread(new Runnable() {
            @Override
            public void run() {

                FileOutputStream fos = null;
                cameraData = new byte[BUF_SIZE_CAMERA];

                try {

//                    DatagramSocket socket = new DatagramSocket();

                    ServerSocket serverSocket = new ServerSocket(port_VideoCall);
//

                    socket = serverSocket.accept();

                    Log.d(LOG_TAG, "***********Socket Server accepted");

                    writeFD = ParcelFileDescriptor.fromSocket(socket);
                    mediaRecorder.setOutputFile(writeFD.getFileDescriptor());

                    int bytes_sent = 0;

                    startMediaRecorder();

                    /*while (recording) {

                        Log.d(LOG_TAG, "recording");


//                        OutputStream outputStream = socket.getOutputStream();
//                        outputStream.write(cameraData, 0, cameraData.length);
//                        outputStream.flush();

//                        Log.d(LOG_TAG, "** bytes_Read = " + bytes_read + " buffer size = " + cameraData.length + " **");
//                        writeFD = ParcelFileDescriptor.fromSocket(socket);

//                        DatagramPacket packet = new DatagramPacket(cameraData, cameraData.length, address, port_VideoCall);
//                        socket.send(packet);

                        bytes_sent += cameraData.length;

                        Log.i(LOG_TAG, "Total bytes sent: " + bytes_sent);
                    }*/

//                    releaseCamera();
//
//                    socket.close();
//
//                    recording = false;
//                    fos.close();
                } catch (SocketException e) {
                    recording = false;
                    Log.e(LOG_TAG, "Error in Send video");
                    e.printStackTrace();
                } catch (IOException e) {
                    recording = false;
                    Log.e(LOG_TAG, "reading parcel error");
                    e.printStackTrace();
                }
            }
        }).start();
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

