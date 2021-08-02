
/*
var assert = require('assert');
describe('Array', function() {
  describe('#indexOf()', function() {
    it('should return -1 when the value is not present', function() {
      assert.equal([1, 2, 3].indexOf(4), -1);
    });
  });
});
*/

const chai = require("chai");
const chaiHttp = require("chai-http");
const expect = chai.expect;
const should = chai.should();

const SERVER_URL = process.env.APP_URL || "http://localhost:8080/blacklab-server";

chai.use(chaiHttp);

function testPropVal(obj, propName, value) {
    obj.should.have.property(propName);
    obj[propName].should.eql(value);
}

describe('Server info page', () => {
    it('should return server info', done => {
        chai
            .request(SERVER_URL)
            .get('/')
            .set('Accept', 'application/json')
            .end((err, res) => {
                if (err)
                    done(err);
                
                expect(res, 'response').to.have.status(200);
                expect(res.body, 'response body')
                    .to.be.a("object")
                    .that.includes.keys('blacklabBuildTime', 'blacklabVersion', 'indices');
                const indices = res.body.indices;
                expect(indices, 'indices').to.have.property('test');
                expect(indices.test, 'test index').to.have.property('tokenCount');
                done();
            });
    });
});

describe('Index info page', () => {
    it('should contain accurate data about test index', done => {
        chai
            .request(SERVER_URL)
            .get('/test')
            .set('Accept', 'application/json')
            .end((err, res) => {
                if (err)
                    done(err);
                expect(res).to.have.status(200);
                const body = res.body;
                expect(body).to.be.a("object").and
                    .to.deep.include({
                        'indexName': 'test',
                        'tokenCount': 766
                    });
                done();
            });
    });
});

describe('Search for "the"', () => {

    it('should return expected JSON structure', done => {
        chai
            .request(SERVER_URL)
            .get('/test/hits')
            .query({
                patt: '"the"',
                sort: "wordleft:word:i",
            })
            .set('Accept', 'application/json')
            .end((err, res) => {
                expect(err).to.be.null;
                expect(res).to.have.status(200);
                const body = res.body;
                expect(body).to.be.a("object").and
                    .to.include.keys("summary", "hits", "docInfos");
                const summary = body.summary;
                expect(summary).to.be.an("object")
                    .that.includes.all.keys(
                        'searchParam',
                        'windowFirstResult',
                        'requestedWindowSize', 
                        'actualWindowSize',
                        'windowHasPrevious',
                        'windowHasNext',
                        'stillCounting',
                        'numberOfHits',
                        'numberOfHitsRetrieved',
                        'stoppedCountingHits',
                        'stoppedRetrievingHits',
                        'numberOfDocs',
                        'numberOfDocsRetrieved',
                        'docFields',
                        'metadataFieldDisplayNames'
                    );
                expect(summary).to.deep.include({
                    'searchParam': {
                        "indexname": "test",
                        "patt": "\"the\"",
                        "sort": "wordleft:word:i"
                    }, 
                    "windowFirstResult": 0,
                    "requestedWindowSize": 20,
                    "windowHasPrevious": false,
                    "stillCounting": false
                });
                const hits = body.hits;
                expect(hits).to.be.an("array").that.is.not.empty;
                const hit = body.hits[0];
                expect(hit).to.be.an("object").that.has.all.keys(
                    'docPid',
                    'start',
                    'end',
                    'left',
                    'match',
                    'right'
                );
                done();
            });
    });

    it('should return expected (number of) hits', done => {
        chai
            .request(SERVER_URL)
            .get('/test/hits')
            .query({
                patt: '"the"',
                sort: "wordleft:word:i",
            })
            .set('Accept', 'application/json')
            .end((err, res) => {
                expect(err).to.be.null;
                expect(res).to.have.status(200);
                const body = res.body;
                expect(body).to.be.a("object").that.has.property("summary");
                const summary = body.summary;
                expect(summary).to.deep.include({
                    "requestedWindowSize": 20,
                    "actualWindowSize": 20,
                    "windowHasPrevious": false,
                    "windowHasNext": true,
                    "numberOfHits": 21,
                    "numberOfHitsRetrieved": 21,
                    "numberOfDocs": 3,
                    "numberOfDocsRetrieved": 3,
                });
                expect(body).to.have.nested.property("hits[0]");
                const hit = body.hits[0];
                expect(hit).to.deep.equal({
                    "docPid": "1",
                    "start": 19,
                    "end": 20,
                    "left": {
                        "punct": [ " ", " ", " ", " ", " " ],
                        "lemma": [ "mhm", "i", "go", "to", "" ],
                        "pos": [ "", "", "", "", "" ],
                        "word": [ "mhm", "i", "went", "to", "_0" ]
                    },
                    "match": {
                        "punct": [ " " ],
                        "lemma": [ "the" ],
                        "pos": [ "" ],
                        "word": [ "the" ]
                    },
                    "right": {
                        "punct": [ " ", " ", " ", " ", " " ],
                        "lemma": [ "district", "office", "and", "they", "tell" ],
                        "pos": [ "", "", "", "", "" ],
                        "word": [ "district", "office", "and", "they", "told" ]
                    }
                });
                done();
            });
    });
});


/*
describe('Search for phrase "the" ""', () => {

    it('should return expected (number of) hits', done => {
        chai
            .request(SERVER_URL)
            .get('/test/hits')
            .query({
                patt: '"the"',
                sort: "wordleft:word:i",
            })
            .set('Accept', 'application/json')
            .end((err, res) => {
                expect(err).to.be.null;
                expect(res).to.have.status(200);
                const body = res.body;
                expect(body).to.be.a("object").that.has.property("summary");
                const summary = body.summary;
                expect(summary).to.deep.include({
                    "requestedWindowSize": 20,
                    "actualWindowSize": 20,
                    "windowHasPrevious": false,
                    "windowHasNext": true,
                    "numberOfHits": 21,
                    "numberOfHitsRetrieved": 21,
                    "numberOfDocs": 3,
                    "numberOfDocsRetrieved": 3,
                });
                expect(body).to.have.nested.property("hits[0]");
                const hit = body.hits[0];
                expect(hit).to.deep.equal({
                    "docPid": "1",
                    "start": 19,
                    "end": 20,
                    "left": {
                        "punct": [ " ", " ", " ", " ", " " ],
                        "lemma": [ "mhm", "i", "go", "to", "" ],
                        "pos": [ "", "", "", "", "" ],
                        "word": [ "mhm", "i", "went", "to", "_0" ]
                    },
                    "match": {
                        "punct": [ " " ],
                        "lemma": [ "the" ],
                        "pos": [ "" ],
                        "word": [ "the" ]
                    },
                    "right": {
                        "punct": [ " ", " ", " ", " ", " " ],
                        "lemma": [ "district", "office", "and", "they", "tell" ],
                        "pos": [ "", "", "", "", "" ],
                        "word": [ "district", "office", "and", "they", "told" ]
                    }
                });
                done();
            });
    });
});

*/