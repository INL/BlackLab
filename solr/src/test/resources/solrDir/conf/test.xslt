<?xml version="1.0"?>
<xsl:stylesheet version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">

    <xsl:output method="text"/>

    <xsl:template match="/">
        Name: <xsl:value-of select="/test/name"/>
        Type: <xsl:apply-templates select="/test/type"/>
    </xsl:template>

</xsl:stylesheet>
