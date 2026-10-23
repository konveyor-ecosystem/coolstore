## Execute
- Status: failed
- Error: `docs/plan.md` not found. It is missing from the working tree, from git history on all branches, and from the filesystem. No migration steps were run and no source files were changed.
- Note: `.konveyor/analysis.json` (Konveyor analysis output) exists, but the execute stage only follows steps from `docs/plan.md` and does not write its own plan. The planning stage needs to produce `docs/plan.md` before this stage is run again.

| Step | File | Action | Result | Error |
|------|------|--------|--------|-------|
| — | docs/plan.md | — | failed | Plan file not found; no steps to execute |
