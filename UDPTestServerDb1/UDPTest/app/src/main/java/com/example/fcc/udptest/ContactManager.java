package com.example.fcc.udptest;

import android.util.Log;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.SocketTimeoutException;

import classes.Contacts;
import classes.Logger;

public class ContactManager {
    private static final String LOG_TAG = "ContactManager";

    private static final int BROADCAST_BUF_SIZE = 1024;
    private boolean CheckContactExist = false;
    public static boolean LISTEN = true;
    private InetAddress broadcastIP;
    private IRefreshRecycler iRefreshRecycler;
    DatagramSocket socket;

    public interface IRefreshRecycler {
        void OnRefresh();
    }

    public void setRefreshRcyclerListener(IRefreshRecycler iRefreshRecycler) {
        this.iRefreshRecycler = iRefreshRecycler;
    }


    public ContactManager() {
    }

    public void addContact(String name, InetAddress address) {

        CheckContactExist = false;

        for (int i = 0; i < G.contactsList.size(); i++) {

            if (G.contactsList.get(i).getC_Ip().equals(address)) {
                Logger.d("ContactManager", "addContact", "Exist >> " + G.contactsList.get(i).getC_Ip());
                Logger.d("ContactManager", "addContact", "Receive >> " + address);
                G.contactsList.get(i).setC_Name(name);
                CheckContactExist = true;
                break;
            }
        }
        if (!CheckContactExist) {
            Contacts contacts = new Contacts();
            contacts.setC_Ip(address);
            contacts.setC_Name(name);
            G.contactsList.add(contacts);
            Logger.d("ContactManager", "addContact", "Add >> Name = " + name + " & Ip = " + address);
        }
        Logger.d("ContactManager", "addContact", "Already Exist  >> Name = " + name + " & Ip = " + address);


    }

    public void removeContact(String name, InetAddress address) {
        for (int i = 0; i < G.contactsList.size(); i++) {

            if (G.contactsList.get(i).getC_Ip().equals(address)) {
                CheckContactExist = true;
                break;
            }
        }
        if (!CheckContactExist) {
            Contacts contacts = new Contacts();
            contacts.setC_Ip(address);
            contacts.setC_Name(name);
            G.contactsList.remove(contacts);
            Logger.d("ContactManager", "remove contact", "remove >> Name = " + name + " & Ip = " + address);
        }
        Logger.d("ContactManager", "Remove failed", "Already Exist  >> Name = " + name + " & Ip = " + address);

    }

    public void listen() {
        // Create the listener thread

        LISTEN = true;

        Log.i(LOG_TAG, "Listening started!");
        Thread listenThread = new Thread(new Runnable() {

            @Override
            public void run() {
                Logger.d("ContactManager", "listen | listen", "Listening started!");

                try {

                    socket = new DatagramSocket(G.CONTACTSYNC_PORT);
                } catch (SocketException e) {

                    Log.e(LOG_TAG, "SocketException in listener: " + e);
                    return;
                }
                byte[] buffer = new byte[BROADCAST_BUF_SIZE];

                while (LISTEN) {

                    listen(socket, buffer);
                }
                Log.i(LOG_TAG, "Listener ending!");
            }

            public void listen(DatagramSocket socket, byte[] buffer) {

                try {
                    //Listen in for new notifications
                    Logger.d("ContactManager", "listen | listen", "Listening for a packet!");
                    DatagramPacket packet = new DatagramPacket(buffer, BROADCAST_BUF_SIZE);
                    socket.setSoTimeout(15000);
                    socket.receive(packet);
                    String data = new String(buffer, 0, packet.getLength());
                    Log.i(LOG_TAG, "Packet received: " + data);
                    String action = data.substring(0, 4);
                    String name = data.substring(4, data.length());
                    Log.d(LOG_TAG, name);

                    if (action.equals("ADD:")) {
                        // Add notification received. Attempt to add contact
                        Logger.d("ContactManager", "listen | listen", "Listener received ADD request from >> " + packet.getAddress()  + " name: " + name);
                        addContact(name, packet.getAddress());

                        iRefreshRecycler.OnRefresh();

                        Logger.d("ContactManager", "listen | listen", "Users >> " + G.contactsList.size());

                    } else if (action.equals("BYE:")) {
                        // Bye notification received. Attempt to remove contact
                        Logger.d("ContactManager", "listen | listen", "Listener received BYE request from >> " + packet.getAddress());
                        removeContact(name, packet.getAddress());

                    } else {
                        // Invalid notification received
                        Log.w(LOG_TAG, "Listener received invalid request: " + action);
                    }

                } catch (SocketTimeoutException e) {

                    Log.i(LOG_TAG, "No packet received!");
                    if (LISTEN) {

                        listen(socket, buffer);
                    }
                } catch (SocketException e) {

                    Logger.d("ContactManager", "listen | listen", "Listener ending!");
                    Logger.d("ContactManager", "listen | listen", "SocketException in listen:" + e);

                } catch (IOException e) {

                    Logger.d("ContactManager", "listen | listen", "IOException in listen: " + e);
                    Logger.d("ContactManager", "listen | listen", "Listener ending!");
                }
            }
        });
        listenThread.start();
    }

    public void stopListening() {
        Logger.d("ContactManager", "stopListening", "stopListening");
        // Stops the listener thread
        LISTEN = false;
        socket.disconnect();
        socket.close();

    }


    /////////////\\\\\\\\\\\\\\\\\\\
    public void startListening() {
        Logger.d("ContactManager", "startListening", "startListening");
        // Stops the listener thread
        listen();
    }
}
