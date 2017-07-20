/**
 * Created by Karina Romero on 18/07/2016.
 */
package com.karinaromeroulloa.videocallandroidapp;

import android.opengl.EGLContext;
import android.util.Log;
import org.json.JSONException;
import org.json.JSONObject;
import org.webrtc.AudioSource;
import org.webrtc.DataChannel;
import org.webrtc.IceCandidate;
import org.webrtc.MediaConstraints;
import org.webrtc.MediaStream;
import org.webrtc.PeerConnection;
import org.webrtc.PeerConnectionFactory;
import org.webrtc.SdpObserver;
import org.webrtc.SessionDescription;
import org.webrtc.VideoCapturer;
import org.webrtc.VideoCapturerAndroid;
import org.webrtc.VideoSource;
import java.util.HashMap;
import java.util.LinkedList;
import de.tavendo.autobahn.WebSocketConnection;
import de.tavendo.autobahn.WebSocketException;
import de.tavendo.autobahn.WebSocketHandler;

/**
 * This class contains the code to create a client WebRTC.
 *
 * @author Karina Romero
 */

public class WebRtcClient{

    private final static String TAG = WebRtcClient.class.getCanonicalName();
    private final static int MAX_PEER = 2;//class Peer
    private boolean[] endPoints = new boolean[MAX_PEER];
    private PeerConnectionFactory factory;
    private HashMap<String, Peer> peers = new HashMap<>();
    private LinkedList<PeerConnection.IceServer> iceServers = new LinkedList<>();
    private PeerConnectionParameters pcParams;
    private MediaConstraints pcConstraints = new MediaConstraints();
    private MediaStream localMS;
    private VideoSource videoSource;
    private RtcListener mListener;
    private final WebSocketConnection mConnection = new WebSocketConnection();//class MenssageHandler
    public Peer peerRemote;// class MessageHandler-webRtcClient
    public SessionDescription sdp;// class MessageHandler-Peer
    public IceCandidate candidate;// class MessageHandler-Peer
    public String name;//class MessageHandler-Peer

    /**
     * Implement this interface to be notified of events.
     */
    public interface RtcListener {
        void onCallReady(String callId);

        void onStatusChanged(String newStatus);

        void onLocalStream(MediaStream localStream);

        void onAddRemoteStream(MediaStream remoteStream, int endPoint);

        void onRemoveRemoteStream(int endPoint);
    }


    /**
     * This class contains all the code that creates the WebSocket connection handles messages sent and received by signaling.
     *
     * @author Karina Romero
     */
    private class MessageHandler {

        private String wsuri;
        private String userName;

        /***
         * Class constructor initializes the parameters; IP address and user name
         *
         * @param wsuri    IP adress
         * @param userName user name
         */

        private MessageHandler(String wsuri, String userName) {
            this.wsuri = wsuri;
            this.userName = userName;
        }

        /***
         * WebSocket connection method
         *
         * @throws Exception
         */

        private void webConnection() throws WebSocketException {
            mConnection.connect(wsuri, new WebSocketHandler() {

                /**
                 * WebSocketHandler class method that initializes the connection, sends a message to the signaling login.
                 */
                @Override
                public void onOpen() {
                    Log.d(TAG, "Status: Connected to " + wsuri);
                    JSONObject message = new JSONObject();
                    try {
                        message.put("type", "login");
                        message.put("name", userName);
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                    mConnection.sendTextMessage(message.toString());
                }

                /**
                 * WebSocketHandler method of the class was listening to receive messages sent by the signaling.
                 *
                 * @param payload Message received by the signaling.
                 */
                @Override
                public void onTextMessage(String payload) {
                    Log.d(TAG, "Got echo: " + payload);
                    JSONObject message = null;
                    try {
                        message = new JSONObject(payload);

                        switch (message.getString("type")) {
                            case "login":

                                if (message.getString("success") == "true") {
                                    Log.d("login", "Sin problema");

                                } else {
                                    Log.d("login", "problemas al conectar con signaling");
                                }
                                break;
                            case "offer":
                                Log.d("offer", "Creando Answer");
                                if (name == null) {
                                    name = message.getString("name");
                                }
                                setCamera();
                                peerRemote = new Peer(name, 0);
                                Log.d("peers", peers.toString());

                                sdp = new SessionDescription(
                                        SessionDescription.Type.fromCanonicalForm("OFFER"),
                                        message.getJSONObject("offer").getString("sdp")
                                );
                                peerRemote.pc.setRemoteDescription(peerRemote, sdp);
                                peerRemote.pc.createAnswer(peerRemote, pcConstraints);

                                Log.d("offer", sdp.description);
                                mListener.onCallReady("call1");
                                peerRemote.pc.addStream(localMS);
                                peers.put(name, peerRemote);
                                Log.d("SDPLOCAL: ", peerRemote.pc.getLocalDescription().description);
                                JSONObject jsonSDP = new JSONObject();
                                jsonSDP.put("type", "answer");
                                jsonSDP.put("sdp", peerRemote.pc.getLocalDescription().description);
                                JSONObject jsonObject = new JSONObject();
                                jsonObject.put("type", "answer");
                                jsonObject.put("answer", jsonSDP);
                                jsonObject.put("name", name);
                                mConnection.sendTextMessage(jsonObject.toString());
                                break;
                            case "answer":
                                peerRemote = peers.get(name);
                                sdp = new SessionDescription(
                                        SessionDescription.Type.fromCanonicalForm("ANSWER"),
                                        message.getJSONObject("answer").getString("sdp")
                                );
                                peerRemote.pc.setRemoteDescription(peerRemote, sdp);
                                mListener.onCallReady("call1");
                                break;
                            case "candidate":
                                PeerConnection pc = peers.get(name).pc;
                                Log.d("candidate", message.getString("candidate"));
                                String cand = message.getJSONObject("candidate").getString("candidate");
                                String sdpMid = message.getJSONObject("candidate").getString("sdpMid");
                                int sdpMLineIndex = Integer.parseInt(message.getJSONObject("candidate").getString("sdpMLineIndex"));

                                if (pc.getRemoteDescription() != null) {
                                    candidate = new IceCandidate(
                                            sdpMid,
                                            sdpMLineIndex,
                                            cand
                                    );
                                    pc.addIceCandidate(candidate);
                                }
                                break;
                            case "leave":
                                mListener.onRemoveRemoteStream(peerRemote.endPoint);
                                peerRemote.pc.close();
                                break;
                            default:
                                break;
                        }

                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                }

                /**
                 * This method closes the connection WebSocket.
                 *
                 * @param code identifier code.
                 * @param reason String to the cause.
                 */
                @Override
                public void onClose(int code, String reason) {
                    Log.d(TAG, "Connection lost.");
                }
            });
        }
    }

    /**
     * This class is the class observer status changes in the description of the secion and PeerConnection
     * implements SdpObserver y PeerConnection.Observer classes.
     *
     * @author Karina Romero
     */
    private class Peer implements SdpObserver, PeerConnection.Observer {
        private PeerConnection pc;
        private String id;
        private int endPoint;

        /**
         * This method creates a description of the session.
         *
         * @param sdp description of the session.
         */

        @Override
        public void onCreateSuccess(final SessionDescription sdp) {
            // TODO: modify sdp to use pcParams prefered codecs
            Log.d("pruebita", sdp.description);
            pc.setLocalDescription(Peer.this, sdp);
        }

        /**
         * creates an answer
         */
        @Override
        public void onSetSuccess() {
            pc.createAnswer(Peer.this, pcConstraints);

        }

        @Override
        public void onCreateFailure(String s) {
        }

        @Override
        public void onSetFailure(String s) {
        }

        /**
         * In this method if the state is HAVE_LOCAL_OFFER an offer is sent
         *
         * @param signalingState It contains the status value
         */
        @Override
        public void onSignalingChange(PeerConnection.SignalingState signalingState) {
            Log.d("SIGNSTATE", signalingState.toString());
            if (signalingState.equals(PeerConnection.SignalingState.HAVE_LOCAL_OFFER)) {
                JSONObject sigOffer = new JSONObject();
                JSONObject sendOffer = new JSONObject();
                try {
                    sigOffer.put("type", "offer");
                    sigOffer.put("sdp", this.pc.getLocalDescription().description);

                    sendOffer.put("type", "offer");
                    sendOffer.put("offer", sigOffer);
                    sendOffer.put("name", id);
                } catch (JSONException e) {
                    e.printStackTrace();
                }
                mConnection.sendTextMessage(sendOffer.toString());
            }
        }

        /**
         * This method reports the status of the ICE connection, should be DISCONNECTED removes the connection.
         *
         * @param iceConnectionState It contains the status value
         */
        @Override
        public void onIceConnectionChange(PeerConnection.IceConnectionState iceConnectionState) {
            if (iceConnectionState == PeerConnection.IceConnectionState.DISCONNECTED) {
                removePeer(id);
                mListener.onStatusChanged("DISCONNECTED");
            }
        }

        @Override
        public void onIceConnectionReceivingChange(boolean b) {

        }

        @Override
        public void onIceGatheringChange(PeerConnection.IceGatheringState iceGatheringState) {
        }

        /**
         * This receives a candidate this is sent to the signaling.
         *
         * @param candidate candidate to send
         */
        @Override
        public void onIceCandidate(final IceCandidate candidate) {

            try {
                JSONObject jsonCandidate = new JSONObject();
                jsonCandidate.put("candidate", candidate.sdp + "," + candidate.sdpMid + "," + String.valueOf(candidate.sdpMLineIndex));
                JSONObject jsonObject = new JSONObject();
                jsonObject.put("type", "candidate");
                jsonObject.put("candidate", jsonCandidate);
                jsonObject.put("name", id);
                Log.d("ELCOMAND", jsonObject.toString());
                mConnection.sendTextMessage(jsonObject.toString());
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }

        /**
         * This method adds a stream
         *
         * @param mediaStream Stream de audio and video
         */
        @Override
        public void onAddStream(MediaStream mediaStream) {
            Log.d(TAG, "onAddStream " + mediaStream.label());
            // remote streams are displayed from 1 to MAX_PEER (0 is localStream)
            mListener.onAddRemoteStream(mediaStream, endPoint + 1);
        }

        /**
         * This method remove a stream
         *
         * @param mediaStream Stream de audio and video
         */
        @Override
        public void onRemoveStream(MediaStream mediaStream) {
            Log.d(TAG, "onRemoveStream " + mediaStream.label());
            removePeer(id);
        }

        @Override
        public void onDataChannel(DataChannel dataChannel) {
        }

        @Override
        public void onRenegotiationNeeded() {
        }

        /**
         * Class constructor, which creates the connection factory, add the local stream and sends the connection status.
         *
         * @param id       it contains the identifier of the call
         * @param endPoint value peer connection arrangement
         */
        public Peer(String id, int endPoint) {
            Log.d(TAG, "new Peer: " + id + " " + endPoint);

            Log.d("contrains", pcConstraints.toString());
            Log.d("iceServers", localMS.toString());

            this.pc = factory.createPeerConnection(iceServers, pcConstraints, this);
            this.id = id;
            this.endPoint = endPoint;

            pc.addStream(localMS); //, new MediaConstraints()

            mListener.onStatusChanged("CONNECTING");
        }

    }

    /**
     * Remove the peer connection.
     *
     * @param id peer identifier to remove
     */
    private void removePeer(String id) {
        Peer peer = peers.get(id);
        mListener.onRemoveRemoteStream(peer.endPoint);
        peer.pc.close();
        peers.remove(peer.id);
        endPoints[peer.endPoint] = false;
    }

    /**
     * Class constructor that initializes the parameters of a client.
     *
     * @param listener    listening states Peer connection
     * @param wsuri       ip adress
     * @param params      parameters of the view where the local video will be shown and remote
     * @param mEGLcontext GLview context, where view video call show
     * @param userName    user name
     */
    public WebRtcClient(RtcListener listener, String wsuri, PeerConnectionParameters params, EGLContext mEGLcontext, String userName) throws WebSocketException {
        mListener = listener;
        pcParams = params;
        PeerConnectionFactory.initializeAndroidGlobals(listener, true, true,
                params.videoCodecHwAcceleration, mEGLcontext);
        factory = new PeerConnectionFactory();
        MessageHandler messageHandler = new MessageHandler(wsuri, userName);

        messageHandler.webConnection();

        iceServers.add(new PeerConnection.IceServer("stun:stun.l.google.com:19302"));

        pcConstraints.mandatory.add(new MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"));
        pcConstraints.mandatory.add(new MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"));
        // pcConstraints.optional.add(new MediaConstraints.KeyValuePair("DtlsSrtpKeyAgreement", "true"));

    }


    /**
     * Call this method in Activity.onPause()
     */
    public void onPause() {
        if (videoSource != null) videoSource.stop();
    }

    /**
     * Call this method in Activity.onResume()
     */
    public void onResume() {
        if (videoSource != null) videoSource.restart();
    }

    /**
     * Call this method in Activity.onDestroy()
     */
    public void onDestroy() {
        try {
            for (Peer peer : peers.values()) {
                peer.pc.dispose();
            }
            videoSource.dispose();
            factory.dispose();
            mConnection.disconnect();
        } catch (Exception e) {

        }

    }

    /**
     * Start the camera.
     */

    public void setCamera() {
        localMS = factory.createLocalMediaStream("ARDAMS");
        if (pcParams.videoCallEnabled) {
            MediaConstraints videoConstraints = new MediaConstraints();
            videoConstraints.mandatory.add(new MediaConstraints.KeyValuePair("maxHeight", Integer.toString(pcParams.videoHeight)));
            videoConstraints.mandatory.add(new MediaConstraints.KeyValuePair("maxWidth", Integer.toString(pcParams.videoWidth)));
            videoConstraints.mandatory.add(new MediaConstraints.KeyValuePair("maxFrameRate", Integer.toString(pcParams.videoFps)));
            videoConstraints.mandatory.add(new MediaConstraints.KeyValuePair("minFrameRate", Integer.toString(pcParams.videoFps)));

            videoSource = factory.createVideoSource(getVideoCapturer(), videoConstraints);
            localMS.addTrack(factory.createVideoTrack("ARDAMSv0", videoSource));
        }

        AudioSource audioSource = factory.createAudioSource(new MediaConstraints());
        localMS.addTrack(factory.createAudioTrack("ARDAMSa0", audioSource));

        mListener.onLocalStream(localMS);
    }

    /**
     * Start the camera.
     */
    private VideoCapturer getVideoCapturer() {
        String frontCameraDeviceName = VideoCapturerAndroid.getNameOfFrontFacingDevice();
        return VideoCapturerAndroid.create(frontCameraDeviceName);
    }
    /**
     * This method sends an offer with the user name you want to call it is called in the call button on the main activity.
     *
     * @param name It contains the name of the recipient
     */
    public void call(String name) {

        setCamera();
        //this.name=name;
        peerRemote = new Peer(name, 0);
        //Log.d("jiji:", peerRemote.pc.getLocalDescription().description);
        this.name = name;
        peers.put(name, peerRemote);
        peerRemote.pc.createOffer(peerRemote, pcConstraints);
    }

    /**
     * Notifies signaling that the call is over, this method is called on the main activity, in the HangUp button.
     *
     * @param name It contains the name of the recipient.
     */
    public void hangUp(String name) {

        JSONObject sendLive = new JSONObject();
        try {
            sendLive.put("type", "leave");
            sendLive.put("name", name);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        mConnection.sendTextMessage(sendLive.toString());
    }

}