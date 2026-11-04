# AI Coach: mobile app

Expo (React Native) app for iOS and Android. It talks to the same backend as the web app but shares no code
with it: both generate their API types from the backend's OpenAPI spec.

## Run it

Start the backend first (see the root README), then:

```bash
cd mobile && npm install
npx expo start
```

- **On your phone:** install **Expo Go** (App Store / Play Store), connect to the same Wi-Fi as your computer
  and scan the QR code (iPhone: Camera app; Android: inside Expo Go).
- **iOS Simulator** (needs Xcode): press `i`. **Android emulator** (needs Android Studio): press `a`.
- **Web preview:** press `w` (layout checks only; the real app runs on phones).

The app finds the backend automatically in development: it uses your computer's LAN address (the one the phone
loaded the app from) on port 8080. To point it elsewhere, set `EXPO_PUBLIC_API_URL`, e.g. in `mobile/.env.local`:

```
EXPO_PUBLIC_API_URL=http://192.168.1.20:8080
```

If the phone shows "Can't reach the server", check that both devices are on the same network and that your
computer's firewall allows incoming connections to Java on port 8080.

## Scripts

| Command | What it does |
| --- | --- |
| `npm run lint` | ESLint (Expo config) |
| `npm run typecheck` | TypeScript |
| `npm test` | Jest + React Native Testing Library |
| `npm run api:types` | Regenerate `src/api/schema.d.ts` from the running backend |

## Layout

```
src/
├── app/          # screens (Expo Router: every file is a route; tests live in src/__tests__)
├── api/          # generated types, client (token refresh), tokens in secure storage
├── auth/         # session: restore on launch, log in/out
├── components/   # shared UI
└── theme.ts      # colours and spacing (same palette as the web app)
```

The refresh token is stored in the iOS Keychain / Android Keystore (`expo-secure-store`); the access token only in
memory.
