package com.example.fcc.udptest;

import android.app.Activity;
import android.content.Intent;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.View;
import android.widget.RelativeLayout;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketException;
import java.net.UnknownHostException;

import classes.Contacts;
import classes.DatabaseManagement;
import classes.DatabaseMap;
import classes.Logger;
import classes.RcyContactsAdapter;
import io.realm.Realm;
import io.realm.RealmResults;

import static com.example.fcc.udptest.G.EXTRA_C_Ip;
import static com.example.fcc.udptest.G.EXTRA_C_Name;

public class MainActivity extends Activity implements ContactManager.IRefreshRecycler {

    static final String LOG_TAG = "UDPchat";
    private static final int CALL_LISTENER_PORT = 50003;
    private static final int VIDEOCALL_LISTENER_PORT = 50004;
    private static final int CheckStatus = 50006;
    private static final int BUF_SIZE = 1024;
    private boolean STARTED = false;
    private boolean Refreshing = true;
    private boolean SERVER_RUNNING = true;
    private boolean LISTEN = false;
    private boolean LISTEN_Video = true;
    private RelativeLayout progressLayout;
    private RecyclerView rcy_contacts;
    RcyContactsAdapter adapter;

    ContactManager contactManager = new ContactManager();
    DatabaseManagement databaseManagement = new DatabaseManagement();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Realm.init(getApplicationContext());
        Logger.i("MainActivity", "onCreate", "Before Db G.contactsList.size()>> " + G.contactsList.size());
        initRealm();
        Logger.i("MainActivity", "onCreate", "After Db G.contactsList.size()>> " + G.contactsList.size());
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main_server);
        Logger.i("MainActivity", "onCreate", "onCreate");

        initViews();
//        contactManager.listen();
        contactManager.setRefreshRcyclerListener(MainActivity.this);
        refreshContacts();
        startCallListener();
        startVideoCallListener();
        Logger.i("MainActivity", "onCreate", "IP is >> " + getBroadcastIp());
    }

    private void initRealm() {

        Realm mRealm = Realm.getInstance(G.myConfig);
        for (DatabaseMap Db : mRealm.where(DatabaseMap.class).findAll()) {
            Contacts contacts = new Contacts();
            try {
                InetAddress address = InetAddress.getByName(Db.getC_ip());
                contacts.setC_Ip(address);
                contacts.setC_Name(Db.getC_Name());
                G.contactsList.add(contacts);
                Logger.i("MainActivity", "initRealm", "Add from Db to List >> " + Db.getC_Name() + " " + Db.getC_ip());
            } catch (UnknownHostException e) {
                e.printStackTrace();
            }


        }
        mRealm.beginTransaction();
        RealmResults<DatabaseMap> results = mRealm.where(DatabaseMap.class).findAll();
        results.deleteAllFromRealm();
        mRealm.commitTransaction();
    }

    private void initViews() {

        rcy_contacts = (RecyclerView) findViewById(R.id.rcy_contactslist);
        progressLayout = (RelativeLayout) findViewById(R.id.progressBarLayout);
        rcy_contacts.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false));
        adapter = new RcyContactsAdapter(G.contactsList, MainActivity.this);
        rcy_contacts.setAdapter(adapter);
        // rcy_contacts.invalidate();
        rcy_contacts.swapAdapter(adapter, true);
        //adapter.notifyAll();
        //refreshRcy();


        Logger.i("MainActivity", "initViews", "Done");

    }

    private void startCallListener() {
        Logger.i("MainActivity", "startCallListener", "Start");
        // Creates the listener thread
        LISTEN = true;
        Thread listener = new Thread(new Runnable() {

            @Override
            public void run() {

                try {
                    // Set up the socket and packet to receive
                    Logger.i("MainActivity", "startCallListener", "Incoming call listener started");
                    DatagramSocket socket = new DatagramSocket(CALL_LISTENER_PORT);
                    socket.setSoTimeout(10000);
                    byte[] buffer = new byte[BUF_SIZE];
                    DatagramPacket packet = new DatagramPacket(buffer, BUF_SIZE);
                    while (LISTEN) {
                        // Listen for incoming call requests
                        try {
                            Logger.i("MainActivity", "startCallListener", "Listening for incoming calls");
                            socket.receive(packet);
                            String data = new String(buffer, 0, packet.getLength());
                            Logger.i("MainActivity", "startCallListener", "\"Packet received from \" + packet.getAddress() + \" with contents: \" + data");
                            String action = data.substring(0, 4);
                            if (action.equals("CAL:")) {
                                // Received a call request. Start the ReceiveCallActivity
                                String address = packet.getAddress().toString();
                                String name = data.substring(4, packet.getLength());

                                Intent intent = new Intent(MainActivity.this, ReceiveCallActivity.class);
                                intent.putExtra(G.EXTRA_C_Name, name);
                                intent.putExtra(G.EXTRA_C_Ip, address.substring(1, address.length()));
                                startActivity(intent);
                            } else {
                                // Received an invalid request
                                Log.w(LOG_TAG, packet.getAddress() + " sent invalid message: " + data);
                            }
                        } catch (Exception e) {
                        }
                    }
                    Logger.i("MainActivity", "startCallListener", "Call Listener ending");
                    socket.disconnect();
                    socket.close();
                } catch (SocketException e) {
                    Logger.i("MainActivity", "startCallListener", "SocketException in listener " + e);
                }
            }
        });
        listener.start();
    }

    private void stopCallListener() {
        // Ends the listener thread
        LISTEN = false;
        Logger.i("MainActivity", "stopCallListener", "Done");
    }

    private void startVideoCallListener() {
        // Creates the listener thread
        LISTEN_Video = true;
        Thread listener = new Thread(new Runnable() {

            @Override
            public void run() {

                try {
                    // Set up the socket and packet to receive
                    Logger.i("MainActivity", "startVideoCallListener", "Incoming video call listener started");
                    DatagramSocket socket = new DatagramSocket(VIDEOCALL_LISTENER_PORT);
                    socket.setSoTimeout(10000);
                    byte[] buffer = new byte[BUF_SIZE];
                    DatagramPacket packet = new DatagramPacket(buffer, BUF_SIZE);
                    while (LISTEN_Video) {
                        // Listen for incoming call requests
                        try {
                            Logger.i("MainActivity", "startVideoCallListener", "Listening for incoming video calls");
                            socket.receive(packet);
                            String data = new String(buffer, 0, packet.getLength());
                            Logger.i("MainActivity", "startVideoCallListener", "Packet received from " + packet.getAddress() + " with contents: " + data);
                            String action = data.substring(0, 4);
                            if (action.equals("CAL:")) {

                                Logger.i("MainActivity", "startVideoCallListener", "**CAL received**");
                                // Received a call request. Start the ReceiveCallActivity
                                String address = packet.getAddress().toString();
                                String name = data.substring(4, packet.getLength());

                                Intent intent = new Intent(MainActivity.this, ReceiveVideoCallActivity.class);
                                intent.putExtra(EXTRA_C_Name, name);
                                intent.putExtra(EXTRA_C_Ip, address.substring(1, address.length()));
                                G.IN_CALL = true;

                                startActivity(intent);
                            } else {
                                // Received an invalid request
                                Logger.i("MainActivity", "startVideoCallListener", packet.getAddress() + " sent invalid message: " + data);
                            }
                        } catch (Exception e) {
                        }
                    }
                    Logger.i("MainActivity", "startVideoCallListener", "Video Call Listener ending");
                    socket.disconnect();
                    socket.close();
                } catch (SocketException e) {
                    Logger.i("MainActivity", "startVideoCallListener", "SocketException in listener " + e);
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
    protected void onResume() {
        contactManager.startListening();
        startCallListener();
        startVideoCallListener();
        Logger.i("MainActivity", "onResume", "G.contactsList.size()>> " + G.contactsList.size());
        super.onResume();
    }

    @Override
    public void onPause() {
        Logger.i("MainActivity", "onPause", "G.contactsList.size()>> " + G.contactsList.size());
        super.onPause();
        Log.i(LOG_TAG, "App paused!");
        SERVER_RUNNING = false;
        contactManager.stopListening();
        stopCallListener();
        stopVideoCallListener();
    }

    @Override
    public void onStop() {
        Logger.i("MainActivity", "onStop", "G.contactsList.size()>> " + G.contactsList.size());
        Log.i(LOG_TAG, "App stopped!");
//        stopCallListener();
//        stopVideoCallListener();
//        contactManager.stopListening();
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Logger.i("MainActivity", "onDestroy", "G.contactsList.size()>> " + G.contactsList.size());
        for (int i = 0; i < G.contactsList.size(); i++) {
            databaseManagement.addContact(G.contactsList.get(i).getC_Name()
                    , G.contactsList.get(i).getC_Ip());
            Logger.i("MainActivity", "initRealm", "Add from List to Db >> " + G.contactsList.get(i).getC_Name() + " " + G.contactsList.get(i).getC_Ip());
        }

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
        Logger.i("MainActivity", "onRestart", "G.contactsList.size()>> " + G.contactsList.size());

        super.onRestart();
        Log.i(LOG_TAG, "App restarted!");
        G.IN_CALL = false;
        STARTED = true;
        SERVER_RUNNING = true;
//        contactManager.listen();
        refreshContacts();
        startCallListener();
        startVideoCallListener();
        // refreshContacts();
    }

    private void refreshContacts() {

        Thread CheckStatusThread = new Thread(new Runnable() {

            @Override
            public void run() {
                while (SERVER_RUNNING) {
                    Logger.i("MainActivity", "refreshContacts", "SERVER_RUNNING >> " + SERVER_RUNNING);
                    // Looper.prepare();
                    Refreshing = true;

                    refreshRcy();
                    while (Refreshing) {
                        Logger.i("MainActivity", "refreshContacts", "Start");

                        for (int i = 0; i < G.contactsList.size(); i++) {
                            Socket socket = new Socket();
                            try {
                                socket.connect(new InetSocketAddress(G.contactsList.get(i).getC_Ip(), CheckStatus), 1000);

                                if (!socket.isConnected()) {
                                    socket.close();
                                    G.contactsList.remove(i);
                                }
                                socket.close();

                            } catch (IOException e) {
                                G.contactsList.remove(i);

                                e.printStackTrace();
                            }
                        }
                        Refreshing = false;
                        Logger.i("MainActivity", "refreshContacts", "Online Users >> " + G.contactsList.size());
                        try {
                            Thread.sleep(5000);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }


                }
            }
        });
        CheckStatusThread.start();


    }

    private void refreshRcy() {


        runOnUiThread(new Runnable() {
            @Override
            public void run() {

                if (G.contactsList.size() == 0) {
                    progressLayout.setVisibility(View.VISIBLE);
                } else {
                    progressLayout.setVisibility(View.INVISIBLE);
                }

                rcy_contacts.swapAdapter(adapter, true);

                Logger.i("MainActivity", "refreshRcy", "Start");
            }
        });
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
                ((ip >> 24) & 0xFF);

    }

    @Override
    public void OnRefresh() {
        refreshRcy();
    }

}
