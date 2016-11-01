package com.sam_chordas.android.stockhawk.ui;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.preference.PreferenceManager;
import android.support.v7.app.AppCompatActivity;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Toast;

import com.sam_chordas.android.stockhawk.R;
import com.sam_chordas.android.stockhawk.sync.StockHawkSyncAdapter;

/**
 * Created by kikkos on 10/30/2016.
 */

public class SettingsFragment extends PreferenceFragment implements
        Preference.OnPreferenceChangeListener, SharedPreferences.OnSharedPreferenceChangeListener {

    String mSymbol = "";
    private boolean FROM_STOCKS_ACTIVITY = false;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Bundle arguments = getArguments();
        if (arguments != null){
            if (arguments.containsKey(getString(R.string.stocks_activity_key))){
                FROM_STOCKS_ACTIVITY = arguments.getBoolean(getString(R.string.stocks_activity_key));
            }
            if (arguments.containsKey(getString(R.string.sync_symbol_key))){
                mSymbol = arguments.getString(getString(R.string.sync_symbol_key));
            }
        }

        addPreferencesFromResource(R.xml.prefs_general);

        bindPreferenceSummaryToValue(findPreference(getString(R.string.prefs_chart_period_key)));
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        // if got into settings from the chart activity then enter settings in fullscreen to match
        // the activity
        if (!FROM_STOCKS_ACTIVITY){
            getActivity().getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                    WindowManager.LayoutParams.FLAG_FULLSCREEN);
        }
        return super.onCreateView(inflater, container, savedInstanceState);
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        // set toolbar
        AppCompatActivity activity = (AppCompatActivity)getActivity();
        activity.getSupportActionBar().setDisplayHomeAsUpEnabled(true);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // set home button function
        switch (item.getItemId()) {
            case android.R.id.home:
                getActivity().onBackPressed();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    public void onResume() {
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(getActivity());
        sp.registerOnSharedPreferenceChangeListener(this);
        super.onResume();
    }

    @Override
    public void onPause() {
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(getActivity());
        sp.unregisterOnSharedPreferenceChangeListener(this);
        super.onPause();
    }

    public void bindPreferenceSummaryToValue(Preference preference){
        preference.setOnPreferenceChangeListener(this);
        setPreferenceSummary(preference, PreferenceManager
                .getDefaultSharedPreferences(preference.getContext())
                .getString(preference.getKey(), ""));
    }

    public void setPreferenceSummary(Preference preference, Object value){
        String stringValue = value.toString();

        if (preference instanceof ListPreference) {
            // For list preferences, look up the correct display value in
            // the preference's 'entries' list (since they have separate labels/values).
            ListPreference listPreference = (ListPreference) preference;
            int prefIndex = listPreference.findIndexOfValue(stringValue);
            if (prefIndex >= 0) {
                preference.setSummary(listPreference.getEntries()[prefIndex]);
            }
        } else {
            // For other preferences, set the summary to the value's simple string representation.
            preference.setSummary(stringValue);
        }
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        // if charts period is changed then initialize a sync for the new data to be downloaded.
        if (key.equals(getString(R.string.prefs_chart_period_key))){
            if (mSymbol.length() > 0 && !FROM_STOCKS_ACTIVITY){
                StockHawkSyncAdapter.syncImmediately(getActivity(), StockHawkSyncAdapter.SYNC_MODE_CHART, mSymbol);
            }
            Toast.makeText(getActivity(), getString(R.string.toast_setting_saved), Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        setPreferenceSummary(preference, newValue);
        return true;
    }
}
