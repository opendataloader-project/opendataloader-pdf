<!--
Title: use Conventional Commits, for example "fix(core): keep the rows of a split table in order".
A squash merge uses the title as the commit message on main.
Fix one problem in each pull request.
-->

## Objective

<!-- The problem for the user, and what is different after this change. -->

## Approach

<!-- How the change fixes the problem, and why you chose this way. -->

## Evidence

<!--
The commands that you ran, and their results.
If the output changes, show a short before and after.
CI runs the tests and the benchmark again when the change touches a path in the `paths` filter of
.github/workflows/test-benchmark.yml (for example java/, python/, or node/).
-->

## Review notes

<!-- Optional: risks, open questions, and problems that you saw outside this change. -->

## AI assistance

<!-- If an AI tool helped, name the tool and the model, and say what you checked yourself. If no AI tool helped, remove this section. -->

**Issue resolved by this Pull Request:**
Resolves #

**Checklist:**

<!-- Remove each line that does not apply to this change. -->

- [ ] Tests cover the change.
- [ ] I ran `npm run sync`, because I changed CLI options.
- [ ] No released option, option value, or public API is removed ([compatibility rules](https://github.com/opendataloader-project/opendataloader-pdf/blob/main/CONTRIBUTING.md#-compatibility-of-released-options)).
- [ ] A maintainer agreed to this new option, default, output field, or dependency: <!-- link -->
- [ ] The documentation is updated.
