# Interfaces related to the BlackLab index structure

The main interface here is BlackLabIndex, which used to be called Searcher.

It manages a BLIndex, which itself manages the components of a BlackLab index:
- LuceneIndex
- ForwardIndex, AnnotationForwardIndex, Terms
- ContentStore
