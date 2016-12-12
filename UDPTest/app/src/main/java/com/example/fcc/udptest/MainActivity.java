package com.example.fcc.udptest;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.widget.Button;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.Socket;
import java.net.SocketException;

import classes.Logger;
import classes.RcyContactsAdapter;

public class MainActivity extends Activity implements ContactManager.IUpdateContact {

    static final String LOG_TAG = "UDPchat";
    private static final int CALL_LISTENER_PORT = 50003;
    private static final int VIDEOCALL_LISTENER_PORT = 50004;
    private static final int CheckStatus = 50006;
    private static final int BUF_SIZE = 1024;
    private String displayName;
    private boolean STARTED = false;
    private boolean IN_CALL = false;
    private boolean Refreshing = true;
    public static boolean IN_VIDEO_CALL = false;
    private boolean LISTEN = false;
    private boolean LISTEN_Video = true;

    public final static String EXTRA_CONTACT = "hw.dt83.udpchat.CONTACT";
    public final static String EXTRA_IP = "hw.dt83.udpchat.IP";
    public final static String EXTRA_DISPLAYNAME = "hw.dt83.udpchat.DISPLAYNAME";
    public final static String EXTRA_BUFF_SIZE = "BUFF_SIZE";
    private String contact;
    private InetAddress ip;

    private Button startButton, callButton, videoCallButton;
    private ScrollView scrollView;
    private RadioGroup radioGroup;
    private RecyclerView rcy_contacts;

    ContactManager contactManager = new ContactManager();

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main_server);
        Logger.i("MainActivity","onCreate","onCreate");
        initViews();
        startCallListener();
        startVideoCallListener();
        contactManager.listen();
        refreshContacts();

    }

    private void initViews() {

        rcy_contacts = (RecyclerView) findViewById(R.id.rcy_contacts);
        callButton = (Button) findViewById(R.id.buttonCall);
        videoCallButton = (Button) findViewById(R.id.btnVideoCall);

        RecyclerView.LayoutManager layoutManager = new LinearLayoutManager(getApplicationContext());
        rcy_contacts.setLayoutManager(layoutManager);
        rcy_contacts.setHasFixedSize(true);
        RcyContactsAdapter adapter = new RcyContactsAdapter(G.contactsList);
        rcy_contacts.setAdapter(adapter);
        Logger.i("MainActivity","initViews","Done");

    }

    private void startCallListener() {
        Logger.i("MainActivity","startCallListener","Start");
        // Creates the listener thread
        LISTEN = true;
        Thread listener = new Thread(new Runnable() {

            @Override
            public void run() {

                try {
                    // Set up the socket and packet to receive
                    Logger.i("MainActivity","startCallListener","Incoming call listener started");
                    DatagramSocket socket = new DatagramSocket(CALL_LISTENER_PORT);
                    socket.setSoTimeout(1000);
                    byte[] buffer = new byte[BUF_SIZE];
                    DatagramPacket packet = new DatagramPacket(buffer, BUF_SIZE);
                    while (LISTEN) {
                        // Listen for incoming call requests
                        try {
                            Logger.i("MainActivity","startCallListener","Listening for incoming calls");
                            socket.receive(packet);
                            String data = new String(buffer, 0, packet.getLength());
                            Logger.i("MainActivity","startCallListener","\"Packet received from \" + packet.getAddress() + \" with contents: \" + data");
                            String action = data.substring(0, 4);
                            if (action.equals("CAL:")) {
                                // Received a call request. Start the ReceiveCallActivity
                                String address = packet.getAddress().toString();
                                String name = data.substring(4, packet.getLength());

                                Intent intent = new Intent(MainActivity.this, ReceiveCallActivity.class);
                                intent.putExtra(EXTRA_CONTACT, name);
                                intent.putExtra(EXTRA_IP, address.substring(1, address.length()));
                                IN_CALL = true;
                                startActivity(intent);
                            } else {
                                // Received an invalid request
                                Log.w(LOG_TAG, packet.getAddress() + " sent invalid message: " + data);
                            }
                        } catch (Exception e) {
                        }
                    }
                    Logger.i("MainActivity","startCallListener","Call Listener ending");
                    socket.disconnect();
                    socket.close();
                } catch (SocketException e) {
                    Logger.i("MainActivity","startCallListener","SocketException in listener " + e);
                }
            }
        });
        listener.start();
    }

    private void stopCallListener() {
        // Ends the listener thread
        LISTEN = false;
        Logger.i("MainActivity","stopCallListener","Done");
    }

    private void startVideoCallListener() {
        // Creates the listener thread
        LISTEN_Video = true;
        Thread listener = new Thread(new Runnable() {

            @Override
            public void run() {

                try {
                    // Set up the socket and packet to receive
                    Logger.i("MainActivity","startVideoCallListener","Incoming video call listener started");
                    DatagramSocket socket = new DatagramSocket(VIDEOCALL_LISTENER_PORT);
                    socket.setSoTimeout(1000);
                    byte[] buffer = new byte[BUF_SIZE];
                    DatagramPacket packet = new DatagramPacket(buffer, BUF_SIZE);
                    while (LISTEN_Video) {
                        // Listen for incoming call requests
                        try {
                            Logger.i("MainActivity","startVideoCallListener","Listening for incoming video calls");
                            socket.receive(packet);
                            String data = new String(buffer, 0, packet.getLength());
                            Logger.i("MainActivity","startVideoCallListener","Packet received from " + packet.getAddress() + " with contents: " + data);
                            String action = data.substring(0, 4);
                            if (action.equals("CAL:")) {

                                Logger.i("MainActivity","startVideoCallListener","**CAL received**");
                                // Received a call request. Start the ReceiveCallActivity
                                String address = packet.getAddress().toString();
                                String name = data.substring(4, packet.getLength());
                                String[] rcvData = name.split("___");

                                name = rcvData[0];
                                int buff_size = Integer.parseInt(rcvData[1]);
                                Log.d(LOG_TAG, buff_size + "");

                                Intent intent = new Intent(MainActivity.this, ReceiveVideoCallActivity.class);
                                intent.putExtra(EXTRA_CONTACT, name);
                                intent.putExtra(EXTRA_IP, address.substring(1, address.length()));
                                intent.putExtra(EXTRA_BUFF_SIZE, buff_size);
                                IN_VIDEO_CALL = true;

                                startActivity(intent);
                            } else {
                                // Received an invalid request
                                Logger.i("MainActivity","startVideoCallListener",packet.getAddress() + " sent invalid message: " + data);
                            }
                        } catch (Exception e) {
                        }
                    }
                    Logger.i("MainActivity","startVideoCallListener","Video Call Listener ending");
                    socket.disconnect();
                    socket.close();
                } catch (SocketException e) {
                    Logger.i("MainActivity","startVideoCallListener","SocketException in listener " + e);
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
        Log.i(LOG_TAG, "App paused!");
    }

    @Override
    public void onStop() {

        super.onStop();
        Log.i(LOG_TAG, "App stopped!");
        stopCallListener();
        stopVideoCallListener();
        if (!IN_CALL && !IN_VIDEO_CALL) {
            finish();
        }
    }

    @Override
    public void onRestart() {

        super.onRestart();
        Log.i(LOG_TAG, "App restarted!");
        IN_CALL = false;
        IN_VIDEO_CALL = false;
        STARTED = true;

        contactManager.listen();
        startCallListener();
        startVideoCallListener();
        refreshContacts();
    }

    @Override
    public void onReceive() {
        Log.d(LOG_TAG, "OnReceive1");
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                refreshContacts();
            }
        });
        Log.d(LOG_TAG, "OnReceive2");
    }


    public void MakeVoiceCall(String C_Name, InetAddress C_Ip) {

        IN_CALL = true;

        // Send this information to the MakeCallActivity and start that activity
        Intent intent = new Intent(MainActivity.this, MakeCallActivity.class);
        intent.putExtra(EXTRA_CONTACT, contact);
        intent.putExtra(EXTRA_IP, C_Ip);
        intent.putExtra(EXTRA_DISPLAYNAME, displayName);
        startActivity(intent);

        Log.d("btn call", "remove all views");
        radioGroup.removeAllViews();
    }

    public void MakeVideoCall(String C_Name, InetAddress C_Ip) {

        IN_VIDEO_CALL = true;

        Intent i = new Intent(MainActivity.this, MakeVideoCallActivity.class);
        i.putExtra(EXTRA_CONTACT, C_Name);
        i.putExtra(EXTRA_IP, C_Ip);
        i.putExtra(EXTRA_DISPLAYNAME, "SERVER");
        startActivity(i);

        Log.d("btn video call", "remove all views");
        radioGroup.removeAllViews();
    }

    private void refreshContacts() {

        Thread CheckStatusThread = new Thread(new Runnable() {
            @Override
            public void run() {
                while (Refreshing) {
                    Logger.i("MainActivity","refreshContacts","Start");
                    for (int i = 0; i < G.contactsList.size(); i++) {
                        try {
                            Socket socket = new Socket(G.contactsList.get(i).getC_Ip(), CheckStatus);
                            if (!socket.isConnected()) {
                                G.contactsList.remove(i);
                            }
                        } catch (SocketException e) {
                            e.printStackTrace();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                    try {
                        Thread.sleep(2000);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }
        });
        CheckStatusThread.start();


    }


}
