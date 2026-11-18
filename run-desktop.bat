@echo off
setlocal
pushd "%~dp0desktop" || exit /b 1
call flutter run -d windows
set "result=%errorlevel%"
popd
exit /b %result%
