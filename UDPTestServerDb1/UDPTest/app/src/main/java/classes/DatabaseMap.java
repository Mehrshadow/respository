package classes;

import io.realm.RealmObject;
import io.realm.annotations.Index;
import io.realm.annotations.PrimaryKey;

/**
 * Created by HaMiD on 12/14/2016.
 */

public class DatabaseMap extends RealmObject {


    private int ID;

    private String C_Name;

    private String C_ip;


    public int getID() {
        return ID;
    }

    public void setID(int ID) {
        this.ID = ID;
    }

    public String getC_Name() {
        return C_Name;
    }

    public void setC_Name(String c_Name) {
        C_Name = c_Name;
    }

    public String getC_ip() {
        return C_ip;
    }

    public void setC_ip(String c_ip) {
        C_ip = c_ip;
    }
}
