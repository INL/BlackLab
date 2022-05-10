const chai = require("chai");
const chaiHttp = require("chai-http");
const expect = chai.expect;
const should = chai.should();
const assert = chai.assert;
const crypto = require('crypto')
const fs = require('fs')
const path = require('path')
const parseXmlString = require('xml2js').parseStringPromise;
chai.use(chaiHttp);

const constants = require('./constants');
const SERVER_URL = constants.SERVER_URL;

const TEST_DATA_ROOT =  constants.INDEX_TEST_DATA_ROOT;
const TEST_CONFIG = JSON.parse(fs.readFileSync(path.resolve(TEST_DATA_ROOT, 'index-test-config.json')));

const INPUT_FORMAT_PATH = path.resolve(TEST_DATA_ROOT, TEST_CONFIG['input-format']);
const DOC_TO_INDEX_PATH = path.resolve(TEST_DATA_ROOT, TEST_CONFIG['docs-to-index']);
const EXPECTED_INDEX_CONTENT_PATH = path.resolve(TEST_DATA_ROOT, TEST_CONFIG['expected-index']);
const EXPECTED_INDEX_METADATA_PATH = path.resolve(TEST_DATA_ROOT, TEST_CONFIG['expected-metadata']);

function addDefaultHeaders(request) {
    request.auth(constants.BLACKLAB_USER, constants.BLACKLAB_PASSWORD)
    let allHeaders = {
        'X-Request-ID': crypto.randomBytes(8).toString('hex'),
    }

    for (let key in allHeaders) {
        request.set(key, allHeaders[key]);
    }
}

function createIndexName() {
    indexName = "test-index-" + crypto.randomInt(10000).toString();
    return indexName;
}

async function createInputFormat(){
    var formatName = path.basename(INPUT_FORMAT_PATH);
    let request = chai
        .request(SERVER_URL)
        .post('/input-formats')
        .set('Accept', 'application/json')
        .attach('data', fs.readFileSync(INPUT_FORMAT_PATH), formatName);
    addDefaultHeaders(request);
    return request;
}

async function createIndex(indexName) {
    index_url = constants.BLACKLAB_USER + ":" + indexName
    inputFormat = TEST_CONFIG['input-format'].split('.')[0];
    let request = chai
        .request(SERVER_URL)
        .post('/')
        .query({
            'name': index_url,
            'display': indexName,
            'format': constants.BLACKLAB_USER + ":" + inputFormat
        })
        .set('Accept', 'application/json')
    addDefaultHeaders(request);
    return request.send();
}

async function getIndexRequest(indexName) {
    indexUrl = constants.BLACKLAB_USER + ":" + indexName
    let request = chai
        .request(SERVER_URL)
        .get("/" + indexUrl + "/status")
        .set('Accept', 'application/json')
    addDefaultHeaders(request);
    return request.send();
}

async function addToIndex(indexName, payloadPath) {
    indexUrl = constants.BLACKLAB_USER + ":" + indexName
    let request = chai
        .request(SERVER_URL)
        .post('/' + indexUrl + '/docs')
        .set('Accept', 'application/json')
        .attach('data', fs.readFileSync(payloadPath), 'testdocs' );
    addDefaultHeaders(request);
    return request;
}
async function getIndexContent(indexName) {
    indexUrl = constants.BLACKLAB_USER + ":" + indexName;
    let request = chai
        .request(SERVER_URL)
        .get("/" + indexUrl + "/docs")
        .set('Accept', 'application/json')
    addDefaultHeaders(request);
    return request.send();
}

function clearKeys(keys, data) {
    var diff = Object.keys(data).filter(k => !keys.includes(k));
    var cleanedData = {}
    for (let k of diff) {
        var value = data[k];
        if (!(value instanceof Array)) {
            cleanedData[k] = value;
            continue;
        }
        if (value.length === 1 && typeof value[0] === "string" && (value[0].trim() === "" || value[0].trim() === "\n")) {
            cleanedData[k] = [];
        } else {
            cleanedData[k] = value;
        }
    }
    return cleanedData
}

async function getIndexMetadata(indexName) {
    indexUrl = constants.BLACKLAB_USER + ":" + indexName
    let request = chai
        .request(SERVER_URL)
        .get("/" + indexUrl + "/")
        .set('Accept', 'application/json')
    addDefaultHeaders(request);
    return request.send();
}

async function toJson(xml) {
    return parseXmlString(xml);
}

function queryIndex(indexName, pattern, filters, format = 'application/json') {
    indexUrl = constants.BLACKLAB_USER + ":" + indexName + "/hits";
    var respFormat = format === "" ? "application/json" : format;
    let request = chai
        .request(SERVER_URL)
        .post("/" + indexUrl + "/")
        .buffer()
        .set('Accept', respFormat)
    if (filters !== "") {
        request.query({"patt": pattern, "filter": filters})
    } else {
        request.query({"patt": pattern})
    }
    addDefaultHeaders(request);
    request.parse((res, callback) => {
        res.text = '';
        res.on('data', (chk) => {
            res.text += chk;
        });
        res.on('end', () => callback(null, res.text));
    });
    return request.send()
}


describe('Indexing tests', () => {
    it('create a new index', async () => {
        indexName = createIndexName();
        let respFormat = await createInputFormat();
        assert.isTrue(respFormat.ok);

        let createRes = await createIndex(indexName);
        assert.isTrue(createRes.ok);

        let resGetIndex = await getIndexRequest(indexName);
        assert.isTrue(resGetIndex.ok);
    });
    it('adds to index', async () => {

        indexName = createIndexName();
        const req = await createInputFormat();
        assert.isTrue(req.ok);

        let createRes = await createIndex(indexName);
        assert.isTrue(createRes.ok);

        let addReq = await addToIndex(indexName, DOC_TO_INDEX_PATH);
        assert.isTrue(addReq.ok);
        
        let indexContents = await getIndexContent(indexName);
        assert.isTrue(indexContents.ok);

        var body = indexContents.body;
        var expectedContent = JSON.parse(fs.readFileSync(EXPECTED_INDEX_CONTENT_PATH));

        var keys = ['summary', 'searchTime'];
        expect(clearKeys(keys, expectedContent)).to.be.deep.equal(clearKeys(keys, body));
    }).timeout(3000); // allow a little more time for slower servers

    it('get index metadata', async () => {
        indexName = createIndexName();
        await createInputFormat();

        let createRes = await createIndex(indexName);
        assert.isTrue(createRes.ok);

        let addReq = await addToIndex(indexName, DOC_TO_INDEX_PATH);
        assert.isTrue(addReq.ok);

        let indexMetadata = await getIndexMetadata(indexName);
        var body = indexMetadata.body;
        assert.isTrue(indexMetadata.ok);

        var expectedMetadata = JSON.parse(fs.readFileSync(EXPECTED_INDEX_METADATA_PATH));

        var keys = ['indexName', 'displayName', 'versionInfo', 'documentFormat']
        expect(clearKeys(keys, expectedMetadata)).to.be.deep.equal(clearKeys(keys, body));
    });

    it('query from config', async () => {
        indexName = createIndexName();
        await createInputFormat();

        let createRes = await createIndex(indexName);
        assert.isTrue(createRes.ok);

        let addReq = await addToIndex(indexName, DOC_TO_INDEX_PATH);
        assert.isTrue(addReq.ok);

        var queriesTest = TEST_CONFIG['queries'];
        for (let [testName, testCase]  of Object.entries(queriesTest)) {
            console.log("Running test case: " + testName);
            const filterTerms = testCase['filters'];
            const queryInd = await queryIndex(indexName, '"120"', filterTerms, "application/xml")
            assert.isTrue(queryInd.ok);
            const body = await toJson(queryInd.body);

            const keys = ['summary'];
            if (testCase['expected'] === null) {
                var results = clearKeys(keys, body['blacklabResponse']);
                expect(results['hits']).to.be.empty;
            } else {
                var expectedOutput = await toJson(fs.readFileSync(path.resolve(TEST_DATA_ROOT, testCase['expected'])));
                expect(clearKeys(keys, expectedOutput['blacklabResponse'])).to.be.deep.equal(clearKeys(keys, body['blacklabResponse']));
            }
        }
    });


});

