package com.example.fcc.udptest;

import android.app.Activity;
import android.media.CamcorderProfile;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.os.Environment;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.Button;

import java.io.IOException;

/**
 * Created by FCC on 12/3/2016.
 */

public class MakeVideoCallActivity extends Activity implements SurfaceHolder.Callback, View.OnClickListener {

    MediaRecorder mediaRecorder;
    SurfaceHolder surfaceHolder;
    boolean recording = false;
    private Button btnRecord;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_make_video_call);

        btnRecord = (Button) findViewById(R.id.btnRecord);
        btnRecord.setOnClickListener(this);

        mediaRecorder = new MediaRecorder();
        initRecorder();
    }

    private void initRecorder() {
        SurfaceView cameraView = (SurfaceView) findViewById(R.id.surfaceView);
        surfaceHolder = cameraView.getHolder();
        surfaceHolder.addCallback(this);
        surfaceHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);

        mediaRecorder.setAudioSource(MediaRecorder.AudioSource.DEFAULT);
        mediaRecorder.setVideoSource(MediaRecorder.VideoSource.DEFAULT);

        CamcorderProfile cpHigh = CamcorderProfile.get(CamcorderProfile.QUALITY_480P);
        mediaRecorder.setProfile(cpHigh);
        mediaRecorder.setOutputFile(Environment.getExternalStorageDirectory().getPath() + "/testCapture.mp4");
//        mediaRecorder.setMaxDuration();
//        mediaRecorder.setMaxFileSize();
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        prepareRecorder();
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {

    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        if (recording) {
            mediaRecorder.stop();
            recording = false;
        }
        mediaRecorder.release();
    }

    private void prepareRecorder() {
        mediaRecorder.setPreviewDisplay(surfaceHolder.getSurface());

        try {
            mediaRecorder.prepare();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onClick(View v) {
        if (recording) {
            mediaRecorder.stop();
            recording = false;

            //prepare to record again
            initRecorder();
            prepareRecorder();
        } else {
            recording = true;
            mediaRecorder.start();
        }
    }
}

