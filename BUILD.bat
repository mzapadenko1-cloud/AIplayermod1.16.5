@echo off
echo === AI Player Mod Builder ===
echo.

REM Проверяем Java
java -version 2>nul
if errorlevel 1 (
    echo [ОШИБКА] Java не найдена! Установи Java 16+ с https://adoptium.net
    pause
    exit /b 1
)

REM Проверяем есть ли Gradle установлен глобально
where gradle >nul 2>nul
if not errorlevel 1 (
    echo Найден системный Gradle, использую его...
    gradle build
    goto done
)

REM Скачиваем Gradle через PowerShell
echo Скачиваю Gradle 7.4 (нужен интернет, ~100MB)...
powershell -Command "& {[Net.ServicePointManager]::SecurityProtocol='Tls12'; Invoke-WebRequest -Uri 'https://services.gradle.org/distributions/gradle-7.4-bin.zip' -OutFile 'gradle-7.4.zip'}"

if not exist gradle-7.4.zip (
    echo [ОШИБКА] Не удалось скачать Gradle. Проверь интернет.
    pause
    exit /b 1
)

echo Распаковываю Gradle...
powershell -Command "Expand-Archive -Path 'gradle-7.4.zip' -DestinationPath 'gradle-dist' -Force"

echo Собираю мод...
gradle-dist\gradle-7.4\bin\gradle.bat build

:done
echo.
if exist build\libs\aiplayermod-1.0.0.jar (
    echo [OK] JAR готов: build\libs\aiplayermod-1.0.0.jar
    echo Скопируй его в папку mods в TLauncher!
) else (
    echo [ОШИБКА] Что-то пошло не так, JAR не создан.
)
pause
