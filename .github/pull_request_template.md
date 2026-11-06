Closes #

## What
<!-- What changes and why, in a few lines. -->

## Verified
<!-- Commands run and their result (tests, manual checks). Say what was NOT verified. -->

## Checklist (`docs/linee-guida.md`)
- [ ] Branch `claude/<issue>-<topic>` (or a personal branch), never a commit on `main`
- [ ] A module hides a decision; no pass-through layer (rules 1-3); layers respected (`LayerDependencyTests`)
- [ ] One class, one task (19); private methods extracted only with a natural name (18)
- [ ] Precise names (6), no magic values (9), no mutable global data (7), no duplication (5)
- [ ] Comments only where they explain why (11); no unused flexibility (13)
- [ ] Tests added or updated; CI green and read (15)
- [ ] `docs/STATO.md` updated if docs, ADRs, dependencies, data model, CI or a feature changed
- [ ] Non-trivial choice (dependency, data model, costly to undo)? Then an ADR with status `Proposta`
- [ ] No secrets, tokens or `.env` content anywhere in the change or this text
