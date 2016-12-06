package com.example.hamid.androidvideochat;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import com.quickblox.core.QBCallbackImpl;
import com.quickblox.core.QBSettings;
import com.quickblox.core.result.Result;
import com.quickblox.module.auth.QBAuth;
import com.quickblox.module.users.QBUsers;
import com.quickblox.module.users.model.QBUser;
import com.quickblox.module.users.result.QBUserResult;

/**
 * Created with IntelliJ IDEA.
 * User: Andrew Dmitrenko
 * Date: 6/17/13
 * Time: 9:55 AM
 */
public class LoginActivity extends Activity {

    private final String FIRST_USER_PASSWORD = "videoChatUser1";
    private final String FIRST_USER_LOGIN = "videoChatUser1";
    private final String SECOND_USER_PASSWORD = "videoChatUser2";
    private final String SECOND_USER_LOGIN = "videoChatUser2";


    private final int firstUserId = 217738;
    private final String firstUserName = "first user";
    private final String secondUserName = "second user";
    private final int secondUserId = 217740;

    private ProgressDialog progressDialog;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.screen_login);
        QBSettings.getInstance().fastConfigInit("2944", "RfghS97xYA29Mha", "L2EXV6ut-vD8Zus");
        progressDialog = new ProgressDialog(this);
        progressDialog.setMessage(getString(R.string.please_wait));
        progressDialog.setCancelable(false);


        findViewById(R.id.loginByFirstUserBtn).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                progressDialog.show();
                authorize(FIRST_USER_LOGIN, FIRST_USER_PASSWORD);
            }
        });

        findViewById(R.id.loginBySecondUserBtn).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                progressDialog.show();
                authorize(SECOND_USER_LOGIN, SECOND_USER_PASSWORD);
            }
        });

//
//        byte[] xorAddress = new byte[]{00, 00, 01, 01, 01, 00, 00, 01};
//        Log.d("XOR__", VideoChatUtils.xorAddressToString(xorAddress));
//
//        String xor = "00010101 01010101";
//        Log.d("XOR__", Arrays.toString(VideoChatUtils.xorAddressToByteArray(xor.replace(" ", ""))));

    }

    @Override
    public void onResume() {
        progressDialog.dismiss();
        super.onResume();
    }

    private void authorize(final String login, final String password) {
        QBAuth.createSession(new QBCallbackImpl() {
            @Override
            public void onComplete(Result result) {
                if (result.isSuccess()) {
                    signIn(login, password);
                }
            }
        });
    }

    private void signIn(String login, final String password) {
        QBUsers.signIn(login, password, new QBCallbackImpl() {
            @Override
            public void onComplete(Result result) {
                if (result.isSuccess()) {
                    QBUser user = ((QBUserResult) result).getUser();
                    user.setPassword(password);
                    DataHolder.getInstance().setCurrentQbUser(((QBUserResult) result).getUser());
                    startUserListActivity();
                }
            }
        });
    }

    private void startUserListActivity() {
        Intent intent = new Intent(this, UserListActivity.class);
        intent.putExtra("userId", DataHolder.getInstance().getCurrentQbUser().getId() == firstUserId ? secondUserId : firstUserId);
        intent.putExtra("userName", DataHolder.getInstance().getCurrentQbUser().getId() == firstUserId ? secondUserName : firstUserName);
        startActivity(intent);
        finish();
    }
}