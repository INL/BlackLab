# Migrating from BlackLab 1.7 to 2.0

While BlackLab 2.0 doesn't include significant changes to BlackLab Server, or to input format configuration files, it does have a different Java API. If you use the Java API, for example because you've written your own DocIndexer class, this page helps you migrate it to 2.0. 

* <a href="#terminology">Terminology</a>
* <a href="#methods">Method naming</a>
* <a href="#renamed">Important renamed packages and classes</a>

<a id="terminology"></a>

## Terminology

- "complex field" -> "annotated field"
- "property on a complex field" -> "annotation on an annotated field"
- "an indexed alternative for a property" (e.g. case- and diacritics-insensitive) -> "an indexed sensitivity for an annotation"

So, for example, an annotated field "contents" might have annotations "word", "lemma" and "pos" (part of speech), and the "word" annotation might have two sensitivities indexed: (case- and diacritics-) sensitive, and (case- and diacritics-) insensitive.

<a id="methods"></a>

## Method naming

For many classes, methods were renamed from getSomeThing() to simply someThing(). While this may not be the convention in Java, it makes for less noisy, more natural-sounding code, especially when chaining methods. It also saves on typing. For example, compare these two examples:

```java
String luceneField = index
    .getAnnotatedField("contents")
    .getAnnotation("word")
    .getSensitivity(MatchSensitivity.SENSITIVE)
    .getLuceneField();

String luceneField = index
    .annotatedField("contents")
    .annotation("word")
    .sensitivity(MatchSensitivity.SENSITIVE)
    .luceneField();
```


## Important renamed packages and classes

General:
- Searcher -> BlackLabIndex

Classes used while indexing:
- ComplexField -> AnnotatedFieldWriter
- ComplexFieldProperty -> AnnotationWriter
- ComplexFieldUtil -> AnnotatedFieldNameUtil

Index structure:
- IndexStructure -> IndexMetadata
- ComplexFieldDesc -> AnnotatedField
- PropertyDesc -> Annotation

Packages:
- nl.inl.blacklab.search.indexstructure -> .search.indexmetadata
- nl.inl.blacklab.externalstorage -> .contentstore
