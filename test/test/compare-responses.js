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
 * @param removeParametersFromResponse if true, also remove summary.searchParam (for comparing different
 *   requests that should have same results) * @return response with variable values replaces with fixed ones
 */
function sanitizeBlsResponse(response, removeParametersFromResponse = false) {
    const keysToMakeConstant = {
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
    };
    if (removeParametersFromResponse) {
        keysToMakeConstant.summary.searchParam = true;
    }
    return sanitizeResponse(response, keysToMakeConstant);
}

/**
 * Clean up a response object for comparison.
 *
 * Replaces specified values in JSON object structure with constant ones,
 * and optionally applies a function to apply to values in the response.
 *
 * This enables us to transform responses into a form that we can easily compare.
 *
 * @param response response to clean up
 * @param keysToMakeConstant (potentially nested) object structure of keys that, when found, should be set to a
 *   constant value. Note that only the keys of this object are used. Alternatively, a single string or a list of
 *   strings is also allowed. If null or undefined are specified, an empty object is used.
 * @param transformValueFunc (optional) function to apply to all values copied from the response (i.e. values not
 *   being made constant)
 * @return sanitized response
 */
function sanitizeResponse(response, keysToMakeConstant, transformValueFunc) {

    // Make sure keysToMakeConstant is an object
    let recursive = false;
    if (keysToMakeConstant instanceof Array) {
        keysToMakeConstant = Object.fromEntries(keysToMakeConstant.map(v => [v, true]));
    } else if (typeof keysToMakeConstant === 'object') {
        recursive = true;
    } else if (typeof keysToMakeConstant === 'string') {
        keysToMakeConstant = { [keysToMakeConstant]: true };
    } else {
        keysToMakeConstant = {};
    }

    // Replace any of the keys from keysToMakeConstant with constant values,
    // and perform any other fixes if fixValueFunc was supplied.
    const cleanedData = {};
    for (let key in response) {
        const value = response[key];
        if (key in keysToMakeConstant) {
            // This is (or contains) a variable value we don't want to compare.
            if (recursive && typeof keysToMakeConstant[key] === 'object' && typeof value === 'object' && !(value instanceof Array)) {
                // Subobject; recursively fix this part of the response
                cleanedData[key] = sanitizeResponse(response[key], keysToMakeConstant[key]);
            } else {
                // Single value or array. Make this response value fixed
                cleanedData[key] = "VALUE_REMOVED";
            }
        } else {
            // No values to make constant, just regular values we want to compare.
            cleanedData[key] = transformValueFunc ? transformValueFunc(value) : value;
        }
    }
    return cleanedData;
}

/**
 * Either save this response if it's the first time we
 * run this test, or compare it to the previously saved version.
 *
 * @param category test category (e.g. "hits")
 * @param testName name of this test, and file name for the response
 * @param actualResponse webservice response we got (parsed JSON)
 * @param removeParametersFromResponse if true, also remove summary.searchParam (for comparing different
 * requests that should have same results)
 */
function expectUnchanged(category, testName, actualResponse, removeParametersFromResponse = false) {
    // Remove anything that's variable (e.g. search time) from the response.
    const sanitized = sanitizeBlsResponse(actualResponse, removeParametersFromResponse);

    // Ensure category dir exists
    const categoryDir = path.resolve(SAVED_RESPONSES_PATH, sanitizeFileName(category));
    if (!fs.existsSync(categoryDir))
        fs.mkdirSync(categoryDir);

    // Did we have a previous response?
    const savedResponseFile = path.resolve(SAVED_RESPONSES_PATH, sanitizeFileName(category), `${sanitizeFileName(testName)}.json`);
    if (fs.existsSync(savedResponseFile)) {
        // Read previously saved response to compare
        const savedResponse = JSON.parse(fs.readFileSync(savedResponseFile, { encoding: 'utf8' }));

        // Compare
        expect(sanitized).to.be.deep.equal(savedResponse);
    } else {
        if (process.env.BLACKLAB_TEST_SAVE_MISSING_RESPONSES === 'true') {
            // Save this response for subsequent tests
            fs.writeFileSync(savedResponseFile, JSON.stringify(sanitized, null, 2), {encoding: 'utf8'});
        } else {
            expect.fail(`Response for ${category}/${testName} not found. Make sure it exists (use run-local.sh to save responses)`);
        }
    }
}

module.exports = {
    sanitizeBlsResponse,
    sanitizeResponse,
    expectUnchanged,
};
