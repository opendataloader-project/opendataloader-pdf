git clone https://github.com/opendataloader-project/opendataloader-pdf.git
cd opendataloader-pdf
git checkout tesseract_ocr
git pull origin tesseract_ocr
:: cd java
call mvn clean install -DskipTests
cd ..
:: cd ..
move opendataloader-pdf\target\open-pdf-dataloader-0.0.1.jar .

setlocal enabledelayedexpansion
set "BASE_URL=https://github.com/tesseract-ocr/tessdata/raw/main/"
if not exist "tessdata" mkdir tessdata
if "%~1"=="" (
    echo [INFO] No languages specified. Using default: eng
    set "languages=eng"
) else (
    set "languages=%*"
)

for %%L in (%languages%) do (
    set "lang=%%L"
    set "filename=!lang!.traineddata"
    
    echo.
    echo [INFO] Downloading !filename!...
    curl -L -o "tessdata\!filename!" "%BASE_URL%!filename!"
    
    if exist "tessdata\!filename!" (
        echo [SUCCESS] !filename! downloaded
    ) else (
        echo [ERROR] Failed to download !filename!
    )
)