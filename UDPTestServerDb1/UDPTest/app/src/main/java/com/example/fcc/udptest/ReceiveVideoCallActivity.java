package com.example.fcc.udptest;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.UnknownHostException;

import classes.Logger;

public class ReceiveVideoCallActivity extends AppCompatActivity implements View.OnClickListener {

    private final static String LOG_TAG = "UDP:ReceiveVideoCall";
    private String contactIp;
    private String displayName;
    private boolean IN_VIDEO_CALL = false;
    private boolean LISTEN = false;
    private boolean receiving = false;
    private Button accept, reject, endCall;
    private ImageView mImgReceive;
    private int BUF_SIZE = 1024;
    private BufferedReader bufferedReader;
    private int mFrameWidth;
    private int mFrameHeight;
    private int mFrameBuffSize;
    private boolean introReceveid = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_receive_video_call);

        Log.d(LOG_TAG, "Receive video call created");

        accept = (Button) findViewById(R.id.buttonAccept);
        reject = (Button) findViewById(R.id.buttonReject);
        endCall = (Button) findViewById(R.id.buttonEndCall);
        mImgReceive = (ImageView) findViewById(R.id.img_receive);
        accept.setOnClickListener(this);
        reject.setOnClickListener(this);
        endCall.setOnClickListener(this);

        Intent intent = getIntent();
        displayName = intent.getStringExtra(G.EXTRA_CONTACT);
        contactIp = intent.getStringExtra(G.EXTRA_IP);

        TextView textView = (TextView) findViewById(R.id.textViewIncomingCall);
        textView.setText("Incoming call: " + displayName);

        startListener();


        //receiveVideo();
    }

    private void startListener() {
        // Create listener thread
        LISTEN = true;
        Thread listenThread = new Thread(new Runnable() {

            @Override
            public void run() {

                try {
                    Logger.d("ReceiveVideoCallActivity", "startListener", "Listener started!");
                    DatagramSocket socket = new DatagramSocket(G.VIDEO_CALL_PORT);
//                    socket.setSoTimeout(5000);
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

    private void receiveVideo() {
        Logger.d("ReceiveVideoCallActivity", "receiveVideo", "mFrameBuffSize >> " + mFrameBuffSize);
        Logger.d("ReceiveVideoCallActivity", "receiveVideo", "**********Receive video");

        udpReceived();
        //tcpReceived();
    }

    private void stopListener() {
        Logger.d("ReceiveVideoCallActivity", "stopListener", "Stopping listener");

        LISTEN = false;
    }

    private void endCall() {
        Log.d(LOG_TAG, "end call");
        stopListener();

        if (IN_VIDEO_CALL) {
            receiving = false;
        }
        sendMessage("END:", G.BROADCAST_PORT);

        finish();
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.buttonAccept:
                sendMessage("ACC:", G.BROADCAST_PORT);
                introListener();
                introReceveid = true;
                while (introReceveid) {

                    Logger.d("ReceiveVideoCallActivity", "onClick", "mFrameBuffSize >> " + mFrameBuffSize);
                }
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
//                    socket.setSoTimeout(2000);
                    bufferedReader = new BufferedReader(new InputStreamReader(socket.getInputStream(), "UTF-8"));
                    String read = bufferedReader.readLine();
                    JSONObject jsonObject = new JSONObject(read);
                    mFrameWidth = jsonObject.getInt("Width");
                    mFrameHeight = jsonObject.getInt("Height");
                    mFrameBuffSize = jsonObject.getInt("Size");
                    Logger.d("ReceiveVideoCallActivity", "introListener", "mFrameBuffSize >> " + mFrameBuffSize);

                    Logger.d("ReceiveVideoCallActivity", "introListener", "Received Data " +
                            ">> width =" + mFrameWidth +
                            " height = " + mFrameHeight +
                            " buffSize = " + mFrameBuffSize);
                    socket.close();

                    if (mFrameHeight != 0) {
                        sendACC();
                        Logger.d("ReceiveVideoCallActivity", "introListener", "mIsFrameReceived is true Sent ACC");
                    } else {
                        finish();
                        Logger.d("ReceiveVideoCallActivity", "introListener", "mIsFrameReceived is false");
                        Logger.d("ReceiveVideoCallActivity", "introListener", "finish Activity");
                    }
                    introReceveid = false;
                } catch (IOException e) {
                    e.printStackTrace();
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
        });
        thread.start();
        Logger.d("ReceiveVideoCallActivity", "introListener End", "mFrameBuffSize >> " + mFrameBuffSize);


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
//            socket.setSoTimeout(2000);
            DataOutputStream outputStream = new DataOutputStream(socket.getOutputStream());


            outputStream.write(data.getBytes());
            outputStream.flush();
            socket.close();
            Logger.d("ReceiveVideoCallActivity", "sendACC", "ACC Sent To >> " + address + " : " + G.INTRODUCE_PORT);
        } catch (UnknownHostException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
            Logger.d("ReceiveVideoCallActivity", "sendACC", "Socket Ex >> " + e.toString());
        } catch (JSONException e) {
            e.printStackTrace();
        }


    }

    private void previewBitmap(final byte[] data) {

        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                /*DisplayMetrics displaymetrics = new DisplayMetrics();
                getWindowManager().getDefaultDisplay().getMetrics(displaymetrics);
                int ht = displaymetrics.heightPixels;
                int wt = displaymetrics.widthPixels;
                Logger.d("ReceiveVideoCallActivity", "", "heightPixels >> "+ht);
                Logger.d("ReceiveVideoCallActivity", "previewBitmap", "widthPixels >> "+wt);*/

                Logger.d("ReceiveVideoCallActivity", "previewBitmap", "Start");

                final Bitmap bitmap = BitmapFactory.decodeByteArray(data, 0, data.length);

                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        mImgReceive.setImageBitmap(bitmap);
                    }
                });


            }
        });
        thread.start();
    }

    private void udpReceived() {
        receiving = true;
        Logger.d("ReceiveVideoCallActivity", "udpReceived", "Start");
        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                Logger.d("ReceiveVideoCallActivity", "udpReceived", " Thread Start");
                try {
                    final byte[] buff = new byte[mFrameBuffSize * 4];
                    Logger.d("ReceiveVideoCallActivity", "udpReceived", "mFrameBuffSize >> " + mFrameBuffSize);

                    DatagramSocket datagramSocket = new DatagramSocket(G.VIDEO_CALL_PORT);
//                    datagramSocket.setSoTimeout(10000);
                    DatagramPacket packet = new DatagramPacket(buff, mFrameBuffSize);


                    while (receiving) {
                        datagramSocket.receive(packet);
                        Logger.d("ReceiveVideoCallActivity", "udpReceived", "buff.size()" + buff.length);
                        previewBitmap(buff);

                    }
                    datagramSocket.disconnect();
                    datagramSocket.close();
                } catch (SocketException e) {
                    receiving = false;
                    e.printStackTrace();
                } catch (IOException e) {
                    receiving = false;
                    e.printStackTrace();
                }
            }
        });
        thread.start();


    }

    private void tcpReceived() {
        if (!receiving) {
            receiving = true;
            new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        ServerSocket serverSocket = new ServerSocket(G.VIDEO_CALL_PORT);
                        Socket socket = serverSocket.accept();
                        //socket.setSoTimeout(5000);

                        while (receiving) {

                            if (socket.isConnected()) {
                                byte[] buff = new byte[mFrameBuffSize];

                                //\\\\\\\\
                                //BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                                //int lenght = bufferedReader.readLine().length();
                                //byte[] buffer = bufferedReader.readLine().getBytes();
                                //Logger.d("ReceiveVideoCallActivity", "receiveVideo", "bufferedReader size >> " + lenght);
                                //\\\\\\\\\\


                                DataInputStream inputStream1 = new DataInputStream(socket.getInputStream());
                                Logger.d("ReceiveVideoCallActivity", "receiveVideo", "DataInputStream size >> " + inputStream1.read());
                                inputStream1.readFully(buff, 0, mFrameBuffSize);


                                // InputStream inputStream = socket.getInputStream();
                                // Logger.d("ReceiveVideoCallActivity", "receiveVideo", "inputStream size >> " + inputStream.toString().length());
                                // int count = inputStream.read(buff,0,mFrameBuffSize);
                                // Logger.d("ReceiveVideoCallActivity", "receiveVideo", "buff size count >> " + count);
                                //\\\\\\\\\


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

}
