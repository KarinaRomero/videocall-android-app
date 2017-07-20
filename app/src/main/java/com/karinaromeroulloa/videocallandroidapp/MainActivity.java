/**
 * Created by Karina Romero on 18/07/2016.
 */
package com.karinaromeroulloa.videocallandroidapp;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Point;
import android.opengl.GLSurfaceView;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.view.WindowManager.LayoutParams;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import org.webrtc.MediaStream;
import org.webrtc.VideoRenderer;
import org.webrtc.VideoRendererGui;

import java.util.List;

import de.tavendo.autobahn.WebSocketException;
/**
 * This class contains the code to display a video call on the screen.
 * @author Karina Romero
 */
public class MainActivity extends Activity implements WebRtcClient.RtcListener {

    private final static int VIDEO_CALL_SENT = 666;
    private static final String VIDEO_CODEC_VP9 = "VP9";
    private static final String AUDIO_CODEC_OPUS = "opus";
    // Local preview screen position before call is connected.
    private static final int LOCAL_X_CONNECTING = 0;
    private static final int LOCAL_Y_CONNECTING = 0;
    private static final int LOCAL_WIDTH_CONNECTING = 100;
    private static final int LOCAL_HEIGHT_CONNECTING = 100;
    // Local preview screen position after call is connected.
    private static final int LOCAL_X_CONNECTED = 72;
    private static final int LOCAL_Y_CONNECTED = 72;
    private static final int LOCAL_WIDTH_CONNECTED = 25;
    private static final int LOCAL_HEIGHT_CONNECTED = 25;
    // Remote video screen position
    private static final int REMOTE_X = 0;
    private static final int REMOTE_Y = 0;
    private static final int REMOTE_WIDTH = 100;
    private static final int REMOTE_HEIGHT = 100;
    private VideoRendererGui.ScalingType scalingType = VideoRendererGui.ScalingType.SCALE_ASPECT_FILL;
    private GLSurfaceView vsv;
    private VideoRenderer.Callbacks localRender;
    private VideoRenderer.Callbacks remoteRender;
    private WebRtcClient client;
    private String callerId;
    private String wsuri = "ws://192.168.1.75:8888";
    private String username;
    private Button btnCall;
    private Button btnHangUp;
    private EditText edtNameCall;
    private String callName;

    /**
     * This method creates all the elements of the activity, buttons, text input and view where the video call is displayed.
     *
     * @param savedInstanceState
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().addFlags(
                LayoutParams.FLAG_FULLSCREEN
                        | LayoutParams.FLAG_KEEP_SCREEN_ON
                        | LayoutParams.FLAG_DISMISS_KEYGUARD
                        | LayoutParams.FLAG_SHOW_WHEN_LOCKED
                        | LayoutParams.FLAG_TURN_SCREEN_ON);
        setContentView(R.layout.activity_main);

        edtNameCall = (EditText) findViewById(R.id.edtNameCall);
        btnCall = (Button) findViewById(R.id.btnCall);
        btnHangUp = (Button) findViewById(R.id.btnHangUp);


        btnCall.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                callName = edtNameCall.getText().toString();

                if (callName != null) {
                    client.call(callName);
                } else {
                    Toast.makeText(getApplicationContext(), "Debes llenar el campo", Toast.LENGTH_SHORT).show();
                }
            }
        });
        btnHangUp.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Log.d("name", username);
                if (callName != null) {
                    client.hangUp(username);
                } else {
                    Toast.makeText(getApplicationContext(), "Debes llenar el campo", Toast.LENGTH_SHORT).show();
                }
            }
        });

        vsv = (GLSurfaceView) findViewById(R.id.glview_call);
        vsv.setPreserveEGLContextOnPause(true);
        vsv.setKeepScreenOn(true);
        VideoRendererGui.setView(vsv, new Runnable() {
            @Override
            public void run() {
                init();
            }
        });

        // local and remote render
        remoteRender = VideoRendererGui.create(
                REMOTE_X, REMOTE_Y,
                REMOTE_WIDTH, REMOTE_HEIGHT, scalingType, true);
        localRender = VideoRendererGui.create(
                LOCAL_X_CONNECTING, LOCAL_Y_CONNECTING,
                LOCAL_WIDTH_CONNECTING, LOCAL_HEIGHT_CONNECTING, scalingType, true);

        final Intent intent = getIntent();
        final String action = intent.getAction();

        //obtains the user name
        Bundle bundle = intent.getExtras();
        username = (String) bundle.get("userName");

        if (Intent.ACTION_VIEW.equals(action)) {
            final List<String> segments = intent.getData().getPathSegments();
            callerId = segments.get(0);
            Log.d("CallerID", callerId);
        }
    }

    /**
     * This method is to initialize the parameters of the video call.
     */
    private void init() {
        Point displaySize = new Point();
        getWindowManager().getDefaultDisplay().getSize(displaySize);
        //params of video call
        PeerConnectionParameters params = new PeerConnectionParameters(
                true, false, displaySize.x, displaySize.y, 30, 1, VIDEO_CODEC_VP9, true, 1, AUDIO_CODEC_OPUS, true);

        try {
            Log.d("init", params.toString());
            client = new WebRtcClient(this, wsuri, params, VideoRendererGui.getEGLContext(), username);
        } catch (WebSocketException e) {
            e.printStackTrace();
        }
    }

    /**
     * Pause the video call when changing screen
     */
    @Override
    public void onPause() {
        super.onPause();
        vsv.onPause();
        if (client != null) {
            client.onPause();
        }
    }

    /**
     * If any element of the graphical interface has changed while the activity was in the background this method is called.
     */
    @Override
    public void onResume() {
        super.onResume();
        vsv.onResume();
        if (client != null) {
            client.onResume();
        }
    }

    /***
     * Eliminates any background process video call.
     */
    @Override
    public void onDestroy() {
        if (client != null) {
            client.onDestroy();
        }
        super.onDestroy();
    }

    /**
     * This method is called by the WebRTC library, go to the function of
     * the WebRtcClient setCamera class, verifies that there is a connection.
     *
     * @param callId ID de la video llamda
     */
    @Override
    public void onCallReady(String callId) {
        if (callerId != null) {
            try {
                client.setCamera();
            } catch (Exception e) {
                e.printStackTrace();
            }
        } else {
        }
    }

    /**
     * This method calls the method setCamera if the activity is in video call.
     *
     * @param requestCode the code requiest happened this method
     * @param resultCode  A result code specified by the second activity
     * @param data        the intention to pass this data.
     */
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        Log.d("CODECODE", String.valueOf(requestCode));
        if (requestCode == VIDEO_CALL_SENT) {
            client.setCamera();
        }
    }

    /**
     * This method creates a toast with the states of the vieo call.
     *
     * @param newStatus String whit the estate.
     */
    @Override
    public void onStatusChanged(final String newStatus) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(getApplicationContext(), newStatus, Toast.LENGTH_SHORT).show();
            }
        });
    }

    /**
     * Method that receives the local stream, is called by WebRTC, to add the streamLocal.
     *
     * @param localStream Stream local.
     */
    @Override
    public void onLocalStream(MediaStream localStream) {
        localStream.videoTracks.get(0).addRenderer(new VideoRenderer(localRender));
        VideoRendererGui.update(localRender,
                LOCAL_X_CONNECTING, LOCAL_Y_CONNECTING,
                LOCAL_WIDTH_CONNECTING, LOCAL_HEIGHT_CONNECTING,
                scalingType, true);
    }

    /**
     * Method adds the remote stream for display on view.
     *
     * @param remoteStream stream Remote.
     * @param endPoint     remote stream identifier
     */
    @Override
    public void onAddRemoteStream(MediaStream remoteStream, int endPoint) {
        remoteStream.videoTracks.get(0).addRenderer(new VideoRenderer(remoteRender));
        VideoRendererGui.update(remoteRender,
                REMOTE_X, REMOTE_Y,
                REMOTE_WIDTH, REMOTE_HEIGHT, scalingType, true);
        VideoRendererGui.update(localRender,
                LOCAL_X_CONNECTED, LOCAL_Y_CONNECTED,
                LOCAL_WIDTH_CONNECTED, LOCAL_HEIGHT_CONNECTED,
                scalingType, true);
    }

    /**
     * Method remove the remote stream for display on view.
     *
     * @param endPoint remote stream identifier.
     */
    @Override
    public void onRemoveRemoteStream(int endPoint) {
        VideoRendererGui.update(localRender,
                LOCAL_X_CONNECTING, LOCAL_Y_CONNECTING,
                LOCAL_WIDTH_CONNECTING, LOCAL_HEIGHT_CONNECTING,
                scalingType, true);
    }
}
