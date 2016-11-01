package com.sam_chordas.android.stockhawk.rest;

import android.content.ContentProviderOperation;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.preference.PreferenceManager;
import android.support.annotation.IntDef;
import android.util.Log;
import android.widget.Toast;

import com.sam_chordas.android.stockhawk.R;
import com.sam_chordas.android.stockhawk.data.QuoteChartColumns;
import com.sam_chordas.android.stockhawk.data.QuoteColumns;
import com.sam_chordas.android.stockhawk.data.QuoteProvider;
import com.sam_chordas.android.stockhawk.sync.StockHawkSyncAdapter;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.UnsupportedEncodingException;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.net.URLEncoder;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;

import static com.sam_chordas.android.stockhawk.sync.StockHawkSyncAdapter.API_STATUS_INVALID;
import static com.sam_chordas.android.stockhawk.sync.StockHawkSyncAdapter.SYNC_MODE_ADD;
import static com.sam_chordas.android.stockhawk.sync.StockHawkSyncAdapter.SYNC_MODE_CHART;
import static com.sam_chordas.android.stockhawk.sync.StockHawkSyncAdapter.SYNC_MODE_INIT;
import static com.sam_chordas.android.stockhawk.sync.StockHawkSyncAdapter.SYNC_MODE_PERIODIC;

/**
 * Created by sam_chordas on 10/8/15.
 */
public class Utils {

  private static String LOG_TAG = Utils.class.getSimpleName();

  public static boolean showPercent = true;

  public static ArrayList quoteJsonToContentVals(Context c, String JSON){
    ArrayList<ContentProviderOperation> batchOperations = new ArrayList<>();
    JSONObject jsonObject = null;
    JSONArray resultsArray = null;
    try{
      jsonObject = new JSONObject(JSON);
      if (jsonObject != null && jsonObject.length() != 0){
        jsonObject = jsonObject.getJSONObject("query");
        int count = Integer.parseInt(jsonObject.getString("count"));
        if (count == 1){
          jsonObject = jsonObject.getJSONObject("results").getJSONObject("quote");
          if (jsonObject.isNull("Name")){
            return null;
          }
          batchOperations.add(buildBatchOperation(c, jsonObject));
        } else{
          resultsArray = jsonObject.getJSONObject("results").getJSONArray("quote");
          if (resultsArray != null && resultsArray.length() != 0){
            for (int i = 0; i < resultsArray.length(); i++){
              jsonObject = resultsArray.getJSONObject(i);
              batchOperations.add(buildBatchOperation(c, jsonObject));
            }
          }
        }
      }
    } catch (JSONException e){
      Log.e(LOG_TAG, "String to JSON failed: " + e);
    }
    return batchOperations;
  }

  public static String truncatePrice(Context c, String price){
    // IF DATA PROVIDED BY THE SERVER ARE NULL INFORM USER
    if (price.isEmpty() || price.equals("null")){
      setApiStatus(c, StockHawkSyncAdapter.API_STATUS_SERVER_ERROR);
      return "0";
    }
    price = String.format("%.2f", Float.parseFloat(price));
    return price;
  }

  public static String truncateChange(Context c, String change, boolean isPercentChange){
    // IF DATA PROVIDED BY THE SERVER ARE NULL INFORM USER
    if (change.isEmpty() || change.equals("null")){
      setApiStatus(c, StockHawkSyncAdapter.API_STATUS_SERVER_ERROR);
      return "0";
    }
    String weight = change.substring(0,1);
    String ampersand = "";
    if (isPercentChange){
      ampersand = change.substring(change.length() - 1, change.length());
      change = change.substring(0, change.length() - 1);
    }
    change = change.substring(1, change.length());
    double round = (double) Math.round(Double.parseDouble(change) * 100) / 100;
    change = String.format("%.2f", round);
    StringBuffer changeBuffer = new StringBuffer(change);
    changeBuffer.insert(0, weight);
    changeBuffer.append(ampersand);
    change = changeBuffer.toString();
    return change;
  }

  public static ContentProviderOperation buildBatchOperation(Context c, JSONObject jsonObject){

    ContentProviderOperation.Builder builder = ContentProviderOperation.newInsert(QuoteProvider.Quotes.CONTENT_URI);

    try {
      String change = truncateChange(c, jsonObject.getString("Change"), false);
      builder.withValue(QuoteColumns.COMPANYNAME, jsonObject.getString("Name"));
      builder.withValue(QuoteColumns.SYMBOL, jsonObject.getString("symbol"));
      builder.withValue(QuoteColumns.BIDPRICE, truncatePrice(c, jsonObject.getString("Bid")));
      builder.withValue(QuoteColumns.PERCENT_CHANGE, truncateChange(c, jsonObject.getString("ChangeinPercent"), true));
      builder.withValue(QuoteColumns.CHANGE, change);
      if (change.charAt(0) == '-'){
        builder.withValue(QuoteColumns.ISUP, 0);
      }else{
        builder.withValue(QuoteColumns.ISUP, 1);
      }

    } catch (JSONException e){
      e.printStackTrace();
    }
    return builder.build();
  }

  public static ArrayList<ContentProviderOperation> chartJsonToContentVals(Context c, String JSON){
    // EXTRACT THE CHART DATA FROM THE RESPONSE STRING TO A CONTENTPROVIDEROPERATIONS VARIABLE
    // THAT WILL BE SAVED INTO THE CACHE
    ArrayList<ContentProviderOperation> batchOperations = new ArrayList<>();
    try {
      JSONObject jsonObject = new JSONObject(JSON);
      JSONArray quotesArray = jsonObject
              .getJSONObject("query")
              .getJSONObject("results")
              .getJSONArray("quote");
      JSONObject quote;
      for (int i = quotesArray.length()-1; i >= 0; i--){
        quote = quotesArray.getJSONObject(i);
        ContentProviderOperation.Builder builder = ContentProviderOperation.newInsert(QuoteProvider.Chart.CONTENT_URI);
        builder.withValue(QuoteChartColumns.SYMBOL, quote.getString("Symbol"));
        builder.withValue(QuoteChartColumns.DATE, quote.getString("Date"));
        builder.withValue(QuoteChartColumns.OPEN, truncatePrice(c, quote.getString("Open")));
        builder.withValue(QuoteChartColumns.CLOSE, truncatePrice(c, quote.getString("Close")));
        builder.withValue(QuoteChartColumns.HIGH, truncatePrice(c, quote.getString("High")));
        builder.withValue(QuoteChartColumns.LOW, truncatePrice(c, quote.getString("Low")));
        batchOperations.add(builder.build());
      }
    } catch (JSONException e) {
      e.printStackTrace();
    }
    return batchOperations;
  }

  public static void setApiStatus(Context context, @StockHawkSyncAdapter.API_STATUS int status){
    //save API STATUS in shared preference file.
    SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(context);
    SharedPreferences.Editor editor = settings.edit();
    editor.putInt(context.getString(R.string.api_status_key), status);
    editor.commit();
  }

  @SuppressWarnings("ResourceType")
  public static @StockHawkSyncAdapter.API_STATUS int getApiStatus(Context c){
    SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(c);
    return settings.getInt(c.getString(R.string.api_status_key), StockHawkSyncAdapter.API_STATUS_OK);
  }

  public static void resetApiStatusAndSyncMode(Context c){
    // RESETING THE API STATUS AND THE SYNC MODE AFTER THE MY STOCKS ACTIVITY IS DESTROYED.
    SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(c);
    SharedPreferences.Editor editor = settings.edit();
    editor.putInt(c.getString(R.string.api_status_key), StockHawkSyncAdapter.API_STATUS_OK);
    editor.putInt(c.getString(R.string.sync_mode_key), SYNC_MODE_INIT);
    editor.apply();
  }

  public static void setSyncMode(Context context, @StockHawkSyncAdapter.SYNC_MODE int mode){
    //SAVE SYNC MODE IN PREFERENCE FOR GLOBAL USAGE.
    SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(context);
    SharedPreferences.Editor editor = settings.edit();
    switch (mode){
      case SYNC_MODE_INIT:{
        editor.putInt(context.getString(R.string.sync_mode_key), SYNC_MODE_INIT);
        break;
      }
      case SYNC_MODE_PERIODIC:{
        editor.putInt(context.getString(R.string.sync_mode_key), SYNC_MODE_PERIODIC);
        break;
      }
      case SYNC_MODE_ADD:{
        editor.putInt(context.getString(R.string.sync_mode_key), SYNC_MODE_ADD);
        break;
      }
      case SYNC_MODE_CHART:{
        editor.putInt(context.getString(R.string.sync_mode_key), SYNC_MODE_CHART);
        break;
      }
    }
    editor.commit();
  }

  @SuppressWarnings("ResourceType")
  public static @StockHawkSyncAdapter.SYNC_MODE int getSyncMode(Context context){
    SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(context);
    return settings.getInt(context.getString(R.string.sync_mode_key), SYNC_MODE_INIT);
  }

  public static boolean isNetworkAvailable(Context c, @StockHawkSyncAdapter.SYNC_MODE int mode){
    //CHECK NETWORK STATUS
    ConnectivityManager cm = (ConnectivityManager) c.getSystemService(Context.CONNECTIVITY_SERVICE);
    NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
    boolean status = activeNetwork != null && activeNetwork.isConnectedOrConnecting();
    if (!status){
      Toast.makeText(c, c.getString(R.string.empty_quotes_list_no_network), Toast.LENGTH_SHORT).show();
      setApiStatus(c, StockHawkSyncAdapter.API_STATUS_UNKNOWN);
    }
    return status;
  }

  // ANOTATION USED FOR SELLECTING A CHART PERIOD
  @Retention(RetentionPolicy.SOURCE)
  @IntDef({CHART_7_DAYS, CHART_14_DAYS, CHART_30_DAYS})
  public @interface CHART_PERIOD_MODE {}

  public static final int CHART_7_DAYS = 1;
  public static final int CHART_14_DAYS = 2;
  public static final int CHART_30_DAYS = 3;

  @SuppressWarnings("ResourceType")
  public static @CHART_PERIOD_MODE int getChartPeriod(Context c){
    SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(c);
    return Integer.parseInt(settings.getString(c.getString(R.string.prefs_chart_period_key), String.valueOf(Utils.CHART_14_DAYS)));
  }

  public static String buildChartUrl(Context context, String symbol, @CHART_PERIOD_MODE int selection){
    // CREATE THE URL THAT WILL QUERY THE HISTORICAL YQL AND GET DATA TO BUILD THE CHART
    // OF THE SELECTED QUOTE.
    StringBuilder urlStringBuilder = new StringBuilder();
    SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
    Calendar cal = Calendar.getInstance();

    switch (selection){
      case CHART_7_DAYS:{
        cal.add(Calendar.DATE, -7);
        break;
      }
      case CHART_14_DAYS:{
        cal.add(Calendar.DATE, -14);
        break;
      }
      case CHART_30_DAYS:{
        cal.add(Calendar.DATE, -30);
        break;
      }
      default:{
        break;
      }
    }

    Date startDate = cal.getTime();
    cal = Calendar.getInstance();
    Date endDate = cal.getTime();

    try{
      urlStringBuilder.append("https://query.yahooapis.com/v1/public/yql?q=");
      urlStringBuilder.append(URLEncoder.encode("select * from yahoo.finance.historicaldata where symbol = \""
              + symbol
              + "\" and startDate = \""
              + sdf.format(startDate)
              +"\" and endDate = \""
              +sdf.format(endDate)
              +"\"", "UTF-8"));
      //select * from yahoo.finance.historicaldata where symbol = "GOOG" and startDate = "2016-09-26" and endDate = "2016-10-26"
      urlStringBuilder.append(context.getString(R.string.yql_api_key));
    } catch (UnsupportedEncodingException e) {
      e.printStackTrace();
    }
    return urlStringBuilder.toString();
  }

  public static StringBuilder buildBaseUrl(){
    // CREATING THE BASE URL THAT WILL BE USED BY THE REST OF THE URLS
    // BSED ON THE REQUIRED TASK.
    StringBuilder urlStringBuilder = new StringBuilder();
    try{
      // Base URL for the Yahoo query
      urlStringBuilder.append("https://query.yahooapis.com/v1/public/yql?q=");
      urlStringBuilder.append(URLEncoder.encode("select * from yahoo.finance.quotes where symbol " + "in (", "UTF-8"));
    } catch (UnsupportedEncodingException e) {
      e.printStackTrace();
    }
    return urlStringBuilder;
  }

  public static String buildInitBaseUrl(Context context, Cursor cursor){
    // CREATING THE BASE URL THAT WILL BE USED TO DOWNLOAD THE INITIAL QUOTES
    StringBuilder sb = buildBaseUrl();
    try {
      if (cursor.getCount() == 0 || cursor == null){
        sb.append(URLEncoder.encode("\"YHOO\",\"AAPL\",\"GOOG\",\"MSFT\")", "UTF-8"));

      }else if (cursor != null){
        StringBuilder mStoredSymbols = new StringBuilder();
        DatabaseUtils.dumpCursor(cursor);
        cursor.moveToFirst();

        for (int i = 0; i < cursor.getCount(); i++){
          mStoredSymbols.append("\""+ cursor.getString(cursor.getColumnIndex(QuoteColumns.SYMBOL))+"\",");
          cursor.moveToNext();
        }

        mStoredSymbols.replace(mStoredSymbols.length() - 1, mStoredSymbols.length(), ")");
        sb.append(URLEncoder.encode(mStoredSymbols.toString(), "UTF-8"));
      }

      sb.append(context.getString(R.string.yql_api_key));
    } catch (UnsupportedEncodingException e) {
      e.printStackTrace();
    }
    return sb.toString();
  }

  public static String buildAddBaseUrl(Context context, String symbol){
    // CREATING THE BASE URL STRING THAT WILL BE USED FOR FINDING AND ADDING
    // A SPECIFIC QUOTE - USED IN THE ADD QOUTE FUNCTION -
    StringBuilder sb = buildBaseUrl();
    try {
      sb.append(URLEncoder.encode("\""+symbol+"\")", "UTF-8"));
      sb.append(context.getString(R.string.yql_api_key));

    } catch (UnsupportedEncodingException e) {
      e.printStackTrace();
      setApiStatus(context, API_STATUS_INVALID);
    }
    return sb.toString();
  }

  public static boolean isGoodInput(String input){
    // CHECKING THE STOCK INPUT THAT WILL ONLY CONTAIN
    // CAPITAL CHARACTERS WITHIN THE A-Z RANGE.
    return input.matches("[A-Z]+");
  }

}
