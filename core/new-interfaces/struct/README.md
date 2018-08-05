# Interfaces related to index structure

Top-level is IndexStructure, which has AnnotatedField and MetadataFields (both with base interface Field).

The rest of BlackLab should use Field, AnnotatedField, Annotation, AnnotationSensitivity and MetadataField to refer to fields, annotations on them, specific sensitivity-indexed versions of an annotation, etc.

