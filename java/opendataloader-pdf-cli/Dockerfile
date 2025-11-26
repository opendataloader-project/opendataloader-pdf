FROM eclipse-temurin:11-jre
WORKDIR /app
COPY target/opendataloader-pdf-cli-*.jar opendataloader-pdf-cli.jar
RUN groupadd -r app && useradd -r -g app appuser && chown -R appuser:app /app
USER appuser
ENTRYPOINT ["java","-jar","opendataloader-pdf-cli.jar"]
