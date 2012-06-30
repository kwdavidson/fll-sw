package fll.web.ajax;

import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.ServletOutputStream;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import javax.sql.DataSource;

import net.mtu.eggplant.util.sql.SQLFunctions;

import org.w3c.dom.Element;

import fll.db.Queries;
import fll.util.JsonUtilities;
import fll.web.ApplicationAttributes;
import fll.web.BaseFLLServlet;
import fll.web.SessionAttributes;
import fll.web.playoff.BracketData;

/**
 * Talk to client brackets in json.
 */
@WebServlet("/ajax/BracketQuery")
public class AJAXBracketQueryServlet extends BaseFLLServlet {
  protected void processRequest(final HttpServletRequest request,
                                final HttpServletResponse response,
                                final ServletContext application,
                                final HttpSession session) throws IOException, ServletException {
    final DataSource datasource = ApplicationAttributes.getDataSource(application);
    Connection connection = null;
    try {
      connection = datasource.getConnection();
      final ServletOutputStream os = response.getOutputStream();
      final String multiParam = request.getParameter("multi");
      if (multiParam != null) {
        // Send off request to helpers
        handleMultipleQuery(parseInputToMap(multiParam), os, application, session, response, connection);
      } else {
        response.reset();
        response.setContentType("application/json");
        os.print("{\"_rmsg\": \"Error: No Params\"}");
      }
    } catch (final SQLException e) {
      throw new RuntimeException(e);
    } finally {
      SQLFunctions.close(connection);
    }
  }

  private Map<Integer, Integer> parseInputToMap(final String param) {
    final String[] pairs = param.split("\\|");
    final Map<Integer, Integer> pairedMap = new HashMap<Integer, Integer>();
    for (final String pair : pairs) {
      final String[] pieces = pair.split("\\-");
      if (pieces.length >= 2) {
        final String one = pieces[0];
        final String two = pieces[1];
        pairedMap.put(Integer.parseInt(one), Integer.parseInt(two));
      }
    }
    return pairedMap;
  }

  private void handleMultipleQuery(final Map<Integer, Integer> pairedMap,
                                   final ServletOutputStream os,
                                   final ServletContext application,
                                   final HttpSession session,
                                   final HttpServletResponse response,
                                   final Connection connection) {
    try {
      BracketData bd = constructBracketData(connection, session, application);
      final Element rootElement = ApplicationAttributes.getChallengeDocument(application).getDocumentElement();
      final Element perfElement = (Element) rootElement.getElementsByTagName("Performance").item(0);
      final boolean showOnlyVerifiedScores = true;
      final boolean showFinalsScores = false;
      response.reset();
      response.setContentType("application/json");
      os.print(JsonUtilities.generateJsonBracketInfo(pairedMap, connection, perfElement, bd, showOnlyVerifiedScores,
                                                     showFinalsScores));
    } catch (final IOException e) {
      throw new RuntimeException(e);
    }
  }

  private BracketData constructBracketData(final Connection connection,
                                           final HttpSession session,
                                           final ServletContext application) {
    final String divisionKey = "playoffDivision";
    final String roundNumberKey = "playoffRoundNumber";
    final String displayNameKey = "displayName";
    final String displayName = SessionAttributes.getAttribute(session, displayNameKey, String.class);
    final String sessionDivision;
    final Number sessionRoundNumber;
    if (null != displayName) {
      sessionDivision = ApplicationAttributes.getAttribute(application, displayName
          + "_" + divisionKey, String.class);
      sessionRoundNumber = ApplicationAttributes.getAttribute(application, displayName
          + "_" + roundNumberKey, Number.class);
    } else {
      sessionDivision = null;
      sessionRoundNumber = null;
    }
    final String division;
    if (null != sessionDivision) {
      division = sessionDivision;
    } else if (null == ApplicationAttributes.getAttribute(application, divisionKey, String.class)) {
      try {
        final List<String> divisions = Queries.getEventDivisions(connection);
        if (!divisions.isEmpty()) {
          division = divisions.get(0);
        } else {
          throw new RuntimeException("No division specified and no divisions in the database!");
        }
      } catch (SQLException e) {
        throw new RuntimeException(e);
      }
    } else {
      division = ApplicationAttributes.getAttribute(application, divisionKey, String.class);
    }
    final int playoffRoundNumber;
    if (null != sessionRoundNumber) {
      playoffRoundNumber = sessionRoundNumber.intValue();
    } else if (null == ApplicationAttributes.getAttribute(application, roundNumberKey, Number.class)) {
      playoffRoundNumber = 1;
    } else {
      playoffRoundNumber = ApplicationAttributes.getAttribute(application, roundNumberKey, Number.class).intValue();
    }
    final int roundsLong = 3;
    final int rowsPerTeam = 4;
    final boolean showFinalsScores = false;
    final boolean onlyShowVerifiedScores = true;
    try {
      return new BracketData(connection, division, playoffRoundNumber, playoffRoundNumber
          + roundsLong - 1, rowsPerTeam, showFinalsScores, onlyShowVerifiedScores);
    } catch (SQLException e) {
      throw new RuntimeException(e);
    }
  }
}