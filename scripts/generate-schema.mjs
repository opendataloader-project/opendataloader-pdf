#!/usr/bin/env node
/**
 * Generates JSON Schema documentation from the single source of truth (schema.json).
 *
 * Usage: node scripts/generate-schema.mjs
 */

import { readFileSync, writeFileSync, mkdirSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { escapeMarkdown, formatTable } from './utils.mjs';

const __dirname = dirname(fileURLToPath(import.meta.url));
const ROOT_DIR = join(__dirname, '..');

// Read schema.json
const schemaPath = join(ROOT_DIR, 'schema.json');
const schema = JSON.parse(readFileSync(schemaPath, 'utf-8'));

const AUTO_GENERATED_HEADER_MDX = `{/* AUTO-GENERATED FROM schema.json - DO NOT EDIT DIRECTLY */}
{/* Run \`npm run generate-schema\` to regenerate */}

`;

/**
 * Get JSON Schema type as a readable string.
 */
function formatType(prop) {
  if (!prop) return 'any';

  if (prop.$ref) {
    const refName = prop.$ref.split('/').pop();
    return `\`${refName}\``;
  }

  if (prop.oneOf) {
    return prop.oneOf.map(formatType).join(' \\| ');
  }

  if (prop.const) {
    return `\`"${prop.const}"\``;
  }

  if (prop.enum) {
    return prop.enum.map(v => `\`${v}\``).join(', ');
  }

  if (Array.isArray(prop.type)) {
    return prop.type.map(t => `\`${t}\``).join(' \\| ');
  }

  if (prop.type === 'array') {
    if (prop.items) {
      return `\`array\``;
    }
    return `\`array\``;
  }

  return `\`${prop.type || 'any'}\``;
}

/**
 * Check if a property is required.
 */
function isRequired(propName, requiredList) {
  return requiredList && requiredList.includes(propName);
}

/**
 * Generate JSON Schema documentation (MDX).
 */
function generateJsonSchemaMdx() {
  const lines = [];
  lines.push('---');
  lines.push('title: JSON Schema');
  lines.push('description: Understand the layout structure emitted by OpenDataLoader PDF');
  lines.push('---');
  lines.push('');
  lines.push(AUTO_GENERATED_HEADER_MDX);

  lines.push('Every conversion that includes the `json` format produces a hierarchical document describing detected elements (pages, tables, lists, captions, etc.). Use the following reference to map fields into your downstream processors.');
  lines.push('');

  // Helper to build rows from schema properties
  const buildRows = (properties, requiredList) =>
    Object.entries(properties).map(([name, prop]) => [
      `\`${name}\``,
      formatType(prop),
      isRequired(name, requiredList) ? 'Yes' : 'No',
      escapeMarkdown(prop.description || '')
    ]);

  // Root node
  const rootRows = buildRows(schema.properties, schema.required);
  lines.push(
    '## Root node',
    '',
    ...formatTable(['Field', 'Type', 'Required', 'Description'], rootRows),
    ''
  );

  // Common content fields (baseElement)
  const baseElement = schema.$defs.baseElement;
  const baseRows = buildRows(baseElement.properties, baseElement.required);
  lines.push(
    '## Common content fields',
    '',
    'All content elements share these base properties:',
    '',
    ...formatTable(['Field', 'Type', 'Required', 'Description'], baseRows),
    ''
  );

  // Text properties
  const textProps = schema.$defs.textProperties;
  const textRows = buildRows(textProps.properties, textProps.required);
  lines.push(
    '## Text properties',
    '',
    'Text nodes (`paragraph`, `heading`, `caption`, `list item`) include these additional fields:',
    '',
    ...formatTable(['Field', 'Type', 'Required', 'Description'], textRows),
    ''
  );

  // Headings
  lines.push(
    '## Headings',
    '',
    ...formatTable(['Field', 'Type', 'Required', 'Description'], [
      ['`heading level`', '`integer`', 'Yes', 'Heading level (e.g., 1 for h1)']
    ]),
    ''
  );

  // Captions
  lines.push(
    '## Captions',
    '',
    ...formatTable(['Field', 'Type', 'Required', 'Description'], [
      ['`linked content id`', '`integer`', 'No', 'ID of the linked content element (table, image, etc.)']
    ]),
    ''
  );

  // Tables
  lines.push(
    '## Tables',
    '',
    ...formatTable(['Field', 'Type', 'Required', 'Description'], [
      ['`number of rows`', '`integer`', 'Yes', 'Row count'],
      ['`number of columns`', '`integer`', 'Yes', 'Column count'],
      ['`previous table id`', '`integer`', 'No', 'Linked table identifier (if broken across pages)'],
      ['`next table id`', '`integer`', 'No', 'Linked table identifier'],
      ['`rows`', '`array`', 'Yes', 'Row objects']
    ]),
    ''
  );

  // Table rows
  lines.push(
    '### Table rows',
    '',
    ...formatTable(['Field', 'Type', 'Required', 'Description'], [
      ['`type`', '`"table row"`', 'Yes', 'Element type'],
      ['`row number`', '`integer`', 'Yes', 'Row index (1-indexed)'],
      ['`cells`', '`array`', 'Yes', 'Cell objects']
    ]),
    ''
  );

  // Table cells
  lines.push(
    '### Table cells',
    '',
    ...formatTable(['Field', 'Type', 'Required', 'Description'], [
      ['`row number`', '`integer`', 'Yes', 'Row index of the cell (1-indexed)'],
      ['`column number`', '`integer`', 'Yes', 'Column index of the cell (1-indexed)'],
      ['`row span`', '`integer`', 'Yes', 'Number of rows spanned'],
      ['`column span`', '`integer`', 'Yes', 'Number of columns spanned'],
      ['`kids`', '`array`', 'Yes', 'Nested content elements']
    ]),
    ''
  );

  // Lists
  lines.push(
    '## Lists',
    '',
    ...formatTable(['Field', 'Type', 'Required', 'Description'], [
      ['`numbering style`', '`string`', 'Yes', 'Marker style (ordered, bullet, etc.)'],
      ['`number of list items`', '`integer`', 'Yes', 'Item count'],
      ['`previous list id`', '`integer`', 'No', 'Linked list identifier'],
      ['`next list id`', '`integer`', 'No', 'Linked list identifier'],
      ['`list items`', '`array`', 'Yes', 'Item nodes']
    ]),
    ''
  );

  // List items
  lines.push(
    '### List items',
    '',
    'List items include text properties plus:',
    '',
    ...formatTable(['Field', 'Type', 'Required', 'Description'], [
      ['`kids`', '`array`', 'Yes', 'Nested content elements']
    ]),
    ''
  );

  // Images
  lines.push(
    '## Images',
    '',
    ...formatTable(['Field', 'Type', 'Required', 'Description'], [
      ['`source`', '`string`', 'No', 'Relative path to the image file'],
      ['`data`', '`string`', 'No', 'Base64 data URI (when embed-images is enabled)'],
      ['`format`', '`string`', 'No', 'Image format (`png`, `jpeg`)']
    ]),
    ''
  );

  // Headers and footers
  lines.push(
    '## Headers and footers',
    '',
    ...formatTable(['Field', 'Type', 'Required', 'Description'], [
      ['`type`', '`string`', 'Yes', 'Either `header` or `footer`'],
      ['`kids`', '`array`', 'Yes', 'Content elements within the header or footer']
    ]),
    ''
  );

  // Text blocks
  lines.push(
    '## Text blocks',
    '',
    ...formatTable(['Field', 'Type', 'Required', 'Description'], [
      ['`kids`', '`array`', 'Yes', 'Text block children']
    ]),
    ''
  );

  lines.push(
    '## JSON Schema',
    '',
    'The complete JSON Schema is available at [`schema.json`](https://github.com/opendataloader-project/opendataloader-pdf/blob/main/schema.json) in the repository root.'
  );
  lines.push('');

  const outputPath = join(ROOT_DIR, 'content/docs/json-schema.mdx');
  mkdirSync(dirname(outputPath), { recursive: true });
  writeFileSync(outputPath, lines.join('\n'));
  console.log(`Generated: ${outputPath}`);
}

// Run all generators
console.log('Generating files from schema.json...\n');

generateJsonSchemaMdx();

console.log('\nDone!');
