/*
 * Copyright (c) 2009 INSciTE.  All rights reserved
 * INSciTE is on the web at: http://www.hightechkids.org
 * This code is released under GPL; see LICENSE.txt for details.
 */

package fll.util;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PushbackInputStream;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;

import org.apache.poi.hssf.usermodel.HSSFDateUtil;
import org.apache.poi.hssf.usermodel.HSSFFormulaEvaluator;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.openxml4j.exceptions.InvalidFormatException;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.FormulaEvaluator;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.xssf.usermodel.XSSFFormulaEvaluator;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import fll.scheduler.ParseSchedule;

/**
 * Read Excel files.
 */
public class ExcelCellReader implements CellFileReader {

  private final DataFormatter formatter;

  private final FormulaEvaluator formulaEvaluator;

  private final Workbook workbook;

  private int lineNumber = -1;

  private final Sheet sheet;

  /**
   * Check if we believe this file to be an Excel file.
   */
  public static boolean isExcelFile(final File file) {
    return file.getName().endsWith(".xls")
        || file.getName().endsWith(".xslx");
  }

  /**
   * Get the names of all sheets in the specified file.
   * @throws IOException 
   * @throws InvalidFormatException 
   */
  public static List<String> getAllSheetNames(final File file) throws InvalidFormatException, IOException {
    final List<String> sheetNames = new LinkedList<String>();
    
    final Workbook workbook = createWorkbook(file);
    final int numSheets = workbook.getNumberOfSheets();
    for(int i=0; i<numSheets; ++i) {
      sheetNames.add(workbook.getSheetName(i));
    }
    
    return sheetNames;
  }

  private static Workbook createWorkbook(final File file) throws IOException, InvalidFormatException {
    final InputStream stream = new PushbackInputStream(new FileInputStream(file));
    try {
      final Workbook workbook = WorkbookFactory.create(stream);
      return workbook;
    } finally {
      stream.close();
    }
  }

  public ExcelCellReader(final File file, final String sheetName) throws IOException, InvalidFormatException {
    workbook = createWorkbook(file);
    if (file.getName().endsWith("xls")) {
      formulaEvaluator = new HSSFFormulaEvaluator((HSSFWorkbook) workbook);
    } else {
      formulaEvaluator = new XSSFFormulaEvaluator((XSSFWorkbook) workbook);
    }

    sheet = workbook.getSheet(sheetName);

    formatter = new DataFormatter();
  }

  /**
   * @see fll.util.CellFileReader#getLineNumber()
   */
  public int getLineNumber() {
    return lineNumber;
  }

  /**
   * @see fll.util.CellFileReader#readNext()
   */
  public String[] readNext() throws IOException {
    if (lineNumber >= sheet.getLastRowNum()) {
      return null;
    }

    ++lineNumber;
    final Row row = sheet.getRow(lineNumber);
    if (null == row) {
      return new String[0];
    }

    final List<String> data = new LinkedList<String>();
    for (final Cell cell : row) {
      final String str;
      if (Cell.CELL_TYPE_NUMERIC == cell.getCellType()) {
        final double d = cell.getNumericCellValue();
        // test if a date!
        if (HSSFDateUtil.isCellDateFormatted(cell)) {
          // format in form of M/D/YY
          final Date date = HSSFDateUtil.getJavaDate(d);
          // TODO make the date formats more visible as they will likely be used
          // elsewhere
          str = ParseSchedule.DATE_FORMAT_AM_PM_SS.get().format(date);
        } else {
          str = formatter.formatCellValue(cell, formulaEvaluator);
        }
      } else {
        str = formatter.formatCellValue(cell, formulaEvaluator);
      }
      data.add(str);
    }
    return data.toArray(new String[data.size()]);
  }

  /**
   * @see java.io.Closeable#close()
   */
  public void close() throws IOException {
    // nop
  }

}
