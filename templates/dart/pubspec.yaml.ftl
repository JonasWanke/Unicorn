name: ${project.name}
<#if project.description??>
description: ${project.description}
</#if>
version: ${project.version}
<#if homepage??>
homepage: ${homepage}
</#if>

environment:
  sdk: '>=${minSdkVersion} <3.0.0'

dependencies:
<#if isGenerator??>
  ${basePackage}: ${project.version}
</#if>
  meta: ^1.1.8

dev_dependencies:
<#if isGenerator??>
  build_runner: '>=0.9.0 <1.8.0'
</#if>
  pedantic: ^1.8.0
  test: ^1.6.0
<#if isGenerator??>
  source_gen_test: ^0.1.0+5
  build_test: ^0.10.9+1
</#if>
