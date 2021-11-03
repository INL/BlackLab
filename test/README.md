# BlackLab test suite

## What and why

This is a collection of tests for BlackLab Server. It will test the functions of BlackLab Server on a known test index, ensuring that the results are still as expected. The goal is to quickly discover when a change has broken some functionality.

This is a work in progress. Not all functionality is yet covered by a test.


## How to use

The easiest way to do this is to run this process in Docker; this automates the process and saves you from having to install (a specific version of) `npm` on your machine. From the top level of this repository, run:

```bash
# Run the tests
docker-compose run --rm test
```

NOTE: if you've made changes and want to ensure the latest BlackLab code and tests are being used:

```bash
# Ensure testserver is recreated (if it changed)
docker-compose up -d --build testserver

# Ensure the test image is up to date
docker-compose build test

# Run the tests
docker-compose run --rm test
```

The tests should now be run and the output shown. If all tests succeed, `$?` should have the value `0`.

If you want to run the tests manually outside of Docker, you should:

- index the data in `test/data` to an index named `test` (using config `voice-tei.blf.yaml`)
- start a local server that can access this index
- run the tests using `npm run test` from the `test` directory


## Tools

This test suite is set up using Node.js, Mocha and Chai. Also provided is a Dockerfile, so the full test, from indexing data to testing search functionality, can be run inside containers. If you have Docker installed, it is therefore not necessary to install Node on your machine.

Mocha/Chai were chosen because they are mature, popular and tests are easy to understand and write. We also use these tools on other projects for user interface testing (with Cypress).


## Test data

We currently use a small sample of data from the [VOICE](https://www.univie.ac.at/voice/) (Vienna-Oxford International Corpus of English) project. The test data consists of lemmatized and PoS-tagged TEI, making it easy to index in BlackLab. Using English also makes it easier for others to read and write tests.

The test data is included in our repository (in the `test/data` subdirectory) because the tests rely on this specific data, so it is important that they can be distributed together.

Data from the VOICE project is licensed under the [Creative Commons Attribution-NonCommercial-ShareAlike 3.0 Unported License](http://creativecommons.org/licenses/by-nc-sa/3.0/). As BlackLab is noncommercial and licensed under the Apache license, we believe that including this test data conforms to this license. If you are the rightsholder and believe otherwise, please let us know.
