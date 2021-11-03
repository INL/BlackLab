const chai = require("chai");
const chaiHttp = require("chai-http");
const expect = chai.expect;
const should = chai.should();
chai.use(chaiHttp);

const constants = require('./constants');
const SERVER_URL = constants.SERVER_URL;

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

