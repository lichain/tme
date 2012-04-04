<%@ page trimDirectiveWhitespaces="true"%>
<%@ page contentType="text/html; charset=UTF-8"%>
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c"%>
<%@ taglib prefix="fn" uri="http://java.sun.com/jsp/jstl/functions"%>
<!DOCTYPE HTML PUBLIC "-//W3C//DTD HTML 4.01//EN" "http://www.w3.org/TR/html4/strict.dtd">
<c:set var="prefix"><%= getServletConfig().getInitParameter("pathprefix") %></c:set>
<html>
<head>
<meta http-equiv="Content-type" content="text/html;charset=UTF-8">
<title>Processors</title>
<link rel="stylesheet" type="text/css" href="${prefix}/static/canviz/canviz.css" />
<link rel="stylesheet" type="text/css" href="${prefix}/static/jquery/jquery-ui/css/ui-lightness/jquery-ui-1.8.16.custom.css" />
<link rel="stylesheet" type="text/css" href="${prefix}/static/graph-editor.css" />

<script type="text/javascript" src="${prefix}/static/jquery/jquery.js"></script>
<script type="text/javascript" src="${prefix}/static/jquery/jquery.uitablefilter.js"></script>
<script type="text/javascript">
  jQuery.noConflict();
</script>
<script type="text/javascript" src="${prefix}/static/jquery/jquery-ui/js/jquery-ui-1.8.16.custom.min.js"></script>
<script type="text/javascript" src="${prefix}/static/canviz/prototype/prototype.js"></script>
<script type="text/javascript" src="${prefix}/static/canviz/canviz.js"></script>
<script type="text/javascript" src="${prefix}/static/canviz/path/path.js"></script>

<script type="text/javascript">
	function add_processor(){
		name = prompt('Enter processor name: ');
	    if(name == null || name == ""){
	    	return;
	    }
		jQuery.ajax({
			type: "PUT",
			async: false,
			url: '${prefix}/webapp/graph-editor/processor/' + name,
			success: function(){
				location.href = '${prefix}/webapp/graph-editor/processor/' + name;
			},
			error: function(xhr,text,err){
				alert(err);					
			}
		});
	}
	
	function remove_processor(){
		var selected=[];
		jQuery('.processorSelected:checked').each(function(){selected.push(this.value)});
		if(!confirm('Are you sure to remove ' + selected)){
			return;
		}
	
		jQuery('.processorSelected:checked').each(function(){
			processor = this.value;
			jQuery.ajax({
				type: "DELETE",
				async: false,
				url: "${prefix}/webapp/graph-editor/processor/" + processor,
				error: function(xhr,text,err){
					alert(err);					
				}
			});
		});
		location.reload();
	}
	
	jQuery(document).ready(function() {
		jQuery("#filter").keyup(function() {
			jQuery.uiTableFilter(jQuery("#processorList"), this.value);
		});
	});
</script>

</head>
<body class="ui-widget" style="text-align: center">
	<%@ include file="../header.jsp"%>

	<div class="ui-widget-content">
		<br>
		<table id="processorList" border=1 align=center>
			<thead>
				<tr>
					<th><input type="checkbox"
						onclick="jQuery('input:checkbox:visible').attr('checked',this.checked); ">
					</th>
					<th>Filter: <input id="filter" type="text"></th>
				</tr>
			</thead>

			<tbody>
				<c:forEach var="processor" items="${it}">
					<tr>
						<td><input type=checkbox class="processorSelected"
							value="${processor}">
						</td>
						<td><a href="${prefix}/webapp/graph-editor/processor/${processor}">${processor}</a>
						</td>
					</tr>
				</c:forEach>
			</tbody>
		</table>
		<br> <input type=button value="Add new processor"
			onclick="add_processor();"> <input type=button
			value="Delete processor" onclick="remove_processor();">
	</div>
</body>
</html>
