package classes;

import java.net.InetAddress;

/**
 * Created by HaMiD on 12/10/2016.
 */

public class Contacts {


    InetAddress C_Ip;
    String C_Name;

    public Contacts(InetAddress c_Ip, String c_Name) {
        C_Ip = c_Ip;
        C_Name = c_Name;
    }

    public Contacts() {
    }

    public InetAddress getC_Ip() {
        return C_Ip;
    }

    public void setC_Ip(InetAddress c_Ip) {
        C_Ip = c_Ip;
    }

    public String getC_Name() {
        return C_Name;
    }

    public void setC_Name(String c_Name) {
        C_Name = c_Name;
    }
}
