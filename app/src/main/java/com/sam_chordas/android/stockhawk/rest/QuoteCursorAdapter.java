package com.sam_chordas.android.stockhawk.rest;

import android.content.Context;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Build;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.sam_chordas.android.stockhawk.R;
import com.sam_chordas.android.stockhawk.data.QuoteChartColumns;
import com.sam_chordas.android.stockhawk.data.QuoteProvider;
import com.sam_chordas.android.stockhawk.touch_helper.ItemTouchHelperAdapter;
import com.sam_chordas.android.stockhawk.touch_helper.ItemTouchHelperViewHolder;
import com.sam_chordas.android.stockhawk.ui.MyStocksFragment;

/**
 * Created by sam_chordas on 10/6/15.
 *  Credit to skyfishjy gist:
 *    https://gist.github.com/skyfishjy/443b7448f59be978bc59
 * for the code structure
 */
public class QuoteCursorAdapter extends CursorRecyclerViewAdapter<QuoteCursorAdapter.ViewHolder>
    implements ItemTouchHelperAdapter{

  private static Context mContext;
  private static Typeface robotoLight;
  private View mEmptyView;
  private final QuoteAdapterOnClickHandler mClickHandler;

  public QuoteCursorAdapter(Context context, QuoteAdapterOnClickHandler clickHandler, Cursor cursor, View emptyView){
    super(context, cursor);
    mContext = context;
    mEmptyView = emptyView;
    mClickHandler = clickHandler;
  }

  @Override
  public Cursor swapCursor(Cursor newCursor) {
    Cursor c = super.swapCursor(newCursor);
    // IF NO DATA IN THE DB SET EMPTY VIEW TO BE VISIBLE.
    mEmptyView.setVisibility(getItemCount() == 0 ? View.VISIBLE : View.GONE);
    return c;
  }

  @Override
  public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType){
    robotoLight = Typeface.createFromAsset(mContext.getAssets(), "fonts/Roboto-Light.ttf");
    View itemView = LayoutInflater.from(parent.getContext()).inflate(R.layout.list_item_quote, parent, false);
    ViewHolder vh = new ViewHolder(itemView);
    return vh;
  }

  @Override
  public void onBindViewHolder(final ViewHolder viewHolder, final Cursor cursor){
    String symbol = cursor.getString(MyStocksFragment.COL_INDEX_SYMBOL);
    viewHolder.symbol.setText(symbol);
    viewHolder.symbol.setContentDescription(mContext.getString(R.string.content_description_symbol, cursor.getString(MyStocksFragment.COL_INDEX_COMPANY_NAME)));

    String bidPrice = cursor.getString(MyStocksFragment.COL_INDEX_BIDPRICE);
    viewHolder.bidPrice.setText(bidPrice);
    viewHolder.bidPrice.setContentDescription(mContext.getString(R.string.content_description_bid_price, bidPrice));

    int sdk = Build.VERSION.SDK_INT;

    if (cursor.getInt(MyStocksFragment.COL_INDEX_ISUP) == 1){
      if (sdk < Build.VERSION_CODES.JELLY_BEAN){
        viewHolder.change.setBackgroundDrawable(mContext.getResources().getDrawable(R.drawable.percent_change_pill_green));
      }else {
        viewHolder.change.setBackground(mContext.getResources().getDrawable(R.drawable.percent_change_pill_green));
      }
    } else {
      if (sdk < Build.VERSION_CODES.JELLY_BEAN) {
        viewHolder.change.setBackgroundDrawable(mContext.getResources().getDrawable(R.drawable.percent_change_pill_red));
      } else{
        viewHolder.change.setBackground(mContext.getResources().getDrawable(R.drawable.percent_change_pill_red));
      }
    }

    if (Utils.showPercent){
      String percentChange = cursor.getString(MyStocksFragment.COL_INDEX_PERCENT_CHANGE);
      viewHolder.change.setText(percentChange);
      viewHolder.change.setContentDescription(mContext.getString(R.string.content_description_change, percentChange));
    } else{
      String change = cursor.getString(MyStocksFragment.COL_INDEX_CHANGE);
      viewHolder.change.setText(change);
      viewHolder.change.setContentDescription(mContext.getString(R.string.content_description_change, change));
    }
  }

  @Override public void onItemDismiss(int position) {
    Cursor c = getCursor();
    c.moveToPosition(position);
    String symbol = c.getString(MyStocksFragment.COL_INDEX_SYMBOL);
    mContext.getContentResolver().delete(QuoteProvider.Quotes.withSymbol(symbol), null, null);
    notifyItemRemoved(position);
  }

  @Override public int getItemCount() {
    return super.getItemCount();
  }

  public static interface QuoteAdapterOnClickHandler{
    void onClick(String symbol);
  }

  public class ViewHolder extends RecyclerView.ViewHolder implements ItemTouchHelperViewHolder, View.OnClickListener{

    public final TextView symbol;
    public final TextView bidPrice;
    public final TextView change;

    public ViewHolder(View itemView){
      super(itemView);
      symbol = (TextView) itemView.findViewById(R.id.stock_symbol);
      symbol.setTypeface(robotoLight);
      bidPrice = (TextView) itemView.findViewById(R.id.bid_price);
      change = (TextView) itemView.findViewById(R.id.change);
      itemView.setOnClickListener(this);
    }

    @Override
    public void onItemSelected(){
      itemView.setBackgroundColor(Color.LTGRAY);
    }

    @Override
    public void onItemClear(){
      itemView.setBackgroundColor(0);
    }

    @Override
    public void onClick(View v) {
      int adapterPosition = getAdapterPosition();
      Cursor cursor = getCursor();
      cursor.moveToPosition(adapterPosition);
      int symbolColumnIndex = cursor.getColumnIndex(QuoteChartColumns.SYMBOL);
      mClickHandler.onClick(cursor.getString(symbolColumnIndex));
    }
  }
}
