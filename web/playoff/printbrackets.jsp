<%@ include file="/WEB-INF/jspf/init.jspf" %>

<%@ page import="org.w3c.dom.Document" %>

<%@ page import="fll.Team" %>
<%@ page import="fll.Utilities" %>
<%@ page import="fll.Queries" %>
<%@ page import="fll.web.playoff.Playoff" %>
  
<%@ page import="java.util.Map" %>
<%@ page import="java.util.List" %>
<%@ page import="java.util.LinkedList" %>
<%@ page import="java.util.Iterator" %>

<%@ page import="java.sql.Connection" %>
  
<%
/*
  Parameters:
  
  division - String for the division
  runNumber - int for the run number, counted since the very first performance round
  nextRound - if present use newRunNumber instead of runNumber
  newRunNumber - if nextRound is present, use this value instead of runNumber
*/
  
final Connection connection = (Connection)application.getAttribute("connection");
final Map tournamentTeams = Queries.getTournamentTeams(connection);
final Document challengeDocument = (Document)application.getAttribute("challengeDocument");
final String currentTournament = Queries.getCurrentTournament(connection);


final String divisionStr = request.getParameter("division");
if(null == divisionStr) {
  throw new RuntimeException("No division specified, please go back to the <a href='index.jsp'>playoff main page</a> and start again.");
}

final int numSeedingRounds = Queries.getNumSeedingRounds(connection);
final String runNumberStr = request.getParameter("runNumber");
final int runNumber;
if(null == runNumberStr) {
  //first time to the page
  runNumber = Queries.getNumSeedingRounds(connection) + 1;
} else if(null != request.getParameter("nextRound")) {
  runNumber = Utilities.NUMBER_FORMAT_INSTANCE.parse(request.getParameter("newRunNumber")).intValue();
} else {
  runNumber = Utilities.NUMBER_FORMAT_INSTANCE.parse(runNumberStr).intValue();
}

int tempRunNumber = numSeedingRounds + 1;
List tempCurrentRound = Playoff.buildInitialBracketOrder(connection, divisionStr, tournamentTeams);

while(tempRunNumber < runNumber) {
  final List newCurrentRound = new LinkedList();
  final Iterator prevIter = tempCurrentRound.iterator();
  while(prevIter.hasNext()) {
    final Team teamA = (Team)prevIter.next();
    final Team teamB = (Team)prevIter.next();
    final Team winner = Playoff.pickWinner(connection, challengeDocument, teamA, teamB, tempRunNumber);
    newCurrentRound.add(winner);
  }
  tempCurrentRound = newCurrentRound;
  tempRunNumber++;
}
final List currentRound = tempCurrentRound;
pageContext.setAttribute("currentRoundSize", new Integer(currentRound.size()));

int tempNumRuns = 1;
while(Math.pow(2, tempNumRuns) < currentRound.size()) {
  tempNumRuns++;
}
pageContext.setAttribute("numRuns", new Integer(tempNumRuns));
%>

<html>
  <head>
    <link rel="stylesheet" type="text/css" href="<c:url value='/style/style.jsp'/>" />
    <title><x:out select="$challengeDocument/fll/@title"/> (Playoff Brackets) Division: <%=divisionStr%> Run Number: <%=runNumber%></title>
  </head>
  <style type='text/css'>
      TD.Leaf {font-family:Arial;border-bottom: solid}
      TD.Bridge {border-left: solid; border-right: solid; border-bottom: solid}
      TD.BridgeTop {border-bottom: solid}
      FONT {font-family:Arial}
      FONT.TeamNumber {font-weight:bold}
      FONT.TeamName {font-weight:bold}
      FONT.TeamScore {font-weight:bold;font-size:10pt}
      FONT.TIE {color:#ff0000;font-weight:bold}
  </style>

  <body>
    <h2><x:out select="$challengeDocument/fll/@title"/> (Playoff Brackets Division: <%=divisionStr%> Run Number: <%=runNumber%>)</h2>
        Number of runs needed to complete: <c:out value="${numRuns}"/><br />
    <form action='printbrackets.jsp' method='get'>
      <input type='hidden' name='runNumber' value='<%=runNumber%>'>
      <input type='hidden' name='division' value='<%=divisionStr%>'>
      <input type='hidden' name='newRunNumber' value='<%=runNumber+1%>'>
      
      <c:if test="${currentRoundSize > 2}">
        <input type='submit' name='nextRound' value='Next Round (<%=runNumber+1%>)'>
        <br><br>
      </c:if>
      
      <table align='center' width='100%' border='0' cellpadding='3' cellspacing='0'>
        <c:if test="${currentRoundSize > 1}"> <!-- need at least 2 teams -->
          <%
          final Iterator currentIter = currentRound.iterator();
          for(int index=0; currentIter.hasNext(); index++) {
            pageContext.setAttribute("evenBracket", Boolean.valueOf( ( (index/2) * 2) == index ));
            final boolean evenBracket = ( ( (index/2) * 2) == index );
            final Team teamA = (Team)currentIter.next();
            final Team teamB = (Team)currentIter.next();
            final Team winner = Playoff.pickWinner(connection, challengeDocument, teamA, teamB, runNumber);
          %>
          <tr> <!-- row 1 -->
            <td class='Leaf' width='200'>
              <%=Playoff.getDisplayString(connection, currentTournament, runNumber, teamA, teamB, false)%>
            </td>
            
            <!-- connect teamA and teamB -->
            <td class='BridgeTop'>&nbsp;</td>

            <td width='200'>&nbsp;</td>
            <c:if test="${evenBracket}">
              <td width='10'>&nbsp;</td>
            </c:if>
            <c:if test="${not evenBracket}">
              <!-- skip column for bracket-bracket bar -->
            </c:if>

            <td width='200'>&nbsp;</td>
          </tr>
          
          <tr> <!-- row 2 -->
            <td width='200'>&nbsp;</td>
            <td class='Bridge' rowspan='4'>&nbsp;</td>
            <td width='200'>&nbsp;</td>
            <c:if test="${evenBracket}">
              <td width='10'>&nbsp;</td>
            </c:if>
            <c:if test="${not evenBracket}">
              <!-- skip column for bracket-bracket bar -->
            </c:if>
            <td width='200'>&nbsp;</td>
          </tr>
          
          <tr> <!-- row 3 -->
            <td width='200'><font size='4'>Bracket <%=index+1%></font><br></td>
            <!-- skip column for A-B bar -->
            <td class='Leaf' width='200'>
              <%=Playoff.getDisplayString(connection, currentTournament, (runNumber+1), winner, null, false)%>
            </td>
            

            
            <c:if test="${evenBracket}">
              <td class='BridgeTop' width='10'>&nbsp;</td>
            </c:if>
            <c:if test="${not evenBracket}">
              <!-- skip column for bracket-bracket bar -->
            </c:if>

            <td width='200'>&nbsp;</td>
          </tr>
          
          <tr> <!-- row 4 -->
            <td width='200'>&nbsp;</td>
            <!-- skip column for A-B bar -->
            <td width='200'>&nbsp;</td>
            
            <c:if test="${evenBracket}">
              <c:if test="${currentRoundSize > 2}">
                <!-- vertical bar down to next bracket -->
                <td rowspan='8' class='Bridge'>&nbsp;</td>
              </c:if>
              <c:if test="${currentRoundSize <= 2}">
                <!-- just do a rowspan to fill out the table properly -->
                <td rowspan='5'>&nbsp;</td>
              </c:if>
            </c:if>
            <c:if test="${not evenBracket}">
              <td width='10'>&nbsp;</td>
            </c:if>
            
            <td width='200'>&nbsp;</td>
          </tr>
          
          <tr> <!-- row 5 -->
            <td class='Leaf' width='200'>
              <%=Playoff.getDisplayString(connection, currentTournament, runNumber, teamB, teamA, false)%>
            </td>
            <!-- skip column for A-B bar -->
            <td width='200'>&nbsp;</td>
            <c:if test="${evenBracket}">
              <!-- skip column for bracket-bracket bar -->
            </c:if>
            <c:if test="${not evenBracket}">
              <td width='10'>&nbsp;</td>
            </c:if>
            <td width='200'>&nbsp;</td>
          </tr>

          <tr> <!-- between row 1 -->
            <td width='200'>&nbsp;</td>
            <td width='10'>&nbsp;</td>
            <td width='200'>&nbsp;</td>
            <c:if test="${evenBracket}">
              <!-- skip column for bracket-bracket bar -->
            </c:if>
            <c:if test="${not evenBracket}">
              <td width='10'>&nbsp;</td>
            </c:if>
            <td width='200'>&nbsp;</td>
          </tr>

          <tr> <!-- between row 2 -->
            <td width='200'>&nbsp;</td>
            <td width='10'>&nbsp;</td>
            <td width='200'>&nbsp;</td>
            <c:if test="${evenBracket}">
              <!-- skip column for bracket-bracket bar -->
              
              <c:if test="${currentRoundSize > 2}">
                <!-- winner of next bracket -->
                <td class='Leaf' width='200'>&nbsp;</td>
              </c:if>
              <c:if test="${currentRoundSize <= 2}">
                <td width='200'>&nbsp;</td>
              </c:if>
              
            </c:if>
            <c:if test="${not evenBracket}">
              <td width='10'>&nbsp;</td>
              <td width='200'>&nbsp;</td>
            </c:if>
          </tr>

          <tr> <!-- between row 3 -->
            <td width='200'>&nbsp;</td>
            <td width='10'>&nbsp;</td>
            <td width='200'>&nbsp;</td>
            <c:if test="${evenBracket}">
              <!-- skip column for bracket-bracket bar -->
            </c:if>
            <c:if test="${not evenBracket}">
              <td width='10'>&nbsp;</td>
            </c:if>
            <td width='200'>&nbsp;</td>
          </tr>
          <%}%> <!-- end for loop -->
      </table>

      <c:if test="${currentRoundSize > 2}">
        <input type='submit' name='nextRound' value='Next Round (<%=runNumber+1%>)'>
      </c:if>
    </c:if> <!-- end if we have more than 2 teams -->
    </form>
    <%@ include file="/WEB-INF/jspf/footer.jspf" %>
  </body>
</html>
