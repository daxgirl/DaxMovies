package com.wubydax.awesomedaxsmovies;


import android.app.AlertDialog;
import android.app.SearchManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Point;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Bundle;
import android.os.Parcelable;
import android.preference.PreferenceManager;
import android.support.v4.app.Fragment;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.CursorLoader;
import android.support.v4.content.Loader;
import android.support.v4.widget.SwipeRefreshLayout;
import android.util.Log;
import android.view.Display;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.SearchView;
import android.widget.Toast;

import com.squareup.picasso.Picasso;
import com.squareup.picasso.Target;
import com.wubydax.awesomedaxsmovies.api.ApiInterface;
import com.wubydax.awesomedaxsmovies.api.GenreResponse;
import com.wubydax.awesomedaxsmovies.api.JsonResponse;
import com.wubydax.awesomedaxsmovies.data.MovieContract;
import com.wubydax.awesomedaxsmovies.utils.DataFragment;
import com.wubydax.awesomedaxsmovies.utils.FavMoviesAdapter;
import com.wubydax.awesomedaxsmovies.utils.FragmentCallbackListener;
import com.wubydax.awesomedaxsmovies.utils.MovieAdapter;
import com.wubydax.awesomedaxsmovies.utils.MyDialogFragment;
import com.wubydax.awesomedaxsmovies.utils.Utils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

import retrofit.Call;
import retrofit.Callback;
import retrofit.GsonConverterFactory;
import retrofit.Response;
import retrofit.Retrofit;

/**
 * A simple {@link Fragment} subclass.
 */
public class MainViewFragment extends Fragment implements SharedPreferences.OnSharedPreferenceChangeListener, LoaderManager.LoaderCallbacks<Cursor> {
    private final static int LOADER_ID = 46;
    private Context c;
    private String SORT_KEY;
    private String mQuery;
    private GridView mGridView;
    private SwipeRefreshLayout swipeRefreshLayout;
    private SharedPreferences mPrefs;
    private MovieAdapter mAdapter;
    private List<JsonResponse.Results> mList;
    private Parcelable state;
    private int width, height, totalPagesNumber, pageNumber = 1;
    private DataFragment dataFragment;
    private FragmentCallbackListener mListener;
    private boolean isLoading = false, isSearch = false, isRefresh = false, isTwoPane;
    private MenuItem sort, search, refresh;
    private FavMoviesAdapter favMoviesAdapter;
    private int mPosition = GridView.INVALID_POSITION;
    private Target mTarget;
    private HashMap<Integer, String> hm;

    public MainViewFragment() {

    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        try {
            mListener = (FragmentCallbackListener) context;
        } catch (ClassCastException e) {
            Log.e("MainViewFragment", "onAttach Activity must implement the interface", e);
        }
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        c = getActivity();
        SORT_KEY = "sort_by";
        mPrefs = PreferenceManager.getDefaultSharedPreferences(c);
        Bundle bundle = this.getArguments();
        isTwoPane = bundle.getBoolean("isTwoPane");
        setupDataFragment();
        setupDimens();
        setHasOptionsMenu(true);

    }

    private void setupDimens() {
        int spacing = Math.round(getResources().getDimension(R.dimen.grid_spacing));
        WindowManager wm = (WindowManager) c.getSystemService(Context.WINDOW_SERVICE);
        Display display = wm.getDefaultDisplay();
        Point size = new Point();
        display.getSize(size);
        int displayWidth = size.x;
        int columns;
        if (!isTwoPane) {
            columns = (getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT) ? c.getResources().getInteger(R.integer.columns_num_portrait) : c.getResources().getInteger(R.integer.columns_num_landscape);

            width = (displayWidth - spacing * 4) / columns;
        } else {
            width = displayWidth / 6;
        }
        height = Math.round(width * 1.5F);
    }

    private void setupDataFragment() {
        dataFragment = (DataFragment) getFragmentManager().findFragmentByTag("data");
        if (dataFragment == null) {
            dataFragment = new DataFragment();
            getFragmentManager().beginTransaction().add(dataFragment, "data").commit();
        }
    }

    private boolean isFavorites() {
        return PreferenceManager.getDefaultSharedPreferences(getActivity()).getString(SORT_KEY, "popular").equals("favourites");
    }


    @Override
    public View onCreateView(LayoutInflater inflater, final ViewGroup container,
                             Bundle savedInstanceState) {


        final View rootView = inflater.inflate(R.layout.fragment_movies_grid, container, false);

        mGridView = (GridView) rootView.findViewById(R.id.movieGridView);
        swipeRefreshLayout = (SwipeRefreshLayout) rootView.findViewById(R.id.swipeRefreshLayout);
        swipeRefreshLayout.setEnabled(false);
        swipeRefreshLayout.setRefreshing(false);
        swipeRefreshLayout.setColorSchemeResources(android.R.color.holo_blue_bright,
                android.R.color.holo_green_light,
                android.R.color.holo_orange_light,
                android.R.color.holo_red_light);


        isSearch = dataFragment.getSearchStatus();
        isLoading = dataFragment.isLoading();
        isRefresh = dataFragment.isRefreshMenu();
        pageNumber = dataFragment.getPageNumber();
        totalPagesNumber = dataFragment.getTotalPagesNumber();
        mList = dataFragment.getMovieDataList();
        mPosition = dataFragment.getmScrollPosition();
        hm = dataFragment.getHashMapGenres();


        if (!isFavorites()) {
            if (mList != null) {
                mAdapter = new MovieAdapter(c, mList, width, height);
                mGridView.setAdapter(mAdapter);
                setUpViewsListeners();
            }

        } else {
            favMoviesAdapter = new FavMoviesAdapter(getActivity(), null, 0);
            mGridView.setAdapter(favMoviesAdapter);
            getLoaderManager().restartLoader(LOADER_ID, null, this);
            getLoaderManager().initLoader(LOADER_ID, null, this);
            setCursorData();

            setUpViewsListeners();

        }


        return rootView;
    }

    private void setUpViewsListeners() {
        mGridView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            Bitmap mBitmap;
            JsonResponse.Results movieToPass;

            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {

                if (!isFavorites()) {
                    ImageView mThumbnail = (ImageView) view.findViewById(R.id.moviePoster);
                    Drawable mDrawable = mThumbnail.getDrawable();


                    if (mList != null && mList.size() > 0) {

                        movieToPass = mList.get(i);


                        if (mDrawable != null) {
                            mBitmap = ((BitmapDrawable) mDrawable).getBitmap();
                            dataFragment.setDetailsData(movieToPass, mBitmap);
                            mListener.onListItemClick();
                        } else {
                            mTarget = new Target() {
                                @Override
                                public void onBitmapLoaded(Bitmap bitmap, Picasso.LoadedFrom from) {
                                    dataFragment.setDetailsData(movieToPass, bitmap);
                                    mListener.onListItemClick();
                                }

                                @Override
                                public void onBitmapFailed(Drawable errorDrawable) {

                                }

                                @Override
                                public void onPrepareLoad(Drawable placeHolderDrawable) {

                                }
                            };
                            Picasso.with(c).load(getString(R.string.db_poster_path_beginning) + movieToPass.getPosterPath()).into(mTarget);
                        }


                    }
                } else {
                    Cursor cursorToPass = (Cursor) adapterView.getItemAtPosition(i);
                    Utils utils = new Utils(getActivity());
                    Bitmap bitmap = utils.getImage(cursorToPass.getBlob(cursorToPass.getColumnIndex(MovieContract.MovieEntry.MOVIE_POSTER_BITMAP_COLUMN)));
                    long movieId = cursorToPass.getLong(cursorToPass.getColumnIndex(MovieContract.MovieEntry.TMDB_ID));
                    dataFragment.setCursorDetailsData(movieId, bitmap);
                    mListener.onListItemClick();
                }
                mPosition = i;
            }
        });


        mGridView.setOnScrollListener(new AbsListView.OnScrollListener() {
            private int mLastVisibleItem;

            @Override
            public void onScrollStateChanged(AbsListView view, int scrollState) {


            }

            @Override
            public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount, int totalItemCount) {
                mLastVisibleItem = firstVisibleItem + visibleItemCount;
                if (mList != null) {
                    if (totalItemCount > 0 && !isLoading && !isFavorites() && mLastVisibleItem == totalItemCount && totalItemCount == mList.size() && pageNumber < totalPagesNumber) {

                        isLoading = true;
                        pageNumber++;

                        if (!isSearch) {
                            fetchData(false, null);
                        } else {
                            fetchData(true, mQuery);
                        }
                    }
                }
            }
        });
    }


    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);


    }


    @Override
    public void onResume() {
        super.onResume();
        mPrefs.registerOnSharedPreferenceChangeListener(this);
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        sort = menu.findItem(R.id.action_sort);
        search = menu.findItem(R.id.search);
        refresh = menu.findItem(R.id.refresh);
        mPrefs = PreferenceManager.getDefaultSharedPreferences(getActivity());


        setupSearchView();
        setMenuItemsVisibility(isRefresh);
        if (mList == null && !isRefresh) {
            if (!isFavorites()) {

                fetchData(false, null);
            }
        }

        getActivity().invalidateOptionsMenu();
        super.onCreateOptionsMenu(menu, inflater);

    }

    private void setupSearchView() {
        final SearchView searchView =
                (android.widget.SearchView) search.getActionView();
        SearchManager searchManager =
                (SearchManager) c.getSystemService(Context.SEARCH_SERVICE);
        searchView.setSearchableInfo(
                searchManager.getSearchableInfo(getActivity().getComponentName()));

        if (isSearch) {
            mQuery = dataFragment.getQuery();
            if (mQuery != null) {
                searchView.onActionViewExpanded();
                searchView.clearFocus();
                searchView.setQuery(mQuery, false);
            }
        }

        searchView.setOnQueryTextListener(new android.widget.SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                mQuery = query;
                searchView.clearFocus();
                isSearch = true;
                pageNumber = 1;
                fetchData(true, query);
                return true;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                mQuery = newText;
                if (newText.length() == 0 && isSearch && !isFavorites()) {
                    isSearch = false;
                    pageNumber = 1;
                    fetchData(false, null);
                }
                return false;
            }
        });

    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        switch (id) {
            case R.id.refresh:
                pageNumber = 1;
                fetchData(false, null);
                break;
            case (R.id.action_sort):
                new AlertDialog.Builder(c)
                        .setTitle(R.string.sort_dialog_title)
                        .setSingleChoiceItems(getResources().getStringArray(R.array.dialog_sort_options), Arrays.asList(getResources().getStringArray(R.array.dialog_sort_values)).indexOf(mPrefs.getString("sort_by", "popular")), new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {

                            }
                        })
                        .setNegativeButton(android.R.string.cancel, null)
                        .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                int selectedItem = ((AlertDialog) dialog).getListView().getCheckedItemPosition();
                                mPrefs.edit().putString(SORT_KEY, getResources().getStringArray(R.array.dialog_sort_values)[selectedItem]).apply();

                            }
                        })
                        .setCancelable(true)
                        .create()
                        .show();
                break;
        }

        return super.onOptionsItemSelected(item);
    }

    private void fetchData(final boolean isSearch, String query) {
        Call<JsonResponse> call;

        final String api_key = BuildConfig.TMDB_KEY;

        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(c.getString(R.string.base_url))
                .addConverterFactory(GsonConverterFactory.create())
                .build();

        final ApiInterface api = retrofit.create(ApiInterface.class);
        swipeRefreshLayout.setRefreshing(true);
        if (!isSearch) {
            call = api.getDataList(mPrefs.getString(SORT_KEY, "popular"), api_key, String.valueOf(pageNumber));
        } else {
            call = api.getSearchDataList(query, api_key, String.valueOf(pageNumber));
        }

        call.enqueue(new Callback<JsonResponse>() {
            @Override
            public void onResponse(Response<JsonResponse> response, Retrofit retrofit) {
                swipeRefreshLayout.setRefreshing(false);

                if (response.errorBody() == null) {
                    isLoading = false;
                    JsonResponse jr = response.body();
                    createList(jr);
                    if (hm == null) {
                        fetchGenreSchema(api, api_key);
                    } else {
                        setupAdapter();
                    }


                } else {

                    handleDataFetchError();

                }

            }

            @Override
            public void onFailure(Throwable t) {
                swipeRefreshLayout.setRefreshing(false);
                handleDataFetchError();


            }


        });

    }

    private void fetchGenreSchema(ApiInterface api, String api_key) {
        Call<GenreResponse> genreTableCall = api.getGenreScheme(api_key);
        genreTableCall.enqueue(new Callback<GenreResponse>() {
            @Override
            public void onResponse(Response<GenreResponse> response, Retrofit retrofit) {
                if (response.errorBody() == null) {
                    handleGenreMap(response);
                } else {
                    handleDataFetchError();
                }
            }

            @Override
            public void onFailure(Throwable t) {
                handleDataFetchError();
            }
        });
    }

    private void createList(JsonResponse jr) {
        totalPagesNumber = jr.getTotalPages();
        if (totalPagesNumber != dataFragment.getTotalPagesNumber()) {
            dataFragment.setTotalPagesNumber(totalPagesNumber);
        }
        if (pageNumber == 1) {
            mList = new ArrayList<>();
        }

        isRefresh = false;
        setMenuItemsVisibility(false);
        for (int i = 0; i < jr.getResults().size(); i++) {
            JsonResponse.Results result = jr.getResults().get(i);
            if (!result.isAdult() && result.getPosterPath() != null) {
                mList.add(result);
            }
        }

    }

    private void setupAdapter() {
        state = mGridView.onSaveInstanceState();
        if (mAdapter == null || pageNumber == 1) {
            mAdapter = new MovieAdapter(c, mList, width, height);
            mGridView.setAdapter(mAdapter);
            final JsonResponse.Results movieToPass = mList.get(0);
            mTarget = new Target() {
                @Override
                public void onBitmapLoaded(Bitmap bitmap, Picasso.LoadedFrom from) {
                    dataFragment.setDetailsData(movieToPass, bitmap);
                    dataFragment.setHashMapGenres(hm);
                    mListener.detailsInfoReady();
                }

                @Override
                public void onBitmapFailed(Drawable errorDrawable) {

                }

                @Override
                public void onPrepareLoad(Drawable placeHolderDrawable) {

                }
            };
            Picasso.with(getActivity()).load(getActivity().getResources().getString(R.string.db_poster_path_beginning) + movieToPass.getPosterPath()).into(mTarget);
        } else {
            mAdapter.notifyDataSetChanged();
            if (state != null) {
                mGridView.onRestoreInstanceState(state);
            }

        }
        setUpViewsListeners();
    }

    private void handleGenreMap(Response<GenreResponse> response) {
        List<GenreResponse.Genre> genreList = response.body().getGenres();
        hm = new HashMap<>();
        for (int i = 0; i < genreList.size(); i++) {
            hm.put(genreList.get(i).getId(), genreList.get(i).getName());
        }
        dataFragment.setHashMapGenres(hm);
        setupAdapter();
        genreList.clear();
    }

    private void handleDataFetchError() {
        MyDialogFragment dialogFragment = new MyDialogFragment();
        Bundle params = new Bundle();
        if (!isConnected()) {
            params.putString("content", "NoConnection");
        } else {
            params.putString("content", "NoData");
        }
        dialogFragment.setArguments(params);
        getFragmentManager().beginTransaction().add(dialogFragment, "dialog").commit();
        isRefresh = true;
        setMenuItemsVisibility(true);


    }

    private void setMenuItemsVisibility(boolean isRefreshNeeded) {
        search.setVisible(!isRefreshNeeded);
        sort.setVisible(!isRefreshNeeded);
        refresh.setVisible(isRefreshNeeded);
        if (isFavorites()) {
            search.setVisible(false);
        }

    }

    private boolean isConnected() {
        ConnectivityManager cm = (ConnectivityManager) c.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo actNet = cm.getActiveNetworkInfo();
        return actNet != null && actNet.isConnected();
    }


    @Override
    public void onPause() {
        state = mGridView.onSaveInstanceState();
        dataFragment.setMovieData(mList);
        dataFragment.setSearchBoolean(isSearch);
        dataFragment.setIsLoading(isLoading);
        dataFragment.setQuery(mQuery);
        dataFragment.setPageNumber(pageNumber);
        dataFragment.setIsRefreshMenu(isRefresh);
        if (mPosition != GridView.INVALID_POSITION) {
            dataFragment.setmScrollPosition(mPosition);
        }
        dataFragment.setHashMapGenres(hm);

        mPrefs.unregisterOnSharedPreferenceChangeListener(this);
        super.onPause();
    }


    @Override
    public void onDetach() {
        super.onDetach();
        mListener = null;
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {

        if (key.equals(SORT_KEY)) {

            if (sharedPreferences.getString(SORT_KEY, "popular").equals("favourites")) {
                Cursor c = getActivity().getContentResolver().query(MovieContract.MovieEntry.CONTENT_URI
                        , null, null, null
                        , MovieContract.MovieEntry.MOVIE_TITLE_COLUMN);
                assert c != null;
                if (!c.moveToFirst()) {
                    sharedPreferences.edit().putString(SORT_KEY, "popular").apply();
                    Toast.makeText(getActivity(), R.string.favourites_empty_toast, Toast.LENGTH_LONG).show();

                } else {
                    search.setVisible(false);
                    setCursorData();
                }
                c.close();
            }
            pageNumber = 1;
            mListener.updateTitleBySort();

            if (!isFavorites()) {
                search.setVisible(true);
                fetchData(false, null);
            }
        }
    }

    private void setCursorData() {
        favMoviesAdapter = new FavMoviesAdapter(getActivity(), null, 0);
        mGridView.setAdapter(favMoviesAdapter);
        getLoaderManager().restartLoader(LOADER_ID, null, this);
        getLoaderManager().initLoader(LOADER_ID, null, this);
        long movieId;
        Bitmap bitmap;

        if (dataFragment.getMovieId() <= 0) {
            Uri uri = MovieContract.MovieEntry.CONTENT_URI;
            Cursor c = getActivity().getContentResolver().query(uri, null, null, null, MovieContract.MovieEntry.MOVIE_TITLE_COLUMN);
            assert c != null;
            if (c.moveToFirst()) {
                movieId = c.getLong(c.getColumnIndex(MovieContract.MovieEntry.TMDB_ID));
                bitmap = new Utils(getActivity()).getImage(c.getBlob(c.getColumnIndex(MovieContract.MovieEntry.MOVIE_POSTER_BITMAP_COLUMN)));
                dataFragment.setCursorDetailsData(movieId, bitmap);
            }
            c.close();
        }
        if (isTwoPane) {
            mListener.detailsInfoReady();
        }
    }


    @Override
    public Loader<Cursor> onCreateLoader(int id, Bundle args) {
        return new CursorLoader(getActivity(),
                MovieContract.MovieEntry.CONTENT_URI,
                null,
                null,
                null,
                MovieContract.MovieEntry.MOVIE_TITLE_COLUMN);
    }

    @Override
    public void onLoadFinished(Loader<Cursor> loader, Cursor data) {
        if (favMoviesAdapter != null) {
            favMoviesAdapter.swapCursor(data);
            if (mPosition != GridView.INVALID_POSITION) {
                mGridView.smoothScrollToPosition(mPosition);
            }
        }

    }

    @Override
    public void onLoaderReset(Loader<Cursor> loader) {
        if (favMoviesAdapter != null) {
            favMoviesAdapter.swapCursor(null);
        }

    }

}

