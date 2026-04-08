// Simple Express web server that converts uploaded PDF files to Markdown
// using the bundled opendataloader-pdf-cli JAR.

import express from 'express';
import multer from 'multer';
import { spawn } from 'node:child_process';
import { fileURLToPath } from 'node:url';
import path from 'node:path';
import fs from 'node:fs';
import os from 'node:os';
import crypto from 'node:crypto';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

// Resolve the bundled JAR (copied into node/opendataloader-pdf/lib by setup).
// Allow override via ODL_JAR env var for flexibility.
const JAR_PATH =
  process.env.ODL_JAR ||
  path.resolve(__dirname, '..', 'node', 'opendataloader-pdf', 'lib', 'opendataloader-pdf-cli.jar');

if (!fs.existsSync(JAR_PATH)) {
  console.error(`ERROR: JAR not found at ${JAR_PATH}`);
  console.error('Build it with `mvn -f java/pom.xml package -DskipTests`');
  console.error('or set the ODL_JAR environment variable to point at the CLI jar.');
  process.exit(1);
}

const PORT = Number(process.env.PORT || 3000);
const MAX_UPLOAD_BYTES = 50 * 1024 * 1024; // 50 MB

const upload = multer({
  storage: multer.memoryStorage(),
  limits: { fileSize: MAX_UPLOAD_BYTES },
  fileFilter: (_req, file, cb) => {
    if (file.mimetype === 'application/pdf' || file.originalname.toLowerCase().endsWith('.pdf')) {
      cb(null, true);
    } else {
      cb(new Error('Only PDF files are accepted.'));
    }
  },
});

const app = express();
app.use(express.static(path.join(__dirname, 'public')));

/**
 * Run the opendataloader-pdf CLI on a single PDF and return the produced
 * markdown text. Cleans up the temp working directory afterwards.
 */
function convertPdfToMarkdown(pdfBuffer, originalName) {
  return new Promise((resolve, reject) => {
    const safeBase = (path.basename(originalName, path.extname(originalName)) || 'document')
      .replace(/[^A-Za-z0-9._-]/g, '_');

    const workDir = fs.mkdtempSync(path.join(os.tmpdir(), 'odl-web-'));
    const inputPath = path.join(workDir, `${safeBase}.pdf`);
    const outputDir = path.join(workDir, 'out');
    fs.mkdirSync(outputDir);
    fs.writeFileSync(inputPath, pdfBuffer);

    const args = ['-jar', JAR_PATH, inputPath, '-o', outputDir, '-f', 'markdown', '-q'];
    const child = spawn('java', args, { stdio: ['ignore', 'pipe', 'pipe'] });

    let stderr = '';
    child.stderr.on('data', (chunk) => {
      stderr += chunk.toString();
    });

    child.on('error', (err) => {
      cleanup(workDir);
      if (err.code === 'ENOENT') {
        reject(new Error("'java' command not found. Install JDK 11+ and ensure it is on PATH."));
      } else {
        reject(err);
      }
    });

    child.on('close', (code) => {
      if (code !== 0) {
        cleanup(workDir);
        reject(new Error(`opendataloader-pdf exited with code ${code}.\n${stderr}`));
        return;
      }

      try {
        const mdPath = findMarkdownOutput(outputDir, safeBase);
        if (!mdPath) {
          throw new Error('Conversion succeeded but no markdown output was produced.');
        }
        const markdown = fs.readFileSync(mdPath, 'utf8');
        resolve(markdown);
      } catch (err) {
        reject(err);
      } finally {
        cleanup(workDir);
      }
    });
  });
}

function findMarkdownOutput(outputDir, baseName) {
  // The CLI writes <basename>.md (sometimes inside a subdir for multi-file runs).
  const direct = path.join(outputDir, `${baseName}.md`);
  if (fs.existsSync(direct)) return direct;

  const stack = [outputDir];
  while (stack.length) {
    const dir = stack.pop();
    for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
      const full = path.join(dir, entry.name);
      if (entry.isDirectory()) {
        stack.push(full);
      } else if (entry.isFile() && entry.name.toLowerCase().endsWith('.md')) {
        return full;
      }
    }
  }
  return null;
}

function cleanup(dir) {
  try {
    fs.rmSync(dir, { recursive: true, force: true });
  } catch {
    // ignore
  }
}

app.post('/api/convert', upload.single('pdf'), async (req, res) => {
  if (!req.file) {
    res.status(400).json({ error: 'No PDF file provided. Upload one in the "pdf" field.' });
    return;
  }

  try {
    const markdown = await convertPdfToMarkdown(req.file.buffer, req.file.originalname);
    const downloadName =
      (path.basename(req.file.originalname, path.extname(req.file.originalname)) || 'document') +
      '.md';

    if (req.query.download === '1') {
      res.setHeader('Content-Type', 'text/markdown; charset=utf-8');
      res.setHeader('Content-Disposition', `attachment; filename="${downloadName}"`);
      res.send(markdown);
      return;
    }

    res.json({ filename: downloadName, markdown });
  } catch (err) {
    console.error('Conversion failed:', err);
    res.status(500).json({ error: err.message || String(err) });
  }
});

app.use((err, _req, res, _next) => {
  if (err instanceof multer.MulterError) {
    res.status(400).json({ error: err.message });
    return;
  }
  if (err) {
    res.status(400).json({ error: err.message || 'Upload failed.' });
    return;
  }
  res.status(500).json({ error: 'Unknown error' });
});

app.listen(PORT, () => {
  const id = crypto.randomBytes(3).toString('hex');
  console.log(`opendataloader-pdf webapp [${id}] listening on http://localhost:${PORT}`);
  console.log(`Using JAR: ${JAR_PATH}`);
});
