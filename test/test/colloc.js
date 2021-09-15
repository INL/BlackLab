const chai = require("chai");
const chaiHttp = require("chai-http");
const expect = chai.expect;
const should = chai.should();
chai.use(chaiHttp);

const constants = require('./constants');
const util = require('./util');

// Test that a hits search for a pattern returns the correct number of hits and docs,
// and optionally test that the first hit matches (either JSON or text).
function expectColloc(pattern, wordFreqs) {
    describe(`/hits?calc=colloc with pattern ${pattern}`, () => {
        it('should return expected response (#hits/docs, structure)', done => {
            chai.request(constants.SERVER_URL)
            .get('/test/hits')
            .query({
                calc: 'colloc',
                patt: pattern,
                wordsaroundhit: 10,
                sensitive: 'false'
            })
            .set('Accept', 'application/json')
            .end((err, res) => {
                expect(err).to.be.null;
                expect(res).to.have.status(200);
                const body = res.body;
                expect(body).to.be.a("object").that.has.all.keys("tokenFrequencies");
                const tokenFrequencies = body.tokenFrequencies;
                expect(tokenFrequencies).to.include(wordFreqs);
                done();
            });
        });
    });
}


// Single word
expectColloc('"the"', {
    for: 24,
    yeah: 23,
    the: 18,
    er: 15
});

// Phrase
expectColloc('"a" []', {
    a: 15,
    er: 14,
    i: 13,
    you: 12
});
