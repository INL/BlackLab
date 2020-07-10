# Module refactoring TODO

- how to deal with moved methods nl.inl.blacklab.tmputil?
  (+document API changes)

- ..indexers.preprocess: move ConvertPlugin and TagPlugin to contrib/convert-and-tag?

- blacklab-tools: tools and test utils to separate module, doesn't need to be included in core?
  (+update docs; provide convenient way to run tools, e.g. shell scripts)

- blacklab-indexing: code related to indexing?

- blacklab-contentstore

- blacklab-util (util package)?

- code dealing with files (config, input, ..)? try to make sure core is not dependent on file system.

- document modules and their function. core remains the same, other modules could be used separately to some extent

- move mocks to test code
