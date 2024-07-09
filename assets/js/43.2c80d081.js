(window.webpackJsonp=window.webpackJsonp||[]).push([[43],{318:function(e,t,a){"use strict";a.r(t);var s=a(13),r=Object(s.a)({},(function(){var e=this,t=e._self._c;return t("ContentSlotsDistributor",{attrs:{"slot-key":e.$parent.slotKey}},[t("h1",{attrs:{id:"using-blacklab-server"}},[t("a",{staticClass:"header-anchor",attrs:{href:"#using-blacklab-server"}},[e._v("#")]),e._v(" Using BlackLab Server")]),e._v(" "),t("div",{staticClass:"custom-block warning"},[t("p",{staticClass:"custom-block-title"},[e._v("Work in progress")]),e._v(" "),t("p",[e._v("This will become a guided introduction to BlackLab Server. See the "),t("RouterLink",{attrs:{to:"/server/rest-api/"}},[e._v("REST API reference")]),e._v(" for details about each endpoint.")],1)]),e._v(" "),t("h2",{attrs:{id:"overview"}},[t("a",{staticClass:"header-anchor",attrs:{href:"#overview"}},[e._v("#")]),e._v(" Overview")]),e._v(" "),t("h3",{attrs:{id:"json-xml-or-csv"}},[t("a",{staticClass:"header-anchor",attrs:{href:"#json-xml-or-csv"}},[e._v("#")]),e._v(" JSON, XML or CSV?")]),e._v(" "),t("p",[e._v("The webservice answers in JSON or XML. Selection of the desired output format can be done two ways:")]),e._v(" "),t("ul",[t("li",[e._v("by passing the HTTP header "),t("code",[e._v("Accept")]),e._v(" with the value "),t("code",[e._v("application/json")]),e._v(", "),t("code",[e._v("application/xml")]),e._v(" or "),t("code",[e._v("text/csv")])]),e._v(" "),t("li",[e._v("by passing an extra parameter "),t("code",[e._v("outputformat")]),e._v(" with the value "),t("code",[e._v("json")]),e._v(", "),t("code",[e._v("xml")]),e._v(" or "),t("code",[e._v("csv")])])]),e._v(" "),t("p",[e._v("If both are specified, the parameter has precedence.")]),e._v(" "),t("p",[e._v("We'll usually use JSON in our examples.")]),e._v(" "),t("h3",{attrs:{id:"running-results-count"}},[t("a",{staticClass:"header-anchor",attrs:{href:"#running-results-count"}},[e._v("#")]),e._v(" Running results count")]),e._v(" "),t("p",[e._v("BlackLab Server is mostly stateless: a particular URL will always result in the same response. An exception to this is the running result count. When you're requesting a page of results, and there are more results to the query, BlackLab Server will retrieve these results in the background. It will report how many results it has retrieved and whether it has finished or is still retrieving.")]),e._v(" "),t("p",[e._v("A note about retrieving versus counting. BLS has two limits for processing results: maximum number of hits to retrieve/process and maximum number of hits to count. Retrieving or processing hits means the hit is stored and will appear on the results page, is sorted, grouped, faceted, etc. If the retrieval limit is reached, BLS will still keep counting hits but will no longer store them.")]),e._v(" "),t("h2",{attrs:{id:"examples"}},[t("a",{staticClass:"header-anchor",attrs:{href:"#examples"}},[e._v("#")]),e._v(" Examples")]),e._v(" "),t("p",[e._v("There's code examples of using BlackLab Server from "),t("RouterLink",{attrs:{to:"/server/from-different-languages.html"}},[e._v("a number of different programming languages")]),e._v(".")],1),e._v(" "),t("p",[e._v("Below are examples of individual requests to BlackLab Server.")]),e._v(" "),t("p",[t("strong",[e._v("NOTE:")]),e._v(" for clarity, double quotes have not been URL-encoded.")]),e._v(" "),t("h4",{attrs:{id:"searches"}},[t("a",{staticClass:"header-anchor",attrs:{href:"#searches"}},[e._v("#")]),e._v(" Searches")]),e._v(" "),t("p",[e._v("All occurrences of “test” in the “opensonar” corpus (CorpusQL query)")]),e._v(" "),t("div",{staticClass:"language- extra-class"},[t("pre",[t("code",[e._v('http://blacklab.ivdnt.org/blacklab-server/opensonar/hits?patt="test"\n')])])]),t("p",[e._v("All documents having “guide” in the title and “test” in the contents, sorted by author and date, results 61-90")]),e._v(" "),t("div",{staticClass:"language- extra-class"},[t("pre",[t("code",[e._v('http://blacklab.ivdnt.org/blacklab-server/opensonar/docs?filter=title:guide&patt="test"& sort=field:author,field:date&first=61&number=30\n')])])]),t("p",[e._v("Occurrences of “test”, grouped by the word left of each hit")]),e._v(" "),t("div",{staticClass:"language- extra-class"},[t("pre",[t("code",[e._v('http://blacklab.ivdnt.org/blacklab-server/opensonar/hits?patt="test"&group=wordleft\n')])])]),t("p",[e._v("Documents containing “test”, grouped by author")]),e._v(" "),t("div",{staticClass:"language- extra-class"},[t("pre",[t("code",[e._v('http://blacklab.ivdnt.org/blacklab-server/opensonar/docs?patt="test"&group=field:author\n')])])]),t("p",[e._v("Larger snippet around a hit:")]),e._v(" "),t("div",{staticClass:"language- extra-class"},[t("pre",[t("code",[e._v("http://blacklab.ivdnt.org/blacklab-server/opensonar/docs/0345391802/snippet?hitstart=120&hitend=121&context=50\n")])])]),t("h4",{attrs:{id:"information-about-a-document"}},[t("a",{staticClass:"header-anchor",attrs:{href:"#information-about-a-document"}},[e._v("#")]),e._v(" Information about a document")]),e._v(" "),t("p",[e._v("Metadata of document with specific PID")]),e._v(" "),t("div",{staticClass:"language- extra-class"},[t("pre",[t("code",[e._v("http://blacklab.ivdnt.org/blacklab-server/opensonar/docs/0345391802\n")])])]),t("p",[e._v("The entire original document")]),e._v(" "),t("div",{staticClass:"language- extra-class"},[t("pre",[t("code",[e._v("http://blacklab.ivdnt.org/blacklab-server/opensonar/docs/0345391802/contents\n")])])]),t("p",[e._v("The entire document, with occurrences of “test” highlighted (with <hl/> tags)")]),e._v(" "),t("div",{staticClass:"language- extra-class"},[t("pre",[t("code",[e._v('http://blacklab.ivdnt.org/blacklab-server/opensonar/docs/0345391802/contents?patt="test"\n')])])]),t("p",[e._v("Part of the document (embedded in a "),t("code",[e._v("<blacklabResponse>")]),e._v(" root element; BlackLab makes sure the resulting XML is well-formed)")]),e._v(" "),t("p",[e._v("http://blacklab.ivdnt.org/blacklab-server/opensonar/docs/0345391802/contents?wordstart=1000&wordend=2000")]),e._v(" "),t("h4",{attrs:{id:"information-about-indices"}},[t("a",{staticClass:"header-anchor",attrs:{href:"#information-about-indices"}},[e._v("#")]),e._v(" Information about indices")]),e._v(" "),t("p",[e._v("Information about the webservice; list of available indices")]),e._v(" "),t("div",{staticClass:"language- extra-class"},[t("pre",[t("code",[e._v("http://blacklab.ivdnt.org/blacklab-server/ (trailing slash optional)\n")])])]),t("p",[e._v("Information about the “opensonar” corpus (structure, fields, (sub)annotations, human-readable names)")]),e._v(" "),t("div",{staticClass:"language- extra-class"},[t("pre",[t("code",[e._v("http://blacklab.ivdnt.org/blacklab-server/opensonar/ (trailing slash optional)\n")])])]),t("p",[e._v('Information about the “opensonar” corpus, include all values for "pos" annotation (listvalues is a comma-separated list of annotation names):')]),e._v(" "),t("div",{staticClass:"language- extra-class"},[t("pre",[t("code",[e._v("http://blacklab.ivdnt.org/blacklab-server/opensonar/?listvalues=pos\n")])])]),t("p",[e._v('Information about the “opensonar” corpus, include all values for "pos" annotation and any subannotations (listvalues may contain regexes):')]),e._v(" "),t("div",{staticClass:"language- extra-class"},[t("pre",[t("code",[e._v("http://blacklab.ivdnt.org/blacklab-server/opensonar/?listvalues=pos.*\n")])])]),t("p",[e._v("Autogenerated XSLT stylesheet for transforming whole documents (only available for configfile-based XML formats):")]),e._v(" "),t("div",{staticClass:"language- extra-class"},[t("pre",[t("code",[e._v("http://blacklab.ivdnt.org/blacklab-server/input-formats/folia/xslt\n")])])])])}),[],!1,null,null,null);t.default=r.exports}}]);