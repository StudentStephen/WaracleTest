package com.waracle.androidtest;

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.AsyncTask;
import android.os.Handler;
import android.os.Parcel;
import android.os.Parcelable;
import android.text.TextUtils;
import android.util.Log;
import android.widget.ImageView;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.InvalidParameterException;
import java.util.ArrayList;

/**
 * Created by Riad on 20/05/2015.
 */

/**
 * I have changed some aspects of this class and the way images load. Firstly, it hosts member
 * variables of two ArrayLists that cache the URL and Bitmap image of every URL passes to this
 * class, so that when a URL is accessed once, it need not access it again - but simply go to
 * a bitmap that has been cached.
 *
 * Secondly, in order that images do not block the UI thread leading to an undesirable lag or
 * freezing of the screen, when the load method of ImageLoader is called by the List View adapter
 * an asynctask inner class is instantiated that checks to see if the URL has already been cached, and
 * if not, goes to the relevent URL to access the image. A helper method was made that access the UI
 * thread to publish the results - given that is a fundamental aspect of thread and view heirachrchy.
 *
 * Lastly, the ImageLoader class implements Parcelable as this enables the class to be stored into
 * a bundle in the MainActivity so that when the phone is rotated, or any other event happens that
 * causes the activity lifecycle to call onDestory, the cached URLs and images are saved so that
 * they instantly appear back on the users screen and do not have to be loaded again.
 */
public class ImageLoader implements Parcelable {

    private static final String TAG = ImageLoader.class.getSimpleName();
    //List of URLs that have been accessed previously
    private ArrayList<String> mUrl;
    //List of Bitmaps that have been displayed previously
    private ArrayList<Bitmap> mBitmap;
    //Handler that deals with UI Thread.
    private Handler mHandler;

    //Constructor initialsies the ArrayLists of cached URLs and Bitmaps
    public ImageLoader(){

        mUrl = new ArrayList();
        mBitmap = new ArrayList();
    }


    //Creates the parcelable ArrayLists
    protected ImageLoader(Parcel in) {
        mUrl = in.createStringArrayList();
        mBitmap = in.createTypedArrayList(Bitmap.CREATOR);
    }

    public static final Creator<ImageLoader> CREATOR = new Creator<ImageLoader>() {
        @Override
        public ImageLoader createFromParcel(Parcel in) {
            return new ImageLoader(in);
        }

        @Override
        public ImageLoader[] newArray(int size) {
            return new ImageLoader[size];
        }
    };

    /**
     * Simple function for loading a bitmap image from the web
     *
     * @param url       image url
     * @param imageView view to set image too.
     */
    public void load(final String url, final ImageView imageView) {
        //If the URL is empty somthing has gone wrong throw exception
        if (TextUtils.isEmpty(url)) {
            throw new InvalidParameterException(Resources.getSystem().getString(R.string.url_null));
        }

        //If the URL is valid, load the image in the background
                new imageDownloader().execute(url, imageView);

                // Can you think of a way to improve loading of bitmaps
                // that have already been loaded previously??
                //Yes... By caching the URL we can tell if the image has been displayed or not,
                //by running a quick for-loop over a list we add the URL to we can find out if it
                //has already been used, and find the corrosponding position in another ArryList
                //of Bitmaps.
    }


    /**
     * This method connects with the internet to retrieve data from a URL using an input stream.
     * @param url the image url of the picture to be displayed
     * @return
     * @throws IOException
     */
    private static byte[] loadImageData(String url) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        InputStream inputStream = null;
        try {
            try {
                // Read data from workstation
                inputStream = connection.getInputStream();
            } catch (IOException e) {
                // Read the error from the workstation
                inputStream = connection.getErrorStream();
            }

            // Can you think of a way to make the entire
            // HTTP more efficient using HTTP headers??
            //Again, by caching headers I believe you can cache the results and have access to them
            //without actually creating more network access. I have done it a different way by
            //caching the URL and Bitmap once thaty have been access in the code.

            return StreamUtils.readUnknownFully(inputStream);
        } finally {
            // Close the input stream if it exists.
            StreamUtils.close(inputStream);

            // Disconnect the connection
            connection.disconnect();
        }
    }


    private static Bitmap convertToBitmap(byte[] data) {
        return BitmapFactory.decodeByteArray(data, 0, data.length);
    }

    private static void setImageView(ImageView imageView, Bitmap bitmap) {
        imageView.setImageBitmap(bitmap);
    }

    /**
     * Method of parcelable that produces a bit mask to represent objects?
     * @return the identifier of the object
     */
    @Override
    public int describeContents() {
        return 0;
    }

    /**
     * Method of parcelable that defines what form the object will take. In this case two arraylists
     * @param parcel the parcel that will package when the object is sent via an intent or saved
     *               instantstate
     * @param i
     */
    @Override
    public void writeToParcel(Parcel parcel, int i) {
        parcel.writeList(mUrl);
        parcel.writeList(mBitmap);
    }


    /**
     * This class runs in the background and is created when an image needs to be displayed from
     * a URL.
     */
    public class imageDownloader extends AsyncTask<Object, Void, Void> {


        /**
         * Constructor that instantiates the handler to run the UI Thread publications
         */
        public imageDownloader(){
            mHandler = new Handler();
        }

        /**
         * Method that runs in the background that checks to see if the image at the URL has
         * already been displayed, and if so, simply bring it from the cached location in code, or
         * if not retreive it from the URL through a network connection.
         * @param objects - the parameters that the task is given when started. In this case
         *                the URL and the Image View to display the image
         * @return
         */
        @Override
        protected Void doInBackground(Object... objects) {
            String url = (String) objects[0];
            //The Image View to display the URL image
            final ImageView imageView = (ImageView) objects[1];
            //Boolean to determine logic if image is cached or not
            boolean isCached = false;
            //Position in Bitmap Array List to be accessed for image
            int a = -1;

            //Iterate through the list of cached URLs to check if the iamge has been accessed
            for (int i = 0; i < mUrl.size(); i++) {
                if (url.equals(mUrl.get(i))) {
                    //It has been cached previously, set a to the correct pos
                    a = i;
                    isCached = true;
                }
            }


            if (!isCached){
                //Cache the URL identified for the image
                mUrl.add(url);
                try {
                    //Call logic to get the bitmap from the URL
                    final Bitmap bitmap = convertToBitmap(loadImageData(url));
                    //Cache the bitmap
                    mBitmap.add(bitmap);
                    //Access the UI Thread from the background thread to public result
                    UIthread(imageView, bitmap);
                } catch (IOException e) {
                    Log.e(TAG, e.getMessage());
                }
            } else {
                //It has already been loaded previously so no network access is required
                UIthread(imageView, mBitmap.get(a));
            }
            return null;
        }

        /**
         * Simple method the uses the handler to manage a runnable that access the UI Thread and
         * updates the ImageView with the correct bitmap
         * @param imageView ImageView the bitmap will be displayed in
         * @param bitmap the image to be displayed
         */
        public void UIthread(final ImageView imageView, final Bitmap bitmap){
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    setImageView(imageView, bitmap);
                }
            });
        }
    }

}
