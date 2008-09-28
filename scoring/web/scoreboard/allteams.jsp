<%@ include file="/WEB-INF/jspf/init.jspf"%>

<%@ page import="java.sql.PreparedStatement"%>
<%@ page import="java.sql.ResultSet"%>
<%@ page import="java.sql.Connection"%>

<%@ page import="java.util.List"%>
<%@ page import="java.util.Iterator"%>

<%@ page import="net.mtu.eggplant.util.sql.SQLFunctions" %>

<%@ page import="fll.Utilities"%>
<%@ page import="fll.db.Queries"%>

<%
      final Connection connection = (Connection) application.getAttribute("connection");
      final String currentTournament = Queries.getCurrentTournament(connection);

      final PreparedStatement prep = connection.prepareStatement("SELECT Teams.TeamNumber, Teams.Organization, Teams.TeamName, current_tournament_teams.event_division,"
          + " verified_performance.Tournament, verified_performance.RunNumber, verified_performance.Bye, verified_performance.NoShow, verified_performance.ComputedTotal FROM Teams,verified_performance,current_tournament_teams"
          + " WHERE verified_performance.Tournament = ? AND current_tournament_teams.TeamNumber = Teams.TeamNumber"
          + " AND Teams.TeamNumber = verified_performance.TeamNumber ORDER BY Teams.Organization, Teams.TeamNumber, verified_performance.RunNumber");
      prep.setString(1, currentTournament);
      final ResultSet rs = prep.executeQuery();
      final List<String> divisions = Queries.getDivisions(connection);
%>

<c:set var="thisURL">
 <c:url value="${pageContext.request.servletPath}">
  <c:param name="scroll" value="${param.scroll}" />
 </c:url>
</c:set>

<HTML>
<head>
<style>
	FONT {color: #ffffff; font-family: "Arial"}
	TABLE.A {background-color:#000080 }
	TABLE.B {background-color:#0000d0 }
</style>

<!-- stuff for automatic scrolling -->
<script type="text/javascript">
var scrollTimer;
var scrollAmount = 2;    // scroll by 100 pixels each time
var documentYposition = 0;
var scrollPause = 100; // amount of time, in milliseconds, to pause between scrolls

//http://www.evolt.org/article/document_body_doctype_switching_and_more/17/30655/index.html
function getScrollPosition() {
  if (window.pageYOffset) {
    return window.pageYOffset
  } else if (document.documentElement && document.documentElement.scrollTop) {
    return document.documentElement.scrollTop
  } else if (document.body) {
    return document.body.scrollTop
  }
}

function myScroll() {
  documentYposition += scrollAmount;
  window.scrollBy(0, scrollAmount);
  if(getScrollPosition()+300 < documentYposition) { //wait 300 pixels until we refresh
    window.clearInterval(scrollTimer);
    window.scroll(0, 0); //scroll back to top and then refresh
    location.href='<c:out value="${thisURL}" />'
  }
}

function start() {
<c:if test="${not empty param.scroll}">
  scrollTimer = window.setInterval('myScroll()',scrollPause);
</c:if>
}
</script>


</head>

<body bgcolor='#000080' onload='start()'>
<br>
<br>
<br>
<br>
<br>
<br>
<br>
<br>

<c:set var="colorStr" value="A" />

<%
        if (rs.next()) {
        boolean done = false;
        while (!done) {
%>
<table border='0' cellpadding='0' cellspacing='0' width='99%'
 class='<c:out value="${colorStr}" />'>
 <tr>
  <td colspan='2'><img src='<c:url value="/images/blank.gif"/>'
   height='15' width='1'></td>
 </tr>
 <tr align='left'>
  <%
            final String divisionStr = rs.getString("event_division");
            final Iterator<String> divisionIter = divisions.iterator();
            boolean found = false;
            int index = 0;
            while (divisionIter.hasNext() && !found) {
              final String div = divisionIter.next();
              if (divisionStr.equals(div)) {
                found = true;
              } else {
                index++;
              }
            }
            final String headerColor = Queries.getColorForDivisionIndex(index);
  %>
  <td width='25%' bgcolor='<%=headerColor%>'><font size='2'><b>&nbsp;&nbsp;Division:&nbsp;<%=divisionStr%>&nbsp;&nbsp;</b></font>
  </td>
  <td align='right'><font size='2'><b>Team&nbsp;#:&nbsp;<%=rs.getInt("TeamNumber")%>&nbsp;&nbsp;</b></font>
  </td>
 </tr>
 <tr align='left'>
  <td colspan='2'><font size='4'>&nbsp;&nbsp;<%=rs.getString("Organization")%></font>
  </td>
 </tr>
 <tr align='left'>
  <td colspan='2'><font size='4'>&nbsp;&nbsp;<%=rs.getString("TeamName")%></font>
  </td>
 </tr>
 <tr>
  <td colspan='2'>
  <hr color='#ffffff' width='96%'>
  </td>
 </tr>
 <tr>
  <td colspan='2'>

  <table border='0' cellpadding='1' cellspacing='0'>
   <tr align='center'>
    <td><img src='<c:url value="/images/blank.gif"/>' height='1'
     width='60'></td>
    <td><font size='4'>Run #</font></td>
    <td><img src='<c:url value="/images/blank.gif"/>' width='20'
     height='1'></td>
    <td><font size='4'>Score</font></td>
   </tr>
   <%
             int prevNum = rs.getInt("TeamNumber");
             do {
   %>
   <tr align='right'>
    <td><img src='<c:url value="/images/blank.gif"/>' height='1'
     width='60'></td>
    <td><font size='4'><%=rs.getInt("RunNumber")%></font></td>
    <td><img src='<c:url value="/images/blank.gif"/>' width='20'
     height='1'></td>
    <td><font size='4'> <%
 if (rs.getBoolean("NoShow")) {
 %> No Show <%
 } else if (rs.getBoolean("Bye")) {
 %> Bye <%
               } else {
               out.println(Utilities.NUMBER_FORMAT_INSTANCE.format(rs.getDouble("ComputedTotal")));
             }
             if (!rs.next()) {
               done = true;
             }
           } while (!done && prevNum == rs.getInt("TeamNumber"));
 %> </font></td>
  </table>

  </td>
 </tr>
 <tr>
  <td colspan='2'><img src='<c:url value="/images/blank.gif"/>'
   width='1' height='15'></td>
 </tr>
</table>

<c:choose>
 <c:when test="${'A' == colorStr}">
  <c:set var="colorStr" value="B" />
 </c:when>
 <c:otherwise>
  <c:set var="colorStr" value="A" />
 </c:otherwise>
</c:choose>

<%
      } //end while(!done)
      }//end if
%>

<table border='0' cellpadding='0' cellspacing='0' width='99%' />
 <tr>
  <td colspan='2'><img src='<c:url value="/images/blank.gif"/>'
   width='1' height='15'></td>
 </tr>
 <tr>
  <td colspan='2'><img src='<c:url value="/images/blank.gif"/>'
   width='1' height='15'></td>
 </tr>
 <tr>
  <td colspan='2'><img src='<c:url value="/images/blank.gif"/>'
   width='1' height='15'></td>
 </tr>
 <tr>
  <td colspan='2'><img src='<c:url value="/images/blank.gif"/>'
   width='1' height='15'></td>
 </tr>
 <tr>
  <td colspan='2'><img src='<c:url value="/images/blank.gif"/>'
   width='1' height='15'></td>
 </tr>
 <tr>
  <td colspan='2'><img src='<c:url value="/images/blank.gif"/>'
   width='1' height='15'></td>
 </tr>
 <tr>
  <td colspan='2'><img src='<c:url value="/images/blank.gif"/>'
   width='1' height='15'></td>
 </tr>
 <tr>
  <td colspan='2'><img src='<c:url value="/images/blank.gif"/>'
   width='1' height='15'></td>
 </tr>
 <tr>
  <td colspan='2'><img src='<c:url value="/images/blank.gif"/>'
   width='1' height='15'></td>
 </tr>
 <tr>
  <td colspan='2'><img src='<c:url value="/images/blank.gif"/>'
   width='1' height='15'></td>
 </tr>
 <tr>
  <td colspan='2'><img src='<c:url value="/images/blank.gif"/>'
   width='1' height='15'></td>
 </tr>
 <tr>
  <td colspan='2'><img src='<c:url value="/images/blank.gif"/>'
   width='1' height='15'></td>
 </tr>
 <tr>
  <td colspan='2'><img src='<c:url value="/images/blank.gif"/>'
   width='1' height='15'></td>
 </tr>
 <tr>
  <td colspan='2'><img src='<c:url value="/images/blank.gif"/>'
   width='1' height='15'></td>
 </tr>
 <tr>
  <td colspan='2'><img src='<c:url value="/images/blank.gif"/>'
   width='1' height='15'></td>
 </tr>
 <tr>
  <td colspan='2'><img src='<c:url value="/images/blank.gif"/>'
   width='1' height='15'></td>
 </tr>
 <tr>
  <td colspan='2'><img src='<c:url value="/images/blank.gif"/>'
   width='1' height='15'></td>
 </tr>
 <tr>
  <td colspan='2'><img src='<c:url value="/images/blank.gif"/>'
   width='1' height='15'></td>
 </tr>
 <tr>
  <td colspan='2'><img src='<c:url value="/images/blank.gif"/>'
   width='1' height='15'></td>
 </tr>
 <tr>
  <td colspan='2'><img src='<c:url value="/images/blank.gif"/>'
   width='1' height='15'></td>
 </tr>
 <tr>
  <td colspan='2'><img src='<c:url value="/images/blank.gif"/>'
   width='1' height='15'></td>
 </tr>
 <tr>
  <td colspan='2'><img src='<c:url value="/images/blank.gif"/>'
   width='1' height='15'></td>
 </tr>
 <tr>
  <td colspan='2'><img src='<c:url value="/images/blank.gif"/>'
   width='1' height='15'></td>
 </tr>
</table>


</body>
<%
      SQLFunctions.closeResultSet(rs);
      SQLFunctions.closePreparedStatement(prep);
%>
</HTML>
