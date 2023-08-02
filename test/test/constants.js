const SERVER_URL = process.env.APP_URL || "http://localhost:8080/blacklab-server";
const BLACKLAB_USER = process.env.BLACKLAB_USER || "user";
const BLACKLAB_PASSWORD = process.env.BLACKLAB_USER || "";

const TEST_DATA_ROOT = process.env.TEST_DATA_ROOT || "data";

// Determine where test responses are saved
// NOTE: if we're testing another index type, e.g. integrated, use responses from corresponding subdir
const INDEX_TYPE = process.env.INDEX_TYPE || "classic-external"; // what index type are we testing? (separate responses)
const OPT_INDEX_TYPE_PATH = INDEX_TYPE === "classic-external" ? "" : `-${INDEX_TYPE}`;
const SAVED_RESPONSES_BASE_PATH = process.env.SAVED_RESPONSES_PATH || `${TEST_DATA_ROOT}/saved-responses`;
const SAVED_RESPONSES_PATH = `${SAVED_RESPONSES_BASE_PATH}${OPT_INDEX_TYPE_PATH}`

const TEST_API_VERSION = undefined; // (if undefined, automatically uses current stable version)

module.exports = {
    SERVER_URL,
    BLACKLAB_USER,
    BLACKLAB_PASSWORD,
    TEST_DATA_ROOT,
    SAVED_RESPONSES_PATH,
    TEST_API_VERSION
};
