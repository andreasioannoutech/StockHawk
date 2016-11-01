package com.sam_chordas.android.stockhawk.ui;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;

import com.sam_chordas.android.stockhawk.R;

public class MyStocksActivity extends AppCompatActivity {

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_my_stocks);

    if (savedInstanceState == null){
      MyStocksFragment stocksFragment = new MyStocksFragment();
      getSupportFragmentManager().beginTransaction().add(R.id.stocks_container_id,  stocksFragment).commit();
    }
  }

}
