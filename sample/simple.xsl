<?xml version="1.0"?>
<xsl:stylesheet version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
    <xsl:template match="/">
        <html>
            <body>
                <h2><xsl:value-of select="/params/name" /></h2>
            </body>
        </html>
    </xsl:template>
</xsl:stylesheet>