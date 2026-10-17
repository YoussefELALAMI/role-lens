# RoleLens web

React + Vite frontend for RoleLens.

## Running

```bash
npm install
npm run dev
```

The dev server runs on Vite's default port and proxies `/api/*` requests to
the backend at `http://localhost:8080` (see `vite.config.ts`), so make sure
the API (`../api`) is running too.

## Structure

- `src/api/` — HTTP client and typed calls to the backend. No UI code here;
  pages import from this layer instead of calling `fetch` directly.
- `src/App.tsx` — the single page: paste a job ad, save it, see the saved list.
