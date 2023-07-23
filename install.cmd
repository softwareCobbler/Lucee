del /F /Q ..\docker\bind-mount\lucee\*

rem as lib jars for docker container
copy loader\target\*.lco ..\docker\bind-mount\lucee\
copy loader\target\*.jar ..\docker\bind-mount\lucee\

rem as dependencies for trufflecf build
copy loader\target\*.lco ..\app\extern\
copy loader\target\*.jar ..\app\extern\
