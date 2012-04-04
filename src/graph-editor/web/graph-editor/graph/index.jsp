<%@ page trimDirectiveWhitespaces="true"%>
<%@ page contentType="text/html; charset=UTF-8"%>
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c"%>
<%@ taglib prefix="fn" uri="http://java.sun.com/jsp/jstl/functions"%>
<!DOCTYPE HTML PUBLIC "-//W3C//DTD HTML 4.01//EN" "http://www.w3.org/TR/html4/strict.dtd">
<c:set var="prefix"><%= getServletConfig().getInitParameter("pathprefix") %></c:set>
<html>
<head>
<meta http-equiv="Content-type" content="text/html;charset=UTF-8">
<title>Graphs</title>
<link rel="stylesheet" type="text/css" href="${prefix}/static/canviz/canviz.css" />
<link rel="stylesheet" type="text/css" href="${prefix}/static/jquery/jquery-ui/css/ui-lightness/jquery-ui-1.8.16.custom.css" />
<link rel="stylesheet" type="text/css" href="${prefix}/static/graph-editor.css" />

<script type="text/javascript" src="${prefix}/static/jquery/jquery.js"></script>
<script type="text/javascript">
  jQuery.noConflict();
</script>
<script type="text/javascript" src="${prefix}/static/jquery/jquery.cookie.js"></script>
<script type="text/javascript" src="${prefix}/static/jquery/jquery.uitablefilter.js"></script>
<script type="text/javascript" src="${prefix}/static/jquery/jquery-ui/js/jquery-ui-1.8.16.custom.min.js"></script>
<script type="text/javascript" src="${prefix}/static/canviz/prototype/prototype.js"></script>
<script type="text/javascript" src="${prefix}/static/canviz/canviz.js"></script>
<script type="text/javascript" src="${prefix}/static/canviz/path/path.js"></script>

<script type="text/javascript">
	if(jQuery.cookie('graph_editor_scale') == null){
		jQuery.cookie('graph_editor_scale', '1.0', {path: '/' });
	}

	function add_graph(){
		name = prompt('Enter graph name: ');
	    if(name == null || name == ""){
	    	return;
	    }
		jQuery.ajax({
			type: "PUT",
			async: false,
			url: '${prefix}/webapp/graph-editor/graph/' + name,
			success: function(){
				location.href = '${prefix}/webapp/graph-editor/graph/' + name;
			},
			error: function(xhr,text,err){
				alert(err);					
			}
		});
	}
	
	function remove_graph(){
		var selected=[];
		jQuery('.graphSelected:checked').each(function(){selected.push(this.value)});
		if(!confirm('Are you sure to remove ' + selected)){
			return;
		}
		
		jQuery('.graphSelected:checked').each(function(){
			graph = this.value;
			jQuery.ajax({
				type: "DELETE",
				async: false,
				url: "${prefix}/webapp/graph-editor/graph/" + graph,
				error: function(xhr,text,err){
					alert(err);					
				}
			});
		});
		location.reload();
	}
	
	function generate_view(){
		var selected=[];
		jQuery('.graphSelected:checked').each(function(){selected.push(this.value)});
		if(selected.length == 0){
			alert('Nothing selected!');
			return;
		}
		
		jQuery.ajax({
			type: "GET",
			async: true,
			headers: { 
        		Accept : "application/x-xdot",
	        },
			url: "?graphSelected=" + selected,
			success: function(data){
				canviz.setScale(parseFloat(jQuery.cookie('graph_editor_scale')));
   			 	canviz.parse(data);
   			 	
				$('graph_container').show();
			},
			error: function(xhr,text,err){
				alert(err);					
			}
		});
	}
	
	function download_image(){
		var selected=[];
		jQuery('.graphSelected:checked').each(function(){selected.push(this.value)});
		if(selected.length == 0){
			alert('Nothing selected!');
			return;
		}
		
		jQuery('<img src title="Graphs" id="dialog" />')
			.dialog({modal: true, width: window.innerWidth * 0.75, close: function(){this.remove();}});
		
		jQuery('#dialog').css("width", "");
		jQuery('#dialog').css("height", "");
		jQuery('#dialog').css("max-width", window.innerWidth * 0.75);
		jQuery('#dialog').css("max-height", window.innerHeight * 0.75);
		jQuery('#dialog').attr("src", "?graphSelected="+selected);
		jQuery('.ui-dialog').css("top", '100px');
		jQuery('.ui-dialog').css("height",  (window.innerHeight - 200) + 'px');
	}
	
	jQuery(document).ready(function() {		
		canviz = new Canviz('graph_container');
		$('graph_container').hide();
		
		jQuery("#filter").keyup(function() {
			jQuery.uiTableFilter(jQuery("#graphList"), this.value);
		});
	});

	function set_graph_scale() {
		canviz.setScale(parseFloat(jQuery.cookie('graph_editor_scale')));
		canviz.draw();
	}

	function change_scale(inc) {
		if(parseFloat(jQuery.cookie('graph_editor_scale')) + inc <= 0.4){
			return;
		}
		jQuery.cookie('graph_editor_scale', parseFloat(jQuery.cookie('graph_editor_scale')) + inc, {path: '/' });
		set_graph_scale();
	}
</script>

</head>
<body class="ui-widget">
	<%@ include file="../header.jsp"%>

	<div class="ui-widget-content">
		<br>
		<table id="graphList" border=1 align=center>
			<thead>
				<tr>
					<th><input type="checkbox"
						onclick="jQuery('input:checkbox:visible').attr('checked',this.checked); ">
					</th>
					<th>Filter: <input id="filter" type="text">
					</th>
				</tr>
			</thead>

			<tbody>
				<c:forEach var="graph" items="${it}">
					<tr>
						<td><input type=checkbox class="graphSelected"
							value="${graph}"></td>
						<td><a href="${prefix}/webapp/graph-editor/graph/${graph}">${graph}</a>
						</td>
					</tr>

				</c:forEach>
			</tbody>
		</table>
		<br> 
		<input type=button value="Add new graph" onclick="add_graph();">
		<input type=button value="Delete graph" onclick="remove_graph();">
		<br>
		<input type="button" value="Combined view" onclick="generate_view();" />
		<input type="button" value="Download as image" onclick="download_image();" />

		<hr />		
	</div>
	<div id="graph_container" style="text-align: left">
	<input type="button" style="margin-left: 3px; margin-top: 3px;" class="little_button" value="+" onclick="change_scale(0.2)" />
	<input type="button" style="margin-left: 3px; margin-top: 3px;" class="little_button" value="-" onclick="change_scale(-0.2)" /><br>
	</div>
</body>
</html>
