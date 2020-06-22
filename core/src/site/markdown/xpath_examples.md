# example xpath's for use in configuration

Especially when using saxonica you have extensive possibilities using xpath in blacklab configuration. Some noteworthy examples you find here.

- punctPath: ".//text()[.!='' and preceding-sibling::tei:w]|.//tei:pc |.//tei:lb"
-   valuePath: "```let $xid := @xml:id return if (@lemma) then @lemma else if ($xid) then following-sibling::tei:join[@lemma][matches(@target,'#'||$xid||'( |$)')]/@lemma else ()```"
    - use of if then else can significantly speed up processing
    - variables in xpath obsolete the need for captureValuePaths
- You can also do stuff like this:
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