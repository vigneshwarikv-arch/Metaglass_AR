# site/ — HeadsUp Pilot landing page

- `landing.html` — source used for the Claude artifact preview (no `<html>`/`<head>` wrapper).
- `index.html` — standalone copy to host on the real domain (built from `landing.html`).

## Go live on headsuppilot.com (free, GitHub Pages)

1. Buy the domain (e.g. Cloudflare Registrar, Namecheap, Porkbun).
2. Repo **Settings → Pages** → Source: `main` branch, folder `/site`... (Pages only offers `/` or `/docs`;
   simplest: copy `site/index.html` to `docs/index.html`, or use a `gh-pages` branch).
3. In Pages, set **Custom domain** to `headsuppilot.com` and add the DNS records GitHub shows.

## Collect sign-ups

The form needs a backend. Create a free form at Formspree (or similar), then paste its URL into
`FORM_ENDPOINT` near the bottom of `index.html` (and `landing.html`).
