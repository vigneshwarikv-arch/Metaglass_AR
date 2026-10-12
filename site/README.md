# site/ — marketing website

Plain HTML/CSS/JS, no build step.

- `index.html` — home page for MVP 1 ("What's that plane?"). Mobile-first: on a phone, the headline and all four MVP 1 features fit on the first screen without scrolling.
- `landing.html` — same page without the `<html>`/`<head>`/`<body>` wrapper (used for Claude artifact previews). Keep it in sync with `index.html` — regenerate it with the command below after every edit.
- `pilot.html` — the earlier pilot-mode page ("HeadsUp Pilot" was a working name), linked from the home page's "Pilot mode — coming soon" section.

Regenerate `landing.html` (PowerShell, from this folder):

```powershell
$skip = '<!doctype html>','<html lang="en">','<head>','<meta charset="utf-8">','<meta name="viewport" content="width=device-width, initial-scale=1, viewport-fit=cover">','</head>','<body>','</body>','</html>'
$out = Get-Content index.html -Encoding utf8 | Where-Object { $skip -notcontains $_ }
[IO.File]::WriteAllLines("$PWD\landing.html", $out, (New-Object Text.UTF8Encoding $false))
```

## Brand name

Not decided yet. The page uses `[BrandName]`; change the `BRAND` constant near the bottom of `index.html` (and the `[BrandName]` text in the HTML, so it also reads right without JavaScript).

## Preview locally

```bash
python -m http.server 8765 --directory site
```

Then open http://localhost:8765.

## Go live (later — not needed during development)

1. Repo **Settings → Pages → Build and deployment → Source: GitHub Actions**.
   Then open the **Actions** tab, pick **Deploy landing page**, and click **Run workflow** (it's manual-only until launch).
   (GitHub Pages on a private repo needs a paid GitHub plan; on a public repo it's free.)
2. Buy a domain once the brand name is decided.
3. In **Settings → Pages → Custom domain**, enter the domain, then add the DNS records GitHub shows at your registrar. Tick **Enforce HTTPS** once it's available.

## Collect sign-ups

The forms need somewhere to send data. Create a free form at Formspree (or similar) and paste its URL into
`FORM_ENDPOINT` near the bottom of `index.html` (and `landing.html`). Until then the forms show a "not connected yet" message.
Each sign-up is sent as `{ email, list }`, where `list` is `early-access` or `pilot-beta`.
