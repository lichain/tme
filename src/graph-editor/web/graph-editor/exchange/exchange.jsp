<%@ page trimDirectiveWhitespaces="true"%>
<%@ page contentType="text/html; charset=UTF-8"%>
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c"%>
<%@ taglib prefix="fn" uri="http://java.sun.com/jsp/jstl/functions"%>
<!DOCTYPE HTML PUBLIC "-//W3C//DTD HTML 4.01//EN" "http://www.w3.org/TR/html4/strict.dtd">
<c:set var="prefix"><%= getServletConfig().getInitParameter("pathprefix") %></c:set>
<html>
<head>
<meta http-equiv="Content-type" content="text/html;charset=UTF-8">
<title>Processor: ${it.name}</title>
<link rel="stylesheet" type="text/css" href="${prefix}/static/canviz/canviz.css" />
<link rel="stylesheet" type="text/css" href="${prefix}/static/jquery/jquery-ui/css/ui-lightness/jquery-ui-1.8.16.custom.css" />
<link rel="stylesheet" type="text/css" href="${prefix}/static/graph-editor.css" />

<script type="text/javascript" src="${prefix}/static/jquery/jquery.js"></script>
<script type="text/javascript">
  jQuery.noConflict();
</script>
<script type="text/javascript" src="${prefix}/static/jquery/jquery-ui/js/jquery-ui-1.8.16.custom.min.js"></script>
<script type="text/javascript" src="${prefix}/static/canviz/prototype/prototype.js"></script>
<script type="text/javascript" src="${prefix}/static/canviz/canviz.js"></script>
<script type="text/javascript" src="${prefix}/static/canviz/path/path.js"></script>

<script type="text/javascript">
	function block(){
		jQuery.ajax({
			type: "DELETE",
			async: false,
			url: "${prefix}/webapp/graph-editor/exchange/${it.name}/drop",
			success: function(){
			},
			error: function(xhr,text,err){
				alert(err);					
			}
		});
		location.reload();
	}
	
	function drop_newest(){
		jQuery.ajax({
			type: "PUT",
			async: false,
			url: "${prefix}/webapp/graph-editor/exchange/${it.name}/drop",
			contentType: "text/plain",
			data: "newest",
			success: function(){
			},
			error: function(xhr,text,err){
				alert(err);					
			}
		});
		location.reload();
	}
	
	function drop_oldest(){
		jQuery.ajax({
			type: "PUT",
			async: false,
			url: "${prefix}/webapp/graph-editor/exchange/${it.name}/drop",
			contentType: "text/plain",
			data: "oldest",
			success: function(){
			},
			error: function(xhr,text,err){
				alert(err);					
			}
		});
		location.reload();
	}
	
	function change_size(){
		size = prompt("Enter size limit (bytes):");
		jQuery.ajax({
			type: "PUT",
			async: false,
			url: "${prefix}/webapp/graph-editor/exchange/${it.name}/size_limit",
			contentType: "text/plain",
			data: size,
			success: function(){
			},
			error: function(xhr,text,err){
				alert(err);
			}
		});
		location.reload();
	}
	
	function change_count(){
		count = prompt("Enter count limit:");
		jQuery.ajax({
			type: "PUT",
			async: false,
			url: "${prefix}/webapp/graph-editor/exchange/${it.name}/count_limit",
			contentType: "text/plain",
			data: count,
			success: function(){
			},
			error: function(xhr,text,err){
				alert(err);
			}
		});
		location.reload();
	}

	jQuery(document).ready(function() {
	});
</script>

</head>
<body class="ui-widget" style="text-align: center">
<%@ include file="../header.jsp" %>
	<h1>Exchange: ${it.name}</h1>
	<div>
		<input type=button value="Make ${it.name} Blocking" onclick="block();"><br>
		<input type=button value="Make ${it.name} Drop Newest" onclick="drop_newest();"><br>
		<input type=button value="Make ${it.name} Drop Oldest" onclick="drop_oldest();"><br>
		<input type=button value="Change ${it.name} Size Limit" onclick="change_size();"><br>
		<input type=button value="Change ${it.name} Count Limit" onclick="change_count();"><br>
	</div>
</body>
</html>
	