# AI Issue Testing Framework

A test framework for validating AI Issue workflow decisions.

## Purpose

- Prevent regressions when prompts or policies change
- Verify expected decisions for various issue types
- Test cases serve as living documentation

## Directory Structure

```
.github/scripts/ai-issue/
├── build-stage1-prompt.sh           # Build Stage 1 prompts
├── build-stage2-prompt.sh           # Build Stage 2 prompts
├── call-claude-api.sh               # Call Claude API
├── call-claude-code.sh              # Call Claude Code CLI
├── parse-stage1-response.sh         # Parse Stage 1 responses
├── parse-stage2-response.sh         # Parse Stage 2 responses
└── test/
    ├── run-tests.sh                 # Test runner
    ├── cases/
    │   ├── stage1/                  # Stage 1 test cases
    │   │   ├── invalid-*.json
    │   │   ├── duplicate-*.json
    │   │   ├── question-*.json
    │   │   └── valid-*.json
    │   └── stage2/                  # Stage 2 test cases
    │       ├── auto-fix-*.json
    │       └── manual-*.json
    └── fixtures/
        ├── existing-issues.txt      # Existing issues for duplicate detection
        └── readme-excerpt.txt       # README excerpt
```

## Test Case Format

### Stage 1 (Triage)

```json
{
  "name": "Valid bug report with reproduction steps",
  "description": "Clear bug report with repro steps should be classified as valid",
  "input": {
    "title": "PDF parsing fails for password-protected files",
    "body": "## Description\nWhen trying to parse a password-protected PDF...\n\n## Steps to Reproduce\n1. Create a password-protected PDF\n2. Run `parse(file)`\n3. Error thrown\n\n## Environment\n- OS: macOS 14.0\n- Node: 20.10.0"
  },
  "expected": {
    "decision": "valid",
    "duplicate_of": null,
    "reason_contains": ["password", "protected"]
  }
}
```

### Stage 2 (Analyze)

```json
{
  "name": "Simple typo fix should be auto-eligible",
  "description": "Simple typo corrections should be eligible for auto-fix",
  "input": {
    "title": "Typo in error message",
    "body": "The error message says 'Invlaid input' instead of 'Invalid input'",
    "labels": [],
    "comments": []
  },
  "expected": {
    "action": "fix/auto-eligible",
    "labels_include": ["bug"],
    "labels_exclude": ["fix/manual-required"],
    "priority_in": ["P2"],
    "analysis_contains": ["typo", "error message"]
  }
}
```

## Validation Rules

| Field | Validation Method | Example |
|-------|-------------------|---------|
| `decision` | Exact match | `"decision": "valid"` |
| `action` | Exact match | `"action": "fix/auto-eligible"` |
| `duplicate_of` | null or specific value | `"duplicate_of": null` |
| `reason_contains` | Keywords in reason string | `["spam", "gibberish"]` |
| `labels_include` | Labels present in output | `["bug"]` |
| `labels_exclude` | Labels absent from output | `["fix/manual-required"]` |
| `priority_in` | Priority within range | `["P0", "P1"]` |
| `analysis_contains` | Keywords in analysis | `["affected_files"]` |

## Test Case Categories

### Stage 1: Triage

| Category | Expected Result | GitHub Label | Example Cases |
|----------|----------------|--------------|---------------|
| **Invalid** | `decision: "invalid"` | `wontfix` | Spam, ads, gibberish, unrelated to project |
| **Duplicate** | `decision: "duplicate"` | `duplicate` | Same problem as existing issue, similar title/content |
| **Needs Info** | `decision: "needs-info"` | `question` | Missing repro steps, no environment info, unclear description |
| **Valid** | `decision: "valid"` | (none) | Clear bug report, feature request, docs improvement |

### Stage 2: Analyze

| Category | Expected Result | Example Cases |
|----------|----------------|---------------|
| **Auto-fix Eligible** | `action: "fix/auto-eligible"` | Typo fix, type error, lint error, simple bug |
| **Manual Required** | `action: "fix/manual-required"` | Architecture change, security-related, UX decision needed |

## Test Runner Logic

```typescript
// runner.ts pseudocode
async function runTests() {
  const cases = loadTestCases();
  const results = [];

  for (const testCase of cases) {
    // 1. Build prompt identical to workflow
    const prompt = buildPrompt(testCase.input);

    // 2. Call Claude API
    const response = await callClaude(prompt);

    // 3. Parse JSON response
    const parsed = parseResponse(response);

    // 4. Compare with expected values
    const validation = validate(parsed, testCase.expected);

    results.push({
      name: testCase.name,
      passed: validation.passed,
      details: validation.details
    });
  }

  return generateReport(results);
}
```

## Workflow Integration

```yaml
# .github/workflows/ai-issue-test.yml
name: "AI Issue: Tests"

on:
  pull_request:
    paths:
      - '.github/workflows/ai-issue-*.yml'
      - '.github/scripts/ai-issue/**'
      - '.claude/skills/ai-issue/**'
  workflow_dispatch:

jobs:
  test:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - name: Run tests
        run: .github/scripts/ai-issue/test/run-tests.sh
        env:
          ANTHROPIC_API_KEY: ${{ secrets.ANTHROPIC_API_KEY }}
```

## Running Tests

```bash
# Run all tests
.github/scripts/ai-issue/test/run-tests.sh

# Run Stage 1 only
.github/scripts/ai-issue/test/run-tests.sh --stage=1

# Run Stage 2 only
.github/scripts/ai-issue/test/run-tests.sh --stage=2

# Run specific case
.github/scripts/ai-issue/test/run-tests.sh --case=valid-bug-report

# Verbose output
.github/scripts/ai-issue/test/run-tests.sh --verbose

# Dry run (no API calls)
.github/scripts/ai-issue/test/run-tests.sh --dry-run
```

## Report Format

```
========================================
AI Issue Test Runner
========================================

Stage 1: Triage
----------------------------------------
✓ invalid-spam.json (decision: invalid) [3s]
✓ duplicate-table-extraction.json (decision: duplicate) [2s]
✗ question-missing-steps.json [4s]
    → Expected decision 'needs-info', got 'valid'
✓ valid-bug-report.json (decision: valid) [3s]

Stage 2: Analyze
----------------------------------------
✓ auto-fix-simple-bug.json (action: fix/auto-eligible) [45s]
✓ manual-architecture.json (action: fix/manual-required) [52s]

========================================
✓ All tests passed: 5/6
========================================
```

## Cost Considerations

- Each test case requires 1 Claude API call
- Stage 1: Uses Sonnet 4.5 (affordable)
- Stage 2: Uses Haiku 4.5 (more affordable)
- Only runs on PR changes to minimize cost
- Manual execution available via `workflow_dispatch`

## Test Case Writing Guidelines

1. **Clear naming**: Immediately understand what is being tested
2. **Include description**: Explain why this result is expected
3. **Realistic input**: Use issue formats that occur in practice
4. **Edge cases**: Include ambiguous cases (e.g., partial information)
5. **Negative cases**: Verify incorrect classifications don't occur
