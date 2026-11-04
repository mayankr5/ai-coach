# AI Coach: mobile app

Expo (React Native, SDK 58) app for iOS and Android. It talks to the same backend as the web app but shares no
code with it: both generate their API types from the backend's OpenAPI spec.

## What it does

- **Goals:** Active / Completed / Archived, pull to refresh; new goal with the phone's date picker and
  tap-to-choose fields.
- **Goal screen:** generate a plan, the coach's last reply, today's check-in (every task marked, haptic
  feedback), the next 7 days, progress and milestones.
- **Get back on track** (while behind): move the dates or add daily time (confirmed in a system dialog), or drop
  tasks the coach suggests (a sheet to untick and confirm).
- **Daily reminder** (Settings): a local notification at a chosen time, planned 7 days ahead and skipped once
  every goal is checked in; tapping it opens the goal.
- **Offline:** the last data loaded stays visible with an "Offline · showing data from …" note, and a saved
  session survives an offline start. Checking in needs a connection.

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
computer's firewall allows incoming connections to Java on port 8080. No shared Wi-Fi? Connect the computer to
the phone's hotspot.

To see offline mode: open a goal, turn on Airplane mode and pull down to refresh. (Reopening the app offline
needs a store build: in development Expo Go loads the app's code from your computer.)

## What is stored on the phone

| What | Where | Cleared |
| --- | --- | --- |
| Refresh token | iOS Keychain / Android Keystore (`expo-secure-store`) | on logout or when the server rejects it |
| Access token | memory only | when the app closes |
| Last goals, plan and progress (offline view), who is logged in | app storage (AsyncStorage, not encrypted) | on logout; data older than 7 days isn't shown |
| Reminder setting and scheduled reminders | app storage, the phone's notification schedule | on logout |

## Scripts

| Command | What it does |
| --- | --- |
| `npm run lint` | ESLint (Expo config) |
| `npm run typecheck` | TypeScript |
| `npm test` | Jest + React Native Testing Library |
| `npm run api:types` | Regenerate `src/api/schema.d.ts` from the running backend |

GitHub Actions runs lint, typecheck, tests and `expo-doctor` on every push to `main` and every pull request that
touches `mobile/`.

## Layout

```
src/
├── app/          # screens (Expo Router: every file is a route; tests live in src/__tests__)
├── api/          # generated types, client (token refresh), tokens in secure storage
├── auth/         # session: restore on launch, log in/out
├── components/   # shared UI; goal/ holds the goal screen's cards
├── reminders.ts  # daily check-in reminder (local notifications)
└── theme.ts      # colours and spacing (same palette as the web app)
```

