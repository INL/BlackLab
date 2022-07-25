# Example XPaths

[When using Saxonica](/guide/how-to-configure-indexing.md#xpath-support-level) you have extensive possibilities using XPath in BlackLab configuration. Some noteworthy examples are shown below (thanks @eduarddrenth).

## punctPath

To capture text content between `<w/>` tags:

`.//text()[.!='' and preceding-sibling::tei:w]|.//tei:pc |.//tei:lb`

## valuePath

A more complex expression with conditionals and variables:

`let $xid := @xml:id return if (@lemma) then @lemma else if ($xid) then following-sibling::tei:join[@lemma][matches(@target,'#'||$xid||'( |$)')]/@lemma else ()`

- use of if then else can significantly speed up processing
- variables in xpath obsolete the need for captureValuePaths

## Advanced

You can also do stuff like this:

```xpath
for $w in //tei:w[@xml:id]
return let $xid := $w/@xml:id
    return 
    if ($w/@lemma) then
        $w/@lemma else
            if ($xid) then
                let $join := $w/following-sibling::tei:join[@lemma][matches(@target,concat('#',$xid,'( |$)'))]
                return
                $join/@lemma else
                ()
```
