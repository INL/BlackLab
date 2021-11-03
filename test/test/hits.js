const chai = require("chai");
const chaiHttp = require("chai-http");
const expect = chai.expect;
const should = chai.should();
chai.use(chaiHttp);

const constants = require('./constants');
const util = require('./util');

// Test that a hits search for a pattern returns the correct number of hits and docs,
// and optionally test that the first hit matches (either JSON or text).
function expectHitsImpl(pattern, numberOfHits, numberOfDocs, expectedFirstHitJson, expectedFirstHitText, firstHitJsonIncludes) {
    describe(`/hits with pattern ${pattern}`, () => {
        it('should return expected response (#hits/docs, structure)', done => {
            chai.request(constants.SERVER_URL)
            .get('/test/hits')
            .query({
                patt: pattern,
                sort: "wordleft:word:i,wordright:word:i,field:pid",
                wordsaroundhit: 1
            })
            .set('Accept', 'application/json')
            .end((err, res) => {
                expect(err).to.be.null;
                expect(res).to.have.status(200);
                const body = res.body;
                expect(body).to.be.a("object").that.has.all.keys(
                    "summary",
                    "hits",
                    "docInfos"
                );

                const numberOfHitsInResponse = Math.min(constants.DEFAULT_WINDOW_SIZE, numberOfHits);

                // Sanity-check summary
                const summary = body.summary;
                expect(summary).to.deep.include({

                    "searchParam": {
                        "indexname": "test",
                        "patt": pattern,
                        "sort": "wordleft:word:i,wordright:word:i,field:pid",
                        "wordsaroundhit": "1"
                    },
                    "windowFirstResult": 0,
                    "requestedWindowSize": constants.DEFAULT_WINDOW_SIZE,
                    "actualWindowSize": numberOfHitsInResponse,
                    "windowHasPrevious": false,
                    "windowHasNext": numberOfHits > constants.DEFAULT_WINDOW_SIZE,
                    "stillCounting": false,
                    "numberOfHits": numberOfHits,
                    "numberOfHitsRetrieved": numberOfHits,
                    "stoppedCountingHits": false,
                    "stoppedRetrievingHits": false,
                    "numberOfDocs": numberOfDocs,
                    "numberOfDocsRetrieved": numberOfDocs,
                    "docFields": {
                        "pidField": "pid",
                        "titleField": "title"
                    },
                    "metadataFieldDisplayNames": {
                        "fromInputFile": "From input file",
                        "pid": "Pid",
                        "title": "Title"
                    }
                });

                // Sanity-check hits
                const hits = body.hits;
                expect(hits).to.be.an("array").that.has.lengthOf(numberOfHitsInResponse);
                const hit = hits[0];
                if (expectedFirstHitJson) {
                    expect(hit, 'hit').to.deep.equal(expectedFirstHitJson);
                } else {
                    expect(hit, 'hit').to.be.an("object").that.has.all.keys(
                        'docPid',
                        'start',
                        'end',
                        'left',
                        'match',
                        'right'
                    );
                    util.toBeContextPart(expect(hit.left, 'left'));
                    util.toBeContextPart(expect(hit.match, 'match'));
                    util.toBeContextPart(expect(hit.right, 'right'));
                    const match = hit.match;
                    const word = match.word;
                    expect(word, 'word').to.be.an("array");
                    if (expectedFirstHitText) {
                        expect(word.join(' '), 'hit text').to.equal(expectedFirstHitText);
                    }
                    if (firstHitJsonIncludes) {
                        expect(hit, 'hit').to.deep.include(firstHitJsonIncludes);
                    }
                }

                // Sanity-check docInfos
                const docInfos = body.docInfos;
                const docPid = hit.docPid;
                expect(docInfos).to.be.an("object").that.includes.key(docPid);
                util.toBeDocInfo(expect(docInfos[docPid], `docInfo ${docPid}`));

                done();
            });
        });
    });
}

// Test that a hits search for a pattern returns the correct number of hits and docs,
// and optionally test that the first hit matches the specified JSON structure.
function expectHitsJson(pattern, numberOfHits, numberOfDocs, expectedFirstHitJson) {
    expectHitsImpl(pattern, numberOfHits, numberOfDocs, expectedFirstHitJson, undefined, undefined);
}

// Test that a hits search for a pattern returns the correct number of hits and docs,
// and optionally test that the first hit text matches the specified text.
function expectHitsText(pattern, numberOfHits, numberOfDocs, expectedFirstHitText) {
    expectHitsImpl(pattern, numberOfHits, numberOfDocs, undefined, expectedFirstHitText, undefined);
}

// Test that a hits search for a pattern returns the correct number of hits and docs,
// and optionally test that the first hit text matches the specified text.
function expectHitsDocPos(pattern, numberOfHits, numberOfDocs, docPid, start, end) {
    const hitIncludes = {
        docPid,
        start,
        end
    };
    expectHitsImpl(pattern, numberOfHits, numberOfDocs, undefined, undefined, hitIncludes);
}


// Single word
expectHitsJson('"the"', 21, 3, {
    "docPid": "PBsve430",
    "start": 19,
    "end": 20,
    "left": {
        "punct": [ " " ],
        "lemma": [ "" ],
        "pos": [ "" ],
        "word": [ "_0" ]
    },
    "match": {
        "punct": [ " " ],
        "lemma": [ "the" ],
        "pos": [ "" ],
        "word": [ "the" ]
    },
    "right": {
        "punct": [ " " ],
        "lemma": [ "district" ],
        "pos": [ "" ],
        "word": [ "district" ]
    }
});

// Phrase
expectHitsJson('"a" [lemma="successful"]', 1, 1, {
    "docPid": "PRint602",
    "start": 150,
    "end": 152,
    "left": {
      "punct": [ " " ],
      "lemma": [ "be" ],
      "pos": [ "" ],
      "word": [ "'s" ]
    },
    "match": {
      "punct": [ " ", " " ],
      "lemma": [ "a", "successful" ],
      "pos": [ "", "" ],
      "word": [ "a", "successful" ]
    },
    "right": {
      "punct": [ " " ],
      "lemma": [ "meet/meeting" ],
      "pos": [ "" ],
      "word": [ "meeting" ]
    }
});

// Simple capture group
expectHitsJson('"one" A:[]', 2, 1, {
    "docPid": "PBsve430",
    "start": 195,
    "end": 197,
    "captureGroups": [
        {
            "name": "A",
            "start": 196,
            "end": 197
        }
    ],
    "left": {
        "punct": [ " " ],
        "lemma": [ "be" ],
        "pos": [ "" ],
        "word": [ "is" ]
    },
    "match": {
        "punct": [ " ", " " ],
        "lemma": [ "one", "hundred" ],
        "pos": [ "", "" ],
        "word": [ "one", "hundred" ]
    },
    "right": {
        "punct": [ " " ],
        "lemma": [ "ninety" ],
        "pos": [ "" ],
        "word": [ "ninety" ]
    }
});

// A few simpler tests, just checking matching text
expectHitsText('[]', 766, 3, "f_hallo");
expectHitsText('"two|four"', 3, 1, "two");
expectHitsText('"two"|"four"', 3, 1, "two");
expectHitsText('[lemma="be" & word="are"]', 7, 2, "are");
expectHitsText('[lemma="be" & word!="are"]', 35, 3, "'m");
expectHitsText('<u/> containing "good"', 5, 1, "oh er it 's it 's very good _0 the the er _0 very fresh air and kind people _0");

// Check if docPid, hit start and hit end match
expectHitsDocPos('"very" "good" within <u/>', 1, 1, 'PRint602', 232, 234);
