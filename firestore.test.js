const path = require("node:path");
const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

const rulesUnitDir = path.dirname(require.resolve("@firebase/rules-unit-testing"));
const firebaseCompat = require(require.resolve("firebase/compat/app", { paths: [rulesUnitDir] })).default;
require(require.resolve("firebase/compat/firestore", { paths: [rulesUnitDir] }));
const serverTimestamp = () => firebaseCompat.firestore.FieldValue.serverTimestamp();

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const DATABASE_ID = "ai-studio-android-0a2534eb-7cf6-40ca-a0b6-242d96fe745c";
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

test("Unauthenticated user: cannot read or write users or usernames", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore(DATABASE_ID);
  await assertFails(unauthDb.collection("usernames").doc("ali1").get());
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).get());
  await assertFails(unauthDb.collection("users").where("userId", "==", ALICE_UID).get());
});

test("Atomic registration succeeds for unique <=4 char username and blocks duplicates", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore(DATABASE_ID);
  const aliceBatch = aliceDb.batch();
  aliceBatch.set(aliceDb.collection("usernames").doc("ali1"), {
    userId: ALICE_UID,
    username: "Ali1",
    usernameKey: "ali1",
    createdAt: serverTimestamp(),
  });
  aliceBatch.set(aliceDb.collection("users").doc(ALICE_UID), {
    userId: ALICE_UID,
    username: "Ali1",
    usernameKey: "ali1",
    createdAt: serverTimestamp(),
    updatedAt: serverTimestamp(),
  });
  await assertSucceeds(aliceBatch.commit());

  // Bob cannot claim the same username "ali1"
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore(DATABASE_ID);
  await assertFails(
    bobDb.collection("usernames").doc("ali1").set({
      userId: BOB_UID,
      username: "ali1",
      usernameKey: "ali1",
      createdAt: serverTimestamp(),
    })
  );
});

test("Username longer than 4 characters is rejected by rules", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore(DATABASE_ID);
  await assertFails(
    aliceDb.collection("usernames").doc("ali12").set({
      userId: ALICE_UID,
      username: "ali12",
      usernameKey: "ali12",
      createdAt: serverTimestamp(),
    })
  );
});

test("Cross-user read and shadow update with ghost field are rejected", async () => {
  await testEnv.withSecurityRulesDisabled(async (context) => {
    const adminDb = context.firestore(DATABASE_ID);
    await adminDb.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      username: "Sara",
      usernameKey: "sara",
      createdAt: serverTimestamp(),
      updatedAt: serverTimestamp(),
    });
  });

  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore(DATABASE_ID);
  await assertFails(bobDb.collection("users").doc(ALICE_UID).get());

  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore(DATABASE_ID);
  // Shadow update with ghost field
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      driveFileId: "drive_1",
      updatedAt: serverTimestamp(),
      isAdmin: true,
    })
  );
  // Allowed update of driveFileId
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).update({
      driveFileId: "drive_1",
      updatedAt: serverTimestamp(),
    })
  );
});

test("Cannot list usernames collection", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore(DATABASE_ID);
  await assertFails(aliceDb.collection("usernames").get());
});
