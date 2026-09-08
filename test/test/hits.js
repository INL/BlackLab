"use strict";
const chai = require("chai");
const chaiHttp = require("chai-http");
const expect = chai.expect;
const should = chai.should();
chai.use(chaiHttp);

const constants = require('./constants');
const { expectUnchanged, expectUrlUnchanged, sanitizeResponse} = require("./compare-responses");
const { corpusUrl } = require("./util");

/**
 * Test that a hits search returns the same response as before.
 *
 * @param testName The name of the test (and file name of the expected response).
 * @param params The search parameters.
 */
function expectHitsUnchanged(testName, params) {

    const corpusName = 'test';

    // You can call this function with one string parameter, which is then used
    // as both the name and the CQL pattern.
    if (params === undefined)
        throw 'Please pass both a test name and CQL pattern (or parameter object)';

    // You can specify a CQL pattern or a map of parameters
    if (typeof params === 'string')
        params = { patt: params };

    describe(`hits/${testName}`, () => {
        it('response should match previous', done => {
            chai.request(constants.SERVER_URL)
            .get(corpusUrl(corpusName) + '/hits')
            .query({
                api: constants.TEST_API_VERSION,
                sort: "field:pid,hitposition", // fully defined sort
                context: 1,
                waitfortotal: "true",
                //usecache: "no", // causes the search to be executed multiple times (hits, count, etc.)
                ...params
            })
            .set('Accept', 'application/json')
            .end((err, res) => {
                expect(err).to.be.null;
                expect(res).to.have.status(200);
                expectUnchanged(corpusName, 'hits', testName, res.body);
                done();
            });
        });
    });
}

// Single word
expectHitsUnchanged("single word the", '"the"');
expectHitsUnchanged("simple phrase a succesful", '"a" [lemma="successful"]');
// Also test that forward index matching either the first or the second clause produces the same results
expectHitsUnchanged("phrase a succesful with fimatch 1st", '_fimatch("a", [lemma="successful"], 0)');
expectHitsUnchanged("phrase a succesful with fimatch 2nd", '_fimatch("a", [lemma="successful"], 1)');

// Simple capture group
expectHitsUnchanged("simple capture group", '"one" A:[]');
expectHitsUnchanged("same hit, different captures", '"one" A:([]{1,2}) []{1,2}');

// A few simpler tests, just checking matching text
expectHitsUnchanged("any token", '[]');
expectHitsUnchanged("two-four-single-regex", '"two|four"');
expectHitsUnchanged("two-four-separate", '"two"|"four"');
expectHitsUnchanged("token level AND", '[lemma="be" & word="are"]');
expectHitsUnchanged("token level AND NOT", '[lemma="be" & word!="are"]');
expectHitsUnchanged("containing", '<u/> containing "good"');
expectHitsUnchanged("within", '[word="very"] [word="good"] within <u/>');

// View a single group from grouped hits
expectHitsUnchanged('view single group', {
    patt: '"a"',
    group: 'field:title',
    viewgroup: 'str:service encounter about visa application for family members',
});

// Matching doc facets
expectUrlUnchanged('test', 'hits', 'document facets',
        corpusUrl('test') + '/hits/?patt=%22the%22&number=0&facets=field:pid');

// Hits CSV
expectUrlUnchanged('test', 'hits', 'CSV results',
        corpusUrl('test') + '/hits/?patt=%22the%22', 'text/csv');

// /termfreq operation
expectUrlUnchanged('test', 'hits', 'Termfreq word sensitive',
        corpusUrl('test') + '/termfreq/?annotation=word&sensitive=true');
expectUrlUnchanged('test', 'hits', 'Termfreq lemma insensitive',
        corpusUrl('test') + '/termfreq/?annotation=lemma');
