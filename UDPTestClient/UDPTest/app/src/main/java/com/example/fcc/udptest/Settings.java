package com.example.fcc.udptest;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

public class Settings extends AppCompatActivity implements View.OnClickListener {
    EditText Edt_Setting_name;
    EditText Edt_Setting_Ip;
    Button Btn_Setting_Save;
    private final int MODE_PRIVATE = 0;
    SharedPreferences preferences;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        preferences = getSharedPreferences("Setting", MODE_PRIVATE);
        initView();
    }

    private void initView() {
        Edt_Setting_Ip = (EditText) findViewById(R.id.etSetiingsServerIP);
        Edt_Setting_name = (EditText) findViewById(R.id.etSettingsName);
        Btn_Setting_Save = (Button) findViewById(R.id.btn_setting_save);

        Edt_Setting_Ip.setText(preferences.getString("SERVER_IP", "0.0.0.0"));
        Edt_Setting_name.setText(preferences.getString("USER_NAME", ""));

        Btn_Setting_Save.setOnClickListener(this);
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {

            case R.id.btn_setting_save:
                if (checkValeus()) {

                    String lastIP = G.ServerIp;

                    G.ServerIp = Edt_Setting_Ip.getText().toString();
                    G.UserName = Edt_Setting_name.getText().toString();
                    doSetting();
                    Toast.makeText(getApplicationContext(), "Data Saved", Toast.LENGTH_SHORT).show();


                    if (!lastIP.equals(G.ServerIp)) {
                        G.isIPChanged = true;
                    }
                    startActivity(new Intent(Settings.this, MainActivity.class));
                    finish();
                }
                break;
        }
    }

    private void doSetting() {

        preferences = getSharedPreferences("Setting", MODE_PRIVATE);
        SharedPreferences.Editor editor = preferences.edit();
        editor.putString("SERVER_IP", G.ServerIp);
        editor.putString("USER_NAME", G.UserName);
        editor.apply();
    }

    public static boolean isNumeric(String string) {
        for (char c : string.toCharArray()) {
            if (c < '0' || c > '9') {
                return false;
            }
        }
        return true;
    }

    private boolean checkValeus() {
        if (!Edt_Setting_Ip.getText().toString().isEmpty() && !Edt_Setting_name.getText().toString().isEmpty()) {
            String[] splitedIP = Edt_Setting_Ip.getText().toString().split("\\.");
            if (splitedIP.length == 4) {
                for (int i = 0; i < splitedIP.length; i++) {
                    if (!isNumeric(splitedIP[i]) && (splitedIP[i].length() > 3)) {
                        Toast.makeText(getApplicationContext(), R.string.ip_not_valid, Toast.LENGTH_LONG).show();
                        return false;
                    }
                }
            } else {
                Toast.makeText(getApplicationContext(), R.string.ip_not_valid, Toast.LENGTH_LONG).show();
                return false;
            }

        } else {
            Toast.makeText(getApplicationContext(), "Please Enter Info", Toast.LENGTH_LONG).show();
            return false;
        }
        return true;
    }
}