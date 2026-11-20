@echo off
cd /d "%~dp0"
title AsadovBank - Spring Boot Server
echo ========================================================
echo   AsadovBank - Server Basladilir...
echo ========================================================

:: 8080 portunu yoxla ve kohne proses varsa avtomatik bagla (Port konfliktini aradan qaldirir)
echo 8080 portu yoxlanilir...
powershell -NoProfile -Command "$conns = Get-NetTCPConnection -LocalPort 8080 -ErrorAction SilentlyContinue; if ($conns) { foreach ($c in $conns) { Stop-Process -Id $c.OwningProcess -Force -ErrorAction SilentlyContinue }; Write-Host '8080 portundaki kohne proses azad edildi.' } else { Write-Host '8080 portu serbestdir.' }"

:: Set Java environment
if exist "C:\Users\user\.jdks\ms-21.0.12\bin\java.exe" (
    set "JAVA_HOME=C:\Users\user\.jdks\ms-21.0.12"
    set "PATH=C:\Users\user\.jdks\ms-21.0.12\bin;%PATH%"
)

echo Java: %JAVA_HOME%
echo.
echo Brauzerde http://localhost:8080 avtomatik acilir...
start powershell -NoProfile -Command "Start-Sleep -Seconds 4; Start-Process 'http://localhost:8080'"

:: Eger paketlenmis JAR movcuddursa birbasa suretli baslat
if exist "%~dp0target\financeapp-0.0.1-SNAPSHOT.jar" (
    echo Suretli baslama (JAR rejimi)...
    "%JAVA_HOME%\bin\java.exe" -Dfile.encoding=UTF-8 -jar "%~dp0target\financeapp-0.0.1-SNAPSHOT.jar"
    goto end
)

:: Find Maven
set "MVN_CMD=mvn"
if exist "%~dp0mvnw.cmd" (
    set "MVN_CMD=%~dp0mvnw.cmd"
) else (
    where mvn >nul 2>nul
    if %errorlevel% neq 0 (
        if exist "C:\Users\user\.m2\wrapper\dists\apache-maven-3.9.16-bin\5grr65jo27hi51sujmtcldfovl\apache-maven-3.9.16\bin\mvn.cmd" (
            set "MVN_CMD=C:\Users\user\.m2\wrapper\dists\apache-maven-3.9.16-bin\5grr65jo27hi51sujmtcldfovl\apache-maven-3.9.16\bin\mvn.cmd"
        )
    )
)

echo Spring Boot server ise salinir...
call "%MVN_CMD%" spring-boot:run

:end
pause
