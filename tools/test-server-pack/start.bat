@echo off
chcp 65001 >nul
title LiteBrawl Test Server
cd /d "%~dp0"

echo ==============================================
echo   LiteBrawl Test Server
echo ==============================================
echo.

where java >nul 2>nul
if errorlevel 1 (
    echo [ERROR] Java not found.
    echo Please install Java 21 first: https://adoptium.net
    echo.
    pause
    exit /b 1
)

java -version 2>&1 | findstr /i "version" >nul
if errorlevel 1 (
    echo [ERROR] "java" exists but does not work. Check your JAVA_HOME / PATH.
    pause
    exit /b 1
)

findstr /i "eula=true" eula.txt >nul 2>nul
if errorlevel 1 (
    echo [STEP 1] You must accept the Minecraft EULA before the first run.
    echo          Open eula.txt and change  eula=false  to  eula=true
    echo          EULA: https://aka.ms/MinecraftEULA
    echo.
    echo          Then run this script again.
    echo.
    pause
    exit /b 1
)

if not exist paper.jar (
    echo [ERROR] paper.jar not found next to this script.
    pause
    exit /b 1
)

echo Starting server...
echo First start generates the world, usually takes 30-60 seconds.
echo When it is ready, the console prints the LiteBrawl web console URL.
echo Type "stop" in this window to shut down safely.
echo.
java -Xms1G -Xmx2G -jar paper.jar nogui

echo.
echo Server stopped.
pause
