# Working Together

We're two people in different time zones (India IST / US). GitHub is the single source of truth.

## Ground rules

- **Docs and decisions** go in `docs/` as Markdown. Mark items as *proposed* until both agree, then *decided*.
- **Code** goes in `app/`, **data** in `data/`, **mockups** in `design/`.
- Work on a **branch**, open a **pull request**, and let the other person review before merging to `main`.
- Never commit secrets (API keys, Meta developer tokens, passwords). Use a local `.env` file — it's in `.gitignore`.
- Use **GitHub Issues** for tasks and questions so nothing gets lost across time zones.

## Changing code

1. `git checkout main && git pull` to start from the latest code.
2. `git checkout -b your-change` (a short name for what you're doing).
3. Make the change. Android: `cd app/android && ./gradlew test`. iPhone: `cd app/ios/PlanesKit && swift test`.
4. `git add -A && git commit -m "What changed and why"` then `git push -u origin your-change`.
5. Open the pull request link Git prints. GitHub's **Actions** check builds both apps automatically;
   a red ✗ means something broke (click it to see where).
6. The other person reviews and merges.

The engine logic exists twice (Kotlin for Android in `app/android/core`, Swift for iPhone in
`app/ios/PlanesKit`). If you change how planes are picked or what is spoken, change both and keep
their tests the same.

## Using Claude on this repo

Each of us uses our own Claude account. To pick up where the other left off:
- Point Claude at this repo — it reads `CLAUDE.md` for project context.
- Update `docs/` with decisions so the next session (yours or mine) starts informed.
