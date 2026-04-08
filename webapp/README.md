# PDF → Markdown Web App

A small self-contained web app that turns any PDF into Markdown using the
[opendataloader-pdf](https://github.com/opendataloader-project/opendataloader-pdf)
CLI engine bundled in this repo.

## Requirements

- **Java 11+** (`java -version`)
- **Node.js 20+**
- The bundled CLI JAR at
  `node/opendataloader-pdf/lib/opendataloader-pdf-cli.jar`. Build it with:

  ```bash
  mvn -f java/pom.xml package -DskipTests
  cp java/opendataloader-pdf-cli/target/opendataloader-pdf-cli-*.jar \
     node/opendataloader-pdf/lib/opendataloader-pdf-cli.jar
  ```

  (Or set `ODL_JAR=/path/to/opendataloader-pdf-cli.jar` when running the
  server to point at any other build.)

## Run

```bash
cd webapp
npm install
npm start
```

Then open http://localhost:3000 and drop a PDF onto the page. The converted
Markdown shows up below the upload box and you can copy it to the clipboard or
download it as a `.md` file.

Set `PORT=8080 npm start` to listen on a different port.

## API

The page uses a single endpoint:

```
POST /api/convert
Content-Type: multipart/form-data
Field: pdf=<your.pdf>
```

Response (JSON):

```json
{ "filename": "your.md", "markdown": "# ..." }
```

Append `?download=1` to get the markdown back as a `.md` attachment instead of
JSON.
