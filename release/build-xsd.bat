@echo off
color 0a

echo 此脚本自动生成当前目录下所有xview文件的xsd文件
echo 建议：将此脚本放在项目目录下运行，项目目录或它的子目录下须有io.xview.jar

SETLOCAL ENABLEDELAYEDEXPANSION
set CLASSPATH=.
for /f "delims=" %%i in ('dir  /b/a-d/s *.jar') do (
    set CLASSPATH=!CLASSPATH!;%%i
)

java -classpath "%CLASSPATH%" -Dfile.encoding=UTF-8 io.xview.XViewXml
pause