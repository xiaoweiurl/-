@ECHO OFF
SETLOCAL EnableExtensions EnableDelayedExpansion
SET "WRAPPER_JAR=%~dp0.mvn\wrapper\maven-wrapper.jar"
SET "MAVEN_PROJECTBASEDIR=%~dp0"
IF "%MAVEN_PROJECTBASEDIR:~-1%"=="\" SET "MAVEN_PROJECTBASEDIR=%MAVEN_PROJECTBASEDIR:~0,-1%"
SET "JAVA_EXE=java"
IF DEFINED JAVA_HOME IF EXIST "%JAVA_HOME%\bin\java.exe" SET "JAVA_EXE=%JAVA_HOME%\bin\java.exe"

REM cmd treats "=" as an argument separator, so IDEA passes:
REM   -Dmaven.ext.class.path   D:\tools\IntelliJ   IDEA   2025.3.2\...\jar
REM instead of one -D...=path. Rejoin -Dprop + value, then drop the
REM broken maven.ext.class.path (IDE event listener; build works without it).

SET "MAVEN_CMD_LINE_ARGS="

:PARSE
IF "%~1"=="" GOTO RUN
SET "CUR=%~1"
SHIFT

REM Rejoin -Dproperty value when "=" was stripped
ECHO(!CUR!| FINDSTR /B /C:"-D" >NUL
IF NOT ERRORLEVEL 1 (
  ECHO(!CUR!| FINDSTR /C:"=" >NUL
  IF ERRORLEVEL 1 (
    IF NOT "%~1"=="" (
      ECHO(%~1| FINDSTR /B /R /C:"-[A-Za-z]" >NUL
      IF ERRORLEVEL 1 (
        SET "CUR=!CUR!=%~1"
        SHIFT
      )
    )
  )
)

REM Drop -Dmaven.ext.class.path[=...] and following path fragments
ECHO(!CUR!| FINDSTR /B /C:"-Dmaven.ext.class.path" >NUL
IF NOT ERRORLEVEL 1 (
  :SKIP_FRAGMENTS
  IF "%~1"=="" GOTO PARSE
  ECHO(%~1| FINDSTR /B /R /C:"-[A-Za-z]" >NUL
  IF NOT ERRORLEVEL 1 GOTO PARSE
  SHIFT
  GOTO SKIP_FRAGMENTS
)

SET "MAVEN_CMD_LINE_ARGS=!MAVEN_CMD_LINE_ARGS! !CUR!"
GOTO PARSE

:RUN
"%JAVA_EXE%" -classpath "%WRAPPER_JAR%" "-Dmaven.multiModuleProjectDirectory=%MAVEN_PROJECTBASEDIR%" org.apache.maven.wrapper.MavenWrapperMain !MAVEN_CMD_LINE_ARGS!
EXIT /B %ERRORLEVEL%