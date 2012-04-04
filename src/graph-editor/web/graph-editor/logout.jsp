<% 
request.getSession().invalidate();
%>
<script>
location.href = "http://logout:logout@" + location.href.split("://")[1].split("/")[0] + "<%= getServletConfig().getInitParameter("pathprefix") + "/webapp/graph-editor/graph" %>";
</script>