# Working Together

We're two people in different time zones (India IST / US). GitHub is the single source of truth.

## Ground rules

- **Docs and decisions** go in `docs/` as Markdown. Mark items as *proposed* until both agree, then *decided*.
- **Code** goes in `app/`, **data** in `data/`, **mockups** in `design/`.
- Work on a **branch**, open a **pull request**, and let the other person review before merging to `main`.
- Never commit secrets (API keys, Meta developer tokens, passwords). Use a local `.env` file — it's in `.gitignore`.
- Use **GitHub Issues** for tasks and questions so nothing gets lost across time zones.

## Using Claude on this repo

Each of us uses our own Claude account. To pick up where the other left off:
- Point Claude at this repo — it reads `CLAUDE.md` for project context.
- Update `docs/` with decisions so the next session (yours or mine) starts informed.
