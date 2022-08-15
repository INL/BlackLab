const chai = require("chai");
const chaiHttp = require("chai-http");
const expect = chai.expect;
chai.use(chaiHttp);

const constants = require('./constants');
const { expectUnchanged } = require("./compare-responses");


/**
 * Test that a hits search for a pattern returns the correct number of hits and docs,
 * and optionally test that the first hit matches (either JSON or text).
 *
 * @param testName name of the test
 * @param params parameters to send, or single CQL pattern
 * @param filter (optional) if previous argument is a CQL pattern, this may be the document filter query
 */
function expectDocsUnchanged(testName, params, filter) {

    if (typeof params === 'string')
        params = { patt: params };
    if (typeof filter === 'string')
        params.filter = filter;

    const crit = [];
    if (params.patt)
        crit.push(`pattern ${params.patt}`);
    if (params.filter)
        crit.push(`filter ${params.filter}`);

    describe(`/docs with ${crit.join(' and ')}`, () => {
        it('response should match previous', done => {
            chai.request(constants.SERVER_URL)
            .get('/test/docs')
            .query({
                sort: "field:pid",
                wordsaroundhit: 1,
                waitfortotal: "true",
                //usecache: "no", // causes the search to be executed multiple times (hits, count, etc.)
                ...params
            })
            .set('Accept', 'application/json')
            .end((err, res) => {
                expect(err).to.be.null;
                expect(res).to.have.status(200);
                expectUnchanged('docs', testName, res.body);
                done();
            });
        });
    });
}

// Test that all hits are fetched before the document result is created!
expectDocsUnchanged('any token', '[]');
expectDocsUnchanged('single word she', '"she"');

// Pattern-only docs search
expectDocsUnchanged('single word they', '"they"');

// Filter-only docs search
expectDocsUnchanged('filter only', { filter: 'pid:PBsve435' });

// Combined docs search
expectDocsUnchanged('pattern and filter', '"the"', 'pid:PBsve435');
