package com.example.corwin.jukebox;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Created by corwin on 12/21/14.
 */
public class Files {

    static void move(File originalFile, File targetFile) throws IOException {
        try {
            moveFast(originalFile, targetFile);
        }
        catch (IOException e) {
            moveNaive(originalFile, targetFile);
        }
    }

    static void moveFast(File originalFile, File targetFile) throws IOException {
        if (!originalFile.renameTo(targetFile)) throw new IOException("cannot move file");
    }

    private static void moveNaive(File originalFile, File targetFile) throws IOException {

        InputStream in = null;
        OutputStream out = null;

        //create output directory if it doesn't exist
        File dir = targetFile.getParentFile();
        if (!dir.exists()) {
            if (!dir.mkdirs())
                throw new IOException("cannot make directory " + dir);
        }


        in = new FileInputStream(originalFile);
        out = new FileOutputStream(targetFile);

        byte[] buffer = new byte[1024];
        int read;
        while ((read = in.read(buffer)) != -1) {
            out.write(buffer, 0, read);
        }
        in.close();
        in = null;

        // write the output file
        out.flush();
        out.close();

        // delete the original file
        originalFile.delete();

    }

}