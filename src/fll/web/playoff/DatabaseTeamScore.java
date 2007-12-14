/*
 * Copyright (c) 2000-2002 INSciTE.  All rights reserved
 * INSciTE is on the web at: http://www.hightechkids.org
 * This code is released under GPL; see LICENSE.txt for details.
 */
package fll.web.playoff;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import org.w3c.dom.Element;

import fll.Utilities;
import fll.db.Queries;

/**
 * TeamScore implementation for a performance score in the database.
 * 
 * @author jpschewe
 * @version $Revision$
 */
public class DatabaseTeamScore extends TeamScore {

  /**
   * Create a database team score object for a non-performance score, for use when
   * the result set is already available.
   * 
   * @param categoryDescription
   *          passed to superclass
   * @param connection
   *          the connection to get the data from
   * @param teamNumber
   *          passed to superclass
   * @throws SQLException
   *           if there is an error getting the current tournament
   */
  public DatabaseTeamScore(final Element categoryDescription, final int teamNumber, final ResultSet rs) throws SQLException {
    super(categoryDescription, teamNumber);

    _result = rs;
    _scoreExists = true;
  }
  
  /**
   * Create a database team score object for a performance score.
   * 
   * @param categoryDescription
   *          passed to superclass
   * @param connection
   *          the connection to get the data from
   * @param teamNumber
   *          passed to superclass
   * @param runNumber
   *          passed to superclass
   * @throws SQLException
   *           if there is an error getting the current tournament
   */
  public DatabaseTeamScore(final Element categoryDescription, final int teamNumber, final int runNumber, final Connection connection)
      throws SQLException {
    super(categoryDescription, teamNumber, runNumber);

    final String tournament = Queries.getCurrentTournament(connection);
    _result = createResultSet(connection, tournament);
    _scoreExists = _result.next();
  }

  /**
   * Create a database team score object for a performance score, for use when
   * the result set is already available.
   * 
   * @param categoryDescription
   *          passed to superclass
   * @param connection
   *          the connection to get the data from
   * @param teamNumber
   *          passed to superclass
   * @param runNumber
   *          passed to superclass
   * @throws SQLException
   *           if there is an error getting the current tournament
   */
  public DatabaseTeamScore(final Element categoryDescription, final int teamNumber, final int runNumber, final ResultSet rs) throws SQLException {
    super(categoryDescription, teamNumber, runNumber);

    _result = rs;
    _scoreExists = true;
  }

  /**
   * @see fll.web.playoff.TeamScore#getEnumRawScore(java.lang.String)
   */
  @Override
  public String getEnumRawScore(final String goalName) {
    try {
      return getResultSet().getString(goalName);
    } catch(final SQLException sqle) {
      throw new RuntimeException(sqle);
    }
  }

  /**
   * @see fll.web.playoff.TeamScore#getRawScore(java.lang.String)
   */
  @Override
  public Double getRawScore(final String goalName) {
    try {
      final double val = getResultSet().getDouble(goalName);
      if(getResultSet().wasNull()) {
        return null;
      } else {
        return val;
      }
    } catch(final SQLException sqle) {
      throw new RuntimeException(sqle);
    }
  }

  /**
   * @see fll.web.playoff.TeamScore#isNoShow()
   */
  @Override
  public boolean isNoShow() {
    try {
      return getResultSet().getBoolean("NoShow");
    } catch(final SQLException sqle) {
      throw new RuntimeException(sqle);
    }
  }

  /**
   * @see fll.web.playoff.TeamScore#scoreExists()
   */
  @Override
  public boolean scoreExists() {
    return _scoreExists;
  }

  private final boolean _scoreExists;

  @Override
  public void cleanup() {
    // don't close the result set in case it was passed into the constructor,
    // closing the prepared statement will take care of the one we create
    Utilities.closePreparedStatement(_prep);
    _prep = null;
  }

  /**
   * Cleanup resources.
   */
  @Override
  public void finalize() {
    cleanup();
  }

  private final ResultSet _result;

  private ResultSet getResultSet() {
    return _result;
  }

  private PreparedStatement _prep = null;

  /**
   * Create the result set.
   * 
   * @return
   */
  private ResultSet createResultSet(final Connection connection, final String tournament) throws SQLException {
    ResultSet result;
    final String categoryName = getCategoryName();
    if(NON_PERFORMANCE_RUN_NUMBER == getRunNumber()) {
      _prep = connection.prepareStatement("SELECT * FROM " + categoryName + " WHERE TeamNumber = ? AND Tournament = ?");
    } else {
      _prep = connection.prepareStatement("SELECT * FROM " + categoryName + " WHERE TeamNumber = ? AND Tournament = ? AND RunNumber = ?");
      _prep.setInt(3, getRunNumber());
    }
    _prep.setInt(1, getTeamNumber());
    _prep.setString(2, tournament);
    result = _prep.executeQuery();
    return result;
  }

}
