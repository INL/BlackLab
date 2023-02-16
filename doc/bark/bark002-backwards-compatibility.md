# BARK 2 - Evolution and backward compatibility

- **type:** process
- **status:** active

How we evolve BlackLab over time and deal with backward compatibility.

## Policy

### General

We prefer to support older index versions and existing applications making use of the BlackLab Server REST API and BlackLab Java API. Minor versions should not break compatibility for any of these. Major versions sometimes have to make changes and remove older functionality. We try to minimize the impact on users and will usually provide a migration guide.


### Index format versions

We generally support index formats for as long as possible. For example, for version 3.0, we removed support for index formats that were replaced more than 6 years ago.


### BlackLab Server REST API

We do our best to keep the BlackLab Server REST API stable. We may add a parameter, or a key in a response object in a minor version, but will do our best not to remove anything or make other breaking changes until a major version update.

We've recently introduced a `parameters.apiCompatibility` setting/parameter to deal with minor API changes. 

If we decide to do a major overhaul of the REST API (which has room for improvement), we will try to add it behind a version path (e.g. `/v2/`) and still support the old API as well, at least for a while.


### BlackLab Java API

Methods slated for removal will generally be deprecated first, with a recommended alternative, then removed in the next major version. There may occasionally be exceptions to this, e.g. if the interface changes so much that supporting the deprecated method is not practical.



## Impact on users

Users wishing to upgrade to a new major version of BlackLab may be confrontend with changes to the index format or API. They may have to re-index their data, and may have to consult the migration guide to update their applications.

Users can always opt to delay upgrading and stick with the older version, although if they do that for too long, they may have to backport library upgrades, bugfixes and other enhancements they might want. PRs for backports are appreciated.
