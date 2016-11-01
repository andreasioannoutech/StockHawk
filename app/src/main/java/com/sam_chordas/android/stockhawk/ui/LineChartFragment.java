package com.sam_chordas.android.stockhawk.ui;

import android.content.Intent;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.CursorLoader;
import android.support.v4.content.Loader;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Toast;

import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.AxisBase;
import com.github.mikephil.charting.components.Legend;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.formatter.IAxisValueFormatter;
import com.github.mikephil.charting.highlight.Highlight;
import com.github.mikephil.charting.interfaces.datasets.ILineDataSet;
import com.github.mikephil.charting.listener.ChartTouchListener;
import com.github.mikephil.charting.listener.OnChartGestureListener;
import com.github.mikephil.charting.listener.OnChartValueSelectedListener;
import com.sam_chordas.android.stockhawk.R;
import com.sam_chordas.android.stockhawk.data.QuoteChartColumns;
import com.sam_chordas.android.stockhawk.data.QuoteProvider;
import com.sam_chordas.android.stockhawk.sync.StockHawkSyncAdapter;

import java.util.ArrayList;

/**
 * Created by kikkos on 10/29/2016.
 */

public class LineChartFragment extends Fragment implements LoaderManager.LoaderCallbacks<Cursor>,
        OnChartValueSelectedListener, OnChartGestureListener {

    protected static final String[] CHART_COLUMNS = {
            QuoteChartColumns._ID,
            QuoteChartColumns.SYMBOL,
            QuoteChartColumns.DATE,
            QuoteChartColumns.OPEN,
            QuoteChartColumns.CLOSE,
            QuoteChartColumns.HIGH,
            QuoteChartColumns.LOW
    };

    public static final int COL_INDEX_ID = 0;
    public static final int COL_INDEX_SYMBOL = 1;
    public static final int COL_INDEX_DATE = 2;
    public static final int COL_INDEX_OPEN = 3;
    public static final int COL_INDEX_CLOSE = 4;
    public static final int COL_INDEX_HIGH = 5;
    public static final int COL_INDEX_LOW = 6;

    private LineChart mChart;
    String mSymbol = "";
    private static final int CHART_DATA_LOADER_ID = 0;

    public LineChartFragment() {
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
    }

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {

        View rootView = inflater.inflate(R.layout.fragment_line_chart, container, false);

        // set fragment to fullscreen.
        getActivity().getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);

        mSymbol = getActivity().getIntent().getExtras().getString(getString(R.string.sync_symbol_key));
        // start chart sync
        StockHawkSyncAdapter.syncImmediately(getActivity(), StockHawkSyncAdapter.SYNC_MODE_CHART, mSymbol);

        //create the chart using the PhilJay:MPAndroidChart library.
        mChart = (LineChart) rootView.findViewById(R.id.chart1);
        mChart.setOnChartGestureListener(this);
        mChart.setOnChartValueSelectedListener(this);
        mChart.setDrawGridBackground(false);

        // no description text
        mChart.getDescription().setEnabled(false);

        // enable touch gestures
        mChart.setTouchEnabled(true);

        // enable scaling and dragging
        mChart.setDragEnabled(true);
        mChart.setScaleEnabled(true);

        // if disabled, scaling can be done on x- and y-axis separately
        mChart.setPinchZoom(true);

        // create a custom MarkerView (extend MarkerView) and specify the layout
        // to use for it
        MyMarkerView mv = new MyMarkerView(getActivity(), R.layout.custom_marker_view);
        mv.setChartView(mChart); // For bounds control
        mChart.setMarker(mv); // Set the marker to the chart

        mChart.getAxisRight().setEnabled(false);

        mChart.animateX(2500);
        //mChart.invalidate();

        // get the legend (only possible after setting data)
        Legend l = mChart.getLegend();

        // modify the legend ...
        l.setForm(Legend.LegendForm.LINE);

        // // dont forget to refresh the drawing
        // mChart.invalidate();
        return rootView;
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        // set toolbar.
        setToolBar();
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.global, menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();

        if (id == R.id.action_settings) {
            // send symbol to settings activity for forcing a data sync if the period changes.
            Intent intent = new Intent(getActivity(), SettingsActivity.class);
            if (mSymbol.length() > 0){
                intent.putExtra(getString(R.string.sync_symbol_key), mSymbol);
            }
            startActivity(intent);
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onStart() {
        super.onStart();
        getLoaderManager().initLoader(CHART_DATA_LOADER_ID, null, this);
    }

    @Override
    public void onDestroy() {
        getLoaderManager().destroyLoader(CHART_DATA_LOADER_ID);
        super.onDestroy();
    }

    @Override
    public Loader<Cursor> onCreateLoader(int id, Bundle args) {
        if (mSymbol.length() > 0){
            String whereClause = QuoteChartColumns.SYMBOL + " = ?";
            String[] whereArgs = new String[]{mSymbol};
            return new CursorLoader(getActivity(), QuoteProvider.Chart.CONTENT_URI,
                    CHART_COLUMNS,
                    whereClause,
                    whereArgs,
                    null);

        }else {
            Toast.makeText(getActivity(), getString(R.string.toast_try_again), Toast.LENGTH_LONG).show();
            return null;
        }
    }

    @Override
    public void onLoadFinished(Loader<Cursor> loader, Cursor data) {
        // setting the chart data.
        setData(data);
    }

    @Override
    public void onLoaderReset(Loader<Cursor> loader) {

    }

    public void setToolBar(){
        // creating and setting the toolbar.
        AppCompatActivity activity = (AppCompatActivity)getActivity();
        Toolbar toolbar = (Toolbar) getView().findViewById(R.id.toolbar);
        if ( null != toolbar ) {
            activity.setSupportActionBar(toolbar);
            activity.getSupportActionBar().setDisplayShowTitleEnabled(true);
            activity.getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
    }

    public class MyXAxisValueFormatter implements IAxisValueFormatter {

        // setting the dates on the X-axis labels
        private String[] mValues;

        public MyXAxisValueFormatter(String[] vals) {
            mValues = vals;
        }

        @Override
        public int getDecimalDigits() {
            return 0;
        }

        @Override
        public String getFormattedValue(float value, AxisBase axis) {
            if (value > mValues.length){
                return "";
            }
            return mValues[(int) value];
        }
    }

    private void setXaxis(final String[] dates){

        // setting the X-axis
        XAxis xAxis = mChart.getXAxis();
        xAxis.setTextColor(Color.WHITE);
        xAxis.setGranularity(1f);
        xAxis.setValueFormatter(new MyXAxisValueFormatter(dates));
        xAxis.setTextSize(5f);
    }

    private void setYaxis(){

        // setting the Y-axis
        YAxis yAxis = mChart.getAxisLeft();
        yAxis.setDrawZeroLine(false);
        yAxis.setTextColor(Color.WHITE);
    }

    private void setData(Cursor c) {

        // setting the data for the line in the chart to be drawn.
        if (c != null && c.getCount() > 0){
            // getting the data from the DB and save them in the array list
            ArrayList<Entry> values = new ArrayList<Entry>();
            c.moveToFirst();
            String[] dates = new String[c.getCount()];

            for (int i = 0; i < c.getCount(); i++) {
                // get the dates into an array of strings.
                dates[i] = c.getString(COL_INDEX_DATE);
                //get the closing price into a values arraylist.
                float val = Float.parseFloat(c.getString(COL_INDEX_CLOSE));
                values.add(new Entry(i, val));
                c.moveToNext();
            }

            LineDataSet set1;

            if (mChart.getData() != null && mChart.getData().getDataSetCount() > 0) {
                set1 = (LineDataSet)mChart.getData().getDataSetByIndex(0);
                set1.setValues(values);
                mChart.getData().notifyDataChanged();
                mChart.notifyDataSetChanged();
            } else {
                // create a dataset and give it a type
                set1 = new LineDataSet(values, "DataSet 1");

                // set the line to be drawn like this "- - - - - -"
                set1.enableDashedLine(10f, 5f, 0f);
                set1.enableDashedHighlightLine(10f, 5f, 0f);
                set1.setColor(Color.WHITE);
                set1.setCircleColor(Color.WHITE);
                set1.setLineWidth(1f);
                set1.setCircleRadius(2f);
                set1.setDrawCircleHole(false);
                set1.setValueTextSize(9f);
                set1.setValueTextColor(Color.WHITE);
                set1.setDrawFilled(false); // CHANGE TO TRUE OR REMOVE THE BELLOW DRAWABLE
                set1.setFormLineWidth(1f);
                set1.setFormLineDashEffect(new DashPathEffect(new float[]{10f, 5f}, 0f));
                set1.setFormSize(15.f);

                ArrayList<ILineDataSet> dataSets = new ArrayList<ILineDataSet>();
                dataSets.add(set1); // add the datasets

                // create a data object with the datasets
                LineData data = new LineData(dataSets);

                // set data
                mChart.setData(data);
//                mChart.invalidate();
            }

            setXaxis(dates);
            setYaxis();
        }
    }

    @Override
    public void onChartGestureStart(MotionEvent me, ChartTouchListener.ChartGesture lastPerformedGesture) {

    }

    @Override
    public void onChartGestureEnd(MotionEvent me, ChartTouchListener.ChartGesture lastPerformedGesture) {
        // un-highlight values after the gesture is finished and no single-tap
        if(lastPerformedGesture != ChartTouchListener.ChartGesture.SINGLE_TAP)
            mChart.highlightValues(null); // or highlightTouch(null) for callback to onNothingSelected(...)
    }

    @Override
    public void onChartLongPressed(MotionEvent me) {

    }

    @Override
    public void onChartDoubleTapped(MotionEvent me) {

    }

    @Override
    public void onChartSingleTapped(MotionEvent me) {

    }

    @Override
    public void onChartFling(MotionEvent me1, MotionEvent me2, float velocityX, float velocityY) {

    }

    @Override
    public void onChartScale(MotionEvent me, float scaleX, float scaleY) {

    }

    @Override
    public void onChartTranslate(MotionEvent me, float dX, float dY) {

    }

    @Override
    public void onValueSelected(Entry e, Highlight h) {

    }

    @Override
    public void onNothingSelected() {

    }
}
