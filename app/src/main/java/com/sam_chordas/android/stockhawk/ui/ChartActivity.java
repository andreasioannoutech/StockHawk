package com.sam_chordas.android.stockhawk.ui;

import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;

import com.sam_chordas.android.stockhawk.R;

/**
 * Created by kikkos on 10/28/2016.
 */

public class ChartActivity extends AppCompatActivity {


    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chart);

        if (savedInstanceState == null){
            LineChartFragment fragment = new LineChartFragment();
            getSupportFragmentManager().beginTransaction().add(R.id.chart_container_id, fragment).commit();
        }
    }

}
