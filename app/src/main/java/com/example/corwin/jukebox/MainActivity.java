package com.example.corwin.jukebox;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.content.ClipData;
import android.content.ClipDescription;
import android.content.ContentResolver;
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
import android.os.Looper;
import android.provider.MediaStore;
import android.support.v7.app.ActionBarActivity;
import android.os.Bundle;
import android.view.DragEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.CheckBox;
import android.widget.CursorAdapter;
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
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;

import nanohttpd.NanoHTTPD;
import nanohttpd.NanoHTTPD.*;
import com.example.corwin.jukebox.widgets.ListAdapterProxy;
import com.example.corwin.jukebox.widgets.ListAdapterWithResize;


public class MainActivity extends ActionBarActivity
        implements MediaScannerConnection.MediaScannerConnectionClient {

    private MediaPlayer mediaPlayer = null;
    private AudioManager audioManager = null;
    private static JukeHTTPD httpd = null;

    private Playlist playlist = null;

    private final boolean honeycomb = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

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
                        if (event.getY() < 0 && event.getX() > 0 && event.getX() < v.getWidth())
                            stop();
                        later(300, new Runnable() {
                            @TargetApi(11)
                            @Override
                            public void run() { if (honeycomb) knob.setAlpha(1.0f); }
                        });
                        break;

                    case MotionEvent.ACTION_MOVE:
                        if (event.getY() < 0 && event.getX() > 0 && event.getX() < v.getWidth())
                            knob.setImageResource(R.drawable.knob_stopped);
                        else {
                            // Restore image
                            if (mediaPlayer != null && mediaPlayer.isPlaying())
                                knob.setImageResource(R.drawable.knob_playing);
                            else if (mediaPlayer != null && isPaused || playlist != null && !isStopped)
                                knob.setImageResource(R.drawable.knob_paused);
                            else
                                knob.setImageResource(R.drawable.knob_stopped);
                        }
                        break;
                }
                return false;
            }
        });
        knob.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mediaPlayer != null && mediaPlayer.isPlaying())
                    pause();
                else if (mediaPlayer != null && isPaused || playlist != null && !isStopped)
                    resume();
                else
                    play(playlistAll());
            }
        });
        knob.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                knobDrag(v, KNOB_ACTION_SLIDER);
                return false;
            }
        });

        final ListView list = (ListView) findViewById(R.id.list);
        list.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                Cursor c = (Cursor) parent.getItemAtPosition(position);
                playlist = null;
                playUri(getTrackUri(c));
            }
        });

        final ListView artists = (ListView) findViewById(R.id.artists);
        final ListView albums = (ListView) findViewById(R.id.albums);

        if (honeycomb)
            configureKnobDrag();

        artists.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                HashMap<String, String> c = (HashMap<String, String>) parent.getItemAtPosition(position);
                String artist = c.get("name");
                if (loadedArtist != null && loadedArtist.equals(artist)) artist = null;
                loadMediaTracks(artist, null);
            }
        });

        albums.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                HashMap<String,String> c = (HashMap<String,String>) parent.getItemAtPosition(position);
                String album = c.get("name");
                loadMediaTracks(null, album);
            }
        });

        SeekBar volume = (SeekBar) findViewById(R.id.volume);
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
            }
        });
        artists.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
            @Override
            public void onLayoutChange(View v, int left, int top, int right, int bottom, int oldLeft, int oldTop, int oldRight, int oldBottom) {
                Geom g = getGeom(artists);
                knob.setTranslationX(g.right + 3 - knob.getWidth() / 2);
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

                if (event.getAction() == DragEvent.ACTION_DRAG_ENDED)
                    knob.setAlpha(1.0f);
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
    static final int KNOB_ACTION_CONTROLS = 0;
    static final int KNOB_ACTION_SLIDER = 1;

    @TargetApi(11)
    void knobDrag(View knob, int action) {
        ClipData.Item item = new ClipData.Item(knob.getTag().toString());
        String[] plain = {ClipDescription.MIMETYPE_TEXT_PLAIN};
        ClipData dragData = new ClipData(knob.getTag().toString(), plain, item);

        knobShadow = new KnobDragShadowBuilder(knob);
        knob.startDrag(dragData, knobShadow, null, 0);
        knobAction = action;
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
                return new Response("Jinx " + e);
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
                Map<String, String> files = new HashMap<>();
                session.parseBody(files);
            }

            String q = session.getQueryParameterString();
            try {
                final Playlist playlist = playlistParseIds(q);
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
                            " files, but " + payloadNames + " names." );
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
     * @return
     */
    private String exportMediaLibrary() throws JSONException {
        JSONArray a = new JSONArray();
        ListView list = (ListView) findViewById(R.id.list);
        int len = list.getCount();
        for (int i = 0; i < len; i++) {
            Cursor c = (Cursor) list.getItemAtPosition(i);
            JSONObject o = new JSONObject();
            if (honeycomb) exportRecord(c, o);
                      else exportRecord_api10(c, o);
            a.put(o);
        }
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
        loadMediaAlbums();
        mediaLibraryTimestamp = System.currentTimeMillis() / 1000L;
    }

    void loadMediaTracks(String byArtist, String byAlbum) {
        ListView list = (ListView) findViewById(R.id.list);

        ContentResolver resolver = getContentResolver();

        String[] proj = {MediaStore.Audio.Media.TITLE,
                MediaStore.Audio.Media.ARTIST, MediaStore.Audio.Media.ALBUM,
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

        Cursor c = resolver.query(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, proj, where,
                args.toArray(new String[args.size()]), null);

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
    }

    void loadMediaArtists() {
        ListView artists = (ListView) findViewById(R.id.artists);
        artists.setAdapter(createAdapterFromValues("name",
                queryDistinct(MediaStore.Audio.Media.ARTIST)));
    }

    void loadMediaAlbums() {
        ListView albums = (ListView) findViewById(R.id.albums);
        albums.setAdapter(createAdapterFromValues("name",
                queryDistinct(MediaStore.Audio.Media.ALBUM)));
    }

    void invalidateAllMediaLists() {
        int[] ids = {R.id.list, R.id.albums, R.id.artists};
        for (int i : ids)
            ((ListView) findViewById(i)).invalidateViews();
    }

    String[] queryDistinct(String column) {
        return queryDistinct(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, column,
                MediaStore.Audio.Media.IS_MUSIC + "=1");
    }

    String[] queryDistinct(Uri table, String column, String where) {
        ContentResolver resolver = getContentResolver();
        String[] proj = {column};
        Cursor c = resolver.query(table, proj, where, null, null);
        try {
            Set<String> s = new HashSet<>();
            while (c.moveToNext()) {
                s.add(c.getString(0));
            }
            return s.toArray(new String[s.size()]);
        }
        finally { c.close(); }
    }

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

    // ----------------
    // MediaPlayer part
    // ----------------

    Timer mediaTimer = new Timer();
    TimerTask mediaProgress = null;
    boolean isPaused = false;
    boolean isStopped = true;
    float volumeLevel = 1.0f;
    float volumeValue = 1.0f;

    private void playUri(Uri uri) {
        if (mediaPlayer != null) {
            mediaPlayer.release();
        }

        mediaPlayer = new MediaPlayer();
        isPaused = false;
        isStopped = false;

        mediaPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
            @Override
            public void onCompletion(MediaPlayer mp) {
                TextView text = (TextView) findViewById(R.id.text);
                text.setText("Time = " + System.currentTimeMillis()/1000);
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
        }
    }

    private void pause() {
        if (mediaPlayer != null) {
            mediaPlayer.pause();
            isPaused = true;
            ImageView knob = (ImageView) findViewById(R.id.knob);
            knob.setImageResource(R.drawable.knob_paused);
        }
        if (mediaProgress != null) mediaProgress.cancel();
        mediaProgress = null;
    }

    private void resume() {
        if (mediaPlayer != null && isPaused) {
            mediaPlayer.start();
            isPaused = false;
            monitorMediaProgress();
            ImageView knob = (ImageView) findViewById(R.id.knob);
            knob.setImageResource(R.drawable.knob_playing);
        }
        else if (playlist != null && !isStopped) {
            play(playlist);
        }
    }

    private void stop() {
        if (mediaPlayer != null) mediaPlayer.stop();
        if (playlist != null) playlist.nowPlaying = -1;
        if (mediaProgress != null) mediaProgress.cancel();
        mediaProgress = null;
        isPaused = false;
        isStopped = true;
        // Set knob
        ImageView knob = (ImageView) findViewById(R.id.knob);
        knob.setImageResource(R.drawable.knob_stopped);
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
            stop();
    }

    private void playNext() {
        if (playlist != null) {
            playlist.nowPlaying++;
            play(playlist);
        }
        else stop();
    }

    private void pauseAfter() {
        // Don't play next track after current track ends
        if (mediaPlayer != null && mediaPlayer.isPlaying()) {
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
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        seek.setMax(duration);
                        seek.setProgress(mediaPlayer.getCurrentPosition());
                    }
                });
            }
        };
        mediaTimer.schedule(mediaProgress, 0, 125);
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
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        switch (id) {
            case R.id.action_server:
                if (item.isChecked()) stopServer();
                else startServer();
                return true;
            case R.id.action_rescan:
                rescanMedia();
                return true;
            case R.id.action_clear:
                clearMediaLibrary();
                return true;
        }

        return super.onOptionsItemSelected(item);
    }

    // ---------------------------
    // MediaScannerConnection part
    // ---------------------------

    private int scanProgress = 0;
    private String lastFileToScan = null;

    private void rescanMedia() {
        List<String> paths = findAllFiles(getMediaRoot());
        String[] mimes = null; //{"*/*"};
        if (paths.isEmpty())
            Toast.makeText(this, "Media directory is empty!", Toast.LENGTH_LONG).show();
        else {
            scanProgress = 0;
            lastFileToScan = paths.get(paths.size() - 1);
            MediaScannerConnection.scanFile(this,
                    paths.toArray(new String[paths.size()]),
                    mimes, this);
        }
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
                text.setText("Scanned " + scanProgress + ((scanProgress == 1) ? " file." : " files."));

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
        String[] paths = {targetFile.getAbsolutePath()};
        String[] mimes = null;
        scanProgress = 0;
        lastFileToScan = paths[paths.length - 1];
        MediaScannerConnection.scanFile(this, paths, mimes, this);
    }

    // Storage sub-part

    private File getMediaRoot() {
        return new File(Environment.getExternalStorageDirectory(), "Music");
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
        TextView indicator = (TextView) findViewById(R.id.sleepIndicator);
        indicator.setText(symbol);
    }

    private void later(int timeoutMillis, Runnable action) {
        new android.os.Handler(Looper.getMainLooper())
                .postDelayed(action, timeoutMillis);
    }

}
