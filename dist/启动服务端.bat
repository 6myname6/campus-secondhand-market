@echo off
chcp 65001 >nul
title 二手交易 - 服务端
echo ========================================
echo   二手交易平台 - 服务端
echo   端口: 8081
echo ========================================
echo.
java -jar server.jar
pause
