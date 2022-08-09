const chai = require("chai");
const expect = chai.expect;
const fs = require('fs')
const path = require('path')
const sanitizeFileName = require("sanitize-filename");

const constants = require('./constants');
const SAVED_RESPONSES_PATH =  constants.SAVED_RESPONSES_PATH;

/**
 * Remove variable values such as build time, version, etc. from response.
 *
 * This enables us to compare responses from tests.
 *
 * @param response response to sanitize
 * @return response with variable values replaces with fixed ones
 */
function sanitizeResponse(response) {
    return makeResponseValuesFixed(response, {
        // Server information page
        blackLabBuildTime: true,
        blacklabVersion: true,

        // Corpus information page
        versionInfo: {
            blackLabBuildTime: true,
            blacklabVersion: true,
            timeCreated: true,
            timeModified: true
        },

        // Hits/docs response
        summary: {
            searchTime: true,
            countTime: true,
        }
    });
}

/**
 * Replace values in  JSON object structure with fixed ones.
 *
 * This enables us to compare responses from tests.
 *
 * @param response response to sanitize
 * @param valuesToFix (potentially nested) object structure of values that, when found, should be set to this fixed value.
 * @return response with variable values replaces with fixed ones
 */
function makeResponseValuesFixed(response, valuesToFix) {
    const cleanedData = {};
    for (let key in response) {
        if (!(key in valuesToFix)) {
            // Nothing to fix. Just copy this part.
            cleanedData[key] = response[key];
        } else {
            if (typeof response[key] === 'object') {
                // Recursively fix this part of the response
                cleanedData[key] = makeResponseValuesFixed(response[key], valuesToFix[key]);
            } else {
                // Make this response value fixed
                cleanedData[key] = "VALUE_REMOVED";
            }
        }
    }
    return cleanedData;
}

/**
 * Either save this response if it's the first time we
 * run this test, or compare it to the previously saved version.
 *
 * @param testName name of this test
 * @param actualResponse webservice response we got (parsed JSON)
 */
function expectUnchanged(category, testName, actualResponse) {
    // Remove anything that's variable (e.g. search time) from the response.
    const sanitized = sanitizeResponse(actualResponse);

    // Did we have a previous response?
    const categoryDir = path.resolve(SAVED_RESPONSES_PATH, sanitizeFileName(category));
    if (!fs.existsSync(categoryDir))
        fs.mkdirSync(categoryDir);
    const savedResponseFile = path.resolve(SAVED_RESPONSES_PATH, sanitizeFileName(category), `${sanitizeFileName(testName)}.json`);
    if (fs.existsSync(savedResponseFile)) {
        // Read previously saved response to compare
        const savedResponse = JSON.parse(fs.readFileSync(savedResponseFile, { encoding: 'utf8' }));

        // Compare
        expect(sanitized).to.be.deep.equal(savedResponse);
    } else {
        // Save this response for subsequent tests
        fs.writeFileSync(savedResponseFile, JSON.stringify(sanitized, null, 2), { encoding: 'utf8' });
    }
}

module.exports = {
    expectUnchanged,
};
