<%@ page trimDirectiveWhitespaces="true"%>
<%@ page contentType="text/html; charset=UTF-8"%>
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c"%>
<%@ taglib prefix="fn" uri="http://java.sun.com/jsp/jstl/functions"%>
<!DOCTYPE HTML PUBLIC "-//W3C//DTD HTML 4.01//EN" "http://www.w3.org/TR/html4/strict.dtd">
<html>
<head>
<meta http-equiv="Content-type" content="text/html;charset=UTF-8">
<title>Processor: ${it.name}</title>
<link rel="stylesheet" type="text/css" href="/static/canviz/canviz.css" />
<link rel="stylesheet" type="text/css" href="/static/jquery/jquery-ui/css/ui-lightness/jquery-ui-1.8.16.custom.css" />
<link rel="stylesheet" type="text/css" href="/static/graph-editor.css" />

<script type="text/javascript" src="/static/jquery/jquery.js"></script>
<script type="text/javascript">
  jQuery.noConflict();
</script>
<script type="text/javascript" src="/static/jquery/jquery-ui/js/jquery-ui-1.8.16.custom.min.js"></script>
<script type="text/javascript" src="/static/canviz/prototype/prototype.js"></script>
<script type="text/javascript" src="/static/canviz/canviz.js"></script>
<script type="text/javascript" src="/static/canviz/path/path.js"></script>

<script type="text/javascript">
	jQuery(document).ready(function() {
	});
</script>

</head>
<body class="ui-widget" style="text-align: center">
<%@ include file="/static/header.html" %>
	<h1>Exchange: ${it.name}</h1>
	<div>
		<input type=button value="Block ${it.name}">
	</div>
</body>
</html>
	