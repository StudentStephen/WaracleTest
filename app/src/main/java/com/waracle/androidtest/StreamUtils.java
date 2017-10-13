package com.waracle.androidtest;

import android.util.Log;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;

/**
 * Created by Riad on 20/05/2015.
 */
public class StreamUtils {
    private static final String TAG = StreamUtils.class.getSimpleName();

    // Can you see what's wrong with this???
    //I have been looking but it seems to make sense to me. The input stream is read until
    //-1 is reached (the first empty space signalling the end) - given that the bytes could be of
    //any size an Array List is used and converted to array when the size is known.
    //
    // Unless that is the problem where we don't have a safety check to make sure that the size
    //of what is in the input stream is too big and could crash, or potentially harm the application
    // or device. If that is the problem then a check could me bade to ensure the data is not above
    // a certain number - also provides security.
    public static byte[] readUnknownFully(InputStream stream) throws IOException {
        // Read in stream of bytes
        ArrayList<Byte> data = new ArrayList<>();
        while (true) {
            int result = stream.read();
            if (result == -1) {
                break;
            }
            data.add((byte) result);
        }

        // Convert ArrayList<Byte> to byte[]
        byte[] bytes = new byte[data.size()];
        for (int i = 0; i < bytes.length; i++) {
            bytes[i] = data.get(i);
        }

        // Return the raw byte array.
        return bytes;
    }

    public static void close(Closeable closeable) {
        if (closeable != null) {
            try {
                closeable.close();
            } catch (IOException e) {
                Log.e(TAG, e.getMessage());
            }
        }
    }
}
