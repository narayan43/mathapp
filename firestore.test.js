const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_123";
const BOB_UID = "bob_456";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

test("Unauthenticated user: cannot read or write user profiles, days, or scores", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).get());
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("days").doc("2026-10-24").get());
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("scores").doc("s1").get());
});

test("Authenticated user: can create and read own profile with valid schema and displayName <= 50 chars", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const now = new Date();
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      displayName: "Alice",
      avatarId: "fox",
      dailyGoal: 20,
      language: "en",
      theme: "LIGHT",
      accentTheme: "LIGHT",
      settingsJson: "{}",
      createdAt: now,
      updatedAt: now,
    })
  );
  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).get());
});

test("Authenticated user: profile with displayName > 50 chars is rejected", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const now = new Date();
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      displayName: "A".repeat(51),
      avatarId: "fox",
      dailyGoal: 20,
      language: "en",
      theme: "LIGHT",
      createdAt: now,
      updatedAt: now,
    })
  );
});

test("Authenticated user: cannot read or write another user's profile, progress, or days", async () => {
  const now = new Date();
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    await ctx.firestore().collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      displayName: "Alice",
      avatarId: "fox",
      dailyGoal: 20,
      language: "en",
      theme: "LIGHT",
      createdAt: now,
      updatedAt: now,
    });
    await ctx.firestore().collection("users").doc(ALICE_UID).collection("days").doc("2026-10-24").set({
      userId: ALICE_UID,
      day: "2026-10-24",
      part: 1,
      totals: { questions: 10, correct: 9, timeMs: 25000 },
      categories: { add_2d_carry: { attempts: 10, correct: 9, totalTimeMs: 25000 } },
      answers: ["[1729750000000,\"add\",\"add_2d_carry\",3,45,45,true,2500]"],
      createdAt: now,
      updatedAt: now,
    });
  });

  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  await assertFails(bobDb.collection("users").doc(ALICE_UID).get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("progress").doc("daily").get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("days").doc("2026-10-24").get());
});

test("Authenticated user: shadow update with ghost field is rejected", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const now = new Date();
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      displayName: "Alice",
      avatarId: "fox",
      dailyGoal: 20,
      language: "en",
      theme: "LIGHT",
      isAdmin: true,
      createdAt: now,
      updatedAt: now,
    })
  );
});

test("Authenticated user: can create daily progress, day sync doc (<= 1500 answers), and score documents", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const now = new Date();
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("progress").doc("daily").set({
      userId: ALICE_UID,
      streakCurrent: 5,
      streakBest: 7,
      freezesAvailable: 1,
      lastCompletedDay: "2026-10-23",
      skillsJson: "{}",
      dailyStateJson: "{}",
      mistakeBookJson: "[]",
      createdAt: now,
      updatedAt: now,
    })
  );
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("days").doc("2026-10-24").set({
      userId: ALICE_UID,
      day: "2026-10-24",
      part: 1,
      totals: { questions: 2, correct: 2, timeMs: 4100 },
      categories: { mul_2dx1d: { attempts: 2, correct: 2, totalTimeMs: 4100 } },
      answers: [
        "[1729750000000,\"mul\",\"mul_2dx1d\",3,376,376,true,2100]",
        "[1729750005000,\"mul\",\"mul_2dx1d\",3,144,144,true,2000]",
      ],
      createdAt: now,
      updatedAt: now,
    })
  );
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("scores").doc("score_1").set({
      userId: ALICE_UID,
      mode: "ADDITION",
      difficulty: "EASY",
      count: 10,
      correct: 9,
      timeMs: 14500,
      accuracy: 0.9,
      ts: 1700000000000,
      createdAt: now,
      updatedAt: now,
    })
  );
});

test("Authenticated user: day doc with answers array > 1500 entries is rejected", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const now = new Date();
  const tooManyAnswers = Array.from({ length: 1501 }, () => "x");
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("days").doc("2026-10-24").set({
      userId: ALICE_UID,
      day: "2026-10-24",
      part: 1,
      totals: { questions: 1501, correct: 1500, timeMs: 300000 },
      categories: {},
      answers: tooManyAnswers,
      createdAt: now,
      updatedAt: now,
    })
  );
});

test("Delete is denied everywhere even for the authenticated owner", async () => {
  const now = new Date();
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await db.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      displayName: "Alice",
      avatarId: "fox",
      dailyGoal: 20,
      language: "en",
      theme: "LIGHT",
      createdAt: now,
      updatedAt: now,
    });
    await db.collection("users").doc(ALICE_UID).collection("progress").doc("daily").set({
      userId: ALICE_UID,
      streakCurrent: 3,
      streakBest: 5,
      freezesAvailable: 1,
      skillsJson: "{}",
      dailyStateJson: "{}",
      createdAt: now,
      updatedAt: now,
    });
    await db.collection("users").doc(ALICE_UID).collection("days").doc("2026-10-24").set({
      userId: ALICE_UID,
      day: "2026-10-24",
      part: 1,
      totals: { questions: 10, correct: 9, timeMs: 20000 },
      categories: {},
      answers: [],
      createdAt: now,
      updatedAt: now,
    });
    await db.collection("users").doc(ALICE_UID).collection("scores").doc("s1").set({
      userId: ALICE_UID,
      mode: "ADDITION",
      difficulty: "EASY",
      count: 10,
      correct: 9,
      timeMs: 20000,
      ts: 1700000000000,
      createdAt: now,
      updatedAt: now,
    });
  });

  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertFails(aliceDb.collection("users").doc(ALICE_UID).delete());
  await assertFails(aliceDb.collection("users").doc(ALICE_UID).collection("progress").doc("daily").delete());
  await assertFails(aliceDb.collection("users").doc(ALICE_UID).collection("days").doc("2026-10-24").delete());
  await assertFails(aliceDb.collection("users").doc(ALICE_UID).collection("scores").doc("s1").delete());
});

test("Guest (anonymous) user can get/list/create/update under users/{uid}/**", async () => {
  const guestUid = "guest_anon_987654";
  const guestDb = testEnv.authenticatedContext(guestUid, { firebase: { sign_in_provider: "anonymous" } }).firestore();
  const now = new Date();
  await assertSucceeds(
    guestDb.collection("users").doc(guestUid).set({
      userId: guestUid,
      displayName: "Guest",
      avatarId: "fox",
      provider: "guest",
      dailyGoal: 20,
      language: "en",
      theme: "LIGHT",
      createdAt: now,
      updatedAt: now,
    })
  );
  await assertSucceeds(
    guestDb.collection("users").doc(guestUid).collection("progress").doc("daily").set({
      userId: guestUid,
      streakCurrent: 1,
      streakBest: 1,
      freezesAvailable: 0,
      skillsJson: "{}",
      dailyStateJson: "{}",
      createdAt: now,
      updatedAt: now,
    })
  );
  await assertFails(guestDb.collection("users").doc(guestUid).delete());
});

