# site/ — HeadsUp Pilot landing page

- `landing.html` — source used for the Claude artifact preview (no `<html>`/`<head>` wrapper).
- `index.html` — standalone copy hosted on the real domain. Keep both in sync when editing.

## Go live (later — not needed during development)

1. Repo **Settings → Pages → Build and deployment → Source: GitHub Actions**.
   Then open the **Actions** tab, pick **Deploy landing page**, and click **Run workflow** (it's manual-only until launch).
   (GitHub Pages on a private repo needs a paid GitHub plan; on a public repo it's free.)
2. Buy `headsuppilot.com` (Cloudflare Registrar, Namecheap, Porkbun…).
3. In **Settings → Pages → Custom domain**, enter `headsuppilot.com`, then add the DNS records GitHub shows at your registrar. Tick **Enforce HTTPS** once it's available.

## Collect sign-ups

The form needs somewhere to send data. Create a free form at Formspree (or similar) and paste its URL into
`FORM_ENDPOINT` near the bottom of `index.html` (and `landing.html`). Until then the form shows a "not connected yet" message.
