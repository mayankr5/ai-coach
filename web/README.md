# AI Coach web

React + TypeScript (Vite), Tailwind CSS, TanStack Query and React Router.

```bash
npm install
npm run dev        # http://localhost:5173, with /api proxied to the backend on :8080
npm test           # Vitest + React Testing Library
npm run build      # typecheck and production build
```

## API types

`src/api/schema.d.ts` is generated from the backend's OpenAPI spec, so the frontend fails to compile when
the API changes shape. With the backend running:

```bash
npm run api:types
```

Response fields are required unless the backend marks them `@MaybeNull`, so types only allow `null`
where it can really happen.

## Login tokens

The access token lives in memory; the refresh token in `localStorage`, so a reload keeps you logged in.
`authFetch` renews the session once on a 401 and retries, and requests that fail together share one
refresh. Moving the refresh token to an `httpOnly` cookie is a planned security upgrade.
