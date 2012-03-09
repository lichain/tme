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
	function exchange_onclick(exchange){
		jQuery('<div title="Exchange Editor"></div>')
		.append('<input type=button value="Remove ' + exchange + '" onclick="remove_input(\'' + exchange + '\')">')
		.append('<iframe onload="jQuery(this).contents().find(\'.ui-widget-header\').remove();"  id="dialog" > </iframe>').dialog({
			close: function(){location.reload();},
			modal: true,
			width: window.innerWidth * 0.5, 
			height: window.innerHeight * 0.5
		});
		
		jQuery('#dialog').css("width", "100%");
		jQuery('#dialog').css("height", "90%");
		jQuery('#dialog').attr("src", "/webapp/graph-editor/exchange/" + exchange);
	}
	
	function remove_output(name){
		if(confirm('Are you sure to remove output ' + name + ' ?')){
		jQuery.ajax({
			type: "DELETE",
			async: false,
			url: "${it.name}/output/" + name,
			success: function(){
			},
			error: function(xhr,text,err){
				alert(err);					
			}
		});
		location.reload();
		}
	}
	
	function add_output(){
	    name = prompt('Enter output name: ');
	    if(name == null || name == ""){
	    	return;
	    }
		jQuery.ajax({
			type: "PUT",
			async: false,
			url: "${it.name}/output/" + name,
			success: function(){
			},
			error: function(xhr,text,err){
				alert(err);					
			}
		});
		location.reload();
	}
	
	function remove_input(name){
		if(confirm('Are you sure to remove input ' + name + ' ?')){
		jQuery.ajax({
			type: "DELETE",
			async: false,
			url: "${it.name}/input/" + name,
			success: function(){
			},
			error: function(xhr,text,err){
				alert(err);					
			}
		});
		location.reload();
		}
	}
	
	function add_input(){
	    name = prompt('Enter input name: ');
	    if(name == null || name == ""){
	    	return;
	    }
		jQuery.ajax({
			type: "PUT",
			async: false,
			url: "${it.name}/input/" + name,
			success: function(){
			},
			error: function(xhr,text,err){
				alert(err);					
			}
		});
		location.reload();
	}
	
	jQuery(document).ready(function() {
		canviz = new Canviz('graph_container');
		
		jQuery.ajax({
			type: "GET",
			async: true,
			url: "",
			headers: { 
        		Accept : "application/x-xdot",
	        },			
			success: function(data){
   			 	canviz.parse(data);
  			},
			error: function(xhr,text,err){
				alert(xhr.responseText);					
			}
		});		
	});
</script>

</head>
<body class="ui-widget" style="text-align: center">
<%@ include file="../header.jsp" %>
	<h1>Processor: ${it.name}</h1>

	<div id="graph_container" style="text-align: left"></div>


</body>
</html>
