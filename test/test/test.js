var assert = require('assert');

describe('Array', function() {
  describe('#indexOf()', function() {
    it('should return -1 when the value is not present', function() {
      assert.equal([1, 2, 3].indexOf(4), -1);
    });
  });
});

const chai = require("chai");
const chaiHttp = require("chai-http");
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
                res.should.have.status(200);
                res.body.should.be.a("object");
                res.body.should.have.property('blacklabBuildTime');
                res.body.should.have.property('blacklabVersion');
                res.body.should.have.property('indices');
                res.body.indices.should.have.property('gysseling');
                res.body.indices.gysseling.should.have.property('tokenCount');
                done();
            });
    });
});

describe('Index info page', () => {
    it('should contain accurate data about test index', done => {
        chai
            .request(SERVER_URL)
            .get('/gysseling')
            .set('Accept', 'application/json')
            .end((err, res) => {
                if (err)
                    done(err);
                res.should.have.status(200);
                res.body.should.be.a("object");
                testPropVal(res.body, 'indexName', 'gysseling')
                testPropVal(res.body, 'tokenCount', 41240)
                done();
            });
    });
});

describe('Hits search', () => {
    it('should return expected (number of) hits', done => {
        chai
            .request(SERVER_URL)
            .get('/gysseling/hits')
            .query({ patt: '"de"' })
            .set('Accept', 'application/json')
            .end((err, res) => {
                if (err)
                    done(err);
                res.should.have.status(200);
                res.body.should.be.a("object");
                res.body.should.have.property('summary');
                res.body.summary.should.deep.include({
                    'searchParam': {
                        "indexname": "gysseling",
                        "patt": "\"de\""
                    },
                    "windowFirstResult": 0,
                    "requestedWindowSize": 20,
                    "actualWindowSize": 20,
                    "windowHasPrevious": false,
                    "windowHasNext": true,
                    "stillCounting": false,
                    "numberOfHits": 1360,
                    "numberOfHitsRetrieved": 1360,
                    "stoppedCountingHits": false,
                    "stoppedRetrievingHits": false,
                    "numberOfDocs": 103,
                    "numberOfDocsRetrieved": 103
                });
                done();
            });
    });
});
