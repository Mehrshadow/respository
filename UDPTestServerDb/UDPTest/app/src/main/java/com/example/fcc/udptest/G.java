package com.example.fcc.udptest;

import android.app.Application;
import android.os.Environment;

import com.facebook.stetho.Stetho;
import com.uphyca.stetho_realm.RealmInspectorModulesProvider;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import classes.Contacts;
import io.realm.Realm;
import io.realm.RealmConfiguration;

/**
 * Created by FCC on 12/4/2016.
 */

public class G extends Application {
    public static final String sendVideoPath = Environment.getExternalStorageDirectory() + "/Send_videoTest.flv";
    public static final String ReceiveVideoPath = Environment.getExternalStorageDirectory() + "/Receive_videoTest.flv";
    public static List<Contacts> contactsList = new ArrayList<>();
    public static final int BROADCAST_PORT = 50002;
    public static final int CONTACTSYNC_PORT = 50001; // Socket on which packets are sent/received
    public static boolean IN_CALL = false;

    public final static String EXTRA_C_Name = "CNAME";
    public final static String EXTRA_C_Ip = "CIP";
    public final static String EXTRA_DISPLAYNAME = "DISPNAME";

    public static RealmConfiguration myConfig = new RealmConfiguration.Builder()
            .name("Contacts")
            .schemaVersion(1)
            .build();

    @Override
    public void onCreate() {
        super.onCreate();

      //  Realm.init(getApplicationContext());

        Stetho.initialize(
                Stetho.newInitializerBuilder(this)
                        .enableDumpapp(Stetho.defaultDumperPluginsProvider(this))
                        .enableWebKitInspector(RealmInspectorModulesProvider.builder(this).build())
                        .build());

        RealmInspectorModulesProvider.builder(this)
                .withFolder(getCacheDir())
                .withEncryptionKey("encrypted.realm", null)
                .withMetaTables()
                .withDescendingOrder()
                .withLimit(1000)
                .databaseNamePattern(Pattern.compile(".+\\.realm"))
                .build();
    }
}
