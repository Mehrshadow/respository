package classes;

/**
 * Created by HaMiD on 12/14/2016.
 */

import com.example.fcc.udptest.G;

import java.net.InetAddress;

import io.realm.Realm;
import io.realm.RealmResults;

/**
 * Created by HaMiD on 12/14/2016.
 */

public class DatabaseManagement {

    public void addContact(final String Name, final InetAddress Ip) {

        final Realm mRealm = Realm.getInstance(G.myConfig);
        final DatabaseMap Db = new DatabaseMap();
        String ip = Ip.toString().substring(1);
        Db.setC_Name(Name);
        Db.setC_ip(ip);

        Logger.d("DatabaseManagement", "ddContact", "Start");
        mRealm.beginTransaction();
        DatabaseMap Db1 = mRealm.createObject(DatabaseMap.class);
        Db1.setC_Name(Name);
        Db1.setC_ip(ip);
        mRealm.commitTransaction();
        Logger.d("DatabaseManagement", "addContact", "Added to Db >> " + Name + " >> " + Ip);


    }

    public void removeContact(DatabaseMap map) {
        Realm mRealm = Realm.getInstance(G.myConfig);
        final RealmResults<DatabaseMap> results = mRealm.where(DatabaseMap.class).equalTo("C_ip",map.getC_ip()).findAll();

        mRealm.beginTransaction();
        results.deleteAllFromRealm();
        mRealm.commitTransaction();


    }

}
