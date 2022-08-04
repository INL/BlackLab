# Autogenerate XSLT from input format

**URL** : `/blacklab-server/input-formats/<name>/xslt`

**Method** : `GET`

## Success Response

**Code** : `200 OK`

### Content example

```xml
<xsl:stylesheet version="2.0" exclude-result-prefixes="">
  <xsl:output encoding="utf-8" method="html" omit-xml-declaration="yes"/>
  <xsl:template match="text()" priority="-10"/>
  <xsl:template match="*[local-name(.)='hl']">
  <span class="hl">
  <xsl:apply-templates select="node()"/>
  </span>
  </xsl:template>

  (...etc...)

</xsl:stylesheet>
```

## Error Response

**Code** : `404 Not Found`

### Content example

```json
{
  "code": "NOT_FOUND",
  "message": "The format <name> does not apply to XML-type documents, and cannot be converted to XSLT."
}
```

## TODO

- maybe use a different error than `404 Not Found` if the format exists but does not target XML.