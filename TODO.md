# Module refactoring TODO

- blacklab-forward-index ? Verweven met BlackLabIndex.
  Je wilt interfaces extraheren naar een module (aangemaakt),
  maar interfaces bevatten default methods die toch naar implementaties refereren. Er moeten dus abstract classes
  bij gemaakt worden om die default methods heen te verplaatsen.

- BLSpanQuery "niveau" vs Hits "niveau" van elkaar scheiden?

- blacklab-indexing: code related to indexing?

- code dealing with files (config, input, ..)? try to make sure engine is not dependent on file system.

- better document API changes (see below)

- document modules and their function. core remains the same, other modules could be used separately to some extent

- blacklab-tools: doesn't need to be included in core?
  (+update docs; provide convenient way to run tools, e.g. shell scripts)

- keep project versions in synch:
  http://www.mojohaus.org/versions-maven-plugin/


# Plugin aanpak

- binnen huidige plugin-aanpak moet BlackLab alle plugintypes kennen. Misschien geen bezwaar.
- we kunnen beginnen met alles binnen blacklab-engine houden en op termijn evt. een dynamisch pluginsysteem gebruiken. misschien beter om niet direct te ingrijpende wijzigingen te gaan maken.


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