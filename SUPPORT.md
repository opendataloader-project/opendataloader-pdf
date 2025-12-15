# Support

This project uses GitHub Issues to track bugs and feature requests. Please search the existing
issues before filing new issues to avoid duplicates. For new issues, file your bug or
feature request as a new Issue.

For help and questions about using this project, please contact our team via Teams or tag us in the issues.

## AI-Powered Issue Processing

This project uses AI to automatically process GitHub issues through a three-stage workflow:

### How It Works

1. **Triage**: Validates your issue (checks for duplicates, spam, and project scope)
2. **Analyze**: Analyzes the codebase to understand the issue and determine the best approach
3. **Fix**: Automatically creates a PR for eligible issues

### What to Expect

After submitting an issue, you may see these labels:

| Label | Meaning |
|-------|---------|
| `fix/auto-eligible` | AI can automatically fix this issue |
| `fix/manual-required` | Requires human expert review |
| `fix/comment-only` | No code change needed; resolved via comment |

### Commands (CODEOWNERS only)

- `@ai-issue analyze` - Request re-analysis of an issue
- `@ai-issue fix` - Trigger automatic fix attempt
