package com.sam_chordas.android.stockhawk.data;

import net.simonvt.schematic.annotation.Database;
import net.simonvt.schematic.annotation.Table;

/**
 * Created by sam_chordas on 10/5/15.
 */
@Database(version = QuoteDatabase.VERSION, fileName = QuoteDatabase.DB_FILE_NAME)
public class QuoteDatabase {

  //DATABASE
  // WE ARE USING THE SCHEMATIC ANNOTATION DATABASE LIBRARY.

  public static final String DB_FILE_NAME = "stocks_DB";

  private QuoteDatabase(){}

  public static final int VERSION = 13;

  @Table(QuoteColumns.class)
  public static final String QUOTES = "quotes";

  @Table(QuoteChartColumns.class)
  public static final String CHART = "chart";

}
