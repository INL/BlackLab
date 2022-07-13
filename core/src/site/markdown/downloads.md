# Downloads

## Releases

See the [GitHub releases page](https://github.com/INL/BlackLab/releases/) for the complete list. This may also include development versions you can try out. If you're looking for the BlackLab library or commandline tools (i.e. not BlackLab Server), choose the version with libraries included.

Also see the [Change log](changelog.html).

## Versions and compatibility

- (future) **version 4.x**: likely Java 11 and Lucene 9. Should be compatible with corpora created with 3.x. Will be compatible with Solr (but with a new BlackLab index format).
- (current) **version 3.x**: Java 11 and Lucene 8. Not compatible with corpora created with earlier versions (because of Lucene dropping support for older index formats)
- **versions 2.x**: Java 8 and Lucene 5. If you don't want to reindex your corpus, you can stay on this version. We will backport certain bugfixes to this version. 
- **versions 1.x**: Java 6 and Lucene 3-5. Very old, don't use these anymore.

Because of Lucene's backward compatibility policy, you will not be able to open your indexed corpus with a newer BlackLab version if it is too old. If you want to use the latest version of BlackLab, you will have to re-index your corpus.

## Build your own

To download and build the development version (bleeding edge, may be unstable), clone the repository and build it 
using Maven:

```bash
git clone git://github.com/INL/BlackLab.git
cd BlackLab
# git checkout dev    # (the default branch)
mvn clean package
```

To instead build the most recent release of BlackLab yourself:

```bash
git checkout main
mvn clean package
```
