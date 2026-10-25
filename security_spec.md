# Security Specification (`security_spec.md`)

## 1. Data Invariants
1. **Strict User Ownership**: Every document under `/users/{userId}` and its subcollections (`/users/{userId}/progress/{docId}`, `/users/{userId}/scores/{scoreId}`, `/users/{userId}/answers/{answerId}`, `/users/{userId}/aggregates/{aggId}`) can only be read, created, updated, or deleted by the authenticated user whose `request.auth.uid == userId` and `incoming().userId == request.auth.uid`.
2. **Schema & Size Bounds**: Every string field (`displayName`, `photoURL`, `phoneMasked`, `email`, `language`, `theme`, `settingsJson`, `skillsJson`, `dailyStateJson`, `mode`, `difficulty`, `lastCompletedDay`) is strictly bounded in character length.
3. **Immutable Audit Fields**: `userId` and `createdAt` cannot be mutated on `update`.
4. **Server Timestamps**: `createdAt` and `updatedAt` must be valid Firestore `timestamp` objects `<= request.time`.

## 2. The "Dirty Dozen" Payloads
1. Unauthenticated `get` on `/users/alice_123` -> Denied.
2. Cross-user `get` by `bob_456` on `/users/alice_123` -> Denied.
3. Spoofed `userId` (`userId: "bob_456"`) written to `/users/alice_123` by `alice_123` -> Denied.
4. Ghost field (`isAdmin: true`) injected into `/users/alice_123` -> Denied by `hasOnly()`.
5. Oversized `displayName` (> 80 chars) or empty `displayName` -> Denied by `isValidUserProfile()`.
6. Mutating `createdAt` or `userId` on `update` of `/users/alice_123` -> Denied.
7. Future timestamp (`createdAt > request.time`) on `/users/alice_123` -> Denied.
8. Cross-user read/write on `/users/alice_123/progress/daily` by `bob_456` -> Denied.
9. Invalid `streakCurrent` (< 0) or `freezesAvailable` (> 2) on `/users/alice_123/progress/daily` -> Denied.
10. Cross-user list query on `/users/alice_123/scores` by `bob_456` -> Denied.
11. Score document with negative `timeMs` or `correct > count` -> Denied by `isValidScoreRecord()`.
12. Write to unknown top-level collection `/public_scores/doc1` -> Denied by default-deny catch-all.
