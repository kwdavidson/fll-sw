<%@ include file="/WEB-INF/jspf/init.jspf" %>
      
<%@ page import="fll.db.Queries" %>
  
<%@ page import="java.sql.Connection" %>

<%
final Connection connection = (Connection)application.getAttribute("connection");
pageContext.setAttribute("currentTournament", Queries.getCurrentTournament(connection));
%>
  
<HTML>
<head>
  <style>
        FONT {color: #ffffff; font-family: "Arial"}
  </style>
<script language=javascript>
  window.setInterval("location.href='last8.jsp'",30000);
</script>
</head>

<body bgcolor='#000080'>
  <center>
    <table border='0' cellpadding='0' cellspacing='0' width='98%'>
      <tr align='center'>
        <td colspan='6'><font size='3'><b>Most Recent Performance Scores</b></font></td>
      </tr>
      <tr>
        <td colspan='6'><img src='<c:url value="/images/blank.gif"/>' width='1' height='4'></td>
      </tr>
      <tr align='center' valign='middle'>
        <td width='10%'><font size='2'><b>Team<br>Num.</b></font></td>
        <td width='28%'><font size='2'><b>Team<br>Name</b></font></td>
        <td><font size='2'><b><br>Organization</b></font></td>
        <td width='5%'><font size='2'><b><br>Div.</b></font></td>
        <td width='5%'><font size='2'><b><br>Run</b></font></td>
        <td width='8%'><font size='2'><b><br>Score</b></font></td>
      </tr>
      <tr>
        <td colspan='6' align='center'>
          <sql:query var="result" dataSource="${datasource}">
            SELECT Teams.TeamNumber, Teams.Organization, Teams.TeamName, current_tournament_teams.event_division, Performance.Tournament, Performance.RunNumber, Performance.Bye, Performance.NoShow, Performance.TimeStamp, Performance.ComputedTotal
              FROM Teams,Performance,current_tournament_teams
              WHERE Performance.Tournament = '<c:out value="${currentTournament}"/>'
                AND Teams.TeamNumber = Performance.TeamNumber
                AND Teams.TeamNumber = current_tournament_teams.TeamNumber
                AND Performance.Bye = False
              ORDER BY Performance.TimeStamp DESC, Teams.TeamNumber ASC LIMIT 8
          </sql:query>
                    
          <!-- scores here -->
          <table border='1' bordercolor='#aaaaaa' cellpadding='4' cellspacing='0' width='100%'>
            <c:forEach items="${result.rows}" var="row">
              <tr align='left'>
                <td width='10%' align='right'>
                  <font size='3'><c:out value="${row.TeamNumber}"/></font>
                </td>
                <td width='28%'>
                  <font size='3'>
                    <c:out value="${fn:substring(row.TeamName, 0, 20)}"/>&nbsp;
                  </font>
                </td>
                <td>
                  <font size='3'>
                    <c:out value="${fn:substring(row.Organization, 0, 35)}"/>&nbsp;
                  </font>
                </td>
                <td width='5%' align='right'>
                  <font size='3'><c:out value="${row.event_division}"/></font>
                </td>
                <td width='5%' align='right'>
                  <font size='3'><c:out value="${row.RunNumber}"/></font>
                </td>
                <td width='8%' align='right'>
                  <font size='3'>
                    <c:choose>
                      <c:when test="${row.NoShow == True}">
                        No Show
                      </c:when>
                      <c:when test="${row.Bye == True}">
                        Bye
                      </c:when>
                      <c:otherwise>
                        <fmt:formatNumber value="${row.ComputedTotal}" minFractionDigits="2" maxFractionDigits="2"/>
                      </c:otherwise>
                    </c:choose>
                  </font>
                </td>
              </tr>
            </c:forEach>
          </table>
          <!-- end scores -->
        </td>
      </tr>
    </table>
  </center>
</body>
</HTML>