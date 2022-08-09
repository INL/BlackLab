const chai = require("chai");
const chaiHttp = require("chai-http");
const expect = chai.expect;
const should = chai.should();
chai.use(chaiHttp);

const constants = require('./constants');
const util = require('./util');

// Test that a hits search for a pattern returns the correct number of hits and docs,
// and optionally test that the first hit matches (either JSON or text).
function expectHitsImpl(params, numberOfHits, numberOfDocs, expectedFirstHitJson, expectedFirstHitText, firstHitJsonIncludes) {

    const useParams = typeof params === 'string' ? { patt: params } : params;
    const pattern = useParams.patt;

    const title = useParams.viewgroup ? `view group from pattern ${pattern}` : `/hits with pattern ${pattern}`;

    describe(title, () => {
        it('should return expected response (#hits/docs, structure)', done => {
            chai.request(constants.SERVER_URL)
            .get('/test/hits')
            .query({
                sort: "wordleft:word:i,wordright:word:i,field:pid",
                wordsaroundhit: 1,
                waitfortotal: "true",
                //usecache: "no", // causes the search to be executed multiple times (hits, count, etc.)
                ...useParams
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
                        "sort": "wordleft:word:i,wordright:word:i,field:pid",
                        "wordsaroundhit": "1",
                        "waitfortotal": "true",
                        //"usecache": "no", // causes the search to be executed multiple times (hits, count, etc.)
                        ...useParams,
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
                    if (constants.SHOULD_HAVE_CONTEXT) {
                        expect(hit, 'hit').to.deep.equal(expectedFirstHitJson);
                    } else {
                        delete expectedFirstHitJson['left'];
                        delete expectedFirstHitJson['right'];
                        expect(hit, 'hit').to.deep.equal(expectedFirstHitJson);
                    }
                } else {
                    if(constants.SHOULD_HAVE_CONTEXT) {
                        expect(hit, 'hit').to.be.an("object").that.has.all.keys(
                            'docPid',
                            'start',
                            'end',
                            'left',
                            'match',
                            'right'
                        );
                        util.toBeContextPart(expect(hit.left, 'left'));
                        util.toBeContextPart(expect(hit.right, 'right'));

                    } else {
                        expect(hit, 'hit').to.be.an("object").that.has.all.keys(
                            'docPid',
                            'start',
                            'end',
                            'match',
                        );
                    }
                    util.toBeContextPart(expect(hit.match, 'match'));
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
function expectHitsTextMultipleSearches(pattern, numberOfHits, numberOfDocs, expectedFirstHitText) {
    for (let i = 0; i < 5; i++) {
        expectHitsImpl(pattern, numberOfHits, numberOfDocs, undefined, expectedFirstHitText, undefined);
    }
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
const expectedResultPhraseQuery = {
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
};
expectHitsJson('"a" [lemma="successful"]', 1, 1, expectedResultPhraseQuery);
// Also test that forward index matching either the first or the second clause produces the same results
expectHitsJson('_FI1("a", [lemma="successful"])', 1, 1, expectedResultPhraseQuery);
expectHitsJson('_FI2("a", [lemma="successful"])', 1, 1, expectedResultPhraseQuery);

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
expectHitsTextMultipleSearches('[lemma="be" & word!="are"]', 35, 3, "'m");
expectHitsText('<u/> containing "good"', 5, 1, "oh er it 's it 's very good _0 the the er _0 very fresh air and kind people _0");

// Check if docPid, hit start and hit end match
expectHitsDocPos('"very" "good" within <u/>', 1, 1, 'PRint602', 232, 234);

// View a single group from grouped hits
expectHitsJson({
    patt: '"a"',
    group: 'field:title',
    viewgroup: 'str:service encounter about visa application for family members',
}, 5, 1, {
    "docPid": "PBsve430",
    "start": 255,
    "end": 256,
    "left": {
      "punct": [
        " "
      ],
      "lemma": [
        "for"
      ],
      "pos": [
        ""
      ],
      "word": [
        "for"
      ]
    },
    "match": {
      "punct": [
        " "
      ],
      "lemma": [
        "a"
      ],
      "pos": [
        ""
      ],
      "word": [
        "a"
      ]
    },
    "right": {
      "punct": [
        " "
      ],
      "lemma": [
        "visa"
      ],
      "pos": [
        ""
      ],
      "word": [
        "visa"
      ]
    }
  });
