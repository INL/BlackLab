# BARK 10 - Searching tree-like structures

- **type:** change
- **status:** in development

We want to enable additional search operations on tree-like structures.

## Why?

We want to be able to search dependency and syntax trees.
This task is part of the CLARIAH-NL project.

## How?

It's now possible to index relationships between (groups of) words, such as dependency relations, and query on them. Querying is limited to explicit parent-child relationships; that is, restrictions on descendants are not supported (yet). We've extended Corpus Query Language to enable relations search. Performance seems to be decent, but certain dedicated solutions may be faster and/or provide more features.

See the [plan](../technical/design/plan-relations.md) for more details.

## When?

Experimental version should be ready before Q3 of 2023. It will be released with version 4.0, probably before the end of 2023.

## Impact on users

None. This is an optional feature.
