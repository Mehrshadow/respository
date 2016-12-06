package com.example.hamid.androidvideochat;



/**
 * Created with IntelliJ IDEA.
 * User: Andrew Dmitrenko
 * Date: 6/17/13
 * Time: 10:17 AM
 */
public class DataHolder {

    private static DataHolder dataHolder;
    private QBUser currentQbUser;
    private QBUser userForVideoChat;
    private String address;
    private int port;


    private DataHolder() {
    }


    public static synchronized DataHolder getInstance() {
        if (dataHolder == null) {
            dataHolder = new DataHolder();
        }
        return dataHolder;
    }

    public void setCurrentQbUser(QBUser currentQbUser) {
        this.currentQbUser = currentQbUser;
    }

    public void setUserForVideoChat(QBUser userForVideoChat) {
        this.userForVideoChat = userForVideoChat;
    }

    public QBUser getUserForVideoChat() {
        return userForVideoChat;
    }

    public QBUser getCurrentQbUser() {
        return currentQbUser;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public String getAddress() {
        return address;
    }

    public int getPort() {
        return port;
    }
}
