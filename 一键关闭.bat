@echo off
setlocal EnableExtensions EnableDelayedExpansion
chcp 936 >nul 2>&1
title CEM 一键关闭

rem ============================================================
rem  CEM 客户管理系统  一键关闭（后端 8082 + 前端 5175）
rem  优先按 一键启动.bat 记录的 PID 结束整个进程树，
rem  再按端口兜底清理，只结束 java / node 进程。
rem ============================================================

pushd "%~dp0"
set "ROOT=%CD%"
set "RUNDIR=%ROOT%\.run"

echo.
echo ============================================================
echo    CEM 客户管理系统  ·  一键关闭
echo    项目目录：%ROOT%
echo ============================================================
echo.

echo [1/3] 关闭前端（5175）
call :stop_service frontend 5175 前端

echo.
echo [2/3] 关闭后端（8082）
call :stop_service backend 8082 后端

echo.
echo [3/3] 校验端口
call :report_port 8082 后端
call :report_port 5175 前端

del /q "%RUNDIR%\backend.pid" "%RUNDIR%\backend.parent.pid" "%RUNDIR%\frontend.pid" "%RUNDIR%\frontend.parent.pid" >nul 2>&1

echo.
echo 处理完毕，日志保留在 %RUNDIR%
echo 按任意键关闭本窗口
pause >nul
popd
exit /b 0

rem ==================== 子过程 ====================

:stop_service
rem %1=服务名  %2=端口  %3=中文名
set "SS_PID="
set "SS_PARENT="
set "SS_KILLED="
set "SS_PARENT_NAME="
set "SS_NOW="
set "SS_NOW_NAME="
if exist "%RUNDIR%\%~1.pid" (
    for /f "delims=" %%r in ('type "%RUNDIR%\%~1.pid"') do set "SS_PID=%%r"
)
if exist "%RUNDIR%\%~1.parent.pid" (
    for /f "delims=" %%r in ('type "%RUNDIR%\%~1.parent.pid"') do set "SS_PARENT=%%r"
)
if defined SS_PARENT (
    for /f "delims=" %%n in ('powershell -NoProfile -Command "(Get-Process -Id !SS_PARENT! -ErrorAction SilentlyContinue).ProcessName"') do set "SS_PARENT_NAME=%%n"
    if /i "!SS_PARENT_NAME!"=="cmd" (
        echo        结束%~3窗口与进程树 PID !SS_PARENT!
        taskkill /F /T /PID !SS_PARENT! >nul 2>&1
        if not errorlevel 1 set "SS_KILLED=1"
    )
)
if defined SS_PID (
    taskkill /F /T /PID !SS_PID! >nul 2>&1
    if not errorlevel 1 set "SS_KILLED=1"
)
call :port_pid %~2 SS_NOW
if defined SS_NOW (
    for /f "delims=" %%n in ('powershell -NoProfile -Command "(Get-Process -Id !SS_NOW! -ErrorAction SilentlyContinue).ProcessName"') do set "SS_NOW_NAME=%%n"
    if /i "!SS_NOW_NAME!"=="java" call :force_kill %~2 %~3
    if /i "!SS_NOW_NAME!"=="node" call :force_kill %~2 %~3
    if /i "!SS_NOW_NAME!"=="javaw" call :force_kill %~2 %~3
)
if defined SS_KILLED (
    echo        %~3服务已关闭
) else (
    echo        未发现运行中的%~3服务
)
exit /b 0

:force_kill
rem %1=端口  %2=中文名
set "FK="
call :port_pid %~1 FK
if not defined FK exit /b 0
echo        端口 %~1 仍被 PID !FK! 占用，按端口清理
taskkill /F /T /PID !FK! >nul 2>&1
if not errorlevel 1 set "SS_KILLED=1"
ping -n 2 127.0.0.1 >nul 2>&1
exit /b 0

:report_port
rem %1=端口  %2=中文名
set "RP="
call :port_pid %~1 RP
if defined RP (
    echo        %~2 %~1 仍被 PID !RP! 占用，请检查
) else (
    echo        %~2 %~1 已释放
)
exit /b 0

:port_pid
rem %1=端口  %2=接收 PID 的变量名
set "%~2="
for /f "tokens=5" %%a in ('netstat -ano ^| findstr ":%~1 " ^| findstr /i "LISTENING"') do set "%~2=%%a"
exit /b 0
