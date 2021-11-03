const chai = require("chai");
const chaiHttp = require("chai-http");
const expect = chai.expect;
const should = chai.should();
chai.use(chaiHttp);

const constants = require('./constants');
const util = require('./util');

// Test that a hits search for a pattern returns the correct number of hits and docs,
// and optionally test that the first hit matches (either JSON or text).
function expectDocs(pattern, filter, numberOfHits, numberOfDocs, firstDocJsonIncludes) {
    const crit = [];
    if (pattern)
        crit.push(`pattern ${pattern}`);
    if (filter)
        crit.push(`filter ${filter}`);
    describe(`/docs with ${crit.join(' and ')}`, () => {
        it('should return expected response (#hits/docs, structure)', done => {
            chai
                .request(constants.SERVER_URL)
                .get('/test/docs')
                .query({
                    patt: pattern,
                    filter,
                    sort: "field:pid",
                    wordsaroundhit: 1
                })
                .set('Accept', 'application/json')
                .end((err, res) => {
                    expect(err).to.be.null;
                    expect(res).to.have.status(200);
                    const body = res.body;
                    expect(body).to.be.a("object").that.has.all.keys(
                        "summary",
                        "docs",
                    );

                const numberOfResultsInResponse = Math.min(constants.DEFAULT_WINDOW_SIZE, numberOfDocs);

                // Sanity-check summary
                const summary = body.summary;
                const searchParam = {
                    "indexname": "test",
                    "sort": "field:pid",
                    "wordsaroundhit": "1"
                };
                let expectedSummaryIncludes = {
                    searchParam,
                    "windowFirstResult": 0,
                    "requestedWindowSize": constants.DEFAULT_WINDOW_SIZE,
                    "actualWindowSize": numberOfResultsInResponse,
                    "windowHasPrevious": false,
                    "windowHasNext": numberOfDocs > constants.DEFAULT_WINDOW_SIZE,
                    "stillCounting": false,
                    "numberOfDocs": numberOfDocs,
                    "numberOfDocsRetrieved": numberOfDocs,
                    "docFields": {
                        "pidField": "pid",
                        "titleField": "title"
                    },
                    "metadataFieldDisplayNames": {
                        "fromInputFile": "From input file",
                        "pid": "Pid",
                        "title": "Title"
                    }
                };
                if (pattern) {
                    // These are only reported if there are hits, i.e. if you specified a pattern.
                    expectedSummaryIncludes = Object.assign(expectedSummaryIncludes, {
                        "numberOfHits": numberOfHits,
                        "numberOfHitsRetrieved": numberOfHits,
                        "stoppedCountingHits": false,
                        "stoppedRetrievingHits": false,
                    });
                    searchParam['patt'] = pattern;
                }
                if (filter) {
                    searchParam['filter'] = filter;
                }
                expect(summary).to.deep.include(expectedSummaryIncludes);

                // Sanity-check hits
                const docs = body.docs;
                expect(docs).to.be.an("array").that.has.lengthOf(numberOfResultsInResponse);
                const doc = docs[0];
                expect(doc, 'doc').to.be.an("object").that.includes.all.keys(
                    'docPid',
                    'docInfo'
                );
                if (pattern) {
                    // Only present if there's hits
                    expect(doc, 'doc').to.be.an("object").that.includes.all.keys('numberOfHits', 'snippets');
                }
                if (firstDocJsonIncludes) {
                    // Check that all the required JSON is there
                    expect(doc, 'doc').to.deep.include(firstDocJsonIncludes);
                }

                // Sanity-check docInfo
                util.toBeDocInfo(expect(doc.docInfo, `docInfo ${doc.docPid}`));

                done();
            });
        });
    });
}

// Test that all hits are fetched before the document result is created!
expectDocs('[]', '', 766, 3, {
    "docPid": "PBsve430",
    "numberOfHits": 334
});

expectDocs('"she"', '', 5, 1, {
    "docPid": "PBsve430",
    "numberOfHits": 5,
    "docInfo": {
        "fromInputFile": [
            "/input/PBsve430.xml"
        ],
        "pid": [
            "PBsve430"
        ],
        "title": [
            "service encounter about visa application for family members"
        ],
        "lengthInTokens": 334,
        "mayView": false
    },
});

// Pattern-only docs search
expectDocs('"they"',  undefined,      3, 1, { "docPid": "PBsve430", "numberOfHits": 3 });

// Filter-only docs search
expectDocs(undefined, 'pid:PBsve435', 0, 1, { "docPid": "PBsve435" });

// Combined docs search
expectDocs('"the"',   'pid:PBsve435', 9, 1, { "docPid": "PBsve435", "numberOfHits": 9 });
