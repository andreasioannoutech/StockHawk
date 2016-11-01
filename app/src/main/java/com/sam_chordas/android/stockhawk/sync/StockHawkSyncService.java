package com.sam_chordas.android.stockhawk.sync;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.support.annotation.Nullable;

/**
 * Created by kikkos on 10/25/2016.
 */

public class StockHawkSyncService extends Service {
    private static final Object sSyncAdapterLock = new Object();
    private static StockHawkSyncAdapter sStockHawkSyncAdapter = null;

    @Override
    public void onCreate() {
        synchronized (sSyncAdapterLock){
            if (sStockHawkSyncAdapter == null){
                sStockHawkSyncAdapter = new StockHawkSyncAdapter(getApplicationContext(), true);
            }
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return sStockHawkSyncAdapter.getSyncAdapterBinder();
    }
}
