@echo off
setlocal EnableExtensions EnableDelayedExpansion
chcp 936 >nul 2>&1
title CEM 一键启动

rem ============================================================
rem  CEM 客户管理系统  一键启动（后端 8082 + 前端 5175）
rem  用法：直接双击本文件；需要重新打包后端时执行  一键启动.bat build
rem  说明：日志写入项目目录 .run\backend.log 与 .run\frontend.log
rem ============================================================

pushd "%~dp0"
set "ROOT=%CD%"
set "RUNDIR=%ROOT%\.run"
set "JARNAME=cem-server-1.0.0.jar"
set "JAR=backend\target\%JARNAME%"
set "BPORT=8082"
set "FPORT=5175"
set "HEALTH=http://127.0.0.1:%BPORT%/api/health"
set "SITE=http://127.0.0.1:%FPORT%"

if not exist "%RUNDIR%" md "%RUNDIR%" >nul 2>&1

echo.
echo ============================================================
echo    CEM 客户管理系统  ·  一键启动
echo    项目目录：%ROOT%
echo    后端端口：%BPORT%    前端端口：%FPORT%
echo ============================================================
echo.

rem ---------- 1. 运行环境 ----------
echo [1/6] 检查 Java 与 Node 环境
where java >nul 2>&1
if errorlevel 1 (
    if exist "%JAVA_HOME%\bin\java.exe" set "PATH=%JAVA_HOME%\bin;!PATH!"
)
where java >nul 2>&1
if errorlevel 1 goto :no_java
where node >nul 2>&1
if errorlevel 1 (
    if exist "D:\Program Files\nodejs\node.exe" set "PATH=D:\Program Files\nodejs;!PATH!"
)
where node >nul 2>&1
if errorlevel 1 goto :no_node
where npm >nul 2>&1
if errorlevel 1 goto :no_npm
for /f "tokens=3" %%v in ('java -version 2^>^&1 ^| findstr /i "version"') do set "JAVA_VER=%%~v"
for /f "delims=" %%v in ('node -v 2^>nul') do set "NODE_VER=%%v"
echo        Java !JAVA_VER!    Node !NODE_VER!

rem ---------- 2. 端口占用 ----------
echo.
echo [2/6] 检查端口占用
set "BOLD="
call :port_pid %BPORT% BOLD
if defined BOLD (
    echo        后端 %BPORT% 已在运行（PID !BOLD!），跳过启动
) else (
    echo        后端 %BPORT% 空闲
)
set "FOLD="
call :port_pid %FPORT% FOLD
if defined FOLD (
    echo        前端 %FPORT% 已在运行（PID !FOLD!），跳过启动
) else (
    echo        前端 %FPORT% 空闲
)

rem ---------- 3. MySQL ----------
echo.
echo [3/6] 检查 MySQL 服务 MySQL80
sc query MySQL80 >nul 2>&1
if errorlevel 1 (
    echo        [警告] 未找到 MySQL80 服务，后端连接数据库可能失败
) else (
    for /f "tokens=4" %%s in ('sc query MySQL80 ^| findstr /i "STATE"') do set "MSTATE=%%s"
    if /i "!MSTATE!"=="RUNNING" (
        echo        MySQL80 运行中
    ) else (
        echo        MySQL80 未运行，尝试启动
        net start MySQL80 >nul 2>&1
        if errorlevel 1 (
            echo        [警告] 启动 MySQL80 失败，可能需要管理员权限，请手动启动该服务
        ) else (
            echo        MySQL80 已启动
        )
    )
)

rem ---------- 4. 后端 ----------
echo.
if defined BOLD goto :backend_ready
echo [4/6] 启动后端
if /i "%~1"=="build" goto :do_build
if exist "%ROOT%\%JAR%" goto :backend_start
echo        未找到 %JAR%，先执行 Maven 打包
:do_build
call :build_backend
if errorlevel 1 goto :fail
:backend_start
del /q "%RUNDIR%\backend.pid" "%RUNDIR%\backend.parent.pid" >nul 2>&1
pushd "%ROOT%\backend"
start "CEM-后端" /min cmd /c "java -jar target\%JARNAME% > ..\.run\backend.log 2>&1"
popd
echo        后端进程已启动，等待健康检查通过
call :wait_health 150
if errorlevel 1 (
    echo        [错误] 后端 150 秒内未就绪
    call :show_log "%RUNDIR%\backend.log"
    goto :fail
)
echo        后端已就绪：%HEALTH%
call :save_pid backend %BPORT%
goto :backend_ready

:backend_ready
rem ---------- 5. 前端 ----------
echo.
if defined FOLD goto :frontend_ready
echo [5/6] 启动前端
if not exist "%ROOT%\frontend\node_modules\vite\bin\vite.js" (
    echo        未安装前端依赖，开始安装
    call :install_frontend
    if errorlevel 1 goto :fail
)
del /q "%RUNDIR%\frontend.pid" "%RUNDIR%\frontend.parent.pid" >nul 2>&1
pushd "%ROOT%\frontend"
start "CEM-前端" /min cmd /c "node node_modules\vite\bin\vite.js dev > ..\.run\frontend.log 2>&1"
popd
echo        前端进程已启动，等待端口 %FPORT%
call :wait_port %FPORT% 90
if errorlevel 1 (
    echo        [错误] 前端 90 秒内未监听 %FPORT%
    call :show_log "%RUNDIR%\frontend.log"
    goto :fail
)
echo        前端已就绪：%SITE%
call :save_pid frontend %FPORT%
goto :frontend_ready

:frontend_ready
rem ---------- 6. 汇总 ----------
echo.
echo [6/6] 启动结果
set "BP="
set "FP="
call :port_pid %BPORT% BP
call :port_pid %FPORT% FP
if defined BP echo        后端 %BPORT% 监听中，进程 PID !BP!
if defined FP echo        前端 %FPORT% 监听中，进程 PID !FP!
echo.
echo ============================================================
echo    启动完成
echo.
echo    系统首页    %SITE%
echo    健康检查    %HEALTH%
echo    登录账号    admin / admin123
echo    后端日志    %RUNDIR%\backend.log
echo    前端日志    %RUNDIR%\frontend.log
echo.
echo    关闭服务    双击 一键关闭.bat
echo ============================================================
echo.
start "" "%SITE%"
echo 按任意键关闭本窗口，服务会在后台继续运行
pause >nul
popd
exit /b 0

rem ==================== 子过程 ====================

:port_pid
rem %1=端口  %2=接收 PID 的变量名
set "%~2="
for /f "tokens=5" %%a in ('netstat -ano ^| findstr ":%~1 " ^| findstr /i "LISTENING"') do set "%~2=%%a"
exit /b 0

:wait_health
rem %1=超时秒数，轮询后端健康检查接口
set /a WT=%~1
:wh_loop
curl -s -m 2 "%HEALTH%" 2>nul | findstr /i "up" >nul
if not errorlevel 1 exit /b 0
set /a WT-=1
if !WT! LEQ 0 exit /b 1
ping -n 2 127.0.0.1 >nul 2>&1
goto :wh_loop

:wait_port
rem %1=端口  %2=超时秒数
set /a WP=%~2
:wp_loop
set "NOWPID="
call :port_pid %~1 NOWPID
if defined NOWPID exit /b 0
set /a WP-=1
if !WP! LEQ 0 exit /b 1
ping -n 2 127.0.0.1 >nul 2>&1
goto :wp_loop

:save_pid
rem %1=服务名  %2=端口，记录监听进程及其窗口父进程
set "SP="
call :port_pid %~2 SP
if not defined SP (
    echo        [提示] 未能识别端口 %~2 对应的进程 PID
    exit /b 0
)
> "%RUNDIR%\%~1.pid" echo !SP!
for /f "delims=" %%q in ('powershell -NoProfile -Command "(Get-CimInstance Win32_Process -Filter 'ProcessId=!SP!').ParentProcessId"') do set "SPP=%%q"
if defined SPP (
    if not "!SPP!"=="0" (
        > "%RUNDIR%\%~1.parent.pid" echo !SPP!
    )
)
exit /b 0

:show_log
rem %1=日志文件路径
if not exist "%~1" exit /b 0
echo        日志文件：%~1
findstr /i /c:"ERROR" /c:"Exception" /c:"Caused by" "%~1" >nul 2>&1
if not errorlevel 1 echo        日志中存在错误信息，请打开日志文件查看
exit /b 0

:build_backend
set "MVN="
where mvn >nul 2>&1
if not errorlevel 1 set "MVN=mvn"
if not defined MVN (
    if defined MAVEN_HOME (
        if exist "%MAVEN_HOME%\bin\mvn.cmd" set "MVN=%MAVEN_HOME%\bin\mvn.cmd"
    )
)
if not defined MVN (
    for /d %%d in ("D:\Program Files\apache-maven-*") do (
        if exist "%%~d\bin\mvn.cmd" set "MVN=%%~d\bin\mvn.cmd"
    )
)
if not defined MVN (
    if exist "C:\Program Files\apache-maven-3.10.0\bin\mvn.cmd" set "MVN=C:\Program Files\apache-maven-3.10.0\bin\mvn.cmd"
)
if not defined MVN (
    echo        [错误] 未找到 Maven，请安装 Maven 3.9+ 后再试
    exit /b 1
)
echo        使用 Maven：%MVN%
pushd "%ROOT%\backend"
call "%MVN%" -q clean package -DskipTests
set "BUILD_RC=%ERRORLEVEL%"
popd
if not "%BUILD_RC%"=="0" (
    echo        [错误] 后端打包失败，请查看上方 Maven 输出
    exit /b 1
)
if not exist "%ROOT%\%JAR%" (
    echo        [错误] 打包后仍未找到 %JAR%
    exit /b 1
)
echo        后端打包完成
exit /b 0

:install_frontend
pushd "%ROOT%\frontend"
if exist package-lock.json (
    call npm.cmd ci
) else (
    call npm.cmd install
)
set "DEPS_RC=%ERRORLEVEL%"
popd
if not "%DEPS_RC%"=="0" (
    echo        [错误] 前端依赖安装失败
    exit /b 1
)
echo        前端依赖安装完成
exit /b 0

:fail
echo.
echo ============================================================
echo    启动未完成，请根据上面的提示处理
echo    日志目录：%RUNDIR%
echo ============================================================
echo 按任意键关闭本窗口
pause >nul
popd
exit /b 1

:no_java
echo        [错误] 未找到 java，请安装 JDK 17 并配置 JAVA_HOME 或加入 PATH
goto :fail

:no_node
echo        [错误] 未找到 node，请安装 Node.js 18 及以上版本
goto :fail

:no_npm
echo        [错误] 未找到 npm，请检查 Node.js 安装是否完整
goto :fail
