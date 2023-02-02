# BlackLab common classes

Classes used by several BlackLab modules or applications, such as exceptions.

This also includes classes that are also used by e.g. the BlackLab Solr module (`solr`) or the BlackLab reverse proxy server (`proxy`), such as `WebserviceParameter` and `WebserviceOperation`.

A note about exception classes: maybe modules should eventually get their own module-specific exception classes, although there will likely always be a few shared exceptions such as `BLRuntimeException`.
