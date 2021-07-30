# BlackLab test suite

## What and why

This is a collection of tests for BlackLab and BlackLab Server. It will test the functions of BlackLab Server on a known test index, ensuring that the results are still as expected. The goal is to quickly discover when a change has broken some functionality.

## Tools

This test suite is set up using Node, Mocha and Chai. Also provided is a Dockerfile, so the full test, from indexing data to testing search functionality, can be run inside a container. If you have Docker installed, it is therefore not necessary to install Node on your machine.

Mocha/Chai were chosen because they are mature, popular and writing and understanding tests with them is simple. We also use these tools with Cypress for user interface testing.

## How to use

TODO