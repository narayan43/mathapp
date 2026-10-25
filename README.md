# Numera — Daily Mental-Math & Speed Trainer

Numera is a local-first Android mental-math trainer built with Kotlin and Jetpack Compose, featuring adaptive Daily Workouts, per-answer analytics, a spaced-repetition Mistake Book, Smart Practice ("Practice from my data"), and once-per-day Firestore cloud sync.

---

## How sync works (once per day)

Numera uses a **local-first** architecture (`StorageService` + SQLite `AnswerLogService`). Every answer, quiz result, Daily Workout state, mistake book update, and setting change is saved **instantly on-device** and marked dirty in the local `LocalSyncQueue`. The app **never** writes to Firestore on every answer.

### 1. Daily Sync Gate (`DailySyncGate`)
- The app maintains `lastSyncDay` (`YYYY-MM-DD`) and a dirty queue (`dirtyProfile`, `dirtyDailyProgress`, `dirtyDays`, `dirtyScoreIds`) per user.
- An automatic cloud sync runs **at most once per calendar day** when **all** of the following are true:
  1. The user is signed in with a non-guest account (Google or Email/Password).
  2. The device is online.
  3. `lastSyncDay != today` (local `YYYY-MM-DD`).
  4. There are unsynced local changes (or it is the user's first sync).
- Automatic sync is triggered on the **first app open/resume of the day** (after a short 800ms delay) or **on logout**, whichever comes first.
- A manual **"Sync Now"** button in **Settings → Account & Cloud Backup** is available and rate-limited to **once per hour** (`3,600,000 ms`).

### 2. Single Firestore `WriteBatch` (Compact Schema)
Each sync commits **one** Firestore `WriteBatch` (capped at 500 operations) containing only changed documents so a typical day costs ~3–6 writes:
- `users/{uid}` — Profile (`displayName`, `avatarId`, `dailyGoal`, `language`, `theme` (`LIGHT` | `DARK` | `SYSTEM`), `accentTheme`, `settingsJson`) merged with `SetOptions.merge()`.
- `users/{uid}/progress/daily` — Adaptive skill levels (`skillsJson`), streak & freezes (`dailyStateJson`), and spaced-repetition Mistake Book summary (`mistakeBookJson`).
- `users/{uid}/days/{YYYY-MM-DD}` — One compact document per dirty day containing:
  - `totals`: `{questions, correct, timeMs}`
  - `categories`: `{category: {attempts, correct, totalTimeMs}}`
  - `answers`: list of compact JSON tuples `[ts, op, category, level, expected, given, correct, timeMs, ...]` capped at **1,500 entries per document** (if a day exceeds 1,500 answers, it automatically splits into `days/{date}_2`, `days/{date}_3`, etc.).
- `users/{uid}/scores/{sessionId}` — Only new quiz session results recorded since the last sync.

### 3. Failure Recovery, Initial Pull & Conflict Resolution
- **Failure handling**: On batch commit success, `lastSyncDay` is set to `today` and dirty flags are cleared. If a sync fails, `lastSyncDay` is not updated and the dirty queue is preserved so the app automatically retries on the next app open the same day.
- **One-time pull on login**: When signing in on a new device, the app pulls `users/{uid}`, `users/{uid}/progress/daily`, the last 30 `users/{uid}/days` documents, and `users/{uid}/scores` once, merges them into local storage, and disables Firestore offline disk persistence (`memoryCacheSettings {}`) without any real-time listeners.
- **Conflict resolution**:
  - Profile & settings: compares `updatedAt` against local modification time.
  - Streaks & skill levels: takes the max-progress rule (`StorageService.mergeDailyStates`).
  - Answer logs & scores: unions records by `id`.
- **Guest upgrade**: Guest data stays local-only until the user signs in or links their account, at which point all local days, scores, and progress are queued and uploaded in the first sync.

---

## Appearance: Light / Dark / System Mode
- **Settings → Appearance & Language → Theme** offers **Light** (default on first install), **Dark** (soft muted blue-grey palette `#151B21` / `#1E262E` / `#26303A` / `#E3E9EE` / `#5BA9CF`), and **System** (follows Android OS setting).
- The 10 colour themes remain available under **Accent colour**.
