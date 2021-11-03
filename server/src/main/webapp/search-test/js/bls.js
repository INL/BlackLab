//var BLS_URL = location.href.indexOf("localhost") >= 0 ? "/blacklab-server/" : "/bls/";  // TEST
var BLS_URL = "/blacklab-server/";

function errorFunc(err) {
	alert(err.message);
}

// Called to perform the search, update the
// total count, and call a success function.
function performAjaxSearchRequest(url, param, successFunc, cache, unavailableHandler) {
	$("#wait").show();
	$("#output").hide();
	
	// Remove empty parameters, BLS ignores these anyway
	for (var name in param) {
        if (param[name] === null || param[name] === undefined || param[name] === '') {
            delete param[name];
        }
    }
	
	$.ajax({
		url,
		data: param,
		dataType: "json",
		cache: !!cache,
		success: function(response) {
			$("#wait").hide();
			if (successFunc)
				successFunc(response);
		},
		error: function(jqXHR, textStatus, errorThrown) {
			$("#wait").hide();
			var data = jqXHR.responseJSON;
			if (data && data.error) {
				if (data.error.code == "SERVER_BUSY" && unavailableHandler) {
					unavailableHandler();
				} else {
					errorFunc(data.error);
				}
			} else {
				errorFunc({
					"code" : "WEBSERVICE_ERROR",
					"message" : "Error contacting webservice: " + textStatus
							+ "; " + errorThrown
				});
			}
		}
	});
}

