package com.sam_chordas.android.stockhawk.ui;

import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.MenuItem;

import com.sam_chordas.android.stockhawk.R;

/**
 * Created by kikkos on 10/29/2016.
 */

public class SettingsActivity extends AppCompatActivity {


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (savedInstanceState == null){
            SettingsFragment fragment = new SettingsFragment();
            Bundle args = new Bundle();

            // pass data to fragment
            Intent intent = getIntent();
            if (intent.hasExtra(getString(R.string.stocks_activity_key))){
                args.putBoolean(getString(R.string.stocks_activity_key),
                        intent.getExtras().getBoolean(getString(R.string.stocks_activity_key), false));
            }
            if (intent.hasExtra(getString(R.string.sync_symbol_key))){
                args.putString(getString(R.string.sync_symbol_key),
                        intent.getExtras().getString(getString(R.string.sync_symbol_key), ""));
            }

            fragment.setArguments(args);
            getFragmentManager().beginTransaction().replace(android.R.id.content, fragment).commit();
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // setting the home button
        switch (item.getItemId()) {
            case android.R.id.home:
                onBackPressed();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }
}
