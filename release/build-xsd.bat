@echo off

SETLOCAL ENABLEDELAYEDEXPANSION
set CLASSPATH=.
for /f "delims=" %%i in ('dir  /b/a-d/s *.jar') do (
    set CLASSPATH=!CLASSPATH!;%%i
)

java -classpath "%CLASSPATH%" -Dfile.encoding=UTF-8 io.xview.XViewXml