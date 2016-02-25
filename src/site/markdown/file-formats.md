# File formats for the forward index and content store

A BlackLab index is just a Lucene index with a few extra components: forward indices and a content store. The forward index is used to produce KWICs (KeyWord In Context hits), to sort or group on hit context, etc. The content store is used to retrieve the original documents. This page documents the file formats used by these components. File layout are described as (nested) lists of primitive types, with a description of what each value represents.   

## Main BlackLab index

As said, this is just a Lucene index with some extras:

- **version.dat** identifies the index as a BlackLab index. It always contains the string "blacklab||2". (The "2" is a version, but is not used right now. It might be used in the future in case of major changes)
- **fi_<i>&lt;fieldname&gt;</i>%<i>&lt;property&gt;</i>** is the forward index for the a specific field and property, for example "fi_contents%lemma" is the forward index for the lemma property of the contents field. It provides a quick way to determine what lemma occurs at a particular corpus position. See below for the layout of this directory.
- **cs_<i>&lt;fieldname&gt;</i>** is the content store for the field. BlackLab indices typically contain only one field with a content store, typically called "contents", so the subdirectory is named "cs_contents". See below for the layout of this directory.

## Forward Index layout

The forward index contains:

- a terms file, which stores all the unique terms that occur in this field property
- a documents file, which stores number of tokens in each document, and where to find them in the tokens file
- a tokens file, which stores the term index for each position in each document
- a version file, which identifies this as a forward index and stores the version

### terms.dat (Terms file)

- int: number of terms n
- some number of blocks (containing all n terms in total), each block:
    - int: number of terms in block, m
    - m x term string byte offset of term in following data block. [0] is always 0.
    - int: size of following data block in bytes, d
    - d bytes: term string data block
    (you're done reading blocks when you've read n terms in total)

(NOTE: the reason for storing terms in blocks is to prevent integer overflow 
for the offset values. We could've used longs for offsets as well, but
if you have that many terms, this saves a nontrivial amount of space)

### docs.dat (Documents file)

- int: number of entries n
- n x long: doc offset (entry number in tokens.dat (x4 to get byte offset) )
- n x int:  doc length (number of tokens)
- n x byte: doc deleted?

NOTE: the forward index id (fiid) is the entry number in this file!

### tokens.dat (Tokens file)

- q x int: term number for all q tokens in the entire corpus

### version.dat

- String "fi||<i>&lt;version&gt;</i>"  (version can currently be 3 or 4)

### (old version (v3) of terms.dat)

- int: number of terms n
- n x int: term string byte offsets in term string data. [0] is always 0.
- int: size of term string data in bytes, b
- int: size of term string data in bytes, b (yes, this occurs twice)
- b bytes: term string data


## Content Store layout

The content store contains:

- a table of contents file, which stores information about the documents and how they are stored
- a contents file, which stores all the document content in a block-based format
- a version file, which identifies this as a content store and stores the version

### toc.dat:

- int: number of entries
- n entries, each entry:
    - int: content store id (cid)
    - int: total length in bytes
    - int: total length in characters
    - boolean: doc deleted?
    - int: number of blocks
    - n x int: indices of blocks containing file contents
    - n x int: corresponding character offset in the file contents for the start of each block (used to determine what blocks to read for certain character offsets)

### file-contents.dat

- a number of fixed-size (4096 bytes) blocks containing zlib-compressed utf-8
  fragments of files. Together with the toc, the original files can be reconstructed.

### version.dat

- String "<i>&lt;type&gt;</i>||<i>&lt;version&gt;</i>"  where type can be fixedblock (current), utf8zip (older), utf8 (legacy) 
  and version is currently always 1.

### (old version (utf8zip) of toc.dat)

- int: number of entries
- n entries, each entry:
    - int: content store id (ciid)
    - int: what CS file doc is stored in
    - int: offset into the CS file of first block
    - int: total length in bytes
    - int: total length in characters
    - boolean: doc deleted?
    - int: size of blocks used (last block may be smaller)
    - int: number of blocks
    - n x int: block offsets in bytes (relative to start offset; first offset is always 0)

### (old version (utf8zip) of file-contents.dat: multiple data\#\#\#\#.dat files)

- a number of encoded blocks, representing a number of whole documents;
  each block is zlib-compressed utf-8. Blocks can have different sizes (they
  have a fixed uncompressed character size - the old toc.dat would tell us where
  a document was stored and how many bytes were used)

