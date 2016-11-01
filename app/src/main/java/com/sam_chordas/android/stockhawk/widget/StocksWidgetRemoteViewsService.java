package com.sam_chordas.android.stockhawk.widget;

import android.annotation.TargetApi;
import android.content.Intent;
import android.database.Cursor;
import android.os.Binder;
import android.os.Build;
import android.widget.AdapterView;
import android.widget.RemoteViews;
import android.widget.RemoteViewsService;

import com.sam_chordas.android.stockhawk.R;
import com.sam_chordas.android.stockhawk.data.QuoteColumns;
import com.sam_chordas.android.stockhawk.data.QuoteProvider;
import com.sam_chordas.android.stockhawk.rest.Utils;
import com.sam_chordas.android.stockhawk.ui.MyStocksFragment;

/**
 * Created by kikkos on 11/1/2016.
 */

public class StocksWidgetRemoteViewsService extends RemoteViewsService {

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

    @Override
    public RemoteViewsFactory onGetViewFactory(Intent intent) {
        return new RemoteViewsFactory() {

            private Cursor data = null;

            @Override
            public void onCreate() {

            }

            @Override
            public void onDataSetChanged() {
                // get data from db
                if (data != null) {
                    data.close();
                }
                final long identityToken = Binder.clearCallingIdentity();
                data = getContentResolver().query(QuoteProvider.Quotes.CONTENT_URI,
                        QUOTE_COLUMNS,
                        null,
                        null,
                        null);
                Binder.restoreCallingIdentity(identityToken);
            }

            @Override
            public void onDestroy() {
                if (data != null) {
                    data.close();
                    data = null;
                }
            }

            @Override
            public int getCount() {
                return data == null ? 0 : data.getCount();
            }

            @Override
            public RemoteViews getViewAt(int position) {
                // set data to view
                if (position == AdapterView.INVALID_POSITION ||
                        data == null || !data.moveToPosition(position)) {
                    return null;
                }
                RemoteViews views = new RemoteViews(getPackageName(), R.layout.widget_list_item);
                String symbol = data.getString(COL_INDEX_SYMBOL);
                String bidPrice = data.getString(COL_INDEX_BIDPRICE);
                views.setTextViewText(R.id.widget_symbol, symbol);
                setRemoteContentDescription(views, R.id.widget_symbol, getString(R.string.content_description_symbol, symbol));

                views.setTextViewText(R.id.widget_bid_price, bidPrice);
                setRemoteContentDescription(views, R.id.widget_bid_price, getString(R.string.content_description_bid_price, bidPrice));

                if (data.getInt(MyStocksFragment.COL_INDEX_ISUP) == 1){
                    views.setInt(R.id.widget_change, "setBackgroundResource", R.drawable.percent_change_pill_green);
                } else {
                    views.setInt(R.id.widget_change, "setBackgroundResource", R.drawable.percent_change_pill_red);
                }
                String change = "0";
                if (Utils.showPercent){
                    change = data.getString(COL_INDEX_PERCENT_CHANGE);
                }else {
                    change = data.getString(COL_INDEX_CHANGE);
                }
                views.setTextViewText(R.id.widget_change, change);
                setRemoteContentDescription(views, R.id.widget_change, getString(R.string.content_description_change, change));

                // set intent to be executed if you click any listed quote
                final Intent fillInIntent = new Intent();
                fillInIntent.putExtra(getString(R.string.sync_symbol_key), symbol);
                views.setOnClickFillInIntent(R.id.widget_list_item, fillInIntent);

                return views;
            }

            @TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH_MR1)
            private void setRemoteContentDescription(RemoteViews views, int id, String description) {
                // set content descriptions
                views.setContentDescription(id, description);
            }

            @Override
            public RemoteViews getLoadingView() {
                return new RemoteViews(getPackageName(), R.layout.widget_list_item);
            }

            @Override
            public int getViewTypeCount() {
                return 1;
            }

            @Override
            public long getItemId(int position) {
                if (data.moveToPosition(position))
                    return data.getInt(COL_INDEX_ID);
                return position;
            }

            @Override
            public boolean hasStableIds() {
                return true;
            }
        };
    }
}
