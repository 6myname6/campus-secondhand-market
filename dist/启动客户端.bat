@echo off
chcp 65001 >nul
title 二手交易 - 客户端
echo ========================================
echo   二手交易平台 - 客户端
echo   默认连接 127.0.0.1:8081
echo   可在登录界面修改服务器地址
echo ========================================
echo.
if "%1"=="" (
    java -jar client.jar
) else if "%2"=="" (
    java -jar client.jar %1
) else (
    java -jar client.jar %1 %2
)
