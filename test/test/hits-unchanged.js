const chai = require("chai");
const chaiHttp = require("chai-http");
const expect = chai.expect;
const should = chai.should();
chai.use(chaiHttp);

const constants = require('./constants');
const util = require('./util');
const expectUnchanged = require('./compare-responses').expectUnchanged;

// Test that a hits search for a pattern returns the correct number of hits and docs,
// and optionally test that the first hit matches (either JSON or text).
function expectHitsUnchanged(testName, params) {

    // You can call this function with one string parameter, which is then used
    // as both the name and the CQL pattern.
    if (params === undefined && typeof testName === 'string')
        params = testName;
    else if (params === undefined)
        throw 'You can only leave out the test name when specifying a single CQL pattern';

    // You can specify a CQL pattern or a map of parameters
    const useParams = typeof params === 'string' ? { patt: params } : params;

    describe(testName, () => {
        it('response should match previous', done => {
            chai.request(constants.SERVER_URL)
            .get('/test/hits')
            .query({
                sort: "wordleft:word:i,wordright:word:i,field:pid", // fully defined sort
                wordsaroundhit: 1,
                waitfortotal: "true",
                //usecache: "no", // causes the search to be executed multiple times (hits, count, etc.)
                ...useParams
            })
            .set('Accept', 'application/json')
            .end((err, res) => {
                expect(err).to.be.null;
                expect(res).to.have.status(200);
                expectUnchanged('hits', testName, res.body);
                done();
            });
        });
    });
}


// Single word
expectHitsUnchanged('"the"');
expectHitsUnchanged('"a" [lemma="successful"]');
// Also test that forward index matching either the first or the second clause produces the same results
expectHitsUnchanged('_FI1("a", [lemma="successful"])');
expectHitsUnchanged('_FI2("a", [lemma="successful"])');

// Simple capture group
expectHitsUnchanged('"one" A:[]');

// A few simpler tests, just checking matching text
expectHitsUnchanged('[]');
expectHitsUnchanged("two-four-single-regex", '"two|four"');
expectHitsUnchanged("two-four-separate", '"two"|"four"');
expectHitsUnchanged('[lemma="be" & word="are"]');
expectHitsUnchanged('[lemma="be" & word!="are"]');
expectHitsUnchanged('<u/> containing "good"');

// Check if docPid, hit start and hit end match
expectHitsUnchanged('"very" "good" within <u/>');

// View a single group from grouped hits
expectHitsUnchanged('view single group', {
    patt: '"a"',
    group: 'field:title',
    viewgroup: 'str:service encounter about visa application for family members',
});
