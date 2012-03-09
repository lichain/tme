<% 
request.getSession().invalidate();
%>
<script>
location.href = "http://logout:logout@" + location.href.split("://")[1].split("/")[0];
</script>