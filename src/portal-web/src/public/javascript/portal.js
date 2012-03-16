
if($.cookie('range') == null){
	$.cookie('range', '10.minutes');
}

var renew = "";
var selected = "";

function getSelected(){
	selected = "";
	$(".selectedExchange:checked").each(function(){
		selected = selected + this.name + ","
	});
	return selected;
}

function getMerge(){
	$.ajax({
	        type: "POST",
	        async: false,
	        headers: {accept: 'text/javascript'},
	        url: "/merge",
	        data: "selected=" + selected,
	        success: function(res){
	                eval(res);
	        },
	        error: function(xhr,text,err){
	                alert(err);
	        }
	});
}

function setMergeRange(range){
	$.cookie('range', range);
	$('.range-button').css('background-color', 'white');
	$('.range-button').each(function(){
		if(this.value == range){
			$(this).css('background-color', 'black');
		}
	});
}

function merge(){
	selected = getSelected();
	if(selected.length == 0){
		alert('No exchange selected!');
		return;
	}
	
	$('#rrdmodal > .modal-header > h5').empty().append('<span>Merged Result</span>&nbsp;&nbsp;&nbsp')
		.append('<input class="range-button" type=button value="10.minutes" onclick="setMergeRange(this.value); getMerge();">&nbsp;')
		.append('<input class="range-button" type=button value="1.hour" onclick="setMergeRange(this.value); getMerge();">&nbsp;')
		.append('<input class="range-button" type=button value="6.hour" onclick="setMergeRange(this.value); getMerge();">&nbsp;')
		.append('<input class="range-button" type=button value="1.day" onclick="setMergeRange(this.value); getMerge();">&nbsp;')
		.append('<input class="range-button" type=button value="1.week" onclick="setMergeRange(this.value); getMerge();">&nbsp;')
		.append('<input class="range-button" type=button value="1.month" onclick="setMergeRange(this.value); getMerge();">&nbsp;')

	$("#rrdmodal > .modal-body")
		.empty()
		.append("<h1>Merging...</h1>");
	
	$("#rrdmodal").modal({
		keyboard: true,
		backdrop: true,
		show: true,
	});
	
	setMergeRange($.cookie('range'));
	
	$.ajax({
		type: "POST",
		headers: {accept: 'text/javascript'},
		url: "/merge",
		data: "selected=" + selected,
		success: function(res){
			eval(res);
			renew = "merge";
			renew_merge();
		},
		error: function(xhr, text, err){
			alert(err);
		},
	});
}
