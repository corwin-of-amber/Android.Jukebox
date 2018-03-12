package com.example.corwin.jukebox;

import android.app.IntentService;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.support.annotation.Nullable;
import android.util.Log;
import android.widget.SeekBar;
import android.widget.Toast;

import java.io.IOException;
import java.util.Timer;
import java.util.TimerTask;

/**
 * Created by corwin on 10/03/2018.
 */

public class PlayerService extends IntentService {

    private static String TAG = "Jukebox/Music";

    public static class Msgs {
        // Activity -> Service
        public static final int STOP = 0;
        public static final int PLAY = 1;
        public static final int PAUSE = 2;
        public static final int VOL_SET = 3;

        // Service -> Activity
        public static final int PROGRESS = 0;
    }

    private MediaPlayer player;
    private float volumeValue = 1.0f;

    private Timer mediaTimer = new Timer();
    private TimerTask mediaProgress = null;


    public PlayerService() {
        super(TAG);
    }

    @Override
    protected void onHandleIntent(@Nullable Intent intent) {
        if (intent != null) {
            Uri uri = intent.getData();
            if (uri != null) {
                player = new MediaPlayer();
                try {
                    player.setDataSource(this, uri);
                    player.prepare();
                    player.start();
                }
                catch (IOException e) {
                    Log.e(TAG, "Cannot play " + uri);
                }
            }
            else {
                player.stop();
                player.release();
            }
        }
    }

    private void playUri(Uri uri) {
        if (player != null) { player.release(); }

        player = new MediaPlayer();

        player.setAudioStreamType(AudioManager.STREAM_MUSIC);
        try {
            player.setDataSource(getApplicationContext(), uri);
            player.setVolume(volumeValue, volumeValue);
            player.prepare();
            player.start();
            monitorMediaProgress();
        }
        catch (IOException e) {
            Toast.makeText(this, "Failed to open " + uri, Toast.LENGTH_LONG).show();
        }
    }

    private void stop() {
        if (player != null) player.stop();
        if (mediaProgress != null) mediaProgress.cancel();
    }

    private void pause() {
        if (player != null) {
            player.pause();
        }
        if (mediaProgress != null) mediaProgress.cancel();
        mediaProgress = null;
    }

    private void resume() {
        if (player != null) {
            player.start();
            monitorMediaProgress();
        }
    }

    /**
     * Computes the value that should be passed to MediaPlayer.setVolume(float, float) to
     * achieve the required amplitude, with a ratio of level/max of the maximum.
     */
    private float setVolume(int level, int max) {
        max++;
        volumeValue = (float) (1 - (Math.log(max - level) / Math.log(max)));
        if (player != null)
            player.setVolume(volumeValue, volumeValue);
        return volumeValue;
    }

    private void monitorMediaProgress() {
        if (mediaProgress != null) mediaProgress.cancel();

        mediaProgress = new TimerTask() {
            private int duration = player.getDuration();
            @Override
            public void run() {
                send(Msgs.PROGRESS, player.getCurrentPosition(), duration);
            }
        };
        mediaTimer.schedule(mediaProgress, 0, 750);
    }

    // ------------------
    // Communication part
    // ------------------

    private final Messenger messenger = new Messenger(new IncomingHandler());
    private Messenger replyTo = null;

    static class Connection implements ServiceConnection {
        public Messenger messenger;

        @Override
        public void onServiceConnected(ComponentName componentName, IBinder service) {
            messenger = new Messenger(service);
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            messenger = null;
        }
    };

    private class IncomingHandler extends Handler {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case Msgs.STOP:
                    stop(); break;
                case Msgs.PLAY:
                    Uri uri = msg.getData().getParcelable("uri");
                    if (uri != null) {
                        if (msg.replyTo != null) replyTo = msg.replyTo;
                        playUri(uri);
                    }
                    else resume();
                    break;
                case Msgs.PAUSE:
                    pause(); break;

                case Msgs.VOL_SET:
                    setVolume(msg.arg1, msg.arg2); break;
            }
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return messenger.getBinder();
    }

    private void send(int what, int arg1, int arg2) {
        try {
            if (replyTo != null)
                replyTo.send(Message.obtain(null, what, arg1, arg2));
        }
        catch (RemoteException e) {
            Log.e(TAG, e.toString());
        }

    }
}
