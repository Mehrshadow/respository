package com.example.fcc.udptest;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.concurrent.ExecutionException;

import classes.Logger;

public class MainActivity extends Activity implements OnClickListener, DialogInterface.OnCancelListener {

    static final String LOG_TAG = "UDPchat";

    private static final int BUF_SIZE = 1024;

    public static boolean Online = false;
    private boolean LISTEN = false;
    private String contact;
    private InetAddress ip;

    private Button callButton, videoCallButton, Btn_Setting, Btn_Connect;

    private String SERVER_IP;
    private String Username;
    private boolean started = false;

    private boolean shouldCheckServer = true;
    private boolean isServerReachable = false;
    private boolean hasBroadcasted = false;
    private ProgressDialog progressDialog;

    DatagramSocket callListenerSocket;

    TextView Txt_Username, Txt_Server_ip;

    SharedPreferences preferences;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        registerReceiver(receiver, new IntentFilter(G.BROADCAST_WIFI_STATUS));
        super.onCreate(savedInstanceState);
        Logger.e("MainActivity", "Lifecycle", "onCreate");

        if (!initName_IP()) {
            Intent intent = new Intent(MainActivity.this, Settings.class);
            startActivity(intent);
            return;
        }
        setContentView(R.layout.activity_main_client);


        initViews();

        Logger.i("MainActivity", "onCreate", "IP is >> " + getBroadcastIp());

    }

    private void initViews() {
        callButton = (Button) findViewById(R.id.buttonCall);
        videoCallButton = (Button) findViewById(R.id.btnVideoCall);
        Btn_Connect = (Button) findViewById(R.id.btn_main_connect);
        Btn_Setting = (Button) findViewById(R.id.btn_main_setting);
        Txt_Server_ip = (TextView) findViewById(R.id.txt_main_serverip);
        Txt_Username = (TextView) findViewById(R.id.txt_main_user);

        Txt_Username.setText(Username);
        Txt_Server_ip.setText(SERVER_IP);
        Txt_Username.setText("UserName : " + Username);
        Txt_Server_ip.setText("Server ip : " + SERVER_IP);

        callButton.setOnClickListener(this);
        videoCallButton.setOnClickListener(this);
        Btn_Setting.setOnClickListener(this);
        Btn_Connect.setOnClickListener(this);
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
        Logger.i("MainActivity", "startCallListener", "Start");
        // Creates the listener thread
        LISTEN = true;
        Thread listener = new Thread(new Runnable() {

            @Override
            public void run() {

                // Set up the socket and packet to receive
                Logger.i("MainActivity", "startCallListener", "Incoming call listener started");
                connectCallListenerSocket();
                byte[] buffer = new byte[BUF_SIZE];
                DatagramPacket packet = new DatagramPacket(buffer, BUF_SIZE);
                while (LISTEN) {
                    // Listen for incoming call requests
                    try {
                        Logger.i("MainActivity", "startCallListener", "Listening for incoming calls");
                        callListenerSocket.receive(packet);
                        String data = new String(buffer, 0, packet.getLength());
                        Logger.i("MainActivity", "startCallListener", "\"Packet received from \" + packet.getAddress() + \" with contents: \" + data");
                        String action = data.substring(0, 9);
                        String name = data.substring(9, packet.getLength());
                        String address = packet.getAddress().toString();

                        if (action.equals("VOICECALL")) {
                            // Received a call request. Start the ReceiveCallActivity
                            closeCallListenerSocket();
                            Intent intent = new Intent(MainActivity.this, ReceiveCallActivity.class);
                            intent.putExtra(G.EXTRA_C_Name, name);
                            intent.putExtra(G.EXTRA_C_Ip, address.substring(1, address.length()));
                            startActivity(intent);

                        } else if (action.equals("VIDEOCALL")) {
                            Logger.i("MainActivity", "startVideoCallListener", "**CAL received**");
                            // Received a call request. Start the ReceiveCallActivity

                            Intent intent = new Intent(MainActivity.this, ReceiveVideoCallActivity.class);
                            intent.putExtra(G.EXTRA_C_Name, name);
                            intent.putExtra(G.EXTRA_C_Ip, address.substring(1, address.length()));

                            G.IN_CALL = true;
                            closeCallListenerSocket();
                            startActivity(intent);
                        } else {
                            // Received an invalid request
                            Log.w(LOG_TAG, packet.getAddress() + " sent invalid message: " + data);
                        }
                    } catch (Exception e) {
                    }
                }
                Logger.i("MainActivity", "startCallListener", "Call Listener ending");
                closeCallListenerSocket();
            }
        });
        listener.start();
    }

//    private void startCallListener() {
//        // Creates the listener thread
//        LISTEN = true;
//        Thread listener = new Thread(new Runnable() {
//
//            @Override
//            public void run() {
//
//                try {
//                    // Set up the socket and packet to receive
//                    Log.i(LOG_TAG, "Incoming call listener started");
//                    DatagramSocket socket = new DatagramSocket(G.CALL_LISTENER_PORT);
//                    socket.setSoTimeout(10000);
//                    byte[] buffer = new byte[BUF_SIZE];
//                    DatagramPacket packet = new DatagramPacket(buffer, BUF_SIZE);
//                    while (LISTEN) {
//                        // Listen for incoming call requests
//                        try {
//                            Log.i(LOG_TAG, "Listening for incoming calls");
//                            socket.receive(packet);
//                            String data = new String(buffer, 0, packet.getLength());
//                            Log.i(LOG_TAG, "Packet received from " + packet.getAddress() + " with contents: " + data);
//                            String action = data.substring(0, 4);
//                            if (action.equals("CAL:")) {
//                                // Received a call request. Start the ReceiveCallActivity
//                                String address = packet.getAddress().toString();
//                                String name = data.substring(4, packet.getLength());
//
//                                Intent intent = new Intent(MainActivity.this, ReceiveCallActivity.class);
//                                intent.putExtra(G.EXTRA_C_Name, name);
//                                intent.putExtra(G.EXTRA_C_Ip, address.substring(1, address.length()));
//                                G.IN_CALL = true;
//                                //LISTEN = false;
//                                //stopCallListener();
//                                startActivity(intent);
//                            } else {
//                                // Received an invalid request
//                                Log.w(LOG_TAG, packet.getAddress() + " sent invalid message: " + data);
//                            }
//                        } catch (Exception e) {
//                        }
//                    }
//                    Log.i(LOG_TAG, "Call Listener ending");
//                    socket.disconnect();
//                    socket.close();
//                } catch (SocketException e) {
//
//                    Log.e(LOG_TAG, "SocketException in listener " + e);
//                    e.printStackTrace();
//                }
//            }
//        });
//        listener.start();
//    }

    private void stopCallListener() {
        // Ends the listener thread
        LISTEN = false;
    }

//    private void startVideoCallListener() {
//        // Creates the listener thread
//        LISTEN_Video = true;
//        Thread listener = new Thread(new Runnable() {
//
//            @Override
//            public void run() {
//
//                try {
//                    // Set up the socket and packet to receive
//                    Logger.d("MainActivity", "startVideoCallListener", "Incoming video call listener started");
//                    DatagramSocket socket = new DatagramSocket(G.BROADCAST_PORT);
//                    socket.setSoTimeout(10000);
//                    byte[] buffer = new byte[BUF_SIZE];
//                    DatagramPacket packet = new DatagramPacket(buffer, BUF_SIZE);
//                    while (LISTEN_Video) {
//                        // Listen for incoming call requests
//                        try {
//                            Logger.d("MainActivity", "startVideoCallListener", "Listening for incoming video calls");
//                            socket.receive(packet);
//                            String data = new String(buffer, 0, packet.getLength());
//                            Logger.d("MainActivity", "startVideoCallListener", "Packet received from " + packet.getAddress() + " with contents: " + data);
//                            String action = data.substring(0, 4);
//                            if (action.equals("CAL:")) {
//                                socket.close();
//                                socket.disconnect();
//                                Log.d(LOG_TAG, "**CAL received**");
//                                // Received a call request. Start the ReceiveCallActivity
//                                String address = packet.getAddress().toString();
//                                String name = data.substring(4, packet.getLength());
//
//                                Intent intent = new Intent(MainActivity.this, ReceiveVideoCallActivity.class);
//                                intent.putExtra(G.EXTRA_C_Name, name);
//                                intent.putExtra(G.EXTRA_C_Ip, address.substring(1, address.length()));
//                                G.IN_CALL = true;
//
//                                startActivity(intent);
//                            } else {
//                                // Received an invalid request
//                                Log.w(LOG_TAG, packet.getAddress() + " sent invalid message: " + data);
//                            }
//                        } catch (Exception e) {
//                        }
//                    }
//                    Log.i(LOG_TAG, "Video Call Listener ending");
//                    socket.disconnect();
//                    socket.close();
//                } catch (SocketException e) {
//
//                    Log.e(LOG_TAG, "SocketException in listener " + e);
//                }
//            }
//        });
//        listener.start();
//    }

    @Override
    public void onPause() {

        Logger.e("MainActivity", "Lifecycle", "onPause");
        unregisterReceiver(receiver);
        super.onPause();
//        Online = false;
        if (started) {
            removeContact(getBroadcastIp());
        }

        stopCallListener();
        //stopVideoCallListener();
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
        Logger.e("MainActivity", "Lifecycle", "onStop");
        callListenerSocket.disconnect();
        callListenerSocket.close();
        super.onStop();
        Log.i(LOG_TAG, "App stopped!");

        if (started) {

            removeContact(getBroadcastIp());
        }

        //stopCallListener();
        //stopVideoCallListener();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Logger.e("MainActivity", "Lifecycle", "onDestroy");
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
        Logger.e("MainActivity", "Lifecycle", "onRestart");

        registerReceiver(receiver, new IntentFilter(G.BROADCAST_WIFI_STATUS));
        initName_IP();
        // startCallListener();
        //startVideoCallListener();
        Log.i(LOG_TAG, "App restarted!");
        G.IN_CALL = false;
        if (G.isIPChanged) {
//            Btn_Connect.setEnabled(true);
            Btn_Connect.setVisibility(View.VISIBLE);
            callButton.setVisibility(View.INVISIBLE);
            videoCallButton.setVisibility(View.INVISIBLE);
            G.isIPChanged = false;
        }
    }

    @Override
    protected void onResume() {
        CheckOnline();
        startCallListener();
        Logger.e("MainActivity", "Lifecycle", "onResume");
        if (hasBroadcasted) {
            InetAddress ServerIp = null;
            try {
                ServerIp = InetAddress.getByName(SERVER_IP);
                broadcastName(Username, ServerIp);
            } catch (UnknownHostException e) {
                e.printStackTrace();
            }

        }
        registerReceiver(receiver, new IntentFilter(G.BROADCAST_WIFI_STATUS));
        super.onResume();
    }

    @Override
    public void onClick(View v) {

        switch (v.getId()) {

            case R.id.btnVideoCall:
                Logger.i("MainActivity", "onClick", "btn_videocall Clicked");

                try {
                    InetAddress inetAddress = InetAddress.getByName(SERVER_IP);
                    MakeVideoCall(Username, inetAddress);
                } catch (UnknownHostException e) {
                    e.printStackTrace();
                }
                break;

            case R.id.buttonCall:

                try {
                    InetAddress inetAddress = InetAddress.getByName(SERVER_IP);
                    MakeVoiceCall(Username, inetAddress.toString().substring(1));
                } catch (UnknownHostException e) {
                    e.printStackTrace();
                }

                break;
            case R.id.btn_main_setting:
                Intent intent = new Intent(MainActivity.this, Settings.class);
                startActivity(intent);
                break;
            case R.id.btn_main_connect:
                CheckRunApp();
                break;
        }
    }

    public void MakeVoiceCall(String Username, String SERVER_IP) {
        Logger.d("MainActivity", "MakeVoiceCall", "Start");

        // Send this information to the MakeCallActivity and start that activity
        Intent intent = new Intent(MainActivity.this, MakeCallActivity.class);
        intent.putExtra(G.EXTRA_C_Name, contact);
        intent.putExtra(G.EXTRA_C_Ip, SERVER_IP);
        intent.putExtra(G.EXTRA_DISPLAYNAME, Username);
        startActivity(intent);
    }

    public void MakeVideoCall(String C_Name, InetAddress C_Ip) {
        Logger.d("MainActivity", "MakeVideoCall", "Start");
        G.IN_CALL = true;

        Intent i = new Intent(MainActivity.this, MakeVideoCallActivity.class);
        i.putExtra(G.EXTRA_C_Name, C_Name);
        i.putExtra(G.EXTRA_C_Ip, C_Ip.toString().substring(1));
        i.putExtra(G.EXTRA_DISPLAYNAME, C_Name);
        startActivity(i);
    }

    public void broadcastName(final String Username, final InetAddress SERVER_IP) {

        if (isServerReachable) {
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

    private void checkServerReachable() {
        if (CheckWifiStatus()) {
            AsyncTask asyncTask = new AsyncTask() {

                @Override
                protected Object doInBackground(Object[] params) {
                    try {
                        isServerReachable = InetAddress.getByName(SERVER_IP).isReachable(2000);
                        Logger.d("Main", "Thread", "server reachable: " + isServerReachable);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    return null;
                }
            };

            try {
                asyncTask.execute().get();
            } catch (InterruptedException e) {
                e.printStackTrace();
            } catch (ExecutionException e) {
                e.printStackTrace();
            }
        }
    }

    private void showProgressDialog() {

        progressDialog = new ProgressDialog(MainActivity.this);
        progressDialog.setTitle(R.string.wait);
        progressDialog.setMessage(getString(R.string.checkingSever));
        progressDialog.setCancelable(true);
        progressDialog.setOnCancelListener(MainActivity.this);
        progressDialog.show();
    }

    private void hideProgressDialog() {
        progressDialog.dismiss();
    }

    private void CheckOnline() {
        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {

                try {
                    ServerSocket serverSocket = new ServerSocket(G.CHECK_ONLINE_MOBILES_PORT);
                    Socket socket = null;
                    Logger.d("MainActivity", "CheckOnline", "Start");

                    Online = true;

                    while (Online) {
                        Logger.d("MainActivity", "CheckOnline", "Listening  . .");
                        socket = serverSocket.accept();
                        Logger.d("MainActivity", "CheckOnline", "Socket Accepted . . .");
//                        socket.close();
                    }
                    socket.close();
                    serverSocket.close();
                } catch (IOException e) {
                    e.printStackTrace();
                    Logger.e("MainActivity", "CheckOnline", "Socket Exception");

                }

            }
        });
        thread.start();
    }

    @Override
    public void onCancel(DialogInterface dialog) {
        shouldCheckServer = false;
    }

    private boolean initName_IP() {
        preferences = getSharedPreferences("Setting", MODE_PRIVATE);
        if (preferences.getString("USER_NAME", "0").equals("0")
                || preferences.getString("SERVER_IP", "0").equals("0")) {
            return false;

        } else {
            Username = preferences.getString("USER_NAME", "0");
            SERVER_IP = preferences.getString("SERVER_IP", "0");


            return true;
        }

    }

    private boolean CheckRunApp() {

        initName_IP();

        if (Username.equals("0") ||
                SERVER_IP.equals("0")) {
            Intent intent = new Intent(MainActivity.this, Settings.class);
            startActivity(intent);
            return false;
        }

        started = true;

        showProgressDialog();
        checkServerReachable();
        hideProgressDialog();

        if (isServerReachable) {
            Logger.d("MainActivity", "CheckRunApp", "ServerReachable");
//            Btn_Connect.setEnabled(false);
            Btn_Connect.setVisibility(View.INVISIBLE);


            startCallListener();
            //startVideoCallListener();
            callButton.setVisibility(View.VISIBLE);
            videoCallButton.setVisibility(View.VISIBLE);
            Thread thread = new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        InetAddress ServerIp = InetAddress.getByName(SERVER_IP);
                        broadcastName(Username, ServerIp);
                        hasBroadcasted = true;
                    } catch (UnknownHostException e) {
                        e.printStackTrace();
                    }
                }
            });
            thread.start();
        } else {
            Toast.makeText(MainActivity.this, R.string.server_not_reachable, Toast.LENGTH_SHORT).show();
        }

        return true;
    }

    private boolean CheckWifiStatus() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo ni = cm.getActiveNetworkInfo();
        if (ni != null && ni.getType() == ConnectivityManager.TYPE_WIFI) {
            Logger.i("MainActivity", "CheckWifiStatus", "Wifi On");
            return true;
        }
        Toast.makeText(getApplicationContext(), R.string.network_not_available, Toast.LENGTH_LONG).show();
        Logger.i("MainActivity", "CheckWifiStatus", "Wifi Off");
        return false;

    }

    private void connectCallListenerSocket(){

        try {
            callListenerSocket = new DatagramSocket(G.BROADCAST_PORT);
            callListenerSocket.setSoTimeout(10000);
        } catch (SocketException e) {
            e.printStackTrace();
        }
    }
    private void closeCallListenerSocket(){
        callListenerSocket.disconnect();
        callListenerSocket.close();
    }

    private BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (CheckWifiStatus()) {
                //Toast.makeText(getApplicationContext(), " Wifi On", Toast.LENGTH_SHORT).show();
            } else {
                stopCallListener();
                //stopVideoCallListener();
                //Toast.makeText(getApplicationContext(), " Wifi Off", Toast.LENGTH_SHORT).show();
            }
        }
    };

}
