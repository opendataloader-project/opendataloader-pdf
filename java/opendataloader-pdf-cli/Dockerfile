FROM eclipse-temurin:17-jre-alpine@sha256:f6ba7667f56ecf95646288862deb66c52fced48fa375111b4f6ff21745b380c7
WORKDIR /app
RUN addgroup -S app && adduser -S -G app appuser
COPY --chown=appuser:app target/opendataloader-pdf-cli-*.jar opendataloader-pdf-cli.jar
USER appuser
ENTRYPOINT ["java","-jar","opendataloader-pdf-cli.jar"]
