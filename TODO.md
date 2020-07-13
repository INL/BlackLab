# Module refactoring TODO

- blacklab-tools: tools and test utils to separate module, doesn't need to be included in core?
  (+update docs; provide convenient way to run tools, e.g. shell scripts)

- blacklab-indexing: code related to indexing?

- blacklab-contentstore

- blacklab-util (util package)?

- code dealing with files (config, input, ..)? try to make sure core is not dependent on file system.

- better document API changes (see below)

- document modules and their function. core remains the same, other modules could be used separately to some extent

- move mocks to test code



# Plugin aanpak

- binnen huidige plugin-aanpak moet BlackLab alle plugintypes kennen. Misschien geen bezwaar.



# (start of) migration guide

## Goals of this refactoring

Over the years, BlackLab Core has improved and acquired more features, but has remained a single large module that does a lot of different things. This makes it more difficult to improve the project's architecture and makes code difficult to test, among other problems.

That's why it was decided to extract the individual parts of the code into separate modules. Unfortunately, this required some API small changes, as it made it clear that the API had been designed assuming this monolithic project structure. Ultimately these API changes are good for the long-term evolution of BlackLab, but in the short term, they will require minor changes in code using the BlackLab Java library. (BlackLab Server users should be unaffected by this)

## API changes

### BlackLabIndex

- REMOVED: 3 methods (find, explain, createSpanQuery) that took a CQL query as string or a TextPattern. Instead, you should use the CQL parser to parse your query and TextPattern.toQuery() to convert it to a BLSpanQuery, which can then be executed by the BlackLabIndex object.


### SearchEmpty

- REMOVED: find() method that took a CQL query. See above.


## Example code

```java

// TODO: write example

```