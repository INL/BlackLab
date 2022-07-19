# BlackLab development documentation

This repo contains various documents related to BlackLab development:

- [BlackLab Archives of Relevant Knowledge (BARKs)](bark/#readme), high-level descriptions of the project, processes and proposed changes.
- [PLAN.md](../PLAN.md), todo-list for the current major change. Quite technical.
- [Docker](../docker/#readme), information about the BlackLab Docker image
- [Instrumentation](../instrumentation/#readme), information about monitoring BlackLab Server using e.g. Prometheus.
- [Integration tests](../test/#readme), tests for BlackLab Server that are automatically run by GitHub Actions and can also be run manually.
- [BlackLab internals](blacklab-internals.md), which describes the structure of the BlackLab project, important classes, etc.
- Different index formats and the structure of their files:
  - [index with external files (classic)](index-formats/external.md) (separate forward index, content store, indexmetadata.yaml, version.dat) 
  - [integrated index format](index-formats/integrated.md) (everything incorporated in the Lucene index; will eventually become the default for new indexes) 
