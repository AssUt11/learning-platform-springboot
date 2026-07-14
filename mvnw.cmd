@echo off
setlocal

set "MAVEN_HOME=%~dp0.tools\apache-maven-3.9.16"
set "JAVA_HOME=C:\Program Files\Java\jdk-17"
set "PATH=%JAVA_HOME%\bin;%MAVEN_HOME%\bin;%PATH%"

"%MAVEN_HOME%\bin\mvn.cmd" %*
