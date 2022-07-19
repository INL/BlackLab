# BARK 6 - Integrated index format

- **type:** change
- **status:** in development

All external files incorporated into the Lucene index.

## Why?

- It is needed for distributed indexing and search. External files wouldn't be synchronized between nodes by SolrCloud; everything needs to be part of the Lucene index.
- The classic index format doesn't deal well with incremental indexing (esp. deleting and re-adding documents, which fragments the forward index and content store). Re-indexing a large corpus is slow.

## Related documents

- [Integrated index format files](../index-formats/integrated.md)
- [PLAN.md](../../PLAN.md)

## Impact on users

We're adding this new index format as an option, in addition to the classic index format. This should not affect existing users.

In a future version, when we've verified that the integrated format works well, we will make it the default for new indexes. It will still be possible to create classic indexes by specifying an option.

Eventually, the classic index format will be deprecated and eventually dropped, but that will be on a scale of years, and only if supporting it becomes a burden.
