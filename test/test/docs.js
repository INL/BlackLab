"use strict";
const chai = require("chai");
const chaiHttp = require("chai-http");
const expect = chai.expect;
chai.use(chaiHttp);

const constants = require('./constants');
const { expectUnchanged, expectUrlUnchanged } = require("./compare-responses");


/**
 * Test that a hits search for a pattern returns the correct number of hits and docs,
 * and optionally test that the first hit matches (either JSON or text).
 *
 * @param corpusName name of the corpus
 * @param testName name of the test
 * @param params parameters to send, or single CQL pattern
 * @param filter (optional) if previous argument is a CQL pattern, this may be the document filter query
 */
function expectDocsUnchanged(corpusName, testName, params, filter) {

    if (typeof params === 'string')
        params = { patt: params };
    if (typeof filter === 'string')
        params.filter = filter;

    describe(`docs/${testName}`, () => {
        it('response should match previous', done => {
            chai.request(constants.SERVER_URL)
            .get(constants.URL_CORPUS_TEST + '/docs')
            .query({
                api: constants.TEST_API_VERSION,
                sort: "field:pid",
                context: 1,
                waitfortotal: "true",
                //usecache: "no", // causes the search to be executed multiple times (hits, count, etc.)
                ...params
            })
            .set('Accept', 'application/json')
            .end((err, res) => {
                expect(err).to.be.null;
                expect(res).to.have.status(200);
                expectUnchanged(corpusName, 'docs', testName, res.body);
                done();
            });
        });
    });
}

let corpus = 'test';

// Test that all hits are fetched before the document result is created!
expectDocsUnchanged(corpus, 'any token', '[]');
expectDocsUnchanged(corpus, 'single word she', '"she"');

// Pattern-only docs search
expectDocsUnchanged(corpus, 'single word they', '"they"');

// Filter-only docs search
expectDocsUnchanged(corpus, 'filter only', { filter: 'pid:PBsve435' });

// Combined docs search
expectDocsUnchanged(corpus, 'pattern and filter', '"the"', 'pid:PBsve435');

// Doc metadata, contents
expectUrlUnchanged(corpus, 'docs', 'document metadata',
        constants.URL_CORPUS_TEST + '/docs/PBsve430');
expectUrlUnchanged(corpus, 'docs', 'document contents',
        constants.URL_CORPUS_TEST + '/docs/PBsve430/contents?patt=%22the%22', 'application/xml');

// Doc snippet
expectUrlUnchanged(corpus, 'docs', 'document snippet wordstart',
        constants.URL_CORPUS_TEST + '/docs/PBsve430/snippet?wordstart=5&wordend=15');
expectUrlUnchanged(corpus, 'docs', 'document snippet hitstart',
        constants.URL_CORPUS_TEST + '/docs/PBsve430/snippet?hitstart=3&hitend=5&context=2');

// Doc facets
expectUrlUnchanged(corpus, 'docs', 'document facets',
        constants.URL_CORPUS_TEST + '/docs/?number=0&facets=field:title');

// Docs CSV
expectUrlUnchanged(corpus, 'docs', 'CSV results',
        constants.URL_CORPUS_TEST + '/docs/', 'text/csv');

// Some tests on the corpus with fragment metadata (should return full docs)

// @@@ saved-response FOR MOST OF THESE ARE WRONG, FIX FIRST!
corpus = 'fragments';
expectDocsUnchanged(corpus, 'single word the', '"the"');
expectDocsUnchanged(corpus, 'both docs', { filter: 'author:*n*' });
expectDocsUnchanged(corpus, 'frag by field', { filter: 'author:Jan' });
expectDocsUnchanged(corpus, 'frag by id', { id: 'doc-01-frag-02' });
expectDocsUnchanged(corpus, 'pattern and filter', '"the"', 'year:1987');
