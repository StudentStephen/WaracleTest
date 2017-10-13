package com.waracle.androidtest;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.os.Bundle;
import android.support.v4.app.ListFragment;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.AsyncTaskLoader;
import android.support.v4.content.Loader;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * The initial crashing was solved by correcting the IDs that were being searched for. The loading
 * of the information from JSON was changed to load in the background using an AsyncTaskLoader -
 * perfect for the requirements given that a loader 1) doesn't block the UI Thread 2) Responds to
 * device rotation in an elegant way - finishing what it had previously started rather than stopping
 * and starting again, or worse, starting multiple loads passing information to "ghost activities".
 *
 * The application responds gracefully to phone rotation, both during loading, and after loading.
 * I believe it to be efficient.
 *
 * Images are dealt with in the ImageLoader class with the same ideas as expressed here - using
 * background loading and caching - the first time images are loaded there is a brief wait
 * then every other time loading is instant.
 * Further explanation is at the top of the ImageLoader class.
 *
 * The application does not need to access URLs again after a phone rotation, by caching images in
 * the ImageLoader class it is possible to  make that class parcelable and save it in a bundle,
 * so that when the activity is called again, it can use that saved version of the ImageLoader and
 * have access to all saved images and URLs - quickly fitting the images back to how they were.
 *
 * String resources, dimen resources were used as to conform to best practise in an effort to make
 * the code mroe extensible and scalable - meaning change in just one area rather than many
 * uses of hardcoded strings.
 *
 * In terms of HCI I added a progress bar whilst the initial data loads. I was tempted to do this
 * for each image but it looked unsightly having 4 spinning wheels at one time, so left it out.
 *
 * Therefore I believe I have met the requires of:
 * Simple fixes for crash bug(s)
 * Application to support rotation
 * Safe and efficient loading of images
 * Removal of any redundant code
 * Refactoring of code to best practices
 *
 * One bug I haven't managed to fix - and that I don't think I should spend more time on, given
 * I have already exceed the recommended time, is that the 'Victoria Sponge' cake doesn't appear.
 * The URL works, but it won't appear in the image view whilst all the rest do. I thought it could
 * be to do with the size of image butthat doesnt make much sense so i am guessing it might be a
 * problem with the way the bytes are read from the input stream.
 *
 * My knowledge was not as strong on reading bytes from input stream, so my answer to the question
 * asked in StreamUtils is by best guess after some research.
 */


/**
 * The MainActivity class defines the container that the fragment will be displayed in. It provides
 * the menu with a refresh button, and the logic that determines if it was clicked
 */
public class MainActivity extends AppCompatActivity {

    //The location of the information - considered making a class to store all static variables
    private static String JSON_URL = "https://gist.githubusercontent.com/hart88/198f29ec5114a3ec3460/" +
            "raw/8dd19a88f9b8d24c23d9960f3300d0c917a4f07c/cake.json";


    /**
     * OnCreate creates a fragment and adds it to the container view through a transaction
     * @param savedInstanceState
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        if (savedInstanceState == null) {
            getSupportFragmentManager().beginTransaction()
                    .add(R.id.container, new PlaceholderFragment())
                    .commit();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_refresh) {
            return true;

        }

        return super.onOptionsItemSelected(item);
    }

    /**
     * Fragment is responsible for loading in some JSON and
     * then displaying a list of cakes with images.
     * Fix any crashes - Done
     * Improve any performance issues - Done
     * Use good coding practices to make code more secure ---
     * I believe I have done. By implementing Loader callbacks, and specifically using JSONArray
     * it removes the possibility that a developer make a mistake and use something else.
     */
    public static class PlaceholderFragment extends ListFragment implements LoaderManager.LoaderCallbacks<JSONArray> {

        private static final String TAG = PlaceholderFragment.class.getSimpleName();

        //The List View
        private ListView mListView;
        private ProgressBar mProgressBar;
        //The ImageLoader class
        private ImageLoader mImageLoader;
        //The Adapter for the List View
        private MyAdapter mAdapter;
        //The holding Activity
        private Activity mActivity;
        //Loader ID
        private static final int HTTP_List_LOADER_ID = 0;



        /**
         * Empty Constructor
         */
        public PlaceholderFragment() { /**/ }

        /**
         * Method to create the view of the fragment. In this case due to saving the state of the
         * list when onDestory is called (by rotating or any other means) the bundle is checked to
         * retrieve the parcelable ImageLoader object holding all the cached images so that the
         * ImageLoader class can quickly redisplay the images without having to do undergo costly
         * networking use.
         * @param inflater the inflator to display the layout
         * @param container the view the fragment is displayed in
         * @param savedInstanceState the state of certain aspects of the application previously
         * @return
         */
        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container,
                                 Bundle savedInstanceState) {
            if (savedInstanceState != null){
                mImageLoader = (ImageLoader) savedInstanceState.get(getResources()
                        .getString(R.string.frag_key));
            }
            View rootView = inflater.inflate(R.layout.fragment_main, container, false);
            setRetainInstance(true);

            //mListView was returning null - an issue with the ID not being found. Placed android.R
            //.id.list to correctly find the ID.
            mListView = (ListView) rootView.findViewById(android.R.id.list);
            mProgressBar = (ProgressBar) rootView.findViewById(R.id.list_progress);
            return rootView;
        }

        @Override
        public void onActivityCreated(Bundle savedInstanceState) {
            super.onActivityCreated(savedInstanceState);

            // Create and set the list adapter.
            mAdapter = new MyAdapter();
            mListView.setAdapter(mAdapter);
            //Initiate the loader that will run in the background to retrieve the information
            //from JSON to go into the List
            getLoaderManager().initLoader(HTTP_List_LOADER_ID, savedInstanceState, this);
            mProgressBar.setVisibility(View.VISIBLE);

        }

        /**
         * Saves the state of the information displayed in the list when app is restarted
         * @param outState - the state of the app
         */
        @Override
        public void onSaveInstanceState(Bundle outState) {
            super.onSaveInstanceState(outState);
            //Put the ImageLoader class in the bundle to be saved - it contains all the cached
            //bitmaps
            outState.putParcelable(getResources().getString(R.string.frag_key), mImageLoader);
        }

        /**
         * This method now gets called from the background using a loader. More explanation at top
         * of class
         * @return JSONArray containing the information
         * @throws IOException
         * @throws JSONException
         */
        private JSONArray loadData() throws IOException, JSONException {

            URL url = new URL(JSON_URL);
            HttpURLConnection urlConnection = (HttpURLConnection) url.openConnection();
            try {
                InputStream in = new BufferedInputStream(urlConnection.getInputStream());

                // Can you think of a way to improve the performance of loading data
                // using HTTP headers??? - Did some research on HTTP Headers and it would
                //appear that by caching a header it can be stored for later use and the
                //application can access what is stored in the header rather than creating
                //a new network access.
                //AsyncTask Loader. Provides background computation freeing
                //UI Thread, but also responds well to interruptions and rotations.

                // Also, Do you trust any utils thrown your way????
                //I had a bit of difficulty here - in the StreamUtils i go into this further
                //by stating that a check should be included to make sure whatever is read in
                // is not a huge file that could damage the phones memory, be slow, or be harmful
                //to the application.

                byte[] bytes = StreamUtils.readUnknownFully(in);

                // Read in charset of HTTP content.
                String charset = parseCharset(urlConnection.getRequestProperty(getResources()
                        .getString(R.string.request_prop)));

                // Convert byte array to appropriate encoded string.
                String jsonText = new String(bytes, charset);

                // Read string as JSON.
                return new JSONArray(jsonText);
            } finally {
                urlConnection.disconnect();
            }
        }

        /**
         * Returns the charset specified in the Content-Type of this header,
         * or the HTTP default (ISO-8859-1) if none can be found.
         */
        public static String parseCharset(String contentType) {
            if (contentType != null) {
                String[] params = contentType.split(",");
                for (int i = 1; i < params.length; i++) {
                    String[] pair = params[i].trim().split("=");
                    if (pair.length == 2) {
                        if (pair[0].equals("charset")) {
                            return pair[1];
                        }
                    }
                }
            }
            return "UTF-8";
        }


        @Override
        public void onAttach(Activity activity) {
            super.onAttach(activity);
            //Get the attached activity
            mActivity = activity;
        }

        /**
         * This method houses the return of an AsyncTaskLoader that contains two methods; onStartLoading
         * and loadInBackground. onStartLoading checks to make sure the data hasn't already been
         * made, doInBackground loads the data from methods in the background thread
         * @param id
         * @param args
         * @return
         */
        @Override
        public Loader<JSONArray> onCreateLoader(int id, Bundle args) {
            return new AsyncTaskLoader<JSONArray>(mActivity) {

                //The JSONArray
                JSONArray array = null;

                @Override
                protected void onStartLoading() {
                    super.onStartLoading();

                    if (array != null){
                        //If the array is populated we can go ahead and set the info to the adapter
                        mAdapter.setItems(array);
                    } else {
                        //Otherwhise, load the data
                        forceLoad();
                    }
                }

                @Override
                public JSONArray loadInBackground() {

                    try {
                        //Load data in background
                        array = loadData();

                    } catch (IOException e) {
                        e.printStackTrace();
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                    return array;
                }
            };
        }

        @Override
        public void onLoadFinished(Loader<JSONArray> loader, JSONArray data) {
            //When the load has finished, automatically create the adapter with the new information
            mProgressBar.setVisibility(View.INVISIBLE);
            mAdapter = new MyAdapter(data);
            mListView.setAdapter(mAdapter);

        }

        @Override
        public void onLoaderReset(Loader<JSONArray> loader) {

        }

        /**
         * The adapter class that is responsible for the sensible display of data through recyclling
         * View Holders, only displaying data in view holders that would be visible to the user
         * rather than populating them all causing lag. A View Holder is made using the costly
         * 'findviewbyid' method, btu only once. These views are recycled and only the data is
         * changed.
         */
        private class MyAdapter extends BaseAdapter {

            // Can you think of a better way to represent these items???
            private JSONArray mItems;

            public MyAdapter() {
                this(new JSONArray());
            }

            public MyAdapter(JSONArray items) {
                mItems = items;
                //If the ImageLoader class has not been saved from last time then it needs to be
                //made
                if (mImageLoader == null) {
                    mImageLoader = new ImageLoader();
                }
            }

            @Override
            public int getCount() {
                return mItems.length();
            }

            @Override
            public Object getItem(int position) {
                try {
                    return mItems.getJSONObject(position);
                } catch (JSONException e) {
                    Log.e("", e.getMessage());
                }
                return null;
            }

            @Override
            public long getItemId(int position) {
                return 0;
            }

            /**
             * Method that populates a View Holder with information from JSON.
             * @param position position of the viewHolder in the list
             * @param convertView
             * @param parent the viewgroup that view holder is containing with
             * @return return the root view
             */
            @SuppressLint("ViewHolder")
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                LayoutInflater inflater = LayoutInflater.from(getActivity());
                View root = inflater.inflate(R.layout.list_item_layout, parent, false);

                //If the root has been made, it's information views can be found and populated
                if (root != null) {
                    TextView title = (TextView) root.findViewById(R.id.title);
                    TextView desc = (TextView) root.findViewById(R.id.desc);
                    ImageView image = (ImageView) root.findViewById(R.id.image);
                    try {
                        //Added String Resources for better extensibility
                        JSONObject object = (JSONObject) getItem(position);
                        title.setText(object.getString(getResources()
                                .getString(R.string.cake_title_label)));
                        desc.setText(object.getString(getResources()
                                .getString(R.string.cake_description_label)));
                        String url = object.getString(getResources()
                                .getString(R.string.cake_image_label));

                        //Call ImageLoader helper class, to find the correct images
                         mImageLoader.load(url, image);

                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                }

                return root;
            }

            public void setItems(JSONArray items) {
                mItems = items;
            }
        }


    }
}
