package com.example.fcc.udptest;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.UnknownHostException;

import classes.Logger;

public class MainActivity extends Activity implements OnClickListener, DialogInterface.OnCancelListener {

    static final String LOG_TAG = "UDPchat";
    private static final int CALL_LISTENER_PORT = 50003;
    private static final int VIDEOCALL_LISTENER_PORT = 50004;
    private static final int BUF_SIZE = 1024;

    private static final int CheckStatus = 50006;
    private static final int BROADCAST_INTERVAL = 10000; // Milliseconds
    public static boolean Online = false;
    private boolean BROADCAST = true;
    private boolean LISTEN = false;
    private boolean LISTEN_Video = true;

    public final static String EXTRA_CONTACT = "hw.dt83.udpchat.CONTACT";
    public final static String EXTRA_IP = "hw.dt83.udpchat.IP";
    public final static String EXTRA_DISPLAYNAME = "hw.dt83.udpchat.DISPLAYNAME";
    private String contact;
    private InetAddress ip;

    private Button startButton, callButton, videoCallButton;
    private EditText Edit_Server_Port;
    private EditText Edit_Username;
    private String SERVER_IP;
    private String Username;
    private boolean started = false;
    private boolean shouldCheckServer = true;
    private int checkServerSleepInterval = 10;
    private boolean isServerReachable = false;
    private int times_server_checked = 0;
    private ProgressDialog progressDialog;
    private CountDownTimer countDownTimer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main_client);

        initViews();
        CheckOnline();
        Logger.i("MainActivity", "onCreate", "IP is >> " + getBroadcastIp());

    }

    private void initViews() {
        startButton = (Button) findViewById(R.id.buttonStart);
        callButton = (Button) findViewById(R.id.buttonCall);
        videoCallButton = (Button) findViewById(R.id.btnVideoCall);
        Edit_Server_Port = (EditText) findViewById(R.id.editText_ip);
        Edit_Username = (EditText) findViewById(R.id.editText_name);


        callButton.setOnClickListener(this);
        videoCallButton.setOnClickListener(this);
        startButton.setOnClickListener(this);
    }

    private InetAddress getBroadcastIp() {
        Logger.d("MainActivity", "getBroadcastIp", "Start");
        // Function to return the broadcast address, based on the IP address of the device
        try {

            WifiManager wifiManager = (WifiManager) getSystemService(WIFI_SERVICE);
            WifiInfo wifiInfo = wifiManager.getConnectionInfo();
            int ipAddress = wifiInfo.getIpAddress();
            String addressString = toBroadcastIp(ipAddress);
            InetAddress broadcastAddress = InetAddress.getByName(addressString);
            return broadcastAddress;
        } catch (UnknownHostException e) {

            Log.e(LOG_TAG, "UnknownHostException in getBroadcastIP: " + e);
            return null;
        }

    }

    private String toBroadcastIp(int ip) {
        // Returns converts an IP address in int format to a formatted string
        return (ip & 0xFF) + "." +
                ((ip >> 8) & 0xFF) + "." +
                ((ip >> 16) & 0xFF) + "." +
                ((ip >> 24) & 0xff);

    }

    private void startCallListener() {
        // Creates the listener thread
        LISTEN = true;
        Thread listener = new Thread(new Runnable() {

            @Override
            public void run() {

                try {
                    // Set up the socket and packet to receive
                    Log.i(LOG_TAG, "Incoming call listener started");
                    DatagramSocket socket = new DatagramSocket(CALL_LISTENER_PORT);
                    socket.setSoTimeout(10000);
                    byte[] buffer = new byte[BUF_SIZE];
                    DatagramPacket packet = new DatagramPacket(buffer, BUF_SIZE);
                    while (LISTEN) {
                        // Listen for incoming call requests
                        try {
                            Log.i(LOG_TAG, "Listening for incoming calls");
                            socket.receive(packet);
                            String data = new String(buffer, 0, packet.getLength());
                            Log.i(LOG_TAG, "Packet received from " + packet.getAddress() + " with contents: " + data);
                            String action = data.substring(0, 4);
                            if (action.equals("CAL:")) {
                                // Received a call request. Start the ReceiveCallActivity
                                String address = packet.getAddress().toString();
                                String name = data.substring(4, packet.getLength());

                                Intent intent = new Intent(MainActivity.this, ReceiveCallActivity.class);
                                intent.putExtra(EXTRA_CONTACT, name);
                                intent.putExtra(EXTRA_IP, address.substring(1, address.length()));
                                G.IN_CALL = true;
                                //LISTEN = false;
                                //stopCallListener();
                                startActivity(intent);
                            } else {
                                // Received an invalid request
                                Log.w(LOG_TAG, packet.getAddress() + " sent invalid message: " + data);
                            }
                        } catch (Exception e) {
                        }
                    }
                    Log.i(LOG_TAG, "Call Listener ending");
                    socket.disconnect();
                    socket.close();
                } catch (SocketException e) {

                    Log.e(LOG_TAG, "SocketException in listener " + e);
                    e.printStackTrace();
                }
            }
        });
        listener.start();
    }

    private void stopCallListener() {
        // Ends the listener thread
        LISTEN = false;
    }

    private void startVideoCallListener() {
        // Creates the listener thread
        LISTEN_Video = true;
        Thread listener = new Thread(new Runnable() {

            @Override
            public void run() {

                try {
                    // Set up the socket and packet to receive
                    Log.i(LOG_TAG, "Incoming video call listener started");
                    DatagramSocket socket = new DatagramSocket(VIDEOCALL_LISTENER_PORT);
                    socket.setSoTimeout(10000);
                    byte[] buffer = new byte[BUF_SIZE];
                    DatagramPacket packet = new DatagramPacket(buffer, BUF_SIZE);
                    while (LISTEN_Video) {
                        // Listen for incoming call requests
                        try {
                            Log.i(LOG_TAG, "Listening for incoming video calls");
                            socket.receive(packet);
                            String data = new String(buffer, 0, packet.getLength());
                            Log.i(LOG_TAG, "Packet received from " + packet.getAddress() + " with contents: " + data);
                            String action = data.substring(0, 4);
                            if (action.equals("CAL:")) {

                                Log.d(LOG_TAG, "**CAL received**");
                                // Received a call request. Start the ReceiveCallActivity
                                String address = packet.getAddress().toString();
                                String name = data.substring(4, packet.getLength());

                                Intent intent = new Intent(MainActivity.this, ReceiveVideoCallActivity.class);
                                intent.putExtra(EXTRA_CONTACT, name);
                                intent.putExtra(EXTRA_IP, address.substring(1, address.length()));
                                G.IN_CALL = true;

                                startActivity(intent);
                            } else {
                                // Received an invalid request
                                Log.w(LOG_TAG, packet.getAddress() + " sent invalid message: " + data);
                            }
                        } catch (Exception e) {
                        }
                    }
                    Log.i(LOG_TAG, "Video Call Listener ending");
                    socket.disconnect();
                    socket.close();
                } catch (SocketException e) {

                    Log.e(LOG_TAG, "SocketException in listener " + e);
                }
            }
        });
        listener.start();
    }

    private void stopVideoCallListener() {
        // Ends the listener thread
        LISTEN_Video = false;
    }

    @Override
    public void onPause() {

        super.onPause();

        Online = false;

        if (started) {

            removeContact(getBroadcastIp());
        }
        stopCallListener();
        stopVideoCallListener();
        Log.i(LOG_TAG, "App paused!");
    }

    public void removeContact(final InetAddress address) {
        // Sends a Bye notification to other devices
        Thread byeThread = new Thread(new Runnable() {

            @Override
            public void run() {

                try {
                    Log.i(LOG_TAG, "Attempting to broadcast BYE notification!");
                    String notification = "BYE:" + ip;
                    byte[] message = notification.getBytes();
                    DatagramSocket socket = new DatagramSocket();
                    socket.setBroadcast(true);
                    DatagramPacket packet = new DatagramPacket(message, message.length, address, G.CONTACTSYNC_PORT);
                    socket.send(packet);
                    Log.i(LOG_TAG, "Broadcast BYE notification!");
                    socket.disconnect();
                    socket.close();
                    return;
                } catch (SocketException e) {

                    Log.e(LOG_TAG, "SocketException during BYE notification: " + e);
                } catch (IOException e) {

                    Log.e(LOG_TAG, "IOException during BYE notification: " + e);
                }
            }
        });
        byeThread.start();
    }

    @Override
    public void onStop() {

        super.onStop();
        Online = false;
        Log.i(LOG_TAG, "App stopped!");
        stopCallListener();
        stopVideoCallListener();
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        if (!G.IN_CALL) {
            finish();
        }
    }

    @Override
    public void onRestart() {

        super.onRestart();
        Log.i(LOG_TAG, "App restarted!");
        G.IN_CALL = false;
        startCallListener();
        startVideoCallListener();
    }

    @Override
    public void onClick(View v) {

        switch (v.getId()) {

            case R.id.buttonStart:

                started = true;

                Logger.d("MainActivity", "onClick", "Start button pressed");

                Username = Edit_Username.getText().toString();
                SERVER_IP = Edit_Server_Port.getText().toString();
                if (CheckInput()) {

                    if (isServerReachable) {
                        startButton.setEnabled(false);

                        startCallListener();
                        startVideoCallListener();
                        callButton.setVisibility(View.VISIBLE);
                        videoCallButton.setVisibility(View.VISIBLE);
                        Thread thread = new Thread(new Runnable() {
                            @Override
                            public void run() {
                                try {
                                    InetAddress ServerIp = InetAddress.getByName(SERVER_IP);
                                    broadcastName(Username, ServerIp);
                                } catch (UnknownHostException e) {
                                    e.printStackTrace();
                                }
                            }
                        });
                        thread.start();
                    }
                }

                break;

            case R.id.btn_videocall:
                if (CheckInput()) {
                    SERVER_IP = Edit_Server_Port.getText().toString();
                    Username = Edit_Username.getText().toString();
                    try {
                        InetAddress inetAddress = InetAddress.getByName(SERVER_IP);
                        MakeVideoCall(Username, inetAddress);
                    } catch (UnknownHostException e) {
                        e.printStackTrace();
                    }
                }
                break;

            case R.id.buttonCall:
                if (CheckInput()) {
                    SERVER_IP = Edit_Server_Port.getText().toString();
                    Username = Edit_Username.getText().toString();
                    try {
                        InetAddress inetAddress = InetAddress.getByName(SERVER_IP);
                        MakeVoiceCall(Username, inetAddress.toString().substring(1));
                    } catch (UnknownHostException e) {
                        e.printStackTrace();
                    }
                }

                break;
        }
    }

    public void MakeVoiceCall(String Username, String SERVER_IP) {
        Logger.d("MainActivity", "MakeVoiceCall", "Start");

        // Send this information to the MakeCallActivity and start that activity
        Intent intent = new Intent(MainActivity.this, MakeCallActivity.class);
        intent.putExtra(EXTRA_CONTACT, contact);
        intent.putExtra(EXTRA_IP, SERVER_IP);
        intent.putExtra(EXTRA_DISPLAYNAME, Username);
        startActivity(intent);
    }

    public void MakeVideoCall(String C_Name, InetAddress C_Ip) {
        Logger.d("MainActivity", "MakeVideoCall", "Start");
        G.IN_CALL = true;

        Intent i = new Intent(MainActivity.this, MakeVideoCallActivity.class);
        i.putExtra(EXTRA_CONTACT, C_Name);
        i.putExtra(EXTRA_IP, C_Ip);
        i.putExtra(EXTRA_DISPLAYNAME, "SERVER");
        startActivity(i);


    }

    private boolean CheckInput() {
        if (
                !Edit_Server_Port.getText().toString().isEmpty() &&
                        !Edit_Username.getText().toString().isEmpty()) {
            return true;
        }
        Toast.makeText(getApplicationContext(), "Please Enter your Info", Toast.LENGTH_LONG).show();
        return false;

    }

    public void broadcastName(final String Username, final InetAddress SERVER_IP) {

        if (checkServerReachable()) {
            // Broadcasts the name of the device at a regular interval
            Logger.d("MainActivity", "broadcastName", "Broadcasting started!");
            Thread broadcastThread = new Thread(new Runnable() {

                @Override
                public void run() {

                    try {

                        String request = "ADD:" + Username;
                        byte[] message = request.getBytes();
                        DatagramSocket socket = new DatagramSocket();
                        socket.setBroadcast(true);
                        DatagramPacket packet = new DatagramPacket(message, message.length, SERVER_IP, G.CONTACTSYNC_PORT);
//                        while (BROADCAST) {

                        socket.send(packet);
                        Logger.d("MainActivity", "broadcastName", "Broadcast packet sent >> name >>" + Username + " Adrs >> " + packet.getAddress().toString() + "   To >> " + SERVER_IP + ":" + G.CONTACTSYNC_PORT);
//                        Thread.sleep(BROADCAST_INTERVAL);
//                        }
                        Logger.d("MainActivity", "broadcastName", "Broadcaster ending!");
                        socket.disconnect();
                        socket.close();
                        return;
                    } catch (SocketException e) {

                        Log.e(LOG_TAG, "SocketException in broadcast: " + e);
                        Log.i(LOG_TAG, "Broadcaster ending!");
                        return;
                    } catch (IOException e) {

                        Log.e(LOG_TAG, "IOException in broadcast: " + e);
                        Log.i(LOG_TAG, "Broadcaster ending!");
                        return;
                    }
                }
            });
            broadcastThread.start();
        } else {
            Toast.makeText(MainActivity.this, R.string.server_not_reachable, Toast.LENGTH_LONG).show();
        }
    }

    private boolean checkServerReachable() {

        showProgressDialog();


        while (shouldCheckServer) {

            times_server_checked += 1;

            try {

                isServerReachable = InetAddress.getByName(SERVER_IP).isReachable(2000);

                if (isServerReachable) {

                    shouldCheckServer = false;
                } else {

                    int sleep_time = checkServerSleepInterval * times_server_checked;

                    Thread.sleep(sleep_time);

                    setCountDownTimer(sleep_time);

                }
            } catch (IOException | InterruptedException e) {
                e.printStackTrace();

                hideProgressDialog();
            }
        }
        hideProgressDialog();

        return isServerReachable;
    }

    private void setCountDownTimer(int sleepTime) {
        countDownTimer = new CountDownTimer(sleepTime, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                progressDialog.setMessage(getString(R.string.checkingSever) + " in " + millisUntilFinished);
            }

            @Override
            public void onFinish() {
            }
        }.start();
    }

    private void showProgressDialog() {
        progressDialog = new ProgressDialog(MainActivity.this);
        progressDialog.setTitle(R.string.wait);
        progressDialog.setMessage(getString(R.string.checkingSever));
        progressDialog.setCancelable(true);
        progressDialog.setOnCancelListener(this);
        progressDialog.show();
    }

    private void hideProgressDialog() {
        progressDialog.dismiss();
    }

    private void CheckOnline() {
        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                Logger.d("MainActivity", "CheckOnline", "Start");
                Online = true;
                while (Online) {
                    try {
                        ServerSocket serverSocket = new ServerSocket(CheckStatus);
                        Socket socket = serverSocket.accept();
                        Logger.d("MainActivity", "CheckOnline", "Socket Accepted . . .");
                        Online = false;
                    } catch (IOException e) {
                        e.printStackTrace();

                    }
                    //                        Thread.sleep(1000);
                }

            }
        });
        thread.start();


    }

    @Override
    public void onCancel(DialogInterface dialog) {
        shouldCheckServer = false;
    }
}
