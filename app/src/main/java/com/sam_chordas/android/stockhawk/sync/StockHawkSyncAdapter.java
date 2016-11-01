package com.sam_chordas.android.stockhawk.sync;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.content.AbstractThreadedSyncAdapter;
import android.content.ContentProviderClient;
import android.content.ContentProviderOperation;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.OperationApplicationException;
import android.content.SyncRequest;
import android.content.SyncResult;
import android.database.Cursor;
import android.os.Build;
import android.os.Bundle;
import android.os.RemoteException;
import android.support.annotation.IntDef;
import android.util.Log;

import com.sam_chordas.android.stockhawk.R;
import com.sam_chordas.android.stockhawk.data.QuoteColumns;
import com.sam_chordas.android.stockhawk.data.QuoteProvider;
import com.sam_chordas.android.stockhawk.rest.Utils;
import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.Response;

import java.io.IOException;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.net.HttpURLConnection;
import java.util.ArrayList;

import static com.sam_chordas.android.stockhawk.rest.Utils.setApiStatus;
import static com.sam_chordas.android.stockhawk.rest.Utils.setSyncMode;

/**
 * Created by kikkos on 10/25/2016.
 */

public class StockHawkSyncAdapter extends AbstractThreadedSyncAdapter {

    private String LOG_TAG = StockHawkSyncAdapter.class.getSimpleName();
    public static final String ACTION_DATA_UPDATED = "com.sam_chordas.android.stockhawk.ACTION_DATA_UPDATED";

    public static final int SYNC_INTERVAL = 60 * 180;
    public static final int SYNC_FLEXTIME = SYNC_INTERVAL/3;

    private OkHttpClient client = new OkHttpClient();
    private Context mContext;
    private boolean isUpdate;

    @Retention(RetentionPolicy.SOURCE)
    @IntDef({SYNC_MODE_INIT, SYNC_MODE_PERIODIC, SYNC_MODE_ADD, SYNC_MODE_CHART})
    public @interface SYNC_MODE {}

    public static final int SYNC_MODE_INIT = 0;
    public static final int SYNC_MODE_PERIODIC = 1;
    public static final int SYNC_MODE_ADD = 2;
    public static final int SYNC_MODE_CHART = 3;

    @Retention(RetentionPolicy.SOURCE)
    @IntDef({API_STATUS_OK, API_STATUS_SERVER_DOWN, API_STATUS_SERVER_ERROR, API_STATUS_UNKNOWN, API_STATUS_INVALID})
    public @interface API_STATUS {}

    public static final int API_STATUS_OK = 0;
    public static final int API_STATUS_SERVER_DOWN = 1;
    public static final int API_STATUS_SERVER_ERROR = 2;
    public static final int API_STATUS_UNKNOWN = 3;
    public static final int API_STATUS_INVALID =4;

    public StockHawkSyncAdapter(Context context, boolean autoInitialize) {
        super(context, autoInitialize);
        mContext = context;
    }

    @Override
    public void onPerformSync(Account account, Bundle extras, String authority, ContentProviderClient provider,
                              SyncResult syncResult) {

        Cursor initQueryCursor;
        String url = "";
        Response response;

        if (mContext == null){
            mContext = getContext();
        }

        // GET SYNC MODE
        @SYNC_MODE int sync_mode = Utils.getSyncMode(mContext);

        if (sync_mode == SYNC_MODE_INIT || sync_mode == SYNC_MODE_PERIODIC){

            // check network connectivity while in periodic syncing.
            if (sync_mode == SYNC_MODE_PERIODIC){
                if (!Utils.isNetworkAvailable(mContext, sync_mode)){
                    return;
                }
            }
            isUpdate = true;
            // GET ALL SYMBOLS STORED IN DB TO DOWNLOAD UPDATED DATA
            initQueryCursor = mContext.getContentResolver().query(QuoteProvider.Quotes.CONTENT_URI,
                    new String[] { "Distinct " + QuoteColumns.SYMBOL }, null, null, null);
            url = Utils.buildInitBaseUrl(mContext, initQueryCursor);

        } else if (sync_mode == SYNC_MODE_ADD){
            isUpdate = false;
            // get symbol from params.getExtra and build query for downloading new symbol data
            String stockInput = extras.getString(mContext.getString(R.string.sync_symbol_key));
            url = Utils.buildAddBaseUrl(mContext, stockInput);

        }else if (sync_mode == SYNC_MODE_CHART){
            isUpdate = false;
            // build url for downloading to download the chart for the selected quote
            String stockInput = extras.getString(mContext.getString(R.string.sync_symbol_key));
            url = Utils.buildChartUrl(mContext, stockInput, Utils.getChartPeriod(mContext));
        }

        if (url.length() > 0){
            try{
                response = fetchData(url);
                // check server response here
                if (!isResponseGood(mContext, response)){
                    return;
                }
                try {
                    ContentValues contentValues = new ContentValues();
                    // delete previous quotes before get the new ones.
                    if (isUpdate){
                        mContext.getContentResolver().delete(QuoteProvider.Quotes.CONTENT_URI, null, null);
                    }
                    // =========> CHANGE DATA SOURCE TO: DummyData <===============
//                    if (!DummyData.getDummyDataUsed(mContext)){
//                        DummyData.setDummyDataUsed(mContext, true);
//                        mContext.getContentResolver().applyBatch(QuoteProvider.AUTHORITY,
//                                Utils.quoteJsonToContentVals(mContext, DummyData.yahooApiResponseStr));
//                    }
                    // =========> CHANGE DATA SOURCE TO: Yahoo API <===============
                    if (sync_mode == SYNC_MODE_CHART){
                        // check if chart temporary db (CACHE) is empty, if not then delete everything to store the new chart data.
                        Cursor c = mContext.getContentResolver().query(QuoteProvider.Chart.CONTENT_URI, null, null, null, null);
                        if (c != null && c.getCount() > 0){
                            mContext.getContentResolver().delete(QuoteProvider.Chart.CONTENT_URI, null, null);
                        }
                        mContext.getContentResolver().applyBatch(QuoteProvider.AUTHORITY,
                                Utils.chartJsonToContentVals(mContext, response.body().string()));
                    }else {
                        // save quote data to db.
                        ArrayList<ContentProviderOperation> batchOperations =
                                Utils.quoteJsonToContentVals(mContext, response.body().string());
                        if (batchOperations == null){
                            setApiStatus(mContext, API_STATUS_INVALID);
                            return;
                        }
                        mContext.getContentResolver().applyBatch(QuoteProvider.AUTHORITY, batchOperations);
                        updateWidgets();
                    }
                }catch (RemoteException | OperationApplicationException e){
                    Log.e(LOG_TAG, "Error applying batch insert", e);
                    setApiStatus(mContext, API_STATUS_UNKNOWN);
                }
            } catch (IOException e){
                e.printStackTrace();
                setApiStatus(mContext, API_STATUS_SERVER_DOWN);
            }
        }
    }

    private Response fetchData(String url) throws IOException {
        // Download data from API.
        Request request = new Request.Builder().url(url).build();
        return client.newCall(request).execute();
    }

    private static boolean isResponseGood(Context c, Response response){
        // CHECK RESPONSE CODE
        switch (response.code()){
            case HttpURLConnection.HTTP_BAD_REQUEST:{
                setApiStatus(c, API_STATUS_SERVER_DOWN);
                break;
            }
            case HttpURLConnection.HTTP_NOT_FOUND: {
                setApiStatus(c, API_STATUS_INVALID);
                break;
            }
            case HttpURLConnection.HTTP_NO_CONTENT: {
                setApiStatus(c, API_STATUS_SERVER_DOWN);
                break;
            }
            case HttpURLConnection.HTTP_OK: {
                setApiStatus(c, API_STATUS_OK);
                return true;
            }
            default: {
                setApiStatus(c, API_STATUS_UNKNOWN);
                break;
            }
        }
        return false;
    }

    /**
     * Helper method to schedule the sync adapter periodic execution
     */
    public static void configurePeriodicSync(Context context, int syncInterval, int flexTime) {
        Account account = getSyncAccount(context);
        String authority = context.getString(R.string.content_authority);
        setSyncMode(context, SYNC_MODE_PERIODIC);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            // we can enable inexact timers in our periodic sync
            SyncRequest request = new SyncRequest.Builder().
                    syncPeriodic(syncInterval, flexTime).
                    setSyncAdapter(account, authority).
                    setExtras(new Bundle()).build();
            ContentResolver.requestSync(request);
        } else {
            ContentResolver.addPeriodicSync(account,
                    authority, new Bundle(), syncInterval);
        }
    }

    /**
     * Helper method to have the sync adapter sync immediately
     * @param context The context used to access the account service
     */
    public static void syncImmediately(Context context, @SYNC_MODE int mode, String symbol){
        Bundle bundle = new Bundle();
        bundle.putBoolean(ContentResolver.SYNC_EXTRAS_EXPEDITED, true);
        bundle.putBoolean(ContentResolver.SYNC_EXTRAS_MANUAL, true);
        // SET SYNC MODE HERE BASED ON STOCKSACTIVITY
        Utils.setSyncMode(context, mode);
        if (symbol != null){
            bundle.putString(context.getString(R.string.sync_symbol_key), symbol);
        }
        ContentResolver.requestSync(getSyncAccount(context), context.getString(R.string.content_authority), bundle);
    }

    public static Account getSyncAccount(Context context) {
        // Get an instance of the Android account manager
        AccountManager accountManager = (AccountManager) context.getSystemService(Context.ACCOUNT_SERVICE);

        // Create the account type and default account
        Account newAccount = new Account(context.getString(R.string.app_name), context.getString(R.string.sync_account_type));

        // If the password doesn't exist, the account doesn't exist
        if ( null == accountManager.getPassword(newAccount) ) {

        /*
         * Add the account and account type, no password or user data
         * If successful, return the Account object, otherwise report an error.
         */
            if (!accountManager.addAccountExplicitly(newAccount, "", null)) {
                return null;
            }
            /*
             * If you don't set android:syncable="true" in
             * in your <provider> element in the manifest,
             * then call ContentResolver.setIsSyncable(account, AUTHORITY, 1)
             * here.
             */

            onAccountCreated(newAccount, context);
        }
        return newAccount;
    }

    private static void onAccountCreated(Account newAccount, Context context) {
        /*
         * Since we've created an account
         */
        StockHawkSyncAdapter.configurePeriodicSync(context, SYNC_INTERVAL, SYNC_FLEXTIME);

        /*
         * Without calling setSyncAutomatically, our periodic sync will not be enabled.
         */
        ContentResolver.setSyncAutomatically(newAccount, context.getString(R.string.content_authority), true);

        /*
         * Finally, let's do a sync to get things started
         */
        syncImmediately(context, SYNC_MODE_INIT, null);
    }

    public static void initializeSyncAdapter(Context context) {
        getSyncAccount(context);
    }

    private void updateWidgets() {
        Context context = getContext();
        // Setting the package ensures that only components in our app will receive the broadcast
        Intent dataUpdatedIntent = new Intent(ACTION_DATA_UPDATED).setPackage(context.getPackageName());
        context.sendBroadcast(dataUpdatedIntent);
    }

}
