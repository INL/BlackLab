const chai = require("chai");
const chaiHttp = require("chai-http");
const expect = chai.expect;
chai.use(chaiHttp);

const { expectUnchanged } = require("./compare-responses");
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
                expectUnchanged('info', 'Server info page', res.body);
                done();
            });
    });
});

describe('Corpus info page', () => {
    it('should contain accurate data about test corpus', done => {
        chai
            .request(SERVER_URL)
            .get('/test')
            .set('Accept', 'application/json')
            .end((err, res) => {
                if (err)
                    done(err);
                expect(res).to.have.status(200);
                expectUnchanged('info', 'Corpus info page', res.body);
                done();
            });
    });
});
