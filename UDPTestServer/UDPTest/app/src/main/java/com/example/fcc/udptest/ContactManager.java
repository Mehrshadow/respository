package com.example.fcc.udptest;

import android.util.Log;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import classes.Contacts;
import classes.Logger;

import static android.R.id.list;

public class ContactManager {
    private static final String LOG_TAG = "ContactManager";
    public static final int BROADCAST_PORT = 50001; // Socket on which packets are sent/received
    private static final int BROADCAST_INTERVAL = 3000; // Milliseconds
    private static final int BROADCAST_BUF_SIZE = 1024;
    private boolean BROADCAST = true;
    private boolean CheckContactExist = false;
    public static boolean LISTEN = true;
    private HashMap<String, InetAddress> contacts;
    private InetAddress broadcastIP;
    public static String displayName;


    public ContactManager() {
    }


    public void addContact(String name, InetAddress address) {

        //\\\\\\\\\\\\\\\\\\\\

            for (int i = 0; i < G.contactsList.size(); i++) {

                if (G.contactsList.get(i).getC_Ip().equals(address)) {
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
    //\\\\\\\\\\

    public void removeContact(String name) {
    /*    // If the contact is known to us, remove it
        if (contacts.containsKey(name)) {

            Log.i(LOG_TAG, "Removing contact: " + name);
            contacts.remove(name);

            Log.d(LOG_TAG, "remove1");
            updateContact.onReceive();
            Log.d(LOG_TAG, "remove2");

            Log.i(LOG_TAG, "#Contacts: " + contacts.size());
            return;
        }
        Log.i(LOG_TAG, "Cannot remove contact. " + name + " does not exist.");
        return;*/
    }

    public void bye(final String name) {
        // Sends a Bye notification to other devices
        Thread byeThread = new Thread(new Runnable() {

            @Override
            public void run() {

                try {
                    Log.i(LOG_TAG, "Attempting to broadcast BYE notification!");
                    String notification = "BYE:" + name;
                    byte[] message = notification.getBytes();
                    DatagramSocket socket = new DatagramSocket();
                    socket.setBroadcast(true);
                    DatagramPacket packet = new DatagramPacket(message, message.length, broadcastIP, BROADCAST_PORT);
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


    public void listen() {
        // Create the listener thread
        Log.i(LOG_TAG, "Listening started!");
        Thread listenThread = new Thread(new Runnable() {

            @Override
            public void run() {

                DatagramSocket socket;
                try {

                    // socket = new DatagramSocket(BROADCAST_PORT);
                    socket = new DatagramSocket(50002);
                } catch (SocketException e) {

                    Log.e(LOG_TAG, "SocketExcepion in listener: " + e);
                    return;
                }
                byte[] buffer = new byte[BROADCAST_BUF_SIZE];

                while (LISTEN) {

                    listen(socket, buffer);
                }
                Log.i(LOG_TAG, "Listener ending!");
                socket.disconnect();
                socket.close();
                return;
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
                        Logger.d("ContactManager", "listen | listen", "Listener received ADD request from >> " + packet.getAddress());
                        addContact(name, packet.getAddress());
                        Logger.d("ContactManager", "listen | listen", "Users >> " + G.contactsList.size());

                    } else if (action.equals("BYE:")) {
                        // Bye notification received. Attempt to remove contact
                        Logger.d("ContactManager", "listen | listen", "Listener received BYE request from >> " + packet.getAddress());
                        removeContact(data.substring(4, data.length()));

                    } else {
                        // Invalid notification received
                        Log.w(LOG_TAG, "Listener received invalid request: " + action);
                    }

                } catch (SocketTimeoutException e) {

                    Log.i(LOG_TAG, "No packet received!");
                    if (LISTEN) {

                        listen(socket, buffer);
                    }
                    return;
                } catch (SocketException e) {

                    Log.e(LOG_TAG, "SocketException in listen: " + e);
                    Log.i(LOG_TAG, "Listener ending!");
                    return;
                } catch (IOException e) {

                    Log.e(LOG_TAG, "IOException in listen: " + e);
                    Log.i(LOG_TAG, "Listener ending!");
                    return;
                }
            }
        });
        listenThread.start();
    }

    public void stopListening() {
        // Stops the listener thread
        LISTEN = false;
    }
}
