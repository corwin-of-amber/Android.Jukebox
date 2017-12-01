package com.example.corwin.jukebox;

import android.annotation.TargetApi;
import android.media.MediaPlayer;
import android.os.AsyncTask;
import android.provider.ContactsContract;
import android.util.Log;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;

/**
 * Communicates with a UDP server (running on a desktop or laptop)
 * to synchronize played audio with video playing on the server's
 * display.
 */

class AudioSync {

    private MediaPlayer mediaPlayer;
    private ServerEndpoint server;
    private DatagramSocket socket;
    private Receiver receiver;

    public static class ServerEndpoint {
        public InetAddress host; public int port;
        ServerEndpoint(InetAddress host, int port) { this.host = host; this.port = port; }
        ServerEndpoint(String host, int port) throws UnknownHostException { this(InetAddress.getByName(host), port); }
    }

    public AudioSync(ServerEndpoint server, MediaPlayer mediaPlayer) throws SocketException {
        this.server = server;
        this.mediaPlayer = mediaPlayer;
        socket = new DatagramSocket();
        receiver = new Receiver();
    }

    public AudioSync(String host, int port, MediaPlayer mediaPlayer) throws SocketException, UnknownHostException {
        this(new ServerEndpoint(host, port), mediaPlayer);
    }

    int now() {
        return mediaPlayer.getCurrentPosition();
    }

    private boolean handshakeSucceeded = false;

    @TargetApi(11)
    public void handshake() {
        handshakeSend();
        //receiver.execute();
        receiver.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);  //API >= 11
    }

    @TargetApi(11)
    private void handshakeSend() {
        final byte[] msg = ("" + now()).getBytes();

        new AsyncTask<Void, Void, Void>() {
            @Override protected Void doInBackground(Void... args) {
                try {
                    DatagramPacket p = new DatagramPacket(msg, msg.length, server.host, server.port);
                    socket.send(p);
                    handshakeSucceeded = true;
                }
                catch (IOException e) { Log.e("AudioSync", "udp send error", e); }
                return null;
            }

        }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }

    private int lastHeartbeat = 0;
    private int serverTimeShift;

    public synchronized void heartbeat() {
        if (!handshakeSucceeded) { handshakeSend(); return; }
        if (socket.isClosed()) return;
        try {
            lastHeartbeat = now();
            DatagramPacket p = new DatagramPacket(new byte[]{32}, 1, server.host, server.port);
            socket.send(p);
        }
        catch (IOException e) { Log.e("AudioSync", "udp send error", e); }
    }

    public synchronized void stop() {
        receiver.cancel(true);
        socket.close();
    }

    @TargetApi(23)
    private void adjust() {
        // play time!
        if (serverTimeShift > 100)
            mediaPlayer.setPlaybackParams(mediaPlayer.getPlaybackParams().setSpeed(1.1f));
        else if (serverTimeShift > 10)
            mediaPlayer.setPlaybackParams(mediaPlayer.getPlaybackParams().setSpeed(1.01f));
        else if (serverTimeShift > 2)
            mediaPlayer.setPlaybackParams(mediaPlayer.getPlaybackParams().setSpeed(1.001f));
            //mediaPlayer.seekTo(mediaPlayer.getCurrentPosition() + 1000);
        else if (serverTimeShift < -10)
            mediaPlayer.setPlaybackParams(mediaPlayer.getPlaybackParams().setSpeed(0.99f));
        else if (serverTimeShift < -2)
            mediaPlayer.setPlaybackParams(mediaPlayer.getPlaybackParams().setSpeed(0.999f));
        else
            mediaPlayer.setPlaybackParams(mediaPlayer.getPlaybackParams().setSpeed(1.0f));
        /*
        a[0].playbackRate = 1.01
        else if client.server-time-shift > 2
        a[0].playbackRate = 1.001
        else if client.server-time-shift < -10
        a[0].playbackRate = 0.99
        else if client.server-time-shift < -2
        a[0].playbackRate = 0.999
        else
        a[0].playbackRate = 1*/
    }

    class Receiver extends AsyncTask<Void, Integer, Void> {

        static final int BUF_SZ = 30;

        @Override
        protected Void doInBackground(Void... args) {
            while (!isCancelled()) {
                try {
                    tick(receiveMessage());
                }
                catch (Exception e) { if (!isCancelled()) Log.e("AudioSync", "receive error", e); }
            }
            Log.i("AudioSync", "receiver task finished.");
            return null;
        }

        int receiveMessage() throws IOException {
            DatagramPacket p = new DatagramPacket(new byte[BUF_SZ], BUF_SZ);
            socket.receive(p);
            return Integer.parseInt(new String(p.getData(), 0, p.getLength()));
        }

        void tick(int reportedServerTime) {
            int clientTime = now();
            // estimate server time as reported time plus half round-trip time
            serverTimeShift = reportedServerTime + (clientTime - lastHeartbeat) / 2 - clientTime;

            Log.i("AudioSync", "tick, now = " + clientTime + ", shift = " + serverTimeShift);

            adjust();
            Log.i("AudioSync", "      now = " + now());
        }
    }
}
