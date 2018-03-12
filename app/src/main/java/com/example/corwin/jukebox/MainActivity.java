package com.example.corwin.jukebox;

import android.Manifest;
import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ClipDescription;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Point;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.provider.MediaStore;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.ActionBarActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.DragEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.CheckBox;
import android.widget.CursorAdapter;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.SeekBar;
import android.widget.SimpleAdapter;
import android.widget.SimpleCursorAdapter;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;

import nanohttpd.NanoHTTPD;
import nanohttpd.NanoHTTPD.*;
import com.example.corwin.jukebox.widgets.ListAdapterProxy;
import com.example.corwin.jukebox.widgets.ListAdapterWithResize;

import static android.content.Intent.ACTION_OPEN_DOCUMENT_TREE;


public class MainActivity extends ActionBarActivity
        implements MediaScannerConnection.MediaScannerConnectionClient {

    private MediaPlayer mediaPlayer = null;
    private AudioManager audioManager = null;
    private static JukeHTTPD httpd = null;

    private Playlist playlist = null;

    private final boolean honeycomb = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB);

    private SharedPreferences prefs = null;


    @SuppressLint("ClickableViewAccessibility")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        prefs = getPreferences(MODE_PRIVATE);

        audioManager = (AudioManager) getSystemService(AUDIO_SERVICE);

        sizer = new ListAdapterWithResize.SizingMixin();

        loadMediaLibrary();

        final ImageView knob = (ImageView) findViewById(R.id.knob);
        knob.setTag("knob");
        knob.setOnTouchListener(new View.OnTouchListener() {
            @SuppressLint("NewApi")
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        if (honeycomb) v.setAlpha(0.5f);
                        break;
                    case MotionEvent.ACTION_UP:
                        if (knobAction == KNOB_ACTION_CONTROLS) {
                            if (event.getY() < 0 && event.getX() > event.getY() && v.getWidth() - event.getX() > event.getY())
                                stop();
                            else if (event.getX() > v.getWidth() && event.getX() - v.getWidth() > event.getY() - v.getHeight() && v.getWidth() - event.getX() < event.getY())
                                playNext();
                        }
                        later(300, new Runnable() {
                            @TargetApi(11)
                            @Override
                            public void run() { if (honeycomb) knob.setAlpha(1.0f); }
                        });
                        knobAction = KNOB_ACTION_IDLE;
                        break;

                    case MotionEvent.ACTION_MOVE:
                        if (event.getY() < 0 && event.getX() > event.getY() && v.getWidth() - event.getX() > event.getY()) {
                            knobAction = KNOB_ACTION_CONTROLS;
                            knob.setImageResource(R.drawable.knob_stopped);
                        }
                        else if (event.getX() > v.getWidth() && event.getX() - v.getWidth() > event.getY() - v.getHeight() && v.getWidth() - event.getX() < event.getY()) {
                            knobAction = KNOB_ACTION_CONTROLS;
                            knob.setImageResource(R.drawable.knob_forward);
                        }
                        else {
                            // Restore image
                            setKnob(playerState);
                            /*
                            if (mediaPlayer != null && mediaPlayer.isPlaying())
                                knob.setImageResource(R.drawable.knob_playing);
                            else if (mediaPlayer != null && isPaused || playlist != null && !isStopped)
                                knob.setImageResource(R.drawable.knob_paused);
                            else
                                knob.setImageResource(R.drawable.knob_stopped); */
                        }
                        break;
                }
                return false;
            }
        });
        knob.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (playerState == PlayerState.PLAY) //mediaPlayer != null && mediaPlayer.isPlaying())
                    pause();
                else if (playerState == PlayerState.PAUSE) //mediaPlayer != null && isPaused || playlist != null && !isStopped)
                    resume();
                else
                    play(playlist != null ? playlist : playlistAll());
            }
        });
        knob.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                if (knobAction == KNOB_ACTION_IDLE)
                    knobDrag(v, KNOB_ACTION_SLIDER);
                return false;
            }
        });
        if (honeycomb) knobConfigureFrame();

        View text = findViewById(R.id.text);
        text.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (playlist != null && playlist.nowPlaying >= 0 &&
                        playlist.nowPlaying < playlist.tracks.size()) {
                    Cursor c = findTrackById(playlist.tracks.get(playlist.nowPlaying).id);
                    if (c != null && c.moveToNext())
                        promptTrackOptions(c);
                }
            }
        });

        final ListView list = (ListView) findViewById(R.id.list);
        list.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                if (isResizing) return ;
                Cursor c = (Cursor) parent.getItemAtPosition(position);
                playlist = null;
                playUri(getTrackUri(c));
            }
        });
        list.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
                if (isResizing) return false;
                Cursor c = (Cursor) parent.getItemAtPosition(position);
                promptTrackOptions(c);
                return true;
            }
        });

        final ListView artists = (ListView) findViewById(R.id.artists);
        final ListView albums = (ListView) findViewById(R.id.albums);

        if (honeycomb)
            configureKnobDrag();

        artists.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                if (isResizing) return ;
                HashMap<String, String> c = (HashMap<String, String>) parent.getItemAtPosition(position);
                String artist = c.get("name");
                if (loadedArtist != null && loadedArtist.equals(artist)) {
                    artist = null;
                }
                loadMediaTracks(artist, null);
                loadMediaAlbums(artist);
            }
        });
        artists.setSelector(R.color.selectedItem);

        albums.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                if (isResizing) return ;
                HashMap<String,String> c = (HashMap<String,String>) parent.getItemAtPosition(position);
                String album = c.get("name");
                if (loadedAlbum != null && loadedAlbum.equals(album)) {
                    album = null;
                }
                loadMediaTracks(null, album);
            }
        });
        albums.setSelector(R.color.selectedItem);

        SeekBar volume = (SeekBar) findViewById(R.id.volume);
        setVolume(volume.getProgress(), volume.getMax());
        volume.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                setVolume(progress, seekBar.getMax());
            }

            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        TextView sleepTime = (TextView) findViewById(R.id.sleepTime);
        sleepTime.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (sleepTick == null)
                    startTimer();
                else stopTimer();
            }
        });

        sgd = new ScaleGestureDetector(this, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override
            public boolean onScaleBegin(ScaleGestureDetector detector) {
                isResizing = true;
                return super.onScaleBegin(detector);
            }

            @Override
            public void onScaleEnd(ScaleGestureDetector detector) {
                // delay to prevent click from happening immediately
                later(500, new Runnable() {
                    @Override
                    public void run() {
                        isResizing = false;
                    }
                });
                super.onScaleEnd(detector);
            }

            @Override
            public boolean onScale(ScaleGestureDetector detector) {
                float scale = detector.getScaleFactor();
                float delta = (scale < 0.9) ? -1f : (scale > 1.1) ? +1f : 0;
                if (delta == 0) return false; else {
                    sizer.setTextSize(sizer.getTextSize() + delta);
                    invalidateAllMediaLists();
                    return true;
                }
            }
        });

        musicServiceConnect();

        if (httpd == null)
            startServer();
        else
            httpd.withServlet(new Servlet());
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        sgd.onTouchEvent(event);

        if (event.getPointerCount() == 1)
            return super.dispatchTouchEvent(event);
        else
            return true;
    }

    private ScaleGestureDetector sgd;
    private boolean isResizing = false;

    static class Geom { int left; int top; int right; int bottom; }

    Geom getGeom(View view) {
        int[] origin = new int[2];
        findViewById(R.id.frame).getLocationInWindow(origin);
        int[] leftTop = new int[2];
        view.getLocationInWindow(leftTop);
        Geom geom = new Geom();
        geom.left = leftTop[0] - origin[0]; geom.top = leftTop[1] - origin[1];
        geom.right = geom.left + view.getWidth(); geom.bottom = geom.top + view.getHeight();
        return geom;
    }

    // ---------
    // Knob part
    // ---------

    @TargetApi(11)
    void configureKnobDrag() {
        final View knob = findViewById(R.id.knob);

        // Center the knob initially
        final View pane = findViewById(R.id.filterPane);
        final ListView artists = (ListView) findViewById(R.id.artists);

        pane.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
            @Override
            public void onLayoutChange(View v, int left, int top, int right, int bottom, int oldLeft, int oldTop, int oldRight, int oldBottom) {
                Geom g = getGeom(pane);
                knob.setTranslationY(g.bottom - knob.getHeight() / 2);
                knobCenterFrame();
            }
        });
        artists.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
            @Override
            public void onLayoutChange(View v, int left, int top, int right, int bottom, int oldLeft, int oldTop, int oldRight, int oldBottom) {
                Geom g = getGeom(artists);
                knob.setTranslationX(g.right + 3 - knob.getWidth() / 2);
                knobCenterFrame();
            }
        });

        // Set dragging
        View overlay = findViewById(R.id.overlay);
        overlay.setOnDragListener(new View.OnDragListener() {
            @Override
            public boolean onDrag(View v, DragEvent event) {
                switch (knobAction) {
                    case KNOB_ACTION_SLIDER:
                        if (event.getAction() == DragEvent.ACTION_DRAG_LOCATION ||
                                event.getAction() == DragEvent.ACTION_DRAG_STARTED) {
                            Point xy = getXY(event);
                            int x = xy.x, y = xy.y;
                            knob.setTranslationX(x - knob.getWidth() / 2);
                            knob.setTranslationY(y - knob.getHeight() / 2);
                            View pane = findViewById(R.id.filterPane);
                            ViewGroup.LayoutParams lp = pane.getLayoutParams();
                            lp.height = Math.max(0, (int) y - getGeom(pane).top);
                            pane.setLayoutParams(lp);
                            View artists = findViewById(R.id.artists);
                            lp = artists.getLayoutParams();
                            lp.width = Math.max(0, (int) x - getGeom(artists).left - 3);
                            artists.setLayoutParams(lp);
                        }
                }

                if (event.getAction() == DragEvent.ACTION_DRAG_ENDED) {
                    knobAction = KNOB_ACTION_IDLE;
                    knob.setAlpha(1.0f);
                }
                return true;
            }

            Point getXY(DragEvent event) {
                int x = (int)event.getX(), y = (int)event.getY();
                if (event.getAction() == DragEvent.ACTION_DRAG_STARTED) {
                    int[] origin = new int[2];
                    findViewById(R.id.frame).getLocationInWindow(origin);
                    x -= origin[0]; y -= origin[1];
                }
                return new Point(x, y);
            }
        });
    }

    @TargetApi(11)
    private static class KnobDragShadowBuilder extends View.DragShadowBuilder {

        // The drag shadow image, defined as a drawable thing
        private static Drawable shadowH;
        private static Drawable shadowV;

        // Defines the constructor for myDragShadowBuilder
        public KnobDragShadowBuilder(View v) {

            // Stores the View parameter passed to myDragShadowBuilder.
            super(v);

            // Creates a draggable image that will fill the Canvas provided by the system.
            shadowH = new ColorDrawable(Color.LTGRAY);
            shadowV = new ColorDrawable(Color.LTGRAY);
        }

        // Defines a callback that sends the drag shadow dimensions and touch point back to the
        // system.
        @Override
        public void onProvideShadowMetrics (Point size, Point touch) {
            // Defines local variables
            int width, height;

            // Sets the width of the shadow to half the width of the original View
            width = getView().getWidth() / 2;

            // Sets the height of the shadow to half the height of the original View
            height = getView().getHeight() / 2;

            // The drag shadow is a ColorDrawable. This sets its dimensions to be the same as the
            // Canvas that the system will provide. As a result, the drag shadow will fill the
            // Canvas.
            shadowH.setBounds(0, 3*height/8, width, 5*height/8);
            shadowV.setBounds(3*width/8, 0, 5*width/8, height);

            // Sets the size parameter's width and height values. These get back to the system
            // through the size parameter.
            size.set(width, height);

            // Sets the touch point's position to be in the middle of the drag shadow
            touch.set(width / 2, height / 2);
        }

        // Defines a callback that draws the drag shadow in a Canvas that the system constructs
        // from the dimensions passed in onProvideShadowMetrics().
        @Override
        public void onDrawShadow(Canvas canvas) {

            // Draws the ColorDrawable in the Canvas passed in from the system.
            shadowH.draw(canvas);
            shadowV.draw(canvas);
        }
    }

    KnobDragShadowBuilder knobShadow = null;
    int knobAction = 0;
    static final int KNOB_ACTION_IDLE = 0;
    static final int KNOB_ACTION_CONTROLS = 1;
    static final int KNOB_ACTION_SLIDER = 2;

    @TargetApi(11)
    void knobDrag(View knob, int action) {
        ClipData.Item item = new ClipData.Item(knob.getTag().toString());
        String[] plain = {ClipDescription.MIMETYPE_TEXT_PLAIN};
        ClipData dragData = new ClipData(knob.getTag().toString(), plain, item);

        knobShadow = new KnobDragShadowBuilder(knob);
        knob.startDrag(dragData, knobShadow, null, 0);
        knobAction = action;
    }

    @TargetApi(11)
    void knobCenterFrame() {
        View knob = findViewById(R.id.knob);
        View frame = findViewById(R.id.knob_frame);
        float deltaX = frame.getWidth() - knob.getWidth();
        float deltaY = frame.getHeight() - knob.getHeight();
        frame.setTranslationX(knob.getTranslationX() - deltaX / 2);
        frame.setTranslationY(knob.getTranslationY() - deltaY / 2);
    }

    @TargetApi(11)
    void knobConfigureFrame() {
        final View knob = findViewById(R.id.knob);
        final View frame = findViewById(R.id.knob_frame);
        knob.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
            @Override
            public void onLayoutChange(View view, int i, int i1, int i2, int i3, int i4, int i5, int i6, int i7) {
                // ** The following would cause an invalid call to View.requestLayout in CircleView
                //frame.setLayoutParams(
                //        new FrameLayout.LayoutParams(knob.getWidth(), knob.getHeight()));
                knobCenterFrame();
            }
        });
    }

    @Override
    protected void onDestroy() {
        stop(); stopServer();
        super.onDestroy();
    }

    // ----------
    // HTTPD part
    // ----------

    static class JukeHTTPD extends NanoHTTPD {

        private Servlet servlet;

        public JukeHTTPD(int port) {
            super(port);
        }

        JukeHTTPD withServlet(Servlet servlet) {
            this.servlet = servlet;
            return this;
        }

        @Override
        public Response serve(IHTTPSession session) {
            try {
                return allow(servlet.route(session));
            } catch (Exception e) {
                return allow(new Response("Jinx " + e));
            }
        }

        private Response allow(Response r) {
            /* This is for development only */
            r.addHeader("Access-Control-Allow-Origin", "http://localhost:5000");
            return r;
        }
    }

    class Servlet {

        private static final String UPLOAD_FORM =
                "<form method=\"post\" enctype=\"multipart/form-data\">" +
                        "  <input name=\"payload\" type=\"file\" multiple><br/><input type=\"submit\">" +
                        "</form>";

        private Response route(IHTTPSession session) throws Exception {
            String path = session.getUri();
            if (path.startsWith("/play"))
                return play(session);
            else if (path.startsWith("/stop"))
                return stop(session);
            else if (path.startsWith("/next"))
                return next(session);
            else if (path.startsWith("/vol"))
                return vol(session);
            else if (path.equals("/upload"))
                return upload(session);
            else if (path.equals("/timestamp"))
                return timestamp(session);
            else
                return root(session);
        }


        private Response root(IHTTPSession session) throws JSONException {
            return new Response(Response.Status.OK, "text/json", exportMediaLibrary());
        }

        private Response play(IHTTPSession session) throws Exception {
            if (Method.POST.equals(session.getMethod())) {
                // Actually not supposed to happen?
                Map<String, String> files = new HashMap<>();
                session.parseBody(files);
            }

            String q = session.getQueryParameterString();
            try {
                final Playlist playlist = playlistParse(q);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() { MainActivity.this.play(playlist); }
                });
                return new Response("ok");
            }
            catch (NumberFormatException e) {
                return new Response("bad request '" + q + "'");
            }
        }

        private Response stop(IHTTPSession session) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() { MainActivity.this.stop(); }
            });
            return new Response("ok");
        }

        private Response next(IHTTPSession session) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() { MainActivity.this.playNext(); }
            });
            return new Response("ok");
        }

        private Response vol(IHTTPSession session) {
            String q = session.getQueryParameterString();
            try {
                if (q == null || q.equals(""))
                    return new Response("" + volumeLevel);
                else {
                    String[] parts = q.split("/");
                    if (parts.length == 2) {
                        int level = Integer.parseInt(parts[0]);
                        int max = Integer.parseInt(parts[1]);
                        if (max > 0 && level <= max)
                            setVolume(level, max);
                        else throw new NumberFormatException();
                    }
                    else throw new NumberFormatException();
                    return new Response("ok");
                }
            } catch (NumberFormatException e) {
                return new Response("bad request '" + q + "'");
            }
        }

        private Response upload(IHTTPSession session) throws IOException, ResponseException {
            if (Method.GET.equals(session.getMethod())) {
                return new Response(UPLOAD_FORM);
            }
            else if (Method.POST.equals(session.getMethod())) {
                Map<String, String> files = new HashMap<>();
                session.parseBody(files, getMediaRoot().getAbsolutePath());
                StringBuilder b = new StringBuilder();
                for (Map.Entry<String,String> entry : files.entrySet())
                    b.append(entry.getKey() + " = [" + entry.getValue() +  "]\n");
                for (Map.Entry<String,String> entry : session.getParms().entrySet())
                    b.append(entry.getKey() + " = '" + entry.getValue() + "'\n");

                String payloadPath = files.get("payload");
                String payloadName = session.getParms().get("payload");
                if (payloadPath != null && payloadName != null) {
                    // if multiple files are uploaded, the values would be null-delimited
                    String[] payloadPaths = payloadPath.split(IHTTPSession.MULTIPLE_VALUE_DELIM);
                    String[] payloadNames = payloadName.split(IHTTPSession.MULTIPLE_VALUE_DELIM);
                    if (payloadPaths.length == payloadNames.length) {
                        for (int i = 0; i < payloadPaths.length; i++) {
                            File payloadFile = new File(payloadPaths[i]);
                            MainActivity.this.upload(payloadFile, payloadNames[i]);
                        }
                        b.append("Uploaded!\n");
                    }
                    else return new Response(Response.Status.BAD_REQUEST, "text/plain",
                            "bad request; received " + payloadPaths.length +
                            " files, but " + payloadNames.length + " names." );
                    // Play first file, if requested
                    Map<String, String> params = session.getParms();
                    if (params.get("play") != null) {
                        final Uri uri = Uri.fromFile(new File(getMediaRoot(), payloadNames[0]));
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() { playlist = null; playUri(uri /*, "192.168.0.3"*/);
                            }
                        });
                    }
                }
                return new Response(b.toString());
            }
            else return new Response("bad method '" + session.getMethod() + "'.");
        }

        private Response timestamp(IHTTPSession session) {
            return new Response(""+mediaLibraryTimestamp);
        }
    }

    void startServer() {

        httpd = new JukeHTTPD(2222).withServlet(new Servlet());

        try {
            httpd.start();
            if (menu != null) {
                MenuItem serverItem = menu.findItem(R.id.action_server);
                serverItem.setChecked(true);
            }
        }
        catch (IOException e) {
            Toast.makeText(this, "Failed to start server: " + e, Toast.LENGTH_LONG).show();
        }
    }

    @SuppressLint("StaticFieldLeak")
    void stopServer() {
        if (httpd != null) {
            new AsyncTask<Object, Object, Object>() {

                @Override
                protected Object doInBackground(Object[] params) {
                    httpd.stop();
                    httpd = null;
                    return null;
                }

                @Override
                protected void onPostExecute(Object o) {
                    if (menu != null) {
                        MenuItem serverItem = menu.findItem(R.id.action_server);
                        serverItem.setChecked(false);
                    }
                }
            }.execute();
        }
    }

    // ---------------
    // MediaStore part
    // ---------------

    private long mediaLibraryTimestamp = 0;
    private String loadedArtist = null;
    private String loadedAlbum = null;
    private ListAdapterWithResize.SizingMixin sizer = null;

    /**
     * Creates a JSON representation of the library.
     */
    private String exportMediaLibrary() throws JSONException {
        JSONArray a = new JSONArray();
        Cursor c = queryMediaTracks(null, null);
        try {
            while (c.moveToNext()) {
                JSONObject o = new JSONObject();
                if (honeycomb) exportRecord(c, o);
                else exportRecord_api10(c, o);
                a.put(o);
            }
        } finally { c.close(); }
        return a.toString();
    }

    @TargetApi(11)
    private void exportRecord(Cursor c, JSONObject o) throws JSONException {
        int width = c.getColumnCount();
        for (int j = 0; j < width; j++) {
            int type = c.getType(j);
            switch (type) {
                case Cursor.FIELD_TYPE_INTEGER:
                    o.put(c.getColumnName(j), c.getInt(j)); break;
                case Cursor.FIELD_TYPE_STRING:
                    o.put(c.getColumnName(j), c.getString(j)); break;
                /* ignore all other types */
            }
        }
    }

    private void exportRecord_api10(Cursor c, JSONObject o) throws JSONException {
        int width = c.getColumnCount();
        for (int j = 0; j < width; j++) {
            o.put(c.getColumnName(j), c.getString(j));
        }
    }

    private void loadMediaLibrary() {
        loadMediaTracks(null, null);
        loadMediaArtists();
        loadMediaAlbums(null);
        mediaLibraryTimestamp = System.currentTimeMillis() / 1000L;
    }

    Cursor queryMediaTracks(String byArtist, String byAlbum) {
        ContentResolver resolver = getContentResolver();

        String[] proj = {MediaStore.Audio.Media.TITLE,
                MediaStore.Audio.Media.ARTIST, MediaStore.Audio.Media.ALBUM,
                MediaStore.Audio.Media.TRACK,
                MediaStore.Audio.Media.DATA,
                MediaStore.Audio.Media._ID};

        String where = MediaStore.Audio.Media.IS_MUSIC + "=1";
        List<String> args = new ArrayList<>();

        if (byArtist != null) {
            where += " AND " + MediaStore.Audio.Media.ARTIST + "=?";
            args.add(byArtist);
        }

        if (byAlbum != null) {
            where += " AND " + MediaStore.Audio.Media.ALBUM + "=?";
            args.add(byAlbum);
        }

        return resolver.query(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, proj, where,
                args.toArray(new String[args.size()]),
                MediaStore.Audio.Media.ALBUM + "," + MediaStore.Audio.Media.TRACK);
    }

    void loadMediaTracks(String byArtist, String byAlbum) {
        ListView list = (ListView) findViewById(R.id.list);

        Cursor c = queryMediaTracks(byArtist, byAlbum);

        String[] columns = {MediaStore.Audio.Media.TITLE};
        int[] views = {R.id.item_text};
        if (list.getAdapter() != null) {
            CursorAdapter a = ((ListAdapterProxy) list.getAdapter()).get();
            a.getCursor().close();
        }
        list.setAdapter(new ListAdapterWithResize(
                //new SimpleCursorAdapter(this, R.layout.simple_item, c, columns, views, 0),  // >= v11
                new SimpleCursorAdapter(this, R.layout.simple_item, c, columns, views),  // <= v10
                sizer));
        loadedArtist = byArtist;
        loadedAlbum = byAlbum;
    }

    void loadMediaArtists() {
        ListView artists = (ListView) findViewById(R.id.artists);
        artists.setAdapter(createAdapterFromValues("name",
                queryDistinct(MediaStore.Audio.Media.ARTIST)));
    }

    void loadMediaAlbums(String byArtist) {
        String where = MediaStore.Audio.Media.IS_MUSIC + "=1";
        List<String> args = new ArrayList<>();

        if (byArtist != null) {
            where += " AND " + MediaStore.Audio.Media.ARTIST + "=?";
            args.add(byArtist);
        }

        ListView albums = (ListView) findViewById(R.id.albums);
        albums.setAdapter(createAdapterFromValues("name",
                queryDistinct(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                        MediaStore.Audio.Media.ALBUM, where, args, MediaStore.Audio.Media.ARTIST)));
    }

    void invalidateAllMediaLists() {
        int[] ids = {R.id.list, R.id.albums, R.id.artists};
        for (int i : ids)
            ((ListView) findViewById(i)).invalidateViews();
    }

    String[] queryDistinct(Uri table, String column, String where, List<String> args, String orderBy) {
        ContentResolver resolver = getContentResolver();
        String[] proj = {column};
        Cursor c = resolver.query(table, proj, where,
                args == null ? null : args.toArray(new String[args.size()]), orderBy);
        if (c == null) return new String[0];
        return cursorToArray(c, 0, new LinkedHashSet<String>(c.getCount()));
    }

    String[] queryDistinct(Uri table, String column0, String column1, String where, List<String> args, String orderBy) {
        ContentResolver resolver = getContentResolver();
        String[] proj = {column0, column1};
        Cursor c = resolver.query(table, proj, where,
                args == null ? null : args.toArray(new String[args.size()]), orderBy);
        if (c == null) return new String[0];
        return cursorToArray(c, 0, 1, new LinkedHashSet<String>(c.getCount()));
    }

    String[] queryDistinct(String column) {
        return queryDistinct(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, column,
                MediaStore.Audio.Media.IS_MUSIC + "=1", null);
    }

    String[] queryDistinct(String column0, String column1) {
        return queryDistinct(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, column0, column1,
                MediaStore.Audio.Media.IS_MUSIC + "=1", null);
    }

    String[] queryDistinct(Uri table, String column, String where, List<String> args) {
        ContentResolver resolver = getContentResolver();
        String[] proj = {column};
        Cursor c = resolver.query(table, proj, where,
                args == null ? null : args.toArray(new String[args.size()]), null);
        if (c == null) return new String[0];
        return cursorToArray(c, 0, new HashSet<String>(c.getCount()));
    }

    String[] queryDistinct(Uri table, String column0, String column1, String where, List<String> args) {
        ContentResolver resolver = getContentResolver();
        String[] proj = {column0, column1};
        Cursor c = resolver.query(table, proj, where,
                args == null ? null : args.toArray(new String[args.size()]), null);
        if (c == null) return new String[0];
        return cursorToArray(c, 0, 1, new HashSet<String>(c.getCount()));
    }

    String[] cursorToArray(Cursor c, int colindex, Collection<String> s) {
        try {
            while (c.moveToNext()) {
                s.add(c.getString(colindex));
            }
            return s.toArray(new String[s.size()]);
        }
        finally { c.close(); }
    }

    String[] cursorToArray(Cursor c, int colindex0, int colindex1, Collection<String> s) {
        try {
            while (c.moveToNext()) {
                s.add(either(c.getString(colindex0), c.getString(colindex1)));
            }
            return s.toArray(new String[s.size()]);
        }
        finally { c.close(); }
    }

    private String either(String a, String b) { return (a != null && !a.equals("")) ? a : b; }

    ListAdapter createAdapterFromValues(String column, String... values) {
        String[] columns = {column};
        int[] views = {R.id.item_text};

        List<Map<String, String>> rows = new ArrayList<>();
        for (String value : values) {
            Map<String, String> row = new HashMap<>();
            row.put(column, value);
            rows.add(row);
        }
        return new ListAdapterWithResize(
                new SimpleAdapter(this, rows, R.layout.simple_item, columns, views),
                sizer);
    }

    void clearMediaLibrary() {
        ContentResolver resolver = getContentResolver();

        resolver.delete(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, null, null);

        loadMediaLibrary();
    }

    private int getTrackId(Cursor c) {
        return c.getInt(c.getColumnIndex(MediaStore.Audio.Media._ID));
    }

    private String getTrackDescription(Cursor c) {
        return getTrackTitle(c) + " / " + getTrackArtist(c);
    }
    private String getTrackTitle(Cursor c) {
        return c.getString(c.getColumnIndex(MediaStore.Audio.Media.TITLE));
    }
    private String getTrackArtist(Cursor c) {
        return c.getString(c.getColumnIndex(MediaStore.Audio.Media.ARTIST));
    }

    private Uri getTrackUri(int id) {
        ContentResolver resolver = getContentResolver();

        String[] proj = {
                MediaStore.Audio.Media._ID, MediaStore.Audio.Media.DATA};

        Cursor c = resolver.query(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, proj,
                MediaStore.Audio.Media._ID + "= ?", new String[] { ""+id }, null);

        try {
            if (c.moveToNext()) return getTrackUri(c, 1);
            else throw new RuntimeException("Track not found (id=" + id + ")");
        }
        finally { c.close(); }
    }

    private Uri getTrackUri(Cursor c) {
        return getTrackUri(c, c.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA));
    }
    private Uri getTrackUri(Cursor c, int column) {
        return Uri.fromFile(new File(c.getString(column)));
    }
    private File getTrackFile(Cursor c) {
        return getTrackFile(c, c.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA));
    }
    private File getTrackFile(Cursor c, int column) {
        return new File(c.getString(column));
    }

    private Cursor findTrackById(int id) {
        ContentResolver resolver = getContentResolver();

        String[] proj = {MediaStore.Audio.Media.TITLE,
                MediaStore.Audio.Media.ARTIST, MediaStore.Audio.Media.ALBUM,
                MediaStore.Audio.Media.DATA,
                MediaStore.Audio.Media._ID};

        String where = MediaStore.Audio.Media._ID + "=?";
        String[] args = {""+id};

        return resolver.query(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, proj, where,
                args, null);
    }

    private Playlist playlistAll() {
        ListView list = (ListView) findViewById(R.id.list);
        Playlist playlist = new Playlist();
        playlist.tracks = new ArrayList<>();
        playlist.nowPlaying = -1;
        for (int i = 0; i < list.getCount(); i++) {
            Cursor c = (Cursor) list.getItemAtPosition(i);
            Playlist.Track entry = new Playlist.Track();
            entry.id = getTrackId(c);
            entry.uri = getTrackUri(c);
            playlist.tracks.add(entry);
        }
        if (shuffled()) playlist.shuffle();
        return playlist;
    }

    private boolean shuffled() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH)
            return ((Switch) findViewById(R.id.shuffle)).isChecked();
        else
            return ((CheckBox) findViewById(R.id.shuffle)).isChecked();
    }

    /**
     * Parses a playlist definition.
     * @param playlistText either a comma-separated list of track id's or an http(s) URL
     * @return
     */
    private Playlist playlistParse(String playlistText) {
        if (playlistText.startsWith("http://") || playlistText.startsWith("https://"))
            return playlistParseUri(playlistText);
        else
            return playlistParseIds(playlistText);
    }

    /**
     * Parses a playlist definition.
     * @param ids comma-separated track id's
     * @return
     */
    private Playlist playlistParseIds(String ids) {
        Playlist playlist = new Playlist();
        playlist.tracks = new ArrayList<Playlist.Track>();
        String[] items = ids.split(",");
        for (String item : items) {
            int id = Integer.parseInt(item);
            Cursor c = findTrackById(id);
            try {
                if (c.moveToNext()) {
                    Playlist.Track entry = new Playlist.Track();
                    entry.id = id;
                    entry.uri = getTrackUri(c);
                    playlist.tracks.add(entry);
                }
            }
            finally { c.close(); }
        }
        return playlist;
    }

    private Playlist playlistParseUri(String uri) {
        Playlist playlist = new Playlist();
        playlist.tracks = new ArrayList<Playlist.Track>();
        Playlist.Track entry = new Playlist.Track();
        entry.id = 0;
        entry.uri = Uri.parse(uri);
        playlist.tracks.add(entry);
        return playlist;
    }

    // ----------------
    // MediaPlayer part
    // ----------------

    enum PlayerState { NONE, PLAY, PAUSE, STOP }

    Timer mediaTimer = new Timer();
    TimerTask mediaProgress = null;
    boolean isPaused = false;
    boolean isStopped = true;
    float volumeLevel = 1.0f;
    float volumeValue = 1.0f;

    PlayerState playerState = PlayerState.NONE;

    PlayerService.Connection playerConn;
    Messenger playerCallback = new Messenger(new Handler() {
        @Override
        public void handleMessage(Message msg) {
            onMediaProgress(msg.arg1, msg.arg2);
        }
    });

    /**
     * Bind to the service holding the MediaPlayer.
     * It is kept there so when the system destroys the Activity, the player state is not lost.
     */
    private void musicServiceConnect() {
        playerConn = new PlayerService.Connection();
        bindService(new Intent(this, PlayerService.class),
                playerConn, Context.BIND_AUTO_CREATE);
    }

    private void musicServiceError(RemoteException e) {
        Toast.makeText(this, "Internal error; problem with music service; " + e.toString(), Toast.LENGTH_LONG).show();
    }

    private void setKnob(PlayerState newState) {
        playerState = newState;
        ImageView knob = (ImageView) findViewById(R.id.knob);
        int image = 0;
        switch (newState) {
            case NONE:
            case STOP:  image = R.drawable.knob_stopped; break;
            case PLAY:  image = R.drawable.knob_playing; break;
            case PAUSE: image = R.drawable.knob_paused;  break;
        }
        if (image != 0)
            knob.setImageResource(image);
    }

    private void playUri(Uri uri, String syncHost) {
        Message m = Message.obtain(null, PlayerService.Msgs.PLAY);
        Bundle b = new Bundle();
        b.putParcelable("uri", uri);
        m.setData(b);
        m.replyTo = playerCallback;
        try {
            playerConn.messenger.send(m);
            setKnob(PlayerState.PLAY);
        } catch (RemoteException e) {
            musicServiceError(e);
        }
        /*
        if (mediaPlayer != null) {
            mediaPlayer.release();
        }
        syncStop();

        mediaPlayer = new MediaPlayer();
        isPaused = false;
        isStopped = false;

        if (syncHost != null) syncStart(syncHost);

        mediaPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
            @Override
            public void onCompletion(MediaPlayer mp) {
                playNext();
            }
        });
        mediaPlayer.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
            @Override
            public void onPrepared(MediaPlayer mp) {
                monitorMediaProgress();
            }
        });

        mediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
        try {
            mediaPlayer.setDataSource(getApplicationContext(), uri);
            mediaPlayer.setVolume(volumeValue, volumeValue);
            mediaPlayer.prepare();
            mediaPlayer.start();
            // Set knob
            ImageView knob = (ImageView) findViewById(R.id.knob);
            knob.setImageResource(R.drawable.knob_playing);
        }
        catch (IOException e) {
            Toast.makeText(this, "Failed to open " + uri, Toast.LENGTH_LONG).show();
        }*/
    }

    private void playUri(Uri uri) {
        playUri(uri, null);
    }

    private void pause() {
        try {
            playerConn.messenger.send(Message.obtain(null, PlayerService.Msgs.PAUSE));
            setKnob(PlayerState.PAUSE);
        } catch (RemoteException e) {
            musicServiceError(e);
        }
        /*
        if (mediaPlayer != null) {
            mediaPlayer.pause();
            isPaused = true;
            ImageView knob = (ImageView) findViewById(R.id.knob);
            knob.setImageResource(R.drawable.knob_paused);
        }
        if (mediaProgress != null) mediaProgress.cancel();
        mediaProgress = null;
        */
    }

    private void resume() {
            //mediaPlayer.start();
            //isPaused = false;
            //monitorMediaProgress();
            //ImageView knob = (ImageView) findViewById(R.id.knob);
            //knob.setImageResource(R.drawable.knob_playing);
        try {
            playerConn.messenger.send(Message.obtain(null, PlayerService.Msgs.PLAY));
            setKnob(PlayerState.PLAY);
        }
        catch (RemoteException e) {
            musicServiceError(e);
        }
        /*}
        else if (playlist != null && !isStopped) {
            play(playlist);
        }*/
    }

    private void stop() {
        try {
            Message m = new Message();
            m.what = 0;
            playerConn.messenger.send(m);
            setKnob(PlayerState.STOP);
        } catch (RemoteException e) {
            musicServiceError(e);
        }
        /*
        if (mediaPlayer != null) mediaPlayer.stop();
        if (playlist != null) playlist.nowPlaying = -1;
        if (mediaProgress != null) mediaProgress.cancel();
        mediaProgress = null;
        isPaused = false;
        isStopped = true;
        syncStop();
        // Set knob
        ImageView knob = (ImageView) findViewById(R.id.knob);
        knob.setImageResource(R.drawable.knob_stopped);*/
    }

    private void finishPlayback() {
        stop();
        onPlaybackEnded();  // should be an event?
    }

    private void play(Playlist playlist) {
        this.playlist = playlist;
        int i = Math.max(0, playlist.nowPlaying);
        if (i < playlist.tracks.size()) {
            playlist.nowPlaying  = i;
            nowPlaying(playlist);
            playUri(playlist.tracks.get(i).uri);
        }
        else
            finishPlayback();
    }

    private void playNext() {
        if (playlist != null) {
            playlist.nowPlaying++;
            play(playlist);
        }
        else finishPlayback();
    }

    private void pauseAfter() {
        // Don't play next track after current track ends
        if (playerState == PlayerState.PLAY) {
            // TODO: use the service callbacks instead
            mediaPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                @Override
                public void onCompletion(MediaPlayer mp) {
                    if (playlist != null) playlist.nowPlaying++;
                    if (mediaProgress != null) mediaProgress.cancel();
                    mediaProgress = null;
                    ImageView knob = (ImageView) findViewById(R.id.knob);
                    knob.setImageResource(R.drawable.knob_paused);

                    later(1000, new Runnable() {
                        @Override
                        public void run() { zeroTimer(); }
                    });
                }
            });
        }
    }

    private void nowPlaying(Playlist playlist) {
        TextView text = (TextView) findViewById(R.id.text);
        try {
            Playlist.Track track = playlist.tracks.get(playlist.nowPlaying);
            Cursor c = findTrackById(track.id);
            try {
                String title = (c.moveToNext()) ?
                        c.getString(c.getColumnIndex(MediaStore.Audio.Media.TITLE))
                        : track.uri.toString();
                text.setText(playlist.nowPlaying + 1 + "/" + playlist.tracks.size() + " " + title);
            }
            finally { c.close(); }
        }
        catch (ArrayIndexOutOfBoundsException e) { text.setText("playlist ended."); }
    }

    private float setVolume(int level, int max) {
        volumeLevel = (float)level / (float)max;
        max++;
        volumeValue = (float) (1 - (Math.log(max - level) / Math.log(max)));
        if (mediaPlayer != null)
            mediaPlayer.setVolume(volumeValue, volumeValue);
        return volumeValue;
    }

    private void monitorMediaProgress() {
        if (mediaProgress != null) mediaProgress.cancel();
        mediaProgress = new TimerTask() {
            private SeekBar seek = (SeekBar) findViewById(R.id.seek);
            private int duration = mediaPlayer.getDuration();
            @Override
            public void run() {
                if (audioSync != null) audioSync.heartbeat();
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        seek.setMax(duration);
                        seek.setProgress(mediaPlayer.getCurrentPosition());
                    }
                });
            }
        };
        mediaTimer.schedule(mediaProgress, 0, 750);

        if (audioSync != null) audioSync.handshake();
    }

    private void onMediaProgress(final int pos, final int duration) {
        final SeekBar seek = (SeekBar) findViewById(R.id.seek);
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
            seek.setMax(duration);
            seek.setProgress(pos);
            }
        });
    }

    // --------------
    // AudioSync part
    // --------------

    /*= Note: this is currently defunct as media control is offloaded to a service. =*/
    /*=   This needs to be moved to the service as well. =*/

    AudioSync audioSync = null;

    private void syncStart(String host) {
        try {
            audioSync = new AudioSync(host, 3333, mediaPlayer);
        }
        catch (Exception e) { Log.e("AudioSync", "not syncing", e); }
    }

    private void syncStop() {
        if (audioSync != null) audioSync.stop();
        audioSync = null;
    }

    // -----------------
    // AudioManager part
    // -----------------

    private void showMasterVolume() {
        int vol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
        int volMax = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);

        TextView text = (TextView) findViewById(R.id.text);
        text.setText(vol + " / " + volMax);
    }

    private void setMasterVolume(int level) {
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, level, 0);
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                showMasterVolume();
            }
        });
    }

    private int getMasterVolume() {
        return audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
    }

    // ---------
    // Menu part
    // ---------

    Menu menu = null;

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        this.menu = menu;
        menu.findItem(R.id.action_server).setChecked(httpd != null);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        switch (id) {
            case R.id.action_server:
                if (item.isChecked()) stopServer();
                else startServer();
                return true;
            case R.id.action_folder:
                promptSelectFolder();
                return true;
            case R.id.action_rescan:
                rescanMedia();
                return true;
            case R.id.action_clear:
                clearMediaLibrary();
                return true;

            case R.id.action_bluetooth:
                /* Temporary experimentation */
                BluetoothAdapter mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
                if (item.isChecked() && mBluetoothAdapter.isEnabled()) {
                    mBluetoothAdapter.disable();
                } else if (!item.isChecked() && !mBluetoothAdapter.isEnabled()) {
                    mBluetoothAdapter.enable();
                }
                return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @TargetApi(21)
    private void promptSelectFolder() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);

        Set<String> pathList = new LinkedHashSet<>();
        pathList.add(new File(Environment.getExternalStorageDirectory(), "Music").getAbsolutePath());
        pathList.add("/mnt/sdcard/Music");
        pathList.add("/mnt/external_sd/Music");
        try {
            /*
            for (File ef : getExternalMediaDirs()) {
                pathList.add(ef.getAbsolutePath());
            }
            */
            /*
            File f = new File("/storage");
            if (f.exists() && f.isDirectory()) {
                File[] files = f.listFiles();
                for (File inFile : files) {
                    if (inFile.isDirectory()) {
                        File music = new File(inFile, "Music");
                        if (music.exists() && music.isDirectory())
                            pathList.add(music.getAbsolutePath());
                    }
                }
            }
            */
        } catch (Exception e) {
            Toast.makeText(this, "Cannot list /storage: " + e, Toast.LENGTH_SHORT).show();
        }
        final int other_index = pathList.size();
        pathList.add("Other...");

        final String[] paths = pathList.toArray(new String[pathList.size()]);

        builder.setItems(paths, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                if (which == other_index) {
                    promptSelectExternalFolder();
                }
                else {
                    setMediaRoot(new File(paths[which]));
                }
            }
        })
        .setTitle("Current: " + getMediaRoot()); //"Set Download Folder");

        AlertDialog dialog = builder.create();
        dialog.show();
    }

    private static int ADD_STORAGE_REQUEST_CODE = 4010;

    private void promptSelectExternalFolder() {
        Intent intent = new Intent(ACTION_OPEN_DOCUMENT_TREE);
        intent.setPackage("com.android.documentsui");
        try {
            startActivityForResult(intent, ADD_STORAGE_REQUEST_CODE);
        } catch (ActivityNotFoundException e) {
            Log.e("jukebox", "Open directory tree failed", e);
        }
    }
    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        Uri uri = null;
        if (resultCode == RESULT_OK) {
            uri = data.getData();
        }
        Toast.makeText(this, "Activity request="+requestCode+" result=" + resultCode + " uri="+uri, Toast.LENGTH_LONG).show();
    }

    private void promptTrackOptions(final Cursor c) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);

        final String[] options = new String[] { "Delete", "Info..." }; // indexes must match switch

        builder.setItems(options, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                switch (which) {
                    case 0: // Delete
                        drop(getTrackFile(c)); break;
                    case 1: // Info...
                }
            }
        })
        .setTitle(getTrackDescription(c));

        AlertDialog dialog = builder.create();
        dialog.show();
    }

    // ---------------------------
    // MediaScannerConnection part
    // ---------------------------

    private int scanProgress = 0;
    private int scanTotal = 0;
    private String lastFileToScan = null;

    private void rescanMedia() {
        List<String> paths = findAllFiles(getMediaRoot());
        String[] mimes = null; //{"*/*"};
        if (paths.isEmpty())
            Toast.makeText(this, "Media directory is empty!", Toast.LENGTH_LONG).show();
        else {
            scanProgress = 0;
            scanTotal = paths.size();
            lastFileToScan = paths.get(paths.size() - 1);
            MediaScannerConnection.scanFile(this,
                    paths.toArray(new String[paths.size()]),
                    mimes, this);
        }
    }

    private void scanSingleFile(File mediaFile) {
        String[] paths = {mediaFile.getAbsolutePath()};
        String[] mimes = null;
        scanProgress = 0;
        scanTotal = paths.length;
        lastFileToScan = paths[paths.length - 1];
        MediaScannerConnection.scanFile(this, paths, mimes, this);
    }

    @Override
    public void onMediaScannerConnected() {
    }

    @Override
    public void onScanCompleted(String path, Uri uri) {
        ++scanProgress;

        final boolean isLast = (lastFileToScan != null && path.equals(lastFileToScan));

        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                TextView text = (TextView) findViewById(R.id.text);
                text.setText("Scanned " + scanProgress + "/" + scanTotal + ((scanTotal == 1) ? " file." : " files."));

                if (isLast) {
                    Toast.makeText(MainActivity.this, "Scan completed.", Toast.LENGTH_SHORT).show();
                    loadMediaLibrary();
                }
            }
        });
    }

    private void upload(File payloadFile, String newName) throws IOException {
        File targetFile = new File(getMediaRoot(), newName);
        if (!payloadFile.exists())
            throw new IOException("File does not exist: '" + payloadFile.getAbsolutePath() + "'");
        Files.move(payloadFile, targetFile);
        // Scan new file
        scanSingleFile(targetFile);
    }

    private void drop(File mediaFile) {
        if (mediaFile.delete()) {
            // Scan should remove file from library?
            scanSingleFile(mediaFile);
        }
    }

    // Storage sub-part

    private File getMediaRoot() {
        String mediaRoot = prefs.getString("mediaRoot", null);

        if (mediaRoot != null && new File(mediaRoot).isDirectory())
            return new File(mediaRoot);
        else
            return new File(Environment.getExternalStorageDirectory(), "Music");
    }

    private void setMediaRoot(File dir) {
        if (dir.isDirectory()) {
            SharedPreferences.Editor editor = prefs.edit();
            editor.putString("mediaRoot", dir.getAbsolutePath());
            editor.apply();
        }
        else
            Toast.makeText(MainActivity.this, "Directory '" + dir.getPath() + "' does not exist.",
                    Toast.LENGTH_LONG).show();
    }

    private List<String> findAllFiles(File dir) {
        ArrayList<String> files = new ArrayList<String>();
        findAllFiles(dir, files);
        return files;
    }

    private void findAllFiles(File dir, List<String> files) {
        File[] entries = dir.listFiles();
        if (entries != null)
            for (File entry : entries) {
                if (entry.isDirectory())
                    findAllFiles(entry, files);
                else
                    files.add(entry.getAbsolutePath());
            }
    }

    // ---------------------
    // BluetoothAdapter part
    // ---------------------

    void setBluetooth(boolean on) {
        BluetoothAdapter bt = BluetoothAdapter.getDefaultAdapter();
        if (bt != null) {
            if (on && !bt.isEnabled())        bt.enable();
            else if (!on && bt.isEnabled())   bt.disable();
        }
    }

    void onPlaybackEnded() {
        setBluetooth(false);
    }

    // ---------------
    // SleepTimer part
    // ---------------

    Timer sleepTimer = null;
    TimerTask sleepTick = null;
    int timeLeft = 80;

    private void startTimer() {
        if (sleepTick != null) sleepTick.cancel();
        sleepTimer = new Timer();
        sleepTick = new TimerTask() {
            @Override
            public void run() {
                if (timeLeft > 0)
                    timeLeft--;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        updateTimeLeft();
                        if (timeLeft == 0) {
                            stopTimer();
                            if (mediaPlayer != null && mediaPlayer.isPlaying())
                                pauseAfter();
                            else
                                later(1000, new Runnable() {
                                    @Override
                                    public void run() { zeroTimer(); }
                                } );
                        }
                    }
                });
            }
        };
        updateTimeLeft();
        sleepIndicator("⇣");
        sleepTimer.schedule(sleepTick, 60000, 60000);
    }

    private void stopTimer() {
        if (sleepTick != null) {
            sleepTick.cancel();
            sleepTick = null;
        }
        sleepIndicator("⧖");
    }

    private void zeroTimer() {
        if (timeLeft == 0) {
            timeLeft = 80;
            updateTimeLeft();
        }
    }

    private void updateTimeLeft() {
        TextView sleepTime = (TextView) findViewById(R.id.sleepTime);
        sleepTime.setText(""+timeLeft);
    }

    private void sleepIndicator(String symbol) {
        ImageView indicator = (ImageView) findViewById(R.id.sleepIndicator);
        switch (symbol) {
            case "⧖":  indicator.setImageResource(R.drawable.hourglass); break;
            case "⇣":  indicator.setImageResource(R.drawable.downarrow); break;
        }
    }

    private void later(int timeoutMillis, Runnable action) {
        new android.os.Handler(Looper.getMainLooper())
                .postDelayed(action, timeoutMillis);
    }

}
