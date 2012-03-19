
var refreshScript = 'location.reload();';
function refresh(){
	eval(refreshScript);
	setTimeout('refresh();', 30000);
}

if($.cookie('range') == null){
	$.cookie('range', '10.minutes');
}

function clearSelected(){
	$('#filter')[0].value = '';
	$.cookie('filter', '');
	$.uiTableFilter($('table#exchanges'), '');
	$('.selectedExchange').each(function(){this.checked = false});
}

function invertSelected(){
	filter=$('#filter')[0].value;
	$(".selectedExchange").each(function(){
		if(filter.length == 0 || this.name.match(filter) != null){
			this.checked = !this.checked;
		}
	});
}

function getSelected(){
	var selected = "";
	$(".selectedExchange:checked").each(function(){
		selected = selected + this.name + ","
	});
	return selected;
}

function setRange(range){
	$.cookie('range', range);
	$('.range-button').css('background-color', 'white');
	$('.range-button').each(function(){
		if(this.value == range){
			$(this).css('background-color', 'black');
		}
	});
}

function postMerge(){
	$.ajax({
		type: 'POST',
		url: '/merge',
		data: 'selected=' + getSelected(),
		dataType: 'script',
		success: function(){
			refreshScript = 'postMerge()';
		},
	});
}

function openModal(title, initContent, updateMethod){
	$('#rrdmodal > .modal-header > h5').empty().append('<span>' + title + '</span><br>')
		.append('<input class="range-button" type=button value="10.minutes" onclick="setRange(this.value);' + updateMethod + '">&nbsp;')
		.append('<input class="range-button" type=button value="1.hour" onclick="setRange(this.value);' + updateMethod + '">&nbsp;')
		.append('<input class="range-button" type=button value="6.hour" onclick="setRange(this.value);' + updateMethod + '">&nbsp;')
		.append('<input class="range-button" type=button value="1.day" onclick="setRange(this.value);' + updateMethod + '">&nbsp;')
		.append('<input class="range-button" type=button value="1.week" onclick="setRange(this.value);' + updateMethod + '">&nbsp;')
		.append('<input class="range-button" type=button value="1.month" onclick="setRange(this.value);' + updateMethod + '">&nbsp;');

	$('#rrdmodal > .modal-body')
		.empty()
		.append('<h1>' + initContent + '</h1>');

	$('#rrdmodal').css('max-height', window.innerHeight - 90 + 'px').css('top', '350px');

	$('#rrdmodal').modal({
		keyboard: true,
		backdrop: true,
		show: true,
	});
}

function merge(){
	if(getSelected().length == 0){
		alert('No exchange selected!');
		return;
	}
	
	openModal('Merged Result', 'Merging...', 'postMerge();');
	setRange($.cookie('range'));
	postMerge();
}

function getExchangeRequest(name){
	$.getScript('/exchanges/' + name, function(){
		refreshScript = "getExchangeRequest('" + name + "')";
	});
}

function getExchange(exchange){
	name = exchange.attr('rrd');
	openModal(name, 'Fetching...', "getExchangeRequest('" + name + "');");
	setRange($.cookie('range'));
	getExchangeRequest(name);
}

$(document).ready(function() {
	$('#rrdmodal').on('hidden', function () {
		refreshScript = 'location.reload();';
	});
	setTimeout('refresh();', 30000);

	$('.rrdmodal-trigger').click(function() {
		getExchange($(this));
	});

	$('a[rel=popover]').popover({
        offset: 10
    }).click(function(e) {
        e.preventDefault()
    });
});
