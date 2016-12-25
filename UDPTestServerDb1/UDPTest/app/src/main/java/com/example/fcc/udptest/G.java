package com.example.fcc.udptest;

import android.app.Application;

import com.facebook.stetho.Stetho;
import com.uphyca.stetho_realm.RealmInspectorModulesProvider;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import classes.Contacts;
import io.realm.RealmConfiguration;

/**
 * Created by FCC on 12/4/2016.
 */

public class G extends Application {

    public static List<Contacts> contactsList = new ArrayList<>();
    public static final int BROADCAST_PORT = 20000;

    public static final int CONTACTSYNC_PORT = 30000;

    public static final int CALL_SENDER_PORT = 40001;
    public static final int CALL_LISTENER_PORT = 40000;

    public static final int VIDEOCALL_LISTENER_PORT = 50000;
    public static final int VIDEOCALL_SENDER_PORT = 50001;

    public static final int CHECK_ONLINE_MOBILES_PORT = 60000;

    public static boolean IN_CALL = false;

    public static final int FRONT_CAMERA = 1, REAR_CAMERA = 0;

    public final static String EXTRA_C_Name = "CNAME";
    public final static String EXTRA_C_Ip = "CIP";
    public final static String EXTRA_DISPLAYNAME = "DISPNAME";
    public final static String EXTRA_CONTACT = "CONTACT";
    public final static String EXTRA_IP = "IP";

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
