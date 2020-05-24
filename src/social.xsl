<?xml version="1.0" encoding="UTF-8" ?>

<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
                xmlns:xsd="http://www.w3.org/2001/XMLSchema"
                xmlns:svg="http://www.w3.org/2000/svg"
                version="2.0">

  <xsl:param name="projectName"
             as="xsd:string"
             required="yes"/>

  <xsl:param name="projectURL"
             as="xsd:string"
             required="yes"/>

  <xsl:param name="projectDescription"
             as="xsd:string"
             required="yes"/>

  <xsl:template match="@*|*|processing-instruction()|comment()">
    <xsl:copy>
      <xsl:apply-templates select="*|@*|text()|processing-instruction()|comment()"/>
    </xsl:copy>
  </xsl:template>

  <xsl:template match="//svg:text[@id='projectURL']/svg:tspan/svg:tspan/text()">
    <xsl:value-of select="$projectURL"/>
  </xsl:template>

  <xsl:template match="//svg:text[@id='projectTitle']/svg:tspan/svg:tspan/text()">
    <xsl:value-of select="$projectName"/>
  </xsl:template>

  <xsl:template match="//svg:text[@id='projectDescription']/svg:tspan/svg:tspan/text()">
    <xsl:value-of select="$projectDescription"/>
  </xsl:template>

</xsl:stylesheet>
