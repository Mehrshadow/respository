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
        Logger.d("DatabaseManagement", "AddContact", "Start");
        String ip = Ip.toString().substring(1);
        Realm mRealm = Realm.getInstance(G.myConfig);
        final RealmResults<DatabaseMap> results = mRealm.where(DatabaseMap.class).equalTo("C_ip",ip).findAll();
        Logger.d("DatabaseManagement", "AddContact", "results.size()>>"+results.size());
        if(results.size()==0){
            mRealm.beginTransaction();
            final DatabaseMap Db = mRealm.createObject(DatabaseMap.class);
            Db.setC_Name(Name);
            Db.setC_ip(ip);
            //mRealm.copyToRealm(Db1);
            mRealm.commitTransaction();
            Logger.d("DatabaseManagement", "addContact", "Added to Db >> " + Name + " >> " + Ip);

        }
        Logger.d("DatabaseManagement", "AddContact", "Is Already Exist >> " + Name + " >> " + Ip);

    }

    public void removeContact(DatabaseMap map) {
        Realm mRealm = Realm.getInstance(G.myConfig);
        final RealmResults<DatabaseMap> results = mRealm.where(DatabaseMap.class).equalTo("C_ip",map.getC_ip()).findAll();

        mRealm.beginTransaction();
        results.deleteAllFromRealm();
        mRealm.commitTransaction();


    }

}
