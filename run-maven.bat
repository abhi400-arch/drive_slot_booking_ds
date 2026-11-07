@echo off
setlocal
if "%DRIVESLOT_DB_USER%"=="" set DRIVESLOT_DB_USER=root
if "%DRIVESLOT_DB_PASSWORD%"=="" set DRIVESLOT_DB_PASSWORD=root
mvn clean compile exec:java
endlocal
