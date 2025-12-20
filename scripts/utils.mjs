/**
 * Shared utilities for code generation scripts.
 */

/**
 * Escape string for use in Markdown table cells.
 * @param {string} str - The string to escape
 * @returns {string} - Escaped string safe for Markdown tables
 */
export function escapeMarkdown(str) {
  if (!str) return '';
  return str
    .replace(/\\/g, String.raw`\\`)   // escape backslashes first
    .replace(/\|/g, String.raw`\|`)   // escape pipe characters
    .replace(/`/g, String.raw`\``)    // escape backticks
    .replace(/\*/g, String.raw`\*`)   // escape asterisks
    .replace(/_/g, String.raw`\_`)    // escape underscores
    .replace(/</g, '&lt;')            // escape HTML angle brackets
    .replace(/>/g, '&gt;');
}

/**
 * Format a markdown table with aligned columns.
 * @param {string[]} headers - Table headers
 * @param {string[][]} rows - Table rows (each row is an array of cell values)
 * @returns {string[]} - Formatted table lines
 */
export function formatTable(headers, rows) {
  // Calculate max width for each column
  const colWidths = headers.map((h, i) => {
    const headerLen = h.length;
    const maxRowLen = rows.reduce((max, row) => Math.max(max, (row[i] || '').length), 0);
    return Math.max(headerLen, maxRowLen);
  });

  // Build header row
  const headerRow = '| ' + headers.map((h, i) => h.padEnd(colWidths[i])).join(' | ') + ' |';

  // Build separator row
  const separatorRow = '|' + colWidths.map(w => '-'.repeat(w + 2)).join('|') + '|';

  // Build data rows
  const dataRows = rows.map(row =>
    '| ' + row.map((cell, i) => (cell || '').padEnd(colWidths[i])).join(' | ') + ' |'
  );

  return [headerRow, separatorRow, ...dataRows];
}
