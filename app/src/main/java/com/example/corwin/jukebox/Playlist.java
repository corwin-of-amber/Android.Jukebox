package com.example.corwin.jukebox;

import android.net.Uri;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;


public class Playlist {

    static class Track {
        public int id;
        public Uri uri;
    }

    List<Track> tracks;
    int nowPlaying;

    public Playlist() {
        tracks = new ArrayList<>();
    }

    public Playlist shuffle() {
        Random random = new Random();
        List<Track> shuffle = new ArrayList<>(tracks.size());
        while (tracks.size() > 0) {
            int i = random.nextInt(tracks.size());
            shuffle.add(tracks.get(i));
            tracks.remove(i);
        }
        tracks = shuffle;
        return this;
    }
}
