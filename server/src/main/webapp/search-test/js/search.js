$(document).ready(function() {


    $('textarea,input').keydown(function (e) {
      if (e.ctrlKey && e.keyCode == 13) {
        $("#zoekForm").submit();
      }
    });
	
	$("#zoekForm").submit(function(event) {
		event.preventDefault();
		
		// Get parameters from form inputs
        let param = {};
		const paramsFromFields = [
		  'patt', 'filter', 'first', 'number', 'wordsaroundhit', 'sort', 'group', 'usecache', 'explain', 'waitfortotal'
		];
		paramsFromFields.forEach(name => param[name] = $(`#${name}`).val());
		
		// Execute search
		$("#wait").text("Executing search...");
		const url = $("#corpusUrl").val() + "/hits";
		performAjaxSearchRequest(url, param, function (results) {
		
		  // Show results
          $("#wait").hide();
          delete results.summary.docFields;
          delete results.summary.metadataFieldDisplayNames;
		  $("#output").text("Results summary: (use browser tools to see full response)\n\n" + JSON.stringify(results.summary, null, 2)).show();
		  
		}, false, null);
	});
});
