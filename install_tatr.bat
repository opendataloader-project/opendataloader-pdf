git clone https://github.com/opendataloader-project/opendataloader-pdf.git
cd opendataloader-pdf
git checkout table_transformer
git pull origin table_transformer
cd java
mvn clean package -DskipTests
cd ..
cd ..
move opendataloader-pdf\java\opendataloader-pdf-cli\target\opendataloader-pdf-cli-0.0.0.jar .

git clone https://github.com/opendataloader-project/open-pdf-dataloader-tatr.git
