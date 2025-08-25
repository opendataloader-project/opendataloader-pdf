git clone https://github.com/opendataloader-project/opendataloader-pdf.git
cd opendataloader-pdf
git checkout table_transformer
git pull origin table_transformer
:: cd java
mvn clean install -DskipTests
cd ..
move opendataloader-pdf\target\open-pdf-dataloader-0.0.1.jar .

git clone https://github.com/opendataloader-project/open-pdf-dataloader-tatr.git