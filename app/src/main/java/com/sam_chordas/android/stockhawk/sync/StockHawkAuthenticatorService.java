package com.sam_chordas.android.stockhawk.sync;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.support.annotation.Nullable;

/**
 * Created by kikkos on 10/25/2016.
 */

public class StockHawkAuthenticatorService extends Service {
    // Instance field that stores the authenticator object
    private StockHawkAuthenticator mAuthenticator;

    @Override
    public void onCreate() {
        // Create a new authenticator object
        mAuthenticator = new StockHawkAuthenticator(this);
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return mAuthenticator.getIBinder();
    }
}
