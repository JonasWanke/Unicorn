name: ${project.name}<#if project.description??>
description: ${project.description}</#if>
version: ${project.version}<#if homepage??>
homepage: ${homepage}</#if>
author: ${author}<#if authorEmail??> <${authorEmail}></#if>

environment:
  sdk: '>=${minSdkVersion} <3.0.0'

#dependencies:
#  path: ^1.6.0

dev_dependencies:
  pedantic: ^1.8.0
  test: ^1.6.0
