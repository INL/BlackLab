# Downloads

## Releases

See the [GitHub releases page](https://github.com/INL/BlackLab/releases/) for the complete list. This may also include development versions you can try out. If you're looking for the BlackLab library or commandline tools (i.e. not BlackLab Server), choose the version with libraries included.

Also see the [Change log](changelog.html).

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
