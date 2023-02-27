@echo off
@call "C:\Program Files (x86)\Microsoft Visual Studio\2017\Community\VC\Auxiliary\Build\vcvarsall.bat" amd64

IF "%1"=="" (
    @call PowerShell.exe -ExecutionPolicy Bypass -Command "& '%~dpn0.ps1'"
) ELSE (
    @call PowerShell.exe -ExecutionPolicy Bypass -Command "& '%~dpn0.ps1' -OutputDir %1"
)

PAUSE