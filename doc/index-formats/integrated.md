# Files in the integrated index

This describes the new index format that integrates all previously external files (forward index, content store, index metadata, version files) into the Lucene index. The goal is to enable distributed indexing and search using Solr. Eventually this will become the default for new indexes.

> **NOTE:** this index format is in development and documentation may still be a little rough.

The integrated index has a codec name `BlackLab40Codec` and version of 1. (Additional versions or codecs may be added in the future)

## Index metadata

The index metadata (the equivalent to the `indexmetadata.yaml` file from the classic index format) is not written to a segment file (like information related to a document), but instead it is written to a special document in the Lucene index.

The document can be found by searching for a field with the name and value `__index_metadata_marker__`. The metadata is stored, in JSON form, in the field `__index_metadata__`.

For now, we also write the contents to a file called `debug-indexmetadata.json`. This file is only written, never read. The JSON structure corresponds to the JAXB annotations in the `IndexMetadataIntegrated` class.

## Forward index

Forward index files currently have a codec name of `BlackLab40Postings` and a version of 1. (Additional versions or codecs may be added in the future)

### fields - where to find information about each Lucene field

**NOTE:** the Lucene field we're talking about here represents one sensitivity of an annotation on an annotated field. For example: the "insensitive" sensitivity of the "lemma" annotation for the "contents" field (Lucene field name: `contents%lemma@i`). The forward index is always stored with the main sensitivity of the annotation (sensitive if it exists, insensitive otherwise).

**NOTE:** we don't store number of fields; we just read until the end of the file.

**NOTE:** a Lucene field with a forward index will have a `FieldInfo` attribute `BL_hasForwardIndex` with a value of `true`. 

- For each field annotation:
  * Lucene field name (str), e.g. "contents%lemma"
  * offset of field in termindex file (long)
  * offset of field in tokensindex file (long)

This file will have an extension of `.blfi.fields`.

### termindex - where to find term strings

**NOTE:** we don't store number of terms as it follows from the file size. If we decide to switch to using VInts, we could write the number of terms at the end of the file. We don't know it at the beginning, we can only iterate through terms at that time.

- For each field annotation:
  * For each term:
    - offset of term string in terms file (long)

This file will have an extension of `.blfi.termindex`.

### terms - term strings

**NOTE:** do we need this file (and the termindex file) at all, or can we just use Lucene's TermsEnum?
(we would have to keep all terms in memory for fast lookups, so maybe a memory-mapped file is better)

- For each field annotation:
  * For each term:
    - Term string (str)

This file will have an extension of `.blfi.terms`.

### termvec - occurrences of terms in documents (temporary)

This is a temporary file. It is eventually replaced by the tokens file.

- For each field annotation:
  * For each term:
    - For each doc term occurs in:
      * Doc id (int)
      * Number of occurrences (int n)
      - For each occurrence:
        * Position (int)

### tokensindex - where to find tokens (forward index) for each document

- For each field annotation:
  * For each document:
    - offset in the tokens file (long)
    - number of tokens in the document (int)
    - encoding used (byte)

See below for the tokens encodings.

This file will have an extension of `.blfi.tokensindex`.

### tokens - sequence of tokens in each document, per annotation

- For each field annotation:
  * For each document:
    - the document, in the encoding given by the tokensindex file. See below.

This file will have an extension of `.blfi.tokens`.

### Tokens encodings

| Name                | Code | Description                                                                     |
|---------------------|-----:|---------------------------------------------------------------------------------|
| INT_PER_TOKEN       |    1 | One 4-byte integer for each token in the document.                              |
| ALL_TOKENS_THE_SAME |    2 | A single 4-byte value representing the value of all the tokens in the document. |

## Content store

Content store files currently have a codec name of `BlackLab40ContentStore` and a version of 1. (Additional versions or codecs may be added in the future)

Please note: the classic (external) content store used a format with a fixed size compressed block; this was to enable reuse of space. This is no longer needed (Lucene will optimize the index by merging segments), so we now use a fixed-character block. This avoids wasting space and is easier to deal with during compression. The compressed blocks therefore all have different sizes, and we store a (relative) byte offset to each block.

> **FUTURE IMPROVEMENTS:** compression could be improved by using a preset dictionary (see https://www.ietf.org/rfc/rfc1950.txt). A reasonable approach could be to take a chunk from the middle of the first file added (middle to increase the chance we're inside actual text, not metadata) and use that as the dictionary for the entire segment. This should ensure common strings (e.g. XML tags, attributes, common words, etc.) are stored more efficiently in each block.

### fields - where to find information about each Lucene field

**NOTE:** the Lucene field we're talking about here belongs to an annotated field. For example, `contents%cs`.

**NOTE:** we don't store number of fields; we just read until the end of the file.

**NOTE:** a Lucene field with a content store will have a `FieldInfo` attribute `BL_hasContentStore` with a value of `true`.

- block size in characters `CHARS_PER_BLOCK` (int)
  Higher values improve compression ratio. Lower values may improve access time and reduce wasted space. Default is 4096.
- For each field with a content store:
  * Lucene field name (str), e.g. `contents%cs`

This file will have an extension of `.blcs.fields`.

### docindex - where each document starts in the valueindex file

- For each document:
  * Offset in the valueindex file (int)
  * Number of fields with a content store for this document (byte)

This file will have an extension of `.blcs.docindex`.

### valueindex - where each document+field value starts in the blockindex file

- For each document:
  * For each field with a content store:
    - field id (based on order in the fields file) (byte)
    - value length in characters (int)\
      from this follows: `NUMBER_OF_BLOCKS = Math.ceil(LENGTH_IN_CHAR / CHARS_PER_BLOCK)`.
    - codec used (byte)\
      (right now it can either be 0 (uncompressed) or 1 (basic zlib compression))
    - offset in the blockindex file (long)
    - base byte offset in the blocks file (long)\
      (block offsets in blockindex file are relative to this)

This file will have an extension of `.blcs.valueindex`.

### blockindex - where to find data blocks in the blocks file

- For each document:
  * For each field with a content store:
    - For each block:
      * byte offset AFTER this block in the blocks file, relative to base offset \[gotten from valueindex file] (int)\
        (determine block size by subtracting this offset from the previous int in the file, except for the first block, where the offset after the block is equal to the length)

The character offset of block `i` is obviously `i * CHARS_PER_BLOCK`, so the character at offset `j` is stored in the block with index `Math.floor(j / CHARS_PER_BLOCK)`, at offset `j % CHARS_PER_BLOCK`)

This file will have an extension of `.blcs.blockindex`.

### blocks - compressed blocks with the field values

- a number of variable-sized blocks (containing `CHARS_PER_BLOCK` characters) containing zlib-compressed utf-8 fragments of values.

`CHARS_PER_BLOCK` is recorded in the fields file, see above.

This file will have an extension of `.blcs.blocks`.
