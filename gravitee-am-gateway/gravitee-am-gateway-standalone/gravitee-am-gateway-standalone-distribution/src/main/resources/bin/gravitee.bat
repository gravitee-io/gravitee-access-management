@REM
@REM Copyright (C) 2015 The Gravitee team (http://gravitee.io)
@REM
@REM Licensed under the Apache License, Version 2.0 (the "License");
@REM you may not use this file except in compliance with the License.
@REM You may obtain a copy of the License at
@REM
@REM         http://www.apache.org/licenses/LICENSE-2.0
@REM
@REM Unless required by applicable law or agreed to in writing, software
@REM distributed under the License is distributed on an "AS IS" BASIS,
@REM WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
@REM See the License for the specific language governing permissions and
@REM limitations under the License.
@REM

@echo off

set DIRNAME=%~dp0

rem set JAVA_HOME=C:\Java\jdk1.8.0_40

for %%B in (%~dp0\.) do set GRAVITEE_HOME=%%~dpB

IF "%JAVA_HOME%"=="" GOTO nojavahome

set JAVA_OPTS="-Djava.net.preferIPv4Stack=true"


set JAVA="%JAVA_HOME%/bin/java"

rem Setup the classpath
for /f %%i in ('dir ..\lib\gravitee-am-gateway-standalone-bootstrap-*.jar /s /b') do set runjar=%%i

set GRAVITEE_BOOT_CLASSPATH=%runjar%

REM enable skip Xms and Xmx to use percentage of resources
if "%GIO_DISABLE_STARTING_MEMORY%" == "" (
    if "%GIO_MIN_MEM%" == "" (
    set GIO_MIN_MEM=256m
    )

    if "%GIO_MAX_MEM%" == "" (
    set GIO_MAX_MEM=256m
    )

    REM min and max heap sizes should be set to the same value to avoid
    REM stop-the-world GC pauses during resize
    set JAVA_OPTS=%JAVA_OPTS% -Xms%GIO_MIN_MEM% -Xmx%GIO_MAX_MEM%
)

# Display our environment
echo "========================================================================="
echo ""
echo "  Gravitee.io - Access Management - Gateway"
echo ""
echo "  GRAVITEE_HOME: %GRAVITEE_HOME%"
echo ""
echo "  JAVA: %JAVA%"
echo ""
echo "  JAVA_OPTS: %JAVA_OPTS%"
echo ""
echo "  CLASSPATH: %GRAVITEE_BOOT_CLASSPATH%  "
echo ""
echo "========================================================================="
echo ""

rem Execute the JVM in the foreground
%JAVA% %JAVA_OPTS% -cp %GRAVITEE_BOOT_CLASSPATH% -Dgravitee.home=%GRAVITEE_HOME% io.gravitee.am.gateway.bootstrap.Bootstrap "%*"

set GRAVITEE_STATUS=%?
goto endbatch


:nojavahome
echo.
echo **************************************************
echo *
echo * WARNING ...
echo * JAVA_HOME must be set before starting Gravitee 
echo * Please check Java documentation to do it 
echo *
echo **************************************************
GOTO endbatch

:endbatch