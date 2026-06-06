@echo off
chcp 65001 >nul
echo 启动两个客户端窗口（用于测试双方聊天、交易）...
start "客户端-A" java -jar client.jar
timeout /t 2 /nobreak >nul
start "客户端-B" java -jar client.jar
echo 两个客户端已启动！
