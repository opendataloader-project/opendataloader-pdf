<!-- See CONTRIBUTING.md#submitting-a-pull-request. Title: a Conventional Commit, such as "fix(table): keep cells that follow a figure". -->

Fixes https://github.com/opendataloader-project/opendataloader-pdf/issues/<number>

## Objective

<!-- The problem, quoted from the issue. -->

## Approach

<!-- How this change solves it, in terms a user understands. -->

## Evidence

<!--
Output of:
  python ../opendataloader-testdocs/scripts/run_expect.py <id> \
    --cli before="uvx opendataloader-pdf@latest" --cli after="java -jar <your build>"
At least one expectation fails under "before"; all pass under "after".
-->

**Test document:** <!-- testdocs id -->

**Benchmark:** <!-- ./scripts/bench.sh --check-regression result -->

## Checklist

- [ ] A test fails without this change and passes with it
- [ ] `npm run sync` was run, or no CLI option changed
- [ ] Documentation is updated, or no user-facing behavior changed
- [ ] Every commit is signed off (`git commit -s`)

## AI assistance

- [ ] None
- [ ] AI-assisted — a person wrote it with help from an AI tool
- [ ] AI agent — drafted by an agent, reviewed by its operator (commits carry `Assisted-by:`)
