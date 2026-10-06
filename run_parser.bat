@echo off
setlocal

:: Navigate to script directory
cd /d "%~dp0"

:: Check for virtual environment
if exist "venv\Scripts\activate.bat" (
    call venv\Scripts\activate.bat
) else (
    echo [!] Warning: Virtual environment 'venv' not found. Using system Python...
)

:: Run Python pipeline
python run_pipeline.py %*

endlocal
