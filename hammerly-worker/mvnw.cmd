@ECHO OFF
SETLOCAL
SET "MAVEN_VERSION=3.9.9"
SET "MAVEN_HOME=%USERPROFILE%\.m2\wrapper\dists\apache-maven-%MAVEN_VERSION%\hammerly\apache-maven-%MAVEN_VERSION%"
IF NOT EXIST "%MAVEN_HOME%\bin\mvn.cmd" (
  curl.exe -fSL "https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/%MAVEN_VERSION%/apache-maven-%MAVEN_VERSION%-bin.zip" -o "%TEMP%\apache-maven-%MAVEN_VERSION%-bin.zip"
  IF ERRORLEVEL 1 EXIT /B 1
  powershell.exe -NoProfile -ExecutionPolicy Bypass -Command "$ErrorActionPreference='Stop'; New-Item -ItemType Directory -Force -Path '%USERPROFILE%\.m2\wrapper\dists\apache-maven-%MAVEN_VERSION%\hammerly' | Out-Null; Expand-Archive -Force -LiteralPath '%TEMP%\apache-maven-%MAVEN_VERSION%-bin.zip' -DestinationPath '%USERPROFILE%\.m2\wrapper\dists\apache-maven-%MAVEN_VERSION%\hammerly'"
  IF ERRORLEVEL 1 EXIT /B 1
)
CALL "%MAVEN_HOME%\bin\mvn.cmd" "-Dmaven.repo.local=%USERPROFILE%\.m2\repository" %*
SET "MVNW_EXIT_CODE=%ERRORLEVEL%"
ENDLOCAL & EXIT /B %MVNW_EXIT_CODE%
