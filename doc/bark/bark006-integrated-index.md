# BARK 6 - Integrated index format

- **type:** change
- **status:** finished

All external files incorporated into the Lucene index.

## Why?

- It is needed for distributed indexing and search. External files wouldn't be synchronized between nodes by SolrCloud; everything needs to be part of the Lucene index.
- The classic index format doesn't deal well with incremental indexing (esp. deleting and re-adding documents, which fragments the forward index and content store). Re-indexing a large corpus is slow.

## Related documents

- [Integrated index format files](../technical/index-formats/integrated.md)
- [Supporting distributed indexing and search](../technical/design/plan-distributed.md)

## Impact on users

We've added this new index format as an option, in addition to the classic index format. This should not affect existing users.

We are currently running the integrated format in production internally and will gradually roll it out externally as well. Eventually, it will become the default in BlackLab for new indexes. It will still be possible to create classic indexes by specifying an option.

Eventually, the classic index format will be deprecated and ultimately dropped, but that will be on a scale of years, and only if supporting it becomes a burden.
