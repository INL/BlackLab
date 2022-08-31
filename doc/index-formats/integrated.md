# Files in the integrated index

This describes the new index format that integrates all previously external files (forward index, content store, index metadata, version files) into the Lucene index. The goal is to enable distributed indexing and search using Solr. Eventually this will become the default for new indexes.

> **NOTE:** this index format is in development and documentation may still be a little rough.

## fields - where to find information about each Lucene field (BlackLab annotation).

**NOTE:** a "field annotation" is a Lucene field that belongs to an annotated field. An example is the "lemma" annotation for the "contents" field (Lucene field name: `contents%lemma@i` for the insensitive alternative or `contents%lemma@s` for the sensitive one).

**NOTE:** we don't store number of fields; we just read until the end of the file.

- For each field annotation:
  * Lucene field name (str), e.g. "contents%lemma"
  * offset of field in termindex file (long)
  * offset of field in tokensindex file (long)

## termindex - where to find term strings

**NOTE:** we don't store number of terms as it follows from the file size. If we decide to switch to using VInts, we could write the number of terms at the end of the file. We don't know it at the beginning, we can only iterate through terms at that time.

- For each field annotation:
  * For each term:
    - offset of term string in terms file (long)

## terms - term strings

**NOTE:** do we need this file (and the termindex file) at all, or can we just use Lucene's TermsEnum?
(we would have to keep all terms in memory for fast lookups, so maybe a memory-mapped file is better)

- For each field annotation:
  * For each term:
    - Term string (str)

## termvec - occurrences of terms in documents (temporary)

This is a temporary file. It is eventually replaced by the tokens file.

- For each field annotation:
  * For each term:
    - For each doc term occurs in:
      * Doc id (int)
      * Number of occurrences (int n)
      - For each occurrence:
        * Position (int)

## tokensindex - where to find tokens (forward index) for each document

- For each field annotation:
  * For each document:
    - offset in the tokens file (long)
    - number of tokens in the document (int)
    - encoding used (byte)

See below for the tokens encodings.

## tokens - sequence of tokens in each document, per annotation

- For each field annotation:
  * For each document:
    - the document, in the encoding given by the tokensindex file. See below.

## Tokens encodings

| Name                | Code | Description                                                                     |
|---------------------|-----:|---------------------------------------------------------------------------------|
| INT_PER_TOKEN       |    1 | One 4-byte integer for each token in the document.                              |
| ALL_TOKENS_THE_SAME |    2 | A single 4-byte value representing the value of all the tokens in the document. |
