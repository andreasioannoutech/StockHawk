package com.sam_chordas.android.stockhawk.ui;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.annotation.Nullable;
import android.support.design.widget.AppBarLayout;
import android.support.v4.app.Fragment;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.CursorLoader;
import android.support.v4.content.Loader;
import android.support.v4.view.ViewCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.support.v7.widget.helper.ItemTouchHelper;
import android.text.InputType;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import com.afollestad.materialdialogs.MaterialDialog;
import com.melnykov.fab.FloatingActionButton;
import com.sam_chordas.android.stockhawk.R;
import com.sam_chordas.android.stockhawk.data.QuoteColumns;
import com.sam_chordas.android.stockhawk.data.QuoteProvider;
import com.sam_chordas.android.stockhawk.rest.QuoteCursorAdapter;
import com.sam_chordas.android.stockhawk.rest.Utils;
import com.sam_chordas.android.stockhawk.sync.StockHawkSyncAdapter;
import com.sam_chordas.android.stockhawk.touch_helper.SimpleItemTouchHelperCallback;

import static android.widget.Toast.makeText;
import static com.sam_chordas.android.stockhawk.rest.Utils.getApiStatus;
import static com.sam_chordas.android.stockhawk.rest.Utils.getSyncMode;

/**
 * Created by kikkos on 10/29/2016.
 */

public class MyStocksFragment extends Fragment implements LoaderManager.LoaderCallbacks<Cursor>,
        SharedPreferences.OnSharedPreferenceChangeListener {

    protected static final String[] QUOTE_COLUMNS = {
            QuoteColumns._ID,
            QuoteColumns.SYMBOL,
            QuoteColumns.BIDPRICE,
            QuoteColumns.PERCENT_CHANGE,
            QuoteColumns.CHANGE,
            QuoteColumns.ISUP,
            QuoteColumns.COMPANYNAME
    };

    public static final int COL_INDEX_ID = 0;
    public static final int COL_INDEX_SYMBOL = 1;
    public static final int COL_INDEX_BIDPRICE = 2;
    public static final int COL_INDEX_PERCENT_CHANGE = 3;
    public static final int COL_INDEX_CHANGE = 4;
    public static final int COL_INDEX_ISUP = 5;
    public static final int COL_INDEX_COMPANY_NAME = 6;


    private ItemTouchHelper mItemTouchHelper;
    private static final int CURSOR_LOADER_ID = 0;
    private QuoteCursorAdapter mCursorAdapter;
    private Context mContext;
    boolean isConnected;

    public MyStocksFragment() {
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
    }

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {

        View rootView = inflater.inflate(R.layout.fragment_my_stocks, container, false);

        mContext = getActivity();
        // check network
        isConnected = Utils.isNetworkAvailable(mContext, getSyncMode(getActivity()));

        if (savedInstanceState == null){
            if (isConnected){
                // start quotes sync
                StockHawkSyncAdapter.syncImmediately(mContext,StockHawkSyncAdapter.SYNC_MODE_INIT, null);
            }
        }

        RecyclerView recyclerView = (RecyclerView) rootView.findViewById(R.id.recycler_view);
        recyclerView.setLayoutManager(new LinearLayoutManager(mContext));

        View emptyView = (View) rootView.findViewById(R.id.quotesView_empty_list);
        mCursorAdapter = new QuoteCursorAdapter(mContext, new QuoteCursorAdapter.QuoteAdapterOnClickHandler() {
            @Override
            public void onClick(String symbol) {
                // start charts activity
                Intent intent = new Intent(mContext, ChartActivity.class);
                intent.putExtra(getString(R.string.sync_symbol_key), symbol);
                startActivity(intent);
            }
        }, null, emptyView);

        recyclerView.setAdapter(mCursorAdapter);

        FloatingActionButton fab = (FloatingActionButton) rootView.findViewById(R.id.fab);
        fab.attachToRecyclerView(recyclerView);
        fab.setContentDescription(getString(R.string.content_description_fab));
        fab.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (isConnected){
                    new MaterialDialog.Builder(mContext).title(R.string.symbol_search)
                            .content(R.string.content_test)
                            .inputType(InputType.TYPE_CLASS_TEXT)
                            .input(R.string.input_hint, R.string.input_prefill, new MaterialDialog.InputCallback() {
                                @Override public void onInput(MaterialDialog dialog, CharSequence input) {
                                    // On FAB click, receive user input. Make sure the stock doesn't already exist
                                    // in the DB and proceed accordingly
                                    Cursor c = mContext.getContentResolver().query(QuoteProvider.Quotes.CONTENT_URI,
                                            new String[] { QuoteColumns.SYMBOL }, QuoteColumns.SYMBOL + "= ?",
                                            new String[] { input.toString() }, null);
                                    if (c.getCount() != 0) {
                                        Toast toast = makeText(mContext, getString(R.string.toast_already_saved), Toast.LENGTH_SHORT);
                                        toast.setGravity(Gravity.CENTER, Gravity.CENTER, 0);
                                        toast.show();
                                        return;
                                    } else {
                                        // Add the stock to DB
                                        if (!Utils.isGoodInput(input.toString())){
                                            Toast toast = makeText(mContext, getString(R.string.empty_quotes_list_invalid_input), Toast.LENGTH_SHORT);
                                            toast.setGravity(Gravity.CENTER, Gravity.CENTER, 0);
                                            toast.show();
                                            return;
                                        }
                                        StockHawkSyncAdapter.syncImmediately(mContext,StockHawkSyncAdapter.SYNC_MODE_ADD, input.toString());
                                    }
                                }
                            })
                            .show();
                }

            }
        });

        ItemTouchHelper.Callback callback = new SimpleItemTouchHelperCallback(mCursorAdapter);
        mItemTouchHelper = new ItemTouchHelper(callback);
        mItemTouchHelper.attachToRecyclerView(recyclerView);

        if (isConnected){
            // every 1 hour // 3600 seconds
            long period = 3600L;
            long flex = 10L;
            // set a 1 hour period sync.
            StockHawkSyncAdapter.configurePeriodicSync(mContext, (int) period, (int) flex);
        }

        // set appbar.
        final AppBarLayout appBarLayout = (AppBarLayout) rootView.findViewById(R.id.appBar);
        if (null != appBarLayout){
            ViewCompat.setElevation(appBarLayout, 0);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP){
                recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
                    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
                    @Override
                    public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
                        if (0 == recyclerView.computeVerticalScrollOffset()){
                            appBarLayout.setElevation(0);
                        }else {
                            appBarLayout.setElevation(appBarLayout.getTargetElevation());
                        }
                    }
                });
            }
        }

        return rootView;
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        //set toolbar
        setToolBar();
    }

    @Override
    public void onPause() {
        // un-register listener on prefs
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(getActivity());
        sp.unregisterOnSharedPreferenceChangeListener(this);
        super.onPause();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Utils.resetApiStatusAndSyncMode(getActivity());
        getLoaderManager().destroyLoader(CURSOR_LOADER_ID);
        // empty chart data cache
        getActivity().getContentResolver().delete(QuoteProvider.Chart.CONTENT_URI, null, null);
    }

    @Override
    public void onResume() {
        // register listener on prefs
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(getActivity());
        sp.registerOnSharedPreferenceChangeListener(this);
        super.onResume();
    }

    @Override
    public void onStart() {
        super.onStart();
        getLoaderManager().initLoader(CURSOR_LOADER_ID, null, this);
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.my_stocks, menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        if (id == R.id.action_settings) {
            Intent intent = new Intent(mContext, SettingsActivity.class);
            intent.putExtra(getString(R.string.stocks_activity_key), true);
            startActivity(intent);
            return true;
        }

        if (id == R.id.action_change_units){
            // this is for changing stock changes from percent value to dollar value
            Utils.showPercent = !Utils.showPercent;
            mContext.getContentResolver().notifyChange(QuoteProvider.Quotes.CONTENT_URI, null);
        }

        if (id == R.id.action_refresh){
            StockHawkSyncAdapter.syncImmediately(mContext,StockHawkSyncAdapter.SYNC_MODE_INIT, null);
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public Loader<Cursor> onCreateLoader(int id, Bundle args) {
        // get all quotes as all data are current
        return new CursorLoader(getActivity(),QuoteProvider.Quotes.CONTENT_URI,
                QUOTE_COLUMNS,
                null,
                null,
                null);
    }

    @Override
    public void onLoadFinished(android.support.v4.content.Loader<Cursor> loader, Cursor data) {
        mCursorAdapter.swapCursor(data);
        updateEmptyView();
    }

    @Override
    public void onLoaderReset(android.support.v4.content.Loader<Cursor> loader) {
        mCursorAdapter.swapCursor(null);
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        // if any change on api status in preferences update empty view
        if (key.equals(getString(R.string.api_status_key))){
            updateEmptyView();
        }
    }

    public void setToolBar(){
        // set toolbar
        AppCompatActivity activity = (AppCompatActivity)getActivity();
        Toolbar toolbar = (Toolbar) getView().findViewById(R.id.toolbar);
        if ( null != toolbar ) {
            activity.setSupportActionBar(toolbar);
            activity.getSupportActionBar().setDisplayShowTitleEnabled(true);
        }
    }

    private void updateEmptyView(){
        // set an empty view with api status warning message
        // also i added a toast in case there are data in the DB but there is an server error for new data.
        @StockHawkSyncAdapter.API_STATUS int status = getApiStatus(mContext);
        if (status != StockHawkSyncAdapter.API_STATUS_OK){
            View rootView = getView();
            TextView tv = (TextView) rootView.findViewById(R.id.quotesView_empty_list);
            if (tv != null){
                int message = R.string.empty_quotes_list;
                switch (status) {
                    case StockHawkSyncAdapter.API_STATUS_SERVER_DOWN:{
                        message = R.string.empty_quotes_list_server_down;
                        break;
                    }
                    case StockHawkSyncAdapter.API_STATUS_SERVER_ERROR:{
                        message = R.string.empty_quotes_list_server_error;
                        break;
                    }
                    case StockHawkSyncAdapter.API_STATUS_INVALID:{
                        message = R.string.empty_quotes_list_invalid_input;
                        break;
                    }
                    case StockHawkSyncAdapter.API_STATUS_UNKNOWN: {
                        message = R.string.empty_quotes_list_no_network;
                        break;
                    }
                }
                Toast.makeText(getContext(), getString(message), Toast.LENGTH_SHORT).show();
                tv.setText(message);
            }
        }
    }
}
