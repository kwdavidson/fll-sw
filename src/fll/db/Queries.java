/*
 * Copyright (c) 2000-2002 INSciTE.  All rights reserved
 * INSciTE is on the web at: http://www.hightechkids.org
 * This code is released under GPL; see LICENSE.txt for details.
 */
package fll.db;

import java.io.InputStreamReader;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

import javax.servlet.http.HttpServletRequest;

import net.mtu.eggplant.util.sql.SQLFunctions;

import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import fll.Team;
import fll.Utilities;
import fll.util.ScoreUtils;
import fll.web.playoff.DatabaseTeamScore;
import fll.web.playoff.Playoff;
import fll.xml.ChallengeParser;
import fll.xml.XMLUtils;

/**
 * Does all of our queries.
 * 
 * @version $Revision$
 */
public final class Queries {

  private static final Logger LOG = Logger.getLogger(Queries.class);

  private Queries() {
    // no instances
  }

  /**
   * Compute the score groups that each team are in for a given category.
   * 
   * @param connection
   *          the connection to the database
   * @param tournament
   *          the tournament to work within
   * @param division
   *          the division to compute the score groups for
   * @param categoryName
   *          the database name of the category
   * @return
   */
  public static Map<String, Collection<Integer>> computeScoreGroups(final Connection connection,
                                                                    final String tournament,
                                                                    final String division,
                                                                    final String categoryName) throws SQLException {
    final Map<String, Collection<Integer>> scoreGroups = new HashMap<String, Collection<Integer>>();

    PreparedStatement prep = null;
    ResultSet rs = null;
    try {
      prep = connection.prepareStatement("SELECT Judge FROM " + categoryName
          + " WHERE TeamNumber = ? AND Tournament = ? AND ComputedTotal IS NOT NULL ORDER BY Judge");
      prep.setString(2, tournament);

      // foreach team, put the team in a score group
      for(Team team : Queries.getTournamentTeams(connection).values()) {
        // only show the teams for the division that we are looking at right
        // now
        if(division.equals(team.getEventDivision())) {
          final int teamNum = team.getTeamNumber();
          StringBuilder scoreGroup = new StringBuilder();
          prep.setInt(1, teamNum);
          rs = prep.executeQuery();
          boolean first = true;
          while(rs.next()) {
            if(!first) {
              scoreGroup.append("-");
            } else {
              first = false;
            }
            scoreGroup.append(rs.getString(1));
          }
          SQLFunctions.closeResultSet(rs);

          final String scoreGroupStr = scoreGroup.toString();
          if(!scoreGroups.containsKey(scoreGroupStr)) {
            scoreGroups.put(scoreGroupStr, new LinkedList<Integer>());
          }
          scoreGroups.get(scoreGroupStr).add(teamNum);
        }
      }
    } finally {
      SQLFunctions.closeResultSet(rs);
      SQLFunctions.closePreparedStatement(prep);
    }

    return scoreGroups;
  }

  /**
   * Get a map of teams for this tournament keyed on team number. Uses the table
   * TournamentTeams to determine which teams should be included.
   */
  public static Map<Integer, Team> getTournamentTeams(final Connection connection) throws SQLException {
    final SortedMap<Integer, Team> tournamentTeams = new TreeMap<Integer, Team>();
    Statement stmt = null;
    ResultSet rs = null;
    try {
      stmt = connection.createStatement();

      final String sql = "SELECT Teams.TeamNumber, Teams.Organization, Teams.TeamName, Teams.Region, Teams.Division, current_tournament_teams.event_division"
          + " FROM Teams, current_tournament_teams" + " WHERE Teams.TeamNumber = current_tournament_teams.TeamNumber";
      rs = stmt.executeQuery(sql);
      while(rs.next()) {
        final Team team = new Team();
        team.setTeamNumber(rs.getInt("TeamNumber"));
        team.setOrganization(rs.getString("Organization"));
        team.setTeamName(rs.getString("TeamName"));
        team.setRegion(rs.getString("Region"));
        team.setDivision(rs.getString("Division"));
        team.setEventDivision(rs.getString("event_division"));
        tournamentTeams.put(new Integer(team.getTeamNumber()), team);
      }
    } finally {
      SQLFunctions.closeResultSet(rs);
      SQLFunctions.closeStatement(stmt);
    }
    return tournamentTeams;
  }

  public static List<String[]> getTournamentTables(final Connection connection) throws SQLException {
    final String currentTournament = getCurrentTournament(connection);

    Statement stmt = null;
    ResultSet rs = null;
    List<String[]> tableList = new LinkedList<String[]>();
    try {
      stmt = connection.createStatement();

      rs = stmt.executeQuery("SELECT SideA,SideB FROM tablenames WHERE Tournament = '" + currentTournament + "'");

      while(rs.next()) {
        final String[] labels = new String[2];
        labels[0] = rs.getString("SideA");
        labels[1] = rs.getString("SideB");
        tableList.add(labels);
      }
    } finally {
      SQLFunctions.closeResultSet(rs);
      SQLFunctions.closeStatement(stmt);
    }

    return tableList;
  }

  /**
   * Get the list of divisions at this tournament as a List of Strings. Uses
   * getCurrentTournament to determine the tournament.
   * 
   * @param connection
   *          the database connection
   * @return the List of divisions. List of strings.
   * @throws SQLException
   *           on a database error
   * @see #getCurrentTournament(Connection)
   */
  public static List<String> getDivisions(final Connection connection) throws SQLException {
    final List<String> list = new LinkedList<String>();

    PreparedStatement prep = null;
    ResultSet rs = null;
    try {
      prep = connection.prepareStatement("SELECT DISTINCT event_division FROM current_tournament_teams ORDER BY event_division");
      rs = prep.executeQuery();
      while(rs.next()) {
        final String division = rs.getString(1);
        list.add(division);
      }
    } finally {
      SQLFunctions.closeResultSet(rs);
      SQLFunctions.closePreparedStatement(prep);
    }
    return list;
  }

  /**
   * Category name used for the overall rank for a team in the map returned by
   * {@link #getTeamRankings(Connection)}.
   */
  public static final String OVERALL_CATEGORY_NAME = "Overall";

  /**
   * Category name used for the performance rank for a team in the map returned
   * by {@link #getTeamRankings(Connection)}.
   */
  public static final String PERFORMANCE_CATEGORY_NAME = "Performance";

  /**
   * Get the ranking of all teams in all categories.
   * 
   * @return Map with key of division and value is another Map. This Map has a
   *         key of team number and a value of another Map. The key of this Map
   *         is the category name {@link #OVERALL_CATEGORY_NAME} is a special
   *         category and the value is the rank.
   */
  public static Map<String, Map<Integer, Map<String, Integer>>> getTeamRankings(final Connection connection, final Document challengeDocument)
      throws SQLException {
    final Map<String, Map<Integer, Map<String, Integer>>> rankingMap = new HashMap<String, Map<Integer, Map<String, Integer>>>();
    PreparedStatement prep = null;
    ResultSet rs = null;
    try {
      final String tournament = getCurrentTournament(connection);
      final List<String> divisions = getDivisions(connection);

      // find the performance ranking
      determinePerformanceRanking(connection, tournament, divisions, rankingMap);

      // find the subjective category rankings
      determineSubjectiveRanking(connection, tournament, divisions, challengeDocument, rankingMap);

      // find the overall ranking
      determineOverallRanking(connection, tournament, divisions, rankingMap);

    } finally {
      SQLFunctions.closeResultSet(rs);
      SQLFunctions.closePreparedStatement(prep);
    }

    return rankingMap;
  }

  /**
   * Determine the subjective category ranking for all teams at a tournament.
   * 
   * @param connection
   * @param tournament
   * @param divisions
   * @param rankingMap
   * @throws SQLException
   */
  private static void determineSubjectiveRanking(final Connection connection,
                                                 final String tournament,
                                                 final List<String> divisions,
                                                 final Document challengeDocument,
                                                 final Map<String, Map<Integer, Map<String, Integer>>> rankingMap) throws SQLException {

    // cache the subjective categories title->dbname
    final Map<String, String> subjectiveCategories = new HashMap<String, String>();
    for(final Element subjectiveElement : XMLUtils.filterToElements(challengeDocument.getDocumentElement().getElementsByTagName("subjectiveCategory"))) {
      final String title = subjectiveElement.getAttribute("title");
      final String name = subjectiveElement.getAttribute("name");
      subjectiveCategories.put(title, name);
    }

    PreparedStatement prep = null;
    ResultSet rs = null;
    try {
      for(final String division : divisions) {
        final Map<Integer, Map<String, Integer>> teamMap;
        if(rankingMap.containsKey(division)) {
          teamMap = rankingMap.get(division);
        } else {
          teamMap = new HashMap<Integer, Map<String, Integer>>();
          rankingMap.put(division, teamMap);
        }

        // foreach subjective category
        for(String categoryTitle : subjectiveCategories.keySet()) {
          final String categoryName = subjectiveCategories.get(categoryTitle);

          final Map<String, Collection<Integer>> scoreGroups = Queries.computeScoreGroups(connection, tournament, division, categoryName);

          // select from FinalScores
          for(String scoreGroup : scoreGroups.keySet()) {
            final String teamSelect = StringUtils.join(scoreGroups.get(scoreGroup).iterator(), ", ");
            prep = connection.prepareStatement("SELECT Teams.TeamNumber,FinalScores." + categoryName
                + " FROM Teams, FinalScores WHERE FinalScores.TeamNumber IN ( " + teamSelect
                + ") AND Teams.TeamNumber = FinalScores.TeamNumber AND FinalScores.Tournament = ? ORDER BY FinalScores." + categoryName + " DESC");
            prep.setString(1, tournament);
            rs = prep.executeQuery();
            int tieRank = 1;
            int rank = 1;
            double prevScore = Double.NaN;
            while(rs.next()) {
              final int team = rs.getInt(1);
              double score = rs.getDouble(2);
              if(rs.wasNull()) {
                score = Double.NaN;
              }

              final Map<String, Integer> teamRanks;
              if(teamMap.containsKey(team)) {
                teamRanks = teamMap.get(team);
              } else {
                teamRanks = new HashMap<String, Integer>();
                teamMap.put(team, teamRanks);
              }
              // 3 decimal places should be considered equal
              if(Math.abs(score - prevScore) < 0.001) {
                teamRanks.put(categoryTitle, tieRank);
              } else {
                tieRank = rank;
                teamRanks.put(categoryTitle, rank);
              }

              // setup for next round
              prevScore = score;

              // increment rank counter
              ++rank;
            } // end score group rank
            SQLFunctions.closeResultSet(rs);
          } // end foreach score group
        } // end foreach category
      } // end foreach division
    } finally {
      SQLFunctions.closeResultSet(rs);
      SQLFunctions.closePreparedStatement(prep);
    }
  }

  /**
   * Determine the overall ranking for all teams at a tournament.
   * 
   * @param connection
   * @param tournament
   * @param divisions
   * @param rankingMap
   * @throws SQLException
   */
  private static void determineOverallRanking(final Connection connection,
                                              final String tournament,
                                              final List<String> divisions,
                                              final Map<String, Map<Integer, Map<String, Integer>>> rankingMap) throws SQLException {
    PreparedStatement prep = null;
    ResultSet rs = null;
    try {
      final StringBuilder query = new StringBuilder();
      query.append("SELECT Teams.TeamNumber, FInalScores.OverallScore");
      query.append(" FROM Teams,FinalScores,current_tournament_teams");
      query.append(" WHERE FinalScores.TeamNumber = Teams.TeamNumber");
      query.append(" AND FinalScores.Tournament = ?");
      query.append(" AND current_tournament_teams.event_division = ?");
      query.append(" AND current_tournament_teams.TeamNumber = Teams.TeamNumber");
      query.append(" ORDER BY FinalScores.OverallScore DESC, Teams.TeamNumber");
      prep = connection.prepareStatement(query.toString());
      prep.setString(1, tournament);
      for(final String division : divisions) {
        final Map<Integer, Map<String, Integer>> teamMap;
        if(rankingMap.containsKey(division)) {
          teamMap = rankingMap.get(division);
        } else {
          teamMap = new HashMap<Integer, Map<String, Integer>>();
          rankingMap.put(division, teamMap);
        }

        prep.setString(2, division);
        rs = prep.executeQuery();
        int rank = 1;
        int tieRank = 1; // handle ties
        double prevScore = Double.NaN;
        while(rs.next()) {
          final int team = rs.getInt(1);
          double score = rs.getDouble(2);
          if(rs.wasNull()) {
            score = Double.NaN;
          }

          final Map<String, Integer> teamRanks;
          if(teamMap.containsKey(team)) {
            teamRanks = teamMap.get(team);
          } else {
            teamRanks = new HashMap<String, Integer>();
            teamMap.put(team, teamRanks);
          }
          // 3 decimal places should be considered equal
          if(Math.abs(score - prevScore) < 0.001) {
            teamRanks.put(OVERALL_CATEGORY_NAME, tieRank);
          } else {
            tieRank = rank;
            teamRanks.put(OVERALL_CATEGORY_NAME, rank);
          }

          // setup for next round
          prevScore = score;

          // increment rank counter
          ++rank;
        }
        SQLFunctions.closeResultSet(rs);
      }
    } finally {
      SQLFunctions.closeResultSet(rs);
      SQLFunctions.closePreparedStatement(prep);
    }
  }

  /**
   * Determine the performance ranking for all teams at a tournament.
   * 
   * @param connection
   * @param tournament
   * @param divisions
   * @param rankingMap
   * @throws SQLException
   */
  private static void determinePerformanceRanking(final Connection connection,
                                                  final String tournament,
                                                  final List<String> divisions,
                                                  final Map<String, Map<Integer, Map<String, Integer>>> rankingMap) throws SQLException {
    PreparedStatement prep = null;
    ResultSet rs = null;
    try {
      final StringBuilder query = new StringBuilder();
      query.append("SELECT Teams.TeamNumber, FInalScores.performance");
      query.append(" FROM Teams,FinalScores,current_tournament_teams");
      query.append(" WHERE FinalScores.TeamNumber = Teams.TeamNumber");
      query.append(" AND FinalScores.Tournament = ?");
      query.append(" AND current_tournament_teams.event_division = ?");
      query.append(" AND current_tournament_teams.TeamNumber = Teams.TeamNumber");
      query.append(" ORDER BY FinalScores.performance DESC, Teams.TeamNumber");
      prep = connection.prepareStatement(query.toString());
      prep.setString(1, tournament);
      for(final String division : divisions) {
        final Map<Integer, Map<String, Integer>> teamMap;
        if(rankingMap.containsKey(division)) {
          teamMap = rankingMap.get(division);
        } else {
          teamMap = new HashMap<Integer, Map<String, Integer>>();
          rankingMap.put(division, teamMap);
        }

        prep.setString(2, division);
        rs = prep.executeQuery();
        int rank = 1;
        int tieRank = 1; // handle ties
        double prevScore = Double.NaN;
        while(rs.next()) {
          final int team = rs.getInt(1);
          double score = rs.getDouble(2);
          if(rs.wasNull()) {
            score = Double.NaN;
          }

          final Map<String, Integer> teamRanks;
          if(teamMap.containsKey(team)) {
            teamRanks = teamMap.get(team);
          } else {
            teamRanks = new HashMap<String, Integer>();
            teamMap.put(team, teamRanks);
          }
          // 3 decimal places should be considered equal
          if(Math.abs(score - prevScore) < 0.001) {
            teamRanks.put(PERFORMANCE_CATEGORY_NAME, tieRank);
          } else {
            tieRank = rank;
            teamRanks.put(PERFORMANCE_CATEGORY_NAME, rank);
          }

          // setup for next round
          prevScore = score;

          // increment rank counter
          ++rank;
        }
        SQLFunctions.closeResultSet(rs);
      }
    } finally {
      SQLFunctions.closeResultSet(rs);
      SQLFunctions.closePreparedStatement(prep);
    }
  }

  /**
   * Figure out the next run number for teamNumber. Does not ignore unverified
   * scores.
   */
  public static int getNextRunNumber(final Connection connection, final int teamNumber) throws SQLException {
    final String currentTournament = getCurrentTournament(connection);
    Statement stmt = null;
    ResultSet rs = null;
    try {
      stmt = connection.createStatement();

      rs = stmt.executeQuery("SELECT COUNT(TeamNumber) FROM Performance WHERE Tournament = '" + currentTournament + "' AND TeamNumber = "
          + teamNumber);
      final int runNumber;
      if(rs.next()) {
        runNumber = rs.getInt(1);
      } else {
        runNumber = 0;
      }
      return runNumber + 1;
    } finally {
      SQLFunctions.closeResultSet(rs);
      SQLFunctions.closeStatement(stmt);
    }
  }

  /**
   * Figure out the highest run number a team has completed. This should be the
   * same as next run number -1, but sometimes we get non-consecutive runs in
   * and this just finds the max run number. Does not ignore unverified scores.
   */
  public static int getMaxRunNumber(final Connection connection, final int teamNumber) throws SQLException {
    final String currentTournament = getCurrentTournament(connection);
    Statement stmt = null;
    ResultSet rs = null;
    try {
      stmt = connection.createStatement();

      rs = stmt.executeQuery("SELECT MAX(RunNumber) FROM Performance WHERE Tournament = '" + currentTournament + "' AND TeamNumber = " + teamNumber);
      final int runNumber;
      if(rs.next()) {
        runNumber = rs.getInt(1);
      } else {
        runNumber = 0;
      }
      return runNumber;
    } finally {
      SQLFunctions.closeResultSet(rs);
      SQLFunctions.closeStatement(stmt);
    }
  }

  /**
   * Get the number of scoresheets to print on a single sheet of paper. Returns
   * an integer value as stored in TournamentParameters table for the
   * ScoresheetLayoutNUp parameter. Possible values at this time are 1 and 2.
   */
  public static int getScoresheetLayoutNUp(final Connection connection) throws SQLException {
    Statement stmt = null;
    ResultSet rs = null;
    try {
      stmt = connection.createStatement();
      rs = stmt.executeQuery("SELECT VALUE FROM TournamentParameters WHERE Param = 'ScoresheetLayoutNUp'");
      final int nup;
      if(rs.next()) {
        nup = rs.getInt(1);
      } else {
        nup = 2; // Default to 2-up layout of parameter doesn't exist in DB
      }
      return nup;
    } finally {
      SQLFunctions.closeResultSet(rs);
      SQLFunctions.closeStatement(stmt);
    }
  }

  /**
   * Insert a performance score into the database. All of the values are
   * expected to be in request.
   * 
   * @return the SQL executed
   * @throws SQLException
   *           on a database error.
   * @throws RuntimeException
   *           if a parameter is missing.
   * @throws ParseException
   *           if the XML document is invalid.
   */
  public static String insertPerformanceScore(final Document document, final Connection connection, final HttpServletRequest request)
      throws SQLException, ParseException, RuntimeException {
    final String currentTournament = getCurrentTournament(connection);

    final String teamNumber = request.getParameter("TeamNumber");
    if(null == teamNumber) {
      throw new RuntimeException("Missing parameter: TeamNumber");
    }

    final String runNumber = request.getParameter("RunNumber");
    if(null == runNumber) {
      throw new RuntimeException("Missing parameter: RunNumber");
    }

    final String noShow = request.getParameter("NoShow");
    if(null == noShow) {
      throw new RuntimeException("Missing parameter: NoShow");
    }

    final int irunNumber = Utilities.NUMBER_FORMAT_INSTANCE.parse(runNumber).intValue();

    final int numSeedingRounds = getNumSeedingRounds(connection);

    // Perform updates to the playoff data table if in playoff rounds.
    if((irunNumber > numSeedingRounds) && "1".equals(request.getParameter("Verified"))) {
      final int playoffRun = irunNumber - numSeedingRounds;
      final int ptLine = getPlayoffTableLineNumber(connection, currentTournament, teamNumber, playoffRun);
      final String division = getEventDivision(connection, Utilities.NUMBER_FORMAT_INSTANCE.parse(teamNumber).intValue());
      if(ptLine > 0) {
        final int siblingTeam = getTeamNumberByPlayoffLine(connection, currentTournament, division, (ptLine % 2 == 0 ? ptLine - 1 : ptLine + 1),
            playoffRun);

        // If sibling team is the NULL team, then no playoff meta data needs
        // updating, since we are still waiting for an earlier round to be
        // entered. Also, if the sibling team isn't verified, we shouldn't
        // be updating the playoffdata table.
        if(Team.NULL_TEAM_NUMBER != siblingTeam && Playoff.performanceScoreExists(connection, siblingTeam, irunNumber)
            && Playoff.isVerified(connection, currentTournament, Team.getTeamFromDatabase(connection, siblingTeam), irunNumber)) {
          final Team opponent = Team.getTeamFromDatabase(connection, siblingTeam);
          final Team winner = Playoff.pickWinner(connection, document, opponent, request, irunNumber);

          if(winner != null) {
            StringBuffer sql = new StringBuffer();
            // update the playoff data table with the winning team...
            sql.append("UPDATE PlayoffData SET Team = " + winner.getTeamNumber());
            sql.append(" WHERE event_division = '" + division + "'");
            sql.append(" AND Tournament = '" + currentTournament + "'");
            sql.append(" AND PlayoffRound = " + (playoffRun + 1));
            sql.append(" AND LineNumber = " + ((ptLine + 1) / 2));

            Statement stmt = null;
            try {
              stmt = connection.createStatement();
              stmt.executeUpdate(sql.toString());
            } finally {
              SQLFunctions.closeStatement(stmt);
            }
            final int semiFinalRound = getNumPlayoffRounds(connection, division) - 1;
            if(playoffRun == semiFinalRound && isThirdPlaceEnabled(connection, division)) {
              final int newLoser;
              if(winner.getTeamNumber() == Utilities.NUMBER_FORMAT_INSTANCE.parse(teamNumber).intValue()) {
                newLoser = opponent.getTeamNumber();
              } else {
                newLoser = Utilities.NUMBER_FORMAT_INSTANCE.parse(teamNumber).intValue();
              }
              try {
                stmt = connection.createStatement();
                sql.append("UPDATE PlayoffData SET Team = " + newLoser);
                sql.append(" WHERE event_division = '" + division + "'");
                sql.append(" AND Tournament = '" + currentTournament + "'");
                sql.append(" AND PlayoffRound = " + (playoffRun + 1));
                sql.append(" AND LineNumber = " + ((ptLine + 5) / 2));
                stmt.executeUpdate(sql.toString());
                sql.append("; ");
              } finally {
                SQLFunctions.closeStatement(stmt);
              }
            }
          }
        }
      } else {
        throw new RuntimeException("Unable to find team " + teamNumber + " in the playoff brackets for playoff round " + playoffRun
            + ". Maybe someone deleted their score from the previous run?");
      }
    }

    final StringBuffer columns = new StringBuffer();
    final StringBuffer values = new StringBuffer();

    columns.append("TeamNumber");
    values.append(teamNumber);

    columns.append(", Tournament");
    values.append(", '" + currentTournament + "'");

    columns.append(", ComputedTotal");
    values.append(", " + request.getParameter("totalScore"));

    columns.append(", RunNumber");
    values.append(", " + runNumber);

    columns.append(", NoShow");
    values.append(", " + noShow);

    columns.append(", Verified");
    values.append(", " + request.getParameter("Verified"));

    // now do each goal
    final Element rootElement = document.getDocumentElement();
    final Element performanceElement = (Element)rootElement.getElementsByTagName("Performance").item(0);
    for(final Element element : XMLUtils.filterToElements(performanceElement.getElementsByTagName("goal"))) {
      final String name = element.getAttribute("name");

      final String value = request.getParameter(name);
      if(null == value) {
        throw new RuntimeException("Missing parameter: " + name);
      }
      columns.append(", " + name);
      final List<Element> valueChildren = XMLUtils.filterToElements(element.getElementsByTagName("value"));
      if(valueChildren.size() > 0) {
        // enumerated
        values.append(", '" + value + "'");
      } else {
        values.append(", " + value);
      }
    }

    final String sql = "INSERT INTO Performance" + " ( " + columns.toString() + ") " + "VALUES ( " + values.toString() + ")";
    Statement stmt = null;
    try {
      stmt = connection.createStatement();
      stmt.executeUpdate(sql);
    } finally {
      SQLFunctions.closeStatement(stmt);
    }

    return sql;
  }

  public static boolean isThirdPlaceEnabled(final Connection connection, final String division) throws SQLException {
    final int finalRound = getNumPlayoffRounds(connection, division);

    final String tournament = getCurrentTournament(connection);

    Statement stmt = null;
    ResultSet rs = null;
    try {
      stmt = connection.createStatement();
      rs = stmt.executeQuery("SELECT count(*) FROM PlayoffData" + " WHERE Tournament='" + tournament + "'" + " AND event_division='" + division + "'"
          + " AND PlayoffRound=" + finalRound);
      if(rs.next()) {
        return rs.getInt(1) == 4;
      } else {
        return false;
      }
    } finally {
      SQLFunctions.closeResultSet(rs);
      SQLFunctions.closeStatement(stmt);
    }
  }

  /**
   * Update a performance score in the database. All of the values are expected
   * to be in request.
   * 
   * @return the SQL executed
   * @throws SQLException
   *           on a database error.
   * @throws ParseException
   *           if the XML document is invalid.
   * @throws RuntimeException
   *           if a parameter is missing.
   */
  public static String updatePerformanceScore(final Document document, final Connection connection, final HttpServletRequest request)
      throws SQLException, ParseException, RuntimeException {
    final String currentTournament = getCurrentTournament(connection);

    final String teamNumber = request.getParameter("TeamNumber");
    if(null == teamNumber) {
      throw new RuntimeException("Missing parameter: TeamNumber");
    }

    final String runNumber = request.getParameter("RunNumber");
    if(null == runNumber) {
      throw new RuntimeException("Missing parameter: RunNumber");
    }

    final String noShow = request.getParameter("NoShow");
    if(null == noShow) {
      throw new RuntimeException("Missing parameter: NoShow");
    }

    final int irunNumber = Utilities.NUMBER_FORMAT_INSTANCE.parse(runNumber).intValue();

    final int numSeedingRounds = getNumSeedingRounds(connection);

    final StringBuffer sql = new StringBuffer();

    // Check if we need to update the PlayoffData table
    if(irunNumber > numSeedingRounds) {
      final int playoffRun = irunNumber - numSeedingRounds;
      final int ptLine = getPlayoffTableLineNumber(connection, currentTournament, teamNumber, playoffRun);
      final String division = getEventDivision(connection, Utilities.NUMBER_FORMAT_INSTANCE.parse(teamNumber).intValue());
      if(ptLine > 0) {
        final int siblingTeam = getTeamNumberByPlayoffLine(connection, currentTournament, division, (ptLine % 2 == 0 ? ptLine - 1 : ptLine + 1),
            playoffRun);

        // If sibling team is the NULL team, then updating this score is okay,
        // and no playoff meta data needs updating.
        if(Team.NULL_TEAM_NUMBER != siblingTeam) {
          // Sibling team is not null so we have to check if update can happen
          // anyway

          // See if the modification affects the result of the playoff match
          final Team teamA = Team.getTeamFromDatabase(connection, Utilities.NUMBER_FORMAT_INSTANCE.parse(teamNumber).intValue());
          final Team teamB = Team.getTeamFromDatabase(connection, siblingTeam);
          if(teamA == null || teamB == null) {
            throw new RuntimeException("Unable to find one of these team numbers in the database: " + teamNumber + " and " + siblingTeam);
          }
          final Team oldWinner = Playoff.pickWinner(connection, document, teamA, teamB, irunNumber);
          final Team newWinner = Playoff.pickWinner(connection, document, teamB, request, irunNumber);
          Statement stmt = null;
          ResultSet rs = null;
          if(oldWinner != null && newWinner != null && !oldWinner.equals(newWinner)) {
            // This score update changes the result of the match, so make sure
            // no other scores exist in later round for either of these 2 teams.
            if(getPlayoffTableLineNumber(connection, currentTournament, teamNumber, playoffRun + 1) > 0) {
              try {
                stmt = connection.createStatement();
                rs = stmt.executeQuery("SELECT TeamNumber FROM Performance" + " WHERE TeamNumber = " + teamNumber + " AND RunNumber > " + irunNumber
                    + " AND Tournament = '" + currentTournament + "'");
                if(rs.next()) {
                  throw new RuntimeException("Unable to update score for team number " + teamNumber + " in playoff round " + playoffRun
                      + " because that team has scores entered in subsequent rounds which would become inconsistent. "
                      + "Delete those scores and then you may update this score.");
                }
              } finally {
                SQLFunctions.closeResultSet(rs);
                SQLFunctions.closeStatement(stmt);
              }
            }
            if(getPlayoffTableLineNumber(connection, currentTournament, Integer.toString(siblingTeam), playoffRun + 1) > 0) {
              try {
                stmt = connection.createStatement();
                rs = stmt.executeQuery("SELECT TeamNumber FROM Performance" + " WHERE TeamNumber = " + siblingTeam + " AND RunNumber > " + irunNumber
                    + " AND Tournament = '" + currentTournament + "'");
                if(rs.next()) {
                  throw new RuntimeException("Unable to update score for team number " + teamNumber + " in playoff round " + playoffRun
                      + " because opponent team " + siblingTeam + " has scores in subsequent rounds which would become inconsistent. "
                      + "Delete those scores and then you may update this score.");
                }
              } finally {
                SQLFunctions.closeResultSet(rs);
                SQLFunctions.closeStatement(stmt);
              }
            }
          }
          // If the second-check flag is NO or the opposing team is not
          // verified, we set the match "winner" (possibly back) to NULL.
          if("0".equals(request.getParameter("Verified"))
              || !(Playoff.performanceScoreExists(connection, teamB, irunNumber) && Playoff.isVerified(connection, currentTournament, teamB,
                  irunNumber))) {
            try {
              stmt = connection.createStatement();
              sql.append("UPDATE PlayoffData SET Team = " + Team.NULL_TEAM_NUMBER);
              sql.append(" WHERE event_division = '" + division + "'");
              sql.append(" AND Tournament = '" + currentTournament + "'");
              sql.append(" AND PlayoffRound = " + (playoffRun + 1));
              sql.append(" AND LineNumber = " + ((ptLine + 1) / 2));
              stmt.executeUpdate(sql.toString());
              sql.append("; ");
            } finally {
              SQLFunctions.closeStatement(stmt);
            }
            final int semiFinalRound = getNumPlayoffRounds(connection, division) - 1;
            if(playoffRun == semiFinalRound && isThirdPlaceEnabled(connection, division)) {
              try {
                stmt = connection.createStatement();
                sql.append("UPDATE PlayoffData SET Team = " + Team.NULL_TEAM_NUMBER);
                sql.append(" WHERE event_division = '" + division + "'");
                sql.append(" AND Tournament = '" + currentTournament + "'");
                sql.append(" AND PlayoffRound = " + (playoffRun + 1));
                sql.append(" AND LineNumber = " + ((ptLine + 5) / 2));
                stmt.executeUpdate(sql.toString());
                sql.append("; ");
              } finally {
                SQLFunctions.closeStatement(stmt);
              }
            }
          } else {
            try {
              stmt = connection.createStatement();
              sql.append("UPDATE PlayoffData SET Team = " + newWinner.getTeamNumber());
              sql.append(" WHERE event_division = '" + division + "'");
              sql.append(" AND Tournament = '" + currentTournament + "'");
              sql.append(" AND PlayoffRound = " + (playoffRun + 1));
              sql.append(" AND LineNumber = " + ((ptLine + 1) / 2));
              stmt.executeUpdate(sql.toString());
              sql.append("; ");
            } finally {
              SQLFunctions.closeStatement(stmt);
            }
            final int semiFinalRound = getNumPlayoffRounds(connection, division) - 1;
            if(playoffRun == semiFinalRound && isThirdPlaceEnabled(connection, division)) {
              final Team newLoser;
              if(newWinner.equals(teamA)) {
                newLoser = teamB;
              } else {
                newLoser = teamA;
              }
              try {
                stmt = connection.createStatement();
                sql.append("UPDATE PlayoffData SET Team = " + newLoser.getTeamNumber());
                sql.append(" WHERE event_division = '" + division + "'");
                sql.append(" AND Tournament = '" + currentTournament + "'");
                sql.append(" AND PlayoffRound = " + (playoffRun + 1));
                sql.append(" AND LineNumber = " + ((ptLine + 5) / 2));
                stmt.executeUpdate(sql.toString());
                sql.append("; ");
              } finally {
                SQLFunctions.closeStatement(stmt);
              }
            }
          }
        }
      } else {
        throw new RuntimeException("Team " + teamNumber + " could not be found in the playoff table for playoff round " + playoffRun);
      }
    }

    sql.append("UPDATE Performance SET ");

    sql.append("NoShow = " + noShow);

    sql.append(", ComputedTotal = " + request.getParameter("totalScore"));

    // now do each goal
    final Element rootElement = document.getDocumentElement();
    final Element performanceElement = (Element)rootElement.getElementsByTagName("Performance").item(0);
    for(final Element element : XMLUtils.filterToElements(performanceElement.getElementsByTagName("goal"))) {
      final String name = element.getAttribute("name");

      final String value = request.getParameter(name);
      if(null == value) {
        throw new RuntimeException("Missing parameter: " + name);
      }
      final List<Element> valueChildren = XMLUtils.filterToElements(element.getElementsByTagName("value"));
      if(valueChildren.size() > 0) {
        // enumerated
        sql.append(", " + name + " = '" + value + "'");
      } else {
        sql.append(", " + name + " = " + value);
      }
    }

    sql.append(", Verified = " + request.getParameter("Verified"));

    sql.append(" WHERE TeamNumber = " + teamNumber);

    sql.append(" AND RunNumber = " + runNumber);

    sql.append(" AND Tournament = '" + currentTournament + "'");

    Statement stmt = null;
    try {
      stmt = connection.createStatement();
      stmt.executeUpdate(sql.toString());
    } finally {
      SQLFunctions.closeStatement(stmt);
    }

    return sql.toString();
  }

  /**
   * Delete a performance score in the database. All of the values are expected
   * to be in request.
   * 
   * @return the SQL executed
   * @throws RuntimeException
   *           if a parameter is missing or if the playoff meta data would
   *           become inconsistent due to the deletion.
   */
  public static String deletePerformanceScore(final Connection connection, final HttpServletRequest request)
      throws SQLException, RuntimeException, ParseException {
    final String currentTournament = getCurrentTournament(connection);
    final StringBuffer sql = new StringBuffer();

    final String teamNumber = request.getParameter("TeamNumber");
    if(null == teamNumber) {
      throw new RuntimeException("Missing parameter: TeamNumber");
    }

    final int numSeedingRounds = getNumSeedingRounds(connection);
    final String runNumber = request.getParameter("RunNumber");
    if(null == runNumber) {
      throw new RuntimeException("Missing parameter: RunNumber");
    }
    final int irunNumber = Utilities.NUMBER_FORMAT_INSTANCE.parse(runNumber).intValue();

    // Check if we need to update the PlayoffData table
    if(irunNumber > numSeedingRounds) {
      final int playoffRun = irunNumber - numSeedingRounds;
      final int ptLine = getPlayoffTableLineNumber(connection, currentTournament, teamNumber, playoffRun);
      final String division = getEventDivision(connection, Utilities.NUMBER_FORMAT_INSTANCE.parse(teamNumber).intValue());
      if(ptLine > 0) {
        final int siblingTeam = getTeamNumberByPlayoffLine(connection, currentTournament, division, (ptLine % 2 == 0 ? ptLine - 1 : ptLine + 1),
            playoffRun);

        if(siblingTeam != Team.NULL_TEAM_NUMBER) {
          Statement stmt = null;
          ResultSet rs = null;
          // See if either teamNumber or siblingTeam has a score entered in
          // subsequent rounds
          if(getPlayoffTableLineNumber(connection, currentTournament, teamNumber, playoffRun + 1) > 0) {
            try {
              stmt = connection.createStatement();
              rs = stmt.executeQuery("SELECT TeamNumber FROM Performance" + " WHERE TeamNumber = " + teamNumber + " AND RunNumber > " + irunNumber
                  + " AND Tournament = '" + currentTournament + "'");
              if(rs.next()) {
                throw new RuntimeException("Unable to delete score for team number " + teamNumber + " in playoff round " + playoffRun
                    + " because that team " + " has scores in subsequent rounds which would become inconsistent. "
                    + "Delete those scores and then you may delete this score.");
              }
            } finally {
              SQLFunctions.closeResultSet(rs);
              SQLFunctions.closeStatement(stmt);
            }
          }
          if(getPlayoffTableLineNumber(connection, currentTournament, Integer.toString(siblingTeam), playoffRun + 1) > 0) {
            try {
              stmt = connection.createStatement();
              rs = stmt.executeQuery("SELECT TeamNumber FROM Performance" + " WHERE TeamNumber = " + siblingTeam + " AND RunNumber > " + irunNumber
                  + " AND Tournament = '" + currentTournament + "'");
              if(rs.next()) {
                throw new RuntimeException("Unable to delete score for team number " + teamNumber + " in playoff round " + playoffRun
                    + " because opposing team " + siblingTeam + " has scores in subsequent rounds which would become inconsistent. "
                    + "Delete those scores and then you may delete this score.");
              }
            } finally {
              SQLFunctions.closeResultSet(rs);
              SQLFunctions.closeStatement(stmt);
            }
          }
          // No dependent score was found, so we can update the playoff table to
          // reflect the deletion of this score by removing the team from the
          // next run column in the bracket
          try {
            stmt = connection.createStatement();
            sql.append("UPDATE PlayoffData SET Team = " + Team.NULL_TEAM_NUMBER);
            sql.append(" WHERE event_division = '" + division + "'");
            sql.append(" AND Tournament = '" + currentTournament + "'");
            sql.append(" AND PlayoffRound = " + (playoffRun + 1));
            sql.append(" AND LineNumber = " + ((ptLine + 1) / 2));
            stmt.executeUpdate(sql.toString());
            sql.append("; ");
          } finally {
            SQLFunctions.closeStatement(stmt);
          }
          final int semiFinalRound = getNumPlayoffRounds(connection, division) - 1;
          if(playoffRun == semiFinalRound && isThirdPlaceEnabled(connection, division)) {
            try {
              stmt = connection.createStatement();
              sql.append("UPDATE PlayoffData SET Team = " + Team.NULL_TEAM_NUMBER);
              sql.append(" WHERE event_division = '" + division + "'");
              sql.append(" AND Tournament = '" + currentTournament + "'");
              sql.append(" AND PlayoffRound = " + (playoffRun + 1));
              sql.append(" AND LineNumber = " + ((ptLine + 5) / 2));
              stmt.executeUpdate(sql.toString());
              sql.append("; ");
            } finally {
              SQLFunctions.closeStatement(stmt);
            }
          }
        } // End if(siblingTeam != Team.NULL_TEAM_NUMBER.
      } else {
        throw new RuntimeException("Team " + teamNumber + " could not be found in the playoff table for playoff round " + playoffRun);
      }
    }

    final String update = "DELETE FROM Performance " + " WHERE TeamNumber = " + teamNumber + " AND RunNumber = " + runNumber;

    Statement stmt = null;
    try {
      stmt = connection.createStatement();
      stmt.executeUpdate(update);
    } finally {
      SQLFunctions.closeStatement(stmt);
    }

    return sql.toString() + update;
  }

  /**
   * Get the division that a team is in for the current tournament.
   * 
   * @param teamNumber
   *          the team's number
   * @return the event division for the team
   * @throws SQLException
   *           on a database error
   * @throws RuntimeException
   *           if <code>teamNumber</code> cannot be found in TournamenTeams
   */
  public static String getEventDivision(final Connection connection, final int teamNumber) throws SQLException, RuntimeException {
    PreparedStatement prep = null;
    ResultSet rs = null;
    try {
      prep = connection.prepareStatement("SELECT event_division FROM current_tournament_teams WHERE TeamNumber = ?");
      prep.setInt(1, teamNumber);
      rs = prep.executeQuery();
      if(rs.next()) {
        return rs.getString(1);
      } else {
        throw new RuntimeException("Couldn't find team number " + teamNumber + " in the list of tournament teams!");
      }
    } finally {
      SQLFunctions.closeResultSet(rs);
      SQLFunctions.closePreparedStatement(prep);
    }
  }

  /**
   * Get a list of team numbers that have fewer runs than seeding rounds. This
   * uses only verified performance scores, so scores that have not been
   * double-checked will show up in this report as not entered.
   * 
   * @param connection
   *          connection to the database
   * @param tournamentTeams
   *          keyed by team number
   * @param division
   *          String with the division to query on, or the special string
   *          "__all__" if all divisions should be queried.
   * @param verifiedScoresOnly
   *          True if the database query should use only verified scores, false
   *          if it should use all scores.
   * @return a List of Team objects
   * @throws SQLException
   *           on a database error
   * @throws RuntimeException
   *           if a team can't be found in tournamentTeams
   */
  public static List<Team> getTeamsNeedingSeedingRuns(final Connection connection,
                                                      final Map<Integer, Team> tournamentTeams,
                                                      final String division,
                                                      final boolean verifiedScoresOnly) throws SQLException, RuntimeException {
    final String currentTournament = getCurrentTournament(connection);
    final String view;

    if(verifiedScoresOnly) {
      view = "verified_performance";
    } else {
      view = "Performance";
    }

    PreparedStatement prep = null;
    ResultSet rs = null;
    try {
      if("__all__".equals(division)) {
        prep = connection.prepareStatement("SELECT TeamNumber,Count(*) FROM " + view + " WHERE Tournament = ? GROUP BY TeamNumber"
            + " HAVING Count(*) < ?");
        prep.setString(1, currentTournament);
        prep.setInt(2, getNumSeedingRounds(connection));
      } else {
        prep = connection.prepareStatement("SELECT " + view + ".TeamNumber,Count(" + view + ".TeamNumber) FROM " + view
            + ",current_tournament_teams WHERE " + view
            + ".TeamNumber = current_tournament_teams.TeamNumber AND current_tournament_teams.event_division = ?" + " AND " + view
            + ".Tournament = ? GROUP BY " + view + ".TeamNumber HAVING Count(" + view + ".TeamNumber) < ?");
        prep.setString(1, division);
        prep.setString(2, currentTournament);
        prep.setInt(3, getNumSeedingRounds(connection));
      }

      rs = prep.executeQuery();
      final List<Team> list = new LinkedList<Team>();
      while(rs.next()) {
        final int teamNumber = rs.getInt(1);
        final Team team = tournamentTeams.get(teamNumber);
        if(null == team) {
          throw new RuntimeException("Couldn't find team number " + teamNumber + " in the list of tournament teams!");
        }
        list.add(team);
      }
      return list;
    } finally {
      SQLFunctions.closeResultSet(rs);
      SQLFunctions.closePreparedStatement(prep);
    }
  }

  /**
   * Convenience function that defaults to querying all scores, not just those
   * that are verified.
   */
  public static List<Team> getTeamsNeedingSeedingRuns(final Connection connection, final Map<Integer, Team> tournamentTeams, final String division)
      throws SQLException, RuntimeException {
    return getTeamsNeedingSeedingRuns(connection, tournamentTeams, division, false);
  }

  /**
   * Get a list of team numbers that have more runs than seeding rounds.
   * 
   * @param connection
   *          connection to the database
   * @param tournamentTeams
   *          keyed by team number
   * @param division
   *          String with the division to query on, or the special string
   *          "__all__" if all divisions should be queried.
   * @param verifiedScoresOnly
   *          True if the database query should use only verified scores, false
   *          if it should use all scores.
   * @return a List of Team objects
   * @throws SQLException
   *           on a database error
   * @throws RuntimeException
   *           if a team can't be found in tournamentTeams
   */
  public static List<Team> getTeamsWithExtraRuns(final Connection connection,
                                                 final Map<Integer, Team> tournamentTeams,
                                                 final String division,
                                                 final boolean verifiedScoresOnly) throws SQLException, RuntimeException {
    final String currentTournament = getCurrentTournament(connection);
    final String view;

    if(verifiedScoresOnly) {
      view = "verified_performance";
    } else {
      view = "Performance";
    }

    PreparedStatement prep = null;
    ResultSet rs = null;
    try {
      if("__all__".equals(division)) {
        prep = connection.prepareStatement("SELECT TeamNumber,Count(*) FROM " + view + " WHERE Tournament = ? GROUP BY TeamNumber"
            + " HAVING Count(*) > ?");
        prep.setString(1, currentTournament);
        prep.setInt(2, getNumSeedingRounds(connection));
      } else {
        prep = connection.prepareStatement("SELECT " + view + ".TeamNumber,Count(" + view + ".TeamNumber) FROM " + view
            + ",current_tournament_teams WHERE " + view + ".TeamNumber = current_tournament_teams.TeamNumber"
            + " AND current_tournament_teams.event_division = ? AND " + view + ".Tournament = ? GROUP BY " + view + ".TeamNumber" + " HAVING Count("
            + view + ".TeamNumber) > ?");
        prep.setString(1, division);
        prep.setString(2, currentTournament);
        prep.setInt(3, getNumSeedingRounds(connection));
      }

      rs = prep.executeQuery();
      final List<Team> list = new LinkedList<Team>();
      while(rs.next()) {
        final int teamNumber = rs.getInt(1);
        final Team team = tournamentTeams.get(teamNumber);
        if(null == team) {
          throw new RuntimeException("Couldn't find team number " + teamNumber + " in the list of tournament teams!");
        }
        list.add(team);
      }
      return list;
    } finally {
      SQLFunctions.closeResultSet(rs);
      SQLFunctions.closePreparedStatement(prep);
    }
  }

  /**
   * Convenience function that defaults to querying all scores, not just those
   * that are verified.
   */
  public static List<Team> getTeamsWithExtraRuns(final Connection connection, final Map<Integer, Team> tournamentTeams, final String division)
      throws SQLException, RuntimeException {
    return getTeamsWithExtraRuns(connection, tournamentTeams, division, false);
  }

  /**
   * Get the order of the teams as seeded in the performance rounds. This will
   * include unverified scores, the assumption being that if you performed the
   * seeding round checks, which exclude unverified scores, you really do want
   * to advance teams.
   * 
   * @param connection
   *          connection to the database
   * @param divisionStr
   *          the division to generate brackets for, as a String
   * @param tournamentTeams
   *          keyed by team number
   * @return a List of team numbers as Integers
   * @throws SQLException
   *           on a database error
   * @throws RuntimeException
   *           if a team can't be found in tournamentTeams
   */
  public static List<Team> getPlayoffSeedingOrder(final Connection connection, final String divisionStr, final Map<Integer, Team> tournamentTeams)
      throws SQLException, RuntimeException {
    final String currentTournament = getCurrentTournament(connection);

    PreparedStatement prep = null;
    ResultSet rs = null;
    try {
      prep = connection
          .prepareStatement("SELECT Performance.TeamNumber,MAX(Performance.ComputedTotal) AS Score FROM Performance,Teams, current_tournament_teams WHERE Performance.RunNumber <= ?"
              + " AND Performance.Tournament = ? AND Teams.TeamNumber = Performance.TeamNumber AND Teams.TeamNumber = current_tournament_teams.TeamNumber"
              + " AND current_tournament_teams.event_division = ? GROUP BY Performance.TeamNumber ORDER BY Score DESC, Performance.TeamNumber");
      prep.setInt(1, getNumSeedingRounds(connection));
      prep.setString(2, currentTournament);
      prep.setString(3, divisionStr);

      rs = prep.executeQuery();
      final List<Team> list = new ArrayList<Team>();
      while(rs.next()) {
        final int teamNumber = rs.getInt(1);
        final Team team = (Team)tournamentTeams.get(new Integer(teamNumber));
        if(null == team) {
          throw new RuntimeException("Couldn't find team number " + teamNumber + " in the list of tournament teams!");
        }
        list.add(team);
      }
      return list;
    } finally {
      SQLFunctions.closeResultSet(rs);
      SQLFunctions.closePreparedStatement(prep);
    }
  }

  /**
   * Get the number of seeding rounds from the database. This value is stored in
   * the table TournamentParameters with the Param of SeedingRounds. If no such
   * value exists a value of 3 is inserted and then returned.
   * 
   * @return the number of seeding rounds
   * @throws SQLException
   *           on a database error
   */
  public static int getNumSeedingRounds(final Connection connection) throws SQLException {
    ResultSet rs = null;
    Statement stmt = null;
    try {
      stmt = connection.createStatement();
      rs = stmt.executeQuery("SELECT Value FROM TournamentParameters WHERE TournamentParameters.Param = 'SeedingRounds'");
      if(rs.next()) {
        return rs.getInt(1);
      } else {
        // insert default entry
        setNumSeedingRounds(connection, 3);
        return 3;
      }
    } finally {
      SQLFunctions.closeResultSet(rs);
      SQLFunctions.closeStatement(stmt);
    }
  }

  /**
   * Set the number of seeding rounds.
   * 
   * @param connection
   *          the connection
   * @param newSeedingRounds
   *          the new value of seeding rounds
   * @see #getNumSeedingRounds(Connection)
   */
  public static void setNumSeedingRounds(final Connection connection, final int newSeedingRounds) throws SQLException {
    Statement stmt = null;
    try {
      stmt = connection.createStatement();
      stmt.executeUpdate("UPDATE TournamentParameters SET Value = " + newSeedingRounds + " WHERE Param = 'SeedingRounds'");
    } finally {
      SQLFunctions.closeStatement(stmt);
    }
  }

  /**
   * Set the number of scoresheets per printed page.
   * 
   * @param connection
   *          The database connection.
   * @param newNup
   *          The new number of scoresheets per printed page. Currently must be
   *          1 or 2.
   * @throws SQLException
   */
  public static void setScoresheetLayoutNUp(final Connection connection, final int newNup) throws SQLException {
    Statement stmt = null;
    try {
      stmt = connection.createStatement();
      stmt.executeUpdate("UPDATE TournamentParameters SET Value = " + newNup + " WHERE Param = 'ScoresheetLayoutNUp'");
    } finally {
      SQLFunctions.closeStatement(stmt);
    }
  }

  /**
   * Get the current tournament from the database.
   * 
   * @return the tournament, or DUMMY if not set. There should always be a DUMMY
   *         tournament in the Tournaments table.
   */
  public static String getCurrentTournament(final Connection connection) throws SQLException {
    ResultSet rs = null;
    Statement stmt = null;
    try {
      stmt = connection.createStatement();
      rs = stmt.executeQuery("SELECT Value FROM TournamentParameters WHERE TournamentParameters.Param = 'CurrentTournament'");
      if(rs.next()) {
        return rs.getString(1);
      } else {
        // insert DUMMY tournament entry
        stmt
            .executeUpdate("INSERT INTO TournamentParameters (Param, Value, Description) VALUES ('CurrentTournament', 'DUMMY', 'Current running tournemnt name - see Tournaments table')");
        return "DUMMY";
      }
    } finally {
      SQLFunctions.closeResultSet(rs);
      SQLFunctions.closeStatement(stmt);
    }
  }

  /**
   * Set the current tournament in the database.
   * 
   * @param connection
   *          db connection
   * @param currentTournament
   *          the new value for the current tournament
   * @return true if everything is fine, false if the value is not in the
   *         Tournaments table and therefore not set
   */
  public static boolean setCurrentTournament(final Connection connection, final String currentTournament) throws SQLException {
    ResultSet rs = null;
    Statement stmt = null;
    try {
      stmt = connection.createStatement();
      rs = stmt.executeQuery("SELECT Name FROM Tournaments WHERE Name = '" + currentTournament + "'");
      if(rs.next()) {
        // getCurrentTournament ensures that a value already exists here, so
        // update will work
        stmt.executeUpdate("UPDATE TournamentParameters SET Value = '" + currentTournament + "' WHERE Param = 'CurrentTournament'");
        return true;
      } else {
        return false;
      }
    } finally {
      SQLFunctions.closeResultSet(rs);
      SQLFunctions.closeStatement(stmt);
    }
  }

  /**
   * Get a list of tournament names in the DB ordered by name.
   * 
   * @return list of tournament names as strings
   */
  public static List<String> getTournamentNames(final Connection connection) throws SQLException {
    final List<String> retval = new LinkedList<String>();
    Statement stmt = null;
    ResultSet rs = null;
    try {
      stmt = connection.createStatement();
      rs = stmt.executeQuery("SELECT Name FROM Tournaments ORDER BY Name");
      while(rs.next()) {
        final String tournamentName = rs.getString(1);
        retval.add(tournamentName);
      }
    } finally {
      SQLFunctions.closeResultSet(rs);
      SQLFunctions.closeStatement(stmt);
    }
    return retval;
  }

  /**
   * Get a list of regions in the DB ordered by region.
   * 
   * @return list of regions as strings
   */
  public static List<String> getRegions(final Connection connection) throws SQLException {
    final List<String> retval = new LinkedList<String>();
    Statement stmt = null;
    ResultSet rs = null;
    try {
      stmt = connection.createStatement();
      rs = stmt.executeQuery("SELECT DISTINCT Region FROM Teams ORDER BY Region");
      while(rs.next()) {
        final String region = rs.getString(1);
        retval.add(region);
      }
    } finally {
      SQLFunctions.closeResultSet(rs);
      SQLFunctions.closeStatement(stmt);
    }
    return retval;
  }

  /**
   * Delete a team from the database. This clears team from the Teams table and
   * all tables specified by the challengeDocument. It is not an error if the
   * team doesn't exist.
   * 
   * @param teamNumber
   *          team to delete
   * @param document
   *          the challenge document
   * @param connection
   *          connection to database, needs delete privileges
   * @throws SQLException
   *           on an error talking to the database
   */
  public static void deleteTeam(final int teamNumber, final Document document, final Connection connection) throws SQLException {
    Statement stmt = null;
    try {
      stmt = connection.createStatement();

      // delete from subjective categories
      for(final Element category : XMLUtils.filterToElements(document.getDocumentElement().getElementsByTagName("subjectiveCategory"))) {
        final String name = category.getAttribute("name");
        stmt.executeUpdate("DELETE FROM " + name + " WHERE TeamNumber = " + teamNumber);
      }

      // delete from Performance
      stmt.executeUpdate("DELETE FROM Performance WHERE TeamNumber = " + teamNumber);

      // delete from Teams
      stmt.executeUpdate("DELETE FROM Teams WHERE TeamNumber = " + teamNumber);

      // delete from TournamentTeams
      stmt.executeUpdate("DELETE FROM TournamentTeams WHERE TeamNumber = " + teamNumber);

      // delete from FinalScores
      stmt.executeUpdate("DELETE FROM FinalScores WHERE TeamNumber = " + teamNumber);

    } finally {
      SQLFunctions.closeStatement(stmt);
    }
  }

  /**
   * Total the scores in the database for the current tournament.
   * 
   * @param document
   *          the challenge document
   * @param connection
   *          connection to database, needs write privileges
   * @throws SQLException
   *           if an error occurs
   * @throws NumberFormantException
   *           if document has invalid numbers
   * @see #updatePerformanceScoreTotals(Document, Connection)
   * @see #updateSubjectiveScoreTotals(Document, Connection)
   */
  public static void updateScoreTotals(final Document document, final Connection connection) throws SQLException, ParseException {

    updatePerformanceScoreTotals(document, connection);

    updateSubjectiveScoreTotals(document, connection);
  }

  /**
   * Compute the total scores for all entered subjective scores.
   * 
   * @param document
   * @param connection
   * @throws SQLException
   * @throws ParseException
   */
  public static void updateSubjectiveScoreTotals(final Document document, final Connection connection) throws SQLException, ParseException {
    final String tournament = getCurrentTournament(connection);
    final Element rootElement = document.getDocumentElement();

    PreparedStatement updatePrep = null;
    PreparedStatement selectPrep = null;
    ResultSet rs = null;
    try {
      // Subjective ---
      for(final Element subjectiveElement : XMLUtils.filterToElements(rootElement.getElementsByTagName("subjectiveCategory"))) {
        final String categoryName = subjectiveElement.getAttribute("name");

        // build up the SQL
        updatePrep = connection.prepareStatement("UPDATE " + categoryName
            + " SET ComputedTotal = ? WHERE TeamNumber = ? AND Tournament = ? AND Judge = ?");
        selectPrep = connection.prepareStatement("SELECT * FROM " + categoryName + " WHERE Tournament = ?");
        selectPrep.setString(1, tournament);
        updatePrep.setString(3, tournament);
        rs = selectPrep.executeQuery();
        while(rs.next()) {
          final int teamNumber = rs.getInt("TeamNumber");
          final double computedTotal = ScoreUtils.computeTotalScore(new DatabaseTeamScore(subjectiveElement, teamNumber, rs));
          if(Double.isNaN(computedTotal)) {
            updatePrep.setNull(1, Types.DOUBLE);
          } else {
            updatePrep.setDouble(1, computedTotal);
          }
          updatePrep.setInt(2, teamNumber);
          final String judge = rs.getString("Judge");
          updatePrep.setString(4, judge);
          updatePrep.executeUpdate();
        }
        rs.close();
        updatePrep.close();
        selectPrep.close();
      }
    } finally {
      SQLFunctions.closeResultSet(rs);
      SQLFunctions.closePreparedStatement(updatePrep);
      SQLFunctions.closePreparedStatement(selectPrep);
    }
  }

  /**
   * Compute the total scores for all entered performance scores in the current
   * tournament. Uses both verified and unverified scores.
   * 
   * @param document
   *          the challenge document
   * @param connection
   *          connection to the database
   * @throws SQLException
   * @throws ParseException
   */
  public static void updatePerformanceScoreTotals(final Document document, final Connection connection) throws SQLException, ParseException {
    final String tournament = getCurrentTournament(connection);
    final Element rootElement = document.getDocumentElement();

    PreparedStatement updatePrep = null;
    PreparedStatement selectPrep = null;
    ResultSet rs = null;
    try {

      // build up the SQL
      updatePrep = connection.prepareStatement("UPDATE Performance SET ComputedTotal = ? WHERE TeamNumber = ? AND Tournament = ? AND RunNumber = ?");
      selectPrep = connection.prepareStatement("SELECT * FROM Performance WHERE Tournament = ?");
      selectPrep.setString(1, tournament);
      updatePrep.setString(3, tournament);

      final Element performanceElement = (Element)rootElement.getElementsByTagName("Performance").item(0);
      final double minimumPerformanceScore = Utilities.NUMBER_FORMAT_INSTANCE.parse(performanceElement.getAttribute("minimumScore")).doubleValue();
      rs = selectPrep.executeQuery();
      while(rs.next()) {
        if(!rs.getBoolean("Bye")) {
          final int teamNumber = rs.getInt("TeamNumber");
          final int runNumber = rs.getInt("RunNumber");
          final double computedTotal = ScoreUtils.computeTotalScore(new DatabaseTeamScore(performanceElement, teamNumber, runNumber, rs));
          if(Double.NaN != computedTotal) {
            updatePrep.setDouble(1, Math.max(computedTotal, minimumPerformanceScore));
          } else {
            updatePrep.setNull(1, Types.DOUBLE);
          }
          updatePrep.setInt(2, teamNumber);
          updatePrep.setInt(4, runNumber);
          updatePrep.executeUpdate();
        }
      }
      rs.close();
      updatePrep.close();
      selectPrep.close();
    } finally {
      SQLFunctions.closeResultSet(rs);
      SQLFunctions.closePreparedStatement(updatePrep);
      SQLFunctions.closePreparedStatement(selectPrep);
    }
  }

  /**
   * Get the challenge document out of the database. This method doesn't
   * validate the document, since it's assumed that the document was validated
   * before it was put in the database.
   * 
   * @param connection
   *          connection to the database
   * @return the document
   * @throws RuntimeException
   *           if the document cannot be found
   * @throws SQLException
   *           on a database error
   */
  public static Document getChallengeDocument(final Connection connection) throws SQLException, RuntimeException {

    Statement stmt = null;
    ResultSet rs = null;
    try {
      stmt = connection.createStatement();
      rs = stmt.executeQuery("SELECT Value FROM TournamentParameters WHERE Param = 'ChallengeDocument'");
      if(rs.next()) {
        return ChallengeParser.parse(new InputStreamReader(rs.getAsciiStream(1)));
      } else {
        throw new RuntimeException("Could not find challenge document in database");
      }
    } finally {
      SQLFunctions.closeResultSet(rs);
      SQLFunctions.closeStatement(stmt);
    }
  }

  /**
   * Advance a team to the next tournament.
   * 
   * @param connection
   *          the database connection
   * @param teamNumber
   *          the team to advance
   * @return true on success. Failure indicates that no next tournament exists
   */
  public static boolean advanceTeam(final Connection connection, final int teamNumber) throws SQLException {

    final String currentTournament = getTeamCurrentTournament(connection, teamNumber);
    final String nextTournament = getNextTournament(connection, currentTournament);
    if(null == nextTournament) {
      if(LOG.isInfoEnabled()) {
        LOG.info("advanceTeam - No next tournament exists for tournament: " + currentTournament + " team: " + teamNumber);
      }
      return false;
    } else {
      PreparedStatement prep = null;
      try {
        prep = connection.prepareStatement("INSERT INTO TournamentTeams (TeamNumber, Tournament, event_division) VALUES (?, ?, ?)");
        prep.setInt(1, teamNumber);
        prep.setString(2, nextTournament);
        prep.setString(3, getDivisionOfTeam(connection, teamNumber));
        prep.executeUpdate();

        return true;
      } finally {
        SQLFunctions.closePreparedStatement(prep);
      }
    }
  }

  /**
   * Get the current tournament that this team is at.
   */
  public static String getTeamCurrentTournament(final Connection connection, final int teamNumber) throws SQLException {
    PreparedStatement prep = null;
    ResultSet rs = null;
    try {
      prep = connection.prepareStatement("SELECT Tournaments.Name, Tournaments.NextTournament" + " FROM TournamentTeams, Tournaments"
          + " WHERE TournamentTeams.TeamNumber = ?" + " AND TournamentTeams.Tournament = Tournaments.Name");
      prep.setInt(1, teamNumber);
      rs = prep.executeQuery();
      final List<String> tournamentNames = new LinkedList<String>();
      final List<String> nextTournaments = new LinkedList<String>();
      while(rs.next()) {
        tournamentNames.add(rs.getString(1));
        nextTournaments.add(rs.getString(2));
      }

      final Iterator<String> iter = nextTournaments.iterator();
      for(int i = 0; iter.hasNext(); i++) {
        final String nextTournament = iter.next();
        if(null == nextTournament) {
          // if no next tournament then this must be the current one since a
          // team can't advance any further.
          return tournamentNames.get(i);
        } else if(!tournamentNames.contains(nextTournament)) {
          // team hasn't advanced past this tournament yet
          return tournamentNames.get(i);
        }
      }

      LOG.error("getTeamCurrentTournament - Cannot determine current tournament for team: " + teamNumber + " tournamentNames: " + tournamentNames
          + " nextTournaments: " + nextTournaments + " - using DUMMY tournament as default");
      return "DUMMY";
    } finally {
      SQLFunctions.closeResultSet(rs);
      SQLFunctions.closePreparedStatement(prep);
    }
  }

  /**
   * Change the current tournament for a team. This will delete all scores for
   * the team in it's current tournament.
   * 
   * @param connection
   *          db connection
   * @param document
   *          the description of the tournament, used to determine what tables
   *          scores exist in
   * @param teamNumber
   *          the team
   * @param newTournament
   *          the new current tournament for this team
   */
  public static void changeTeamCurrentTournament(final Connection connection,
                                                 final Document document,
                                                 final int teamNumber,
                                                 final String newTournament) throws SQLException {

    final String currentTournament = getTeamCurrentTournament(connection, teamNumber);

    PreparedStatement prep = null;
    try {

      // delete from subjective categories
      for(final Element category : XMLUtils.filterToElements(document.getDocumentElement().getElementsByTagName("subjectiveCategory"))) {
        final String name = category.getAttribute("name");
        prep = connection.prepareStatement("DELETE FROM " + name + " WHERE TeamNumber = ? AND Tournament = ?");
        prep.setInt(1, teamNumber);
        prep.setString(2, currentTournament);
        prep.executeUpdate();
        SQLFunctions.closePreparedStatement(prep);
      }

      // delete from Performance
      prep = connection.prepareStatement("DELETE FROM Performance WHERE TeamNumber = ? AND Tournament = ?");
      prep.setInt(1, teamNumber);
      prep.setString(2, currentTournament);
      prep.executeUpdate();
      SQLFunctions.closePreparedStatement(prep);

      // delete from TournamentTeams
      prep = connection.prepareStatement("DELETE FROM TournamentTeams WHERE TeamNumber = ? AND Tournament = ?");
      prep.setInt(1, teamNumber);
      prep.setString(2, currentTournament);
      prep.executeUpdate();
      SQLFunctions.closePreparedStatement(prep);

      // delete from FinalScores
      prep = connection.prepareStatement("DELETE FROM FinalScores WHERE TeamNumber = ? AND Tournament = ?");
      prep.setInt(1, teamNumber);
      prep.setString(2, currentTournament);
      prep.executeUpdate();
      SQLFunctions.closePreparedStatement(prep);

      // set new tournament
      prep = connection.prepareStatement("INSERT INTO TournamentTeams (TeamNumber, Tournament, event_division) VALUES (?, ?, ?)");
      prep.setInt(1, teamNumber);
      prep.setString(2, newTournament);
      prep.setString(3, getDivisionOfTeam(connection, teamNumber));
      prep.executeUpdate();
      SQLFunctions.closePreparedStatement(prep);

    } finally {
      SQLFunctions.closePreparedStatement(prep);
    }
  }

  /**
   * Demote the team to it's previous tournament. This will delete all scores
   * for the team in it's current tournament.
   * 
   * @param connection
   *          db connection
   * @param document
   *          the description of the tournament, used to determine what tables
   *          scores exist in
   * @param teamNumber
   *          the team
   */
  public static void demoteTeam(final Connection connection, final Document document, final int teamNumber) throws SQLException {

    final String currentTournament = getTeamCurrentTournament(connection, teamNumber);

    PreparedStatement prep = null;
    try {
      // delete from subjective categories
      for(final Element category : XMLUtils.filterToElements(document.getDocumentElement().getElementsByTagName("subjectiveCategory"))) {
        final String name = category.getAttribute("name");
        prep = connection.prepareStatement("DELETE FROM " + name + " WHERE TeamNumber = ? AND Tournament = ?");
        prep.setInt(1, teamNumber);
        prep.setString(2, currentTournament);
        prep.executeUpdate();
        SQLFunctions.closePreparedStatement(prep);
      }

      // delete from Performance
      prep = connection.prepareStatement("DELETE FROM Performance WHERE TeamNumber = ? AND Tournament = ?");
      prep.setInt(1, teamNumber);
      prep.setString(2, currentTournament);
      prep.executeUpdate();
      SQLFunctions.closePreparedStatement(prep);

      // delete from TournamentTeams
      prep = connection.prepareStatement("DELETE FROM TournamentTeams WHERE TeamNumber = ? AND Tournament = ?");
      prep.setInt(1, teamNumber);
      prep.setString(2, currentTournament);
      prep.executeUpdate();
      SQLFunctions.closePreparedStatement(prep);

      // delete from FinalScores
      prep = connection.prepareStatement("DELETE FROM FinalScores WHERE TeamNumber = ? AND Tournament = ?");
      prep.setInt(1, teamNumber);
      prep.setString(2, currentTournament);
      prep.executeUpdate();
      SQLFunctions.closePreparedStatement(prep);

    } finally {
      SQLFunctions.closePreparedStatement(prep);
    }
  }

  /**
   * Get the previous tournament for this team, given the current tournament.
   * 
   * @param connection
   *          the database connection
   * @param teamNumber
   *          the team number
   * @param currentTournament
   *          the current tournament to use to find the previous tournament,
   *          generally this is the return value of getTeamCurrentTournament
   * @return the tournament, or null if no such tournament exists
   * @see #getTeamCurrentTournament(Connection, int)
   */
  public static String getTeamPrevTournament(final Connection connection, final int teamNumber, final String currentTournament) throws SQLException {
    PreparedStatement prep = null;
    ResultSet rs = null;
    try {
      prep = connection.prepareStatement("SELECT Tournaments.Name" + " FROM TournamentTeams, Tournaments" + " WHERE TournamentTeams.TeamNumber = ?"
          + " AND TournamentTeams.Tournament = Tournaments.Name" + " AND Tournaments.NextTournament = ?");
      prep.setInt(1, teamNumber);
      prep.setString(2, currentTournament);
      rs = prep.executeQuery();
      if(rs.next()) {
        return rs.getString(1);
      } else {
        return null;
      }

    } finally {
      SQLFunctions.closeResultSet(rs);
      SQLFunctions.closePreparedStatement(prep);
    }

  }

  /**
   * Get the next tournament for the given tournament.
   * 
   * @param connection
   *          the database connection
   * @param tournament
   *          the tournament to find the next tournament for
   * @return the next tournament or null if no such tournament exists
   */
  public static String getNextTournament(final Connection connection, final String tournament) throws SQLException {
    ResultSet rs = null;
    PreparedStatement prep = null;
    try {
      prep = connection.prepareStatement("SELECT NextTournament FROM Tournaments WHERE Name = ?");
      prep.setString(1, tournament);
      rs = prep.executeQuery();
      if(rs.next()) {
        return rs.getString(1);
      } else {
        return null;
      }
    } finally {
      SQLFunctions.closeResultSet(rs);
      SQLFunctions.closePreparedStatement(prep);
    }
  }

  /**
   * Add a team to the database. Automatically adds the team to the current
   * tournament as well.
   * 
   * @return null on success, the name of the other team with the same team
   *         number on an error
   */
  public static String addTeam(final Connection connection,
                               final int number,
                               final String name,
                               final String organization,
                               final String region,
                               final String division) throws SQLException {
    Statement stmt = null;
    ResultSet rs = null;
    PreparedStatement prep = null;
    try {
      // need to check for duplicate teamNumber
      stmt = connection.createStatement();
      rs = stmt.executeQuery("SELECT TeamName FROM Teams WHERE TeamNumber = " + number);
      if(rs.next()) {
        prep = null;
        final String dup = rs.getString(1);
        return dup;
      } else {
        SQLFunctions.closeResultSet(rs);
        rs = null;
      }

      prep = connection.prepareStatement("INSERT INTO Teams (TeamName, Organization, Region, Division, TeamNumber) VALUES (?, ?, ?, ?, ?)");
      prep.setString(1, name);
      prep.setString(2, organization);
      prep.setString(3, region);
      prep.setString(4, division);
      prep.setInt(5, number);
      prep.executeUpdate();
      SQLFunctions.closePreparedStatement(prep);

      prep = connection.prepareStatement("INSERT INTO TournamentTeams (Tournament, TeamNumber, event_division) VALUES(?, ?, ?)");
      prep.setString(1, getCurrentTournament(connection));
      prep.setInt(2, number);
      prep.setString(3, division);
      prep.executeUpdate();
      SQLFunctions.closePreparedStatement(prep);

      return null;
    } finally {
      SQLFunctions.closeResultSet(rs);
      SQLFunctions.closeStatement(stmt);
      SQLFunctions.closePreparedStatement(prep);
    }
  }

  /**
   * Update a team in the database.
   */
  public static void updateTeam(final Connection connection,
                                final int number,
                                final String name,
                                final String organization,
                                final String region,
                                final String division) throws SQLException {

    PreparedStatement prep = null;
    try {
      prep = connection.prepareStatement("UPDATE Teams SET TeamName = ?, Organization = ?, Region = ?, Division = ? WHERE TeamNumber = ?");
      prep.setString(1, name);
      prep.setString(2, organization);
      prep.setString(3, region);
      prep.setString(4, division);
      prep.setInt(5, number);
      prep.executeUpdate();
    } finally {
      SQLFunctions.closePreparedStatement(prep);
    }
  }

  /**
   * Insert a tournament for each region found in the teams table, if it doesn't
   * already exist. Sets name and location equal to the region name.
   */
  public static void insertTournamentsForRegions(final Connection connection) throws SQLException {
    ResultSet rs = null;
    Statement stmt = null;
    PreparedStatement insertPrep = null;
    try {
      insertPrep = connection.prepareStatement("INSERT INTO Tournaments (Name, Location) VALUES(?, ?)");

      stmt = connection.createStatement();
      rs = stmt
          .executeQuery("SELECT DISTINCT Teams.Region FROM Teams LEFT JOIN Tournaments ON Teams.REgion = Tournaments.Name WHERE Tournaments.Name IS NULL");
      while(rs.next()) {
        final String region = rs.getString(1);
        insertPrep.setString(1, region);
        insertPrep.setString(2, region);
        insertPrep.executeUpdate();
      }
    } finally {
      SQLFunctions.closeResultSet(rs);
      SQLFunctions.closeStatement(stmt);
      SQLFunctions.closePreparedStatement(insertPrep);
    }
  }

  /**
   * Make sure all of the judges are properly assigned for the current
   * tournament
   * 
   * @param connection
   *          the database connection
   * @param document
   *          XML document to describe the tournament
   * @return true if everything is ok
   */
  public static boolean isJudgesProperlyAssigned(final Connection connection, final Document document) throws SQLException {


    PreparedStatement prep = null;
    ResultSet rs = null;
    try {
      prep = connection.prepareStatement("SELECT id FROM Judges WHERE Tournament = ? AND category = ?");
      prep.setString(1, getCurrentTournament(connection));

      for(final Element element : XMLUtils.filterToElements(document.getDocumentElement().getElementsByTagName("subjectiveCategory"))) {
        final String categoryName = element.getAttribute("name");
        prep.setString(2, categoryName);
        rs = prep.executeQuery();
        if(!rs.next()) {
          return false;
        }
        SQLFunctions.closeResultSet(rs);
        rs = null;
      }
      return true;
    } finally {
      SQLFunctions.closeResultSet(rs);
      SQLFunctions.closePreparedStatement(prep);
    }
  }

  /**
   * Determines whether or not the playoff data table has been initialized for
   * the specified division. Uses the current tournament value obtained from
   * getCurrentTournament().
   * 
   * @param connection
   *          The database connection to use.
   * @param division
   *          The division to check in the current tournament.
   * @return A boolean, true if the PlayoffData table has been initialized,
   *         false if it has not.
   * @throws SQLException
   *           if database access fails.
   * @throws RuntimeException
   *           if query returns empty results.
   */
  public static boolean isPlayoffDataInitialized(final Connection connection, final String division) throws SQLException, RuntimeException {
    final String curTourney = getCurrentTournament(connection);

    final String sql = "SELECT Count(*) FROM PlayoffData" + " WHERE Tournament = '" + curTourney + "'" + " AND event_division = '" + division + "'";

    Statement stmt = null;
    ResultSet rs = null;
    try {
      stmt = connection.createStatement();
      rs = stmt.executeQuery(sql);
      if(!rs.next()) {
        throw new RuntimeException("Query to obtain count of PlayoffData entries returned no data");
      } else {
        return rs.getInt(1) > 0;
      }
    } finally {
      SQLFunctions.closeResultSet(rs);
      SQLFunctions.closeStatement(stmt);
    }
  }

  /**
   * Get the color for a division index. Below are the colors used.
   * <table>
   * <td>
   * <td bgcolor="#800000">0 - #800000</td>
   * </tr>
   * <td>
   * <td bgcolor="#008000">1 - #008000</td>
   * </tr>
   * <td>
   * <td bgcolor="#CC6600">2 - #CC6600</td>
   * </tr>
   * <td>
   * <td bgcolor="#FF00FF">3 - #FF00FF</td>
   * </tr>
   * <td>
   * <td>continue at the top</td> </tr> </ol>
   * 
   * @param index
   *          the division index
   */
  public static String getColorForDivisionIndex(final int index) throws SQLException {
    final int idx = index % 4;
    switch(idx) {
    case 0:
      return "#800000";
    case 1:
      return "#008000";
    case 2:
      return "#CC6600";
    case 3:
      return "#FF00FF";
    default:
      throw new RuntimeException("Internal error, cannot choose color");
    }
  }

  /**
   * Get the value of Bye for the given team number, tournament and run number
   * 
   * @return true if the score is a bye, false if it's not a bye or the score
   *         does not exist
   * @throws SQLException
   *           on a database error
   */
  public static boolean isBye(final Connection connection, final String tournament, final int teamNumber, final int runNumber)
      throws SQLException, IllegalArgumentException {
    Statement stmt = null;
    ResultSet rs = null;
    try {
      stmt = connection.createStatement();
      rs = stmt.executeQuery("SELECT Bye FROM Performance" + " WHERE TeamNumber = " + teamNumber + " AND Tournament = '" + tournament + "'"
          + " AND RunNumber = " + runNumber);
      if(rs.next()) {
        return rs.getBoolean(1);
      } else {
        return false;
      }
    } finally {
      SQLFunctions.closeResultSet(rs);
      SQLFunctions.closeStatement(stmt);
    }
  }

  /**
   * Used to get the line number of a team from the playoff table for a specific
   * round of the playoff bracket.
   * 
   * @param connection
   *          Database connection to use.
   * @param tournament
   *          Tournament identifier.
   * @param teamNumber
   *          Team number for which to look.
   * @param playoffRunNumber
   *          Playoff round number to search. Based at 1.
   * @return The line number of the playoff bracket in which the team number is
   *         found, or a -1 if the team number was not found in the specified
   *         round of the PlayoffData table.
   * @throws SQLException
   *           on a database error.
   */
  public static int getPlayoffTableLineNumber(final Connection connection,
                                              final String tournament,
                                              final String teamNumber,
                                              final int playoffRunNumber) throws SQLException {
    Statement stmt = null;
    ResultSet rs = null;
    try {
      stmt = connection.createStatement();
      rs = stmt.executeQuery("SELECT LineNumber FROM PlayoffData" + " WHERE Team = " + teamNumber + " AND Tournament = '" + tournament + "'"
          + " AND PlayoffRound = " + playoffRunNumber);
      if(rs.next()) {
        return rs.getInt(1);
      } else {
        return -1; // indicates team not present in this run
      }
    } finally {
      SQLFunctions.closeResultSet(rs);
      SQLFunctions.closeStatement(stmt);
    }
  }

  /**
   * Gets the number of the team from the PlayoffData table given the
   * tournament, division, line number, and playoff round.
   * 
   * @param connection
   *          Database connection.
   * @param tournament
   *          Tournament identifier.
   * @param division
   *          Division string.
   * @param lineNumber
   *          Line number of the playoff bracket, based at 1.
   * @param playoffRunNumber
   *          Run number of the playoff bracket, based at 1.
   * @return The team number located at the specified location in the playoff
   *         bracket.
   * @throws SQLException
   *           if there is a database error.
   */
  public static int getTeamNumberByPlayoffLine(final Connection connection,
                                               final String tournament,
                                               final String division,
                                               final int lineNumber,
                                               final int playoffRunNumber) throws SQLException {
    Statement stmt = null;
    ResultSet rs = null;
    try {
      stmt = connection.createStatement();
      rs = stmt.executeQuery("SELECT Team FROM PlayoffData" + " WHERE event_division = '" + division + "'" + " AND Tournament = '" + tournament + "'"
          + " AND LineNumber = " + lineNumber + " AND PlayoffRound = " + playoffRunNumber);
      if(rs.next()) {
        final int retVal = rs.getInt(1);
        if(rs.wasNull()) {
          return Team.NULL_TEAM_NUMBER;
        } else {
          return retVal;
        }
      }
    } finally {
      SQLFunctions.closeResultSet(rs);
      SQLFunctions.closeStatement(stmt);
    }
    return Team.NULL_TEAM_NUMBER;
  }

  /**
   * Get the division that a team is registered in. This is different from
   * {@link #getEventDivision(Connection, int)} in that it doesn't change
   * throughout the season.
   * 
   * @param connection
   *          Database connection.
   * @param teamNumber
   *          Number of the team from which to look up the division.
   * @return String containing the division for the specified teams.
   * @throws SQLException
   *           on database errors.
   * @throws RuntimeException
   *           if the team number is not found in the Teams table.
   */
  public static String getDivisionOfTeam(final Connection connection, final int teamNumber) throws SQLException, RuntimeException {
    Statement stmt = null;
    ResultSet rs = null;
    try {
      stmt = connection.createStatement();
      rs = stmt.executeQuery("SELECT Division FROM Teams WHERE TeamNumber = " + teamNumber);
      if(rs.next()) {
        return rs.getString(1);
      } else {
        throw new RuntimeException("Unable to find team number " + teamNumber + "in the database.");
      }
    } finally {
      SQLFunctions.closeResultSet(rs);
      SQLFunctions.closeStatement(stmt);
    }
  }

  /**
   * Returns the number of playoff rounds for the specified division. Depends on
   * the PlayoffData table having been initialized for that division.
   * 
   * @param connection
   *          The database connection.
   * @param division
   *          The division for which to get the number of playoff rounds.
   * @return The number of playoff rounds in the specified division, or 0 if
   *         brackets have not been initialized.
   * @throws SQLException
   *           on database errors.
   */
  public static int getNumPlayoffRounds(final Connection connection, final String division) throws SQLException {
    final int x = getFirstPlayoffRoundSize(connection, division);
    if(x > 0) {
      return (int)Math.round(Math.log(x) / Math.log(2));
    } else {
      return 0;
    }
  }

  /**
   * Returns the max number of playoff rounds all divisions. Depends on the
   * PlayoffData table having been initialized for that division.
   * 
   * @param connection
   *          The database connection.
   * @return The maximum number of playoff rounds in all divisions, or 0 if
   *         brackets have not been initialized.
   * @throws SQLException
   *           on database errors.
   */
  public static int getNumPlayoffRounds(final Connection connection) throws SQLException {
    int numRounds = 0;
    for(String division : getDivisions(connection)) {
      final int x = getFirstPlayoffRoundSize(connection, division);
      if(x > 0) {
        numRounds = Math.max((int)Math.round(Math.log(x) / Math.log(2)), numRounds);
      }
    }
    return numRounds;
  }

  /**
   * Get size of first playoff round.
   * 
   * @param connection
   *          Database connection to use.
   * @param division
   *          The division for which to look up round 1 size.
   * @return The size of the first round of the playoffs. This is always a power
   *         of 2, and is greater than the number of teams in the tournament by
   *         the number of byes in the first round.
   * @throws SQLException
   *           on database error.
   */
  public static int getFirstPlayoffRoundSize(final Connection connection, final String division) throws SQLException {
    final String tournament = getCurrentTournament(connection);
    Statement stmt = null;
    ResultSet rs = null;
    try {
      stmt = connection.createStatement();
      rs = stmt.executeQuery("SELECT count(*) FROM PlayoffData" + " WHERE Tournament='" + tournament + "'" + " AND event_division='" + division + "'"
          + " AND PlayoffRound=1");
      if(rs.next()) {
        return rs.getInt(1);
      } else {
        return 0;
      }
    } finally {
      SQLFunctions.closeResultSet(rs);
      SQLFunctions.closeStatement(stmt);
    }
  }

  public static int getTableAssignmentCount(final Connection connection, final String tournament, final String division) throws SQLException {
    Statement stmt = null;
    ResultSet rs = null;
    try {
      stmt = connection.createStatement();
      rs = stmt.executeQuery("SELECT count(*) FROM PlayoffData" + " WHERE Tournament='" + tournament + "'" + " AND event_division='" + division + "'"
          + " AND AssignedTable IS NOT NULL");
      if(rs.next()) {
        return rs.getInt(1);
      } else {
        return 0;
      }
    } finally {
      SQLFunctions.closeResultSet(rs);
      SQLFunctions.closeStatement(stmt);
    }
  }

  /**
   * Returns the table assignment string for the given tournament, event
   * division, round number, and line number. If the table assignment is NULL,
   * returns null.
   */
  public static String getAssignedTable(final Connection connection,
                                        final String tournament,
                                        final String eventDivision,
                                        final int round,
                                        final int line) throws SQLException {
    Statement stmt = null;
    ResultSet rs = null;
    try {
      stmt = connection.createStatement();
      rs = stmt.executeQuery("SELECT AssignedTable from PlayoffData WHERE Tournament='" + tournament + "' AND event_division='" + eventDivision + "'"
          + " AND PlayoffRound=" + round + " AND LineNumber=" + line + " AND AssignedTable IS NOT NULL");
      if(rs.next()) {
        return rs.getString(1);
      } else {
        return null;
      }
    } finally {
      SQLFunctions.closeResultSet(rs);
      SQLFunctions.closeStatement(stmt);
    }
  }
}