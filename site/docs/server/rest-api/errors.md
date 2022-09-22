# Error and status responses

BLS can return these error and status codes. The associated human-readable message is informational only and can be shown to the user if you want; note though that the precise wording may change in future versions. The codes in the left column will not change and may be used to show your own custom messages (e.g. translations).

Operations that do not return status or error codes and messages (which is all succesful retrieval operations) will always set the HTTP status to "200 OK".

<table>
	<tr>
		<th style="text-align:left;">HTTP status </th>
		<th style="text-align:left;">Error code     </th>
		<th style="text-align:left;">Error message</th>
	</tr>
	<tr>
		<td>200 OK </td>
		<td>(no code) </td>
		<td>(no message, just search results)</td>
	</tr>
	<tr>
		<td>200 OK </td>
		<td>SUCCESS </td>
		<td>Index deleted succesfully.</td>
	</tr>
	<tr>
		<td>201 Created </td>
		<td>SUCCESS </td>
		<td>Index created succesfully.</td>
	</tr>
	<tr>
		<td>202 Accepted </td>
		<td>SUCCESS </td>
		<td>Documents uploaded succesfully; indexing started.</td>
	</tr>
	<tr>
		<td>400 Bad Request </td>
		<td>UNKNOWN_OPERATION </td>
		<td>Unknown operation. Check your URL.</td>
	</tr>
	<tr>
		<td>400 Bad Request </td>
		<td>NO_DOC_ID </td>
		<td>Specify document pid.</td>
	</tr>
	<tr>
		<td>400 Bad Request </td>
		<td>NO_FILTER_GIVEN </td>
		<td>Document filter required. Please specify &#39;filter&#39; parameter.</td>
	</tr>
	<tr>
		<td>400 Bad Request </td>
		<td>FILTER_SYNTAX_ERROR </td>
		<td>Error parsing FILTERLANG filter query: ERRORMESSAGE<br/><i>(<b>NOTE:</b> see <a href="https://lucene.apache.org/core/8_8_1/queryparser/org/apache/lucene/queryparser/classic/package-summary.html#package.description">Lucene query syntax</a>)</i></td>
	</tr>
	<tr>
		<td>400 Bad Request </td>
		<td>UNKNOWN_FILTER_LANG </td>
		<td>Unknown filter language &#39; FILTERLANG &#39;. Supported: SUPPORTED_LANGS.</td>
	</tr>
	<tr>
		<td>400 Bad Request </td>
		<td>NO_PATTERN_GIVEN </td>
		<td>Text search pattern required. Please specify &#39;patt&#39; parameter.</td>
	</tr>
	<tr>
		<td>400 Bad Request </td>
		<td>PATT_SYNTAX_ERROR </td>
		<td>Syntax error in PATTLANG pattern: ERRORMESSAGE</td>
	</tr>
	<tr>
		<td>400 Bad Request </td>
		<td>UNKNOWN_PATT_LANG </td>
		<td>Unknown pattern language &#39;PATTLANG&#39;. Supported: SUPPORTED_LANGS.</td>
	</tr>
	<tr>
		<td>400 Bad Request </td>
		<td>UNKNOWN_GROUP_PROPERTY </td>
		<td>Unknown group property &#39;NAME&#39;.</td>
	</tr>
	<tr>
		<td>400 Bad Request </td>
		<td>UNKNOWN_SORT_PROPERTY </td>
		<td>Unknown sort property &#39;NAME&#39;.</td>
	</tr>
	<tr>
		<td>400 Bad Request </td>
		<td>ERROR_IN_GROUP_VALUE </td>
		<td>Parameter &#39;viewgroup&#39; has an illegal value: GROUPID /<br/>
		    Parameter 'viewgroup' specified, but required 'group' parameter is missing.</td>
	</tr>
	<tr>
		<td>400 Bad Request </td>
		<td>GROUP_NOT_FOUND </td>
		<td>Group not found: GROUPID</td>
	</tr>
	<tr>
		<td>400 Bad Request </td>
		<td>REGEXP_TOO_LARGE</td>
		<td>Regular expression too large. (NOTE: maximum size depends on Java stack size)</td>
	</tr>
	<tr>
		<td>400 Bad Request </td>
		<td>JSONP_ILLEGAL_CALLBACK </td>
		<td>Illegal JSONP callback function name. Must be a valid Javascript name.</td>
	</tr>
	<tr>
		<td>400 Bad Request </td>
		<td>SNIPPET_TOO_LARGE </td>
		<td>Snippet too large. Maximum size for a snippet is MAXSIZE words.</td>
	</tr>
	<tr>
		<td>400 Bad Request </td>
		<td>ILLEGAL_BOUNDARIES </td>
		<td>Illegal word boundaries specified. Please check parameters.</td>
	</tr>
	<tr>
		<td>400 Bad Request </td>
		<td>HIT_NUMBER_OUT_OF_RANGE </td>
		<td>Non-existent hit number specified.</td>
	</tr>
	<tr>
		<td>400 Bad Request </td>
		<td>CANNOT_CREATE_INDEX </td>
		<td>Could not create index. REASON</td>
	</tr>
	<tr>
		<td>400 Bad Request </td>
		<td>INDEX_ALREADY_EXISTS </td>
		<td>Could not create index. Index already exists.</td>
	</tr>
	<tr>
		<td>400 Bad Request </td>
		<td>ILLEGAL_INDEX_NAME </td>
		<td>Illegal index name (only word characters, underscore and dash allowed): INDEXNAME</td>
	</tr>
	<tr>
		<td>400 Bad Request </td>
		<td>CANNOT_UPLOAD_FILE </td>
		<td>Cannot upload file. REASON</td>
	</tr>
	<tr>
		<td>400 Bad Request </td>
		<td>INDEX_ERROR </td>
		<td>An error occurred during indexing: MESSAGE</td>
	</tr>
	<tr>
		<td>401 Unauthorized </td>
		<td>NOT_AUTHORIZED </td>
		<td>Unauthorized operation. REASON</td>
	</tr>
	<tr>
		<td>403 Forbidden </td>
		<td>FORBIDDEN_REQUEST </td>
		<td>Forbidden request. REASON</td>
	</tr>
	<tr>
		<td>405 Method Not Allowed </td>
		<td>ILLEGAL_REQUEST </td>
		<td>Illegal GET/POST/PUT/DELETE request. REASON</td>
	</tr>
	<tr>
		<td>404 Not Found </td>
		<td>CANNOT_OPEN_INDEX </td>
		<td>Could not open index &#39;NAME&#39;. Please check the name.</td>
	</tr>
	<tr>
		<td>404 Not Found </td>
		<td>DOC_NOT_FOUND </td>
		<td>Document with pid &#39;PID&#39; not found.</td>
	</tr>
	<tr>
		<td>409 Conflict </td>
		<td>INDEX_UNAVAILABLE </td>
		<td>The index &#39;INDEXNAME&#39; is not available right now. Status: STATUS</td>
	</tr>
	<tr>
		<td>429 Too Many Requests </td>
		<td>TOO_MANY_JOBS </td>
		<td>You already have too many running searches. Please wait for some previous searches to complete before starting new ones.</td>
	</tr>
	<tr>
		<td>500 Internal Server Error </td>
		<td>INTERNAL_ERROR </td>
		<td>An internal error occurred. Please contact the administrator.</td>
	</tr>
	<tr>
		<td>503 Service Unavailable </td>
		<td>SERVER_BUSY </td>
		<td>The server is under heavy load right now. Please try again later.</td>
	</tr>
	<tr>
		<td>503 Service Unavailable </td>
		<td>SEARCH_TIMED_OUT </td>
		<td>Search took too long, cancelled.</td>
	</tr>
</table>
