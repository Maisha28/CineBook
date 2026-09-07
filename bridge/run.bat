@echo off
REM ============================================================
REM  CineBook Bridge — Run Script
REM  Run from: Frontend\bridge\
if exist "..\lib\postgresql-42.7.4.jar" (
    set LIB=..\lib\postgresql-42.7.4.jar
) else (
    set LIB=..\..\Backend\lib\postgresql-42.7.4.jar
)
java -cp "out;%LIB%" BridgeServer
