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

test("Authenticated owner can create and list diagnosticLogs; cross-user and updates are blocked", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore(DATABASE_ID);
  const logRef = aliceDb.collection("users").doc(ALICE_UID).collection("diagnosticLogs").doc("log_001");

  const validLog = {
    userId: ALICE_UID,
    reportId: "log_001",
    isFatal: true,
    tag: "UNCAUGHT_CRASH",
    errorType: "java.lang.IllegalStateException",
    message: "Test crash",
    stackTrace: "at com.example.MainActivity.onCreate(MainActivity.kt:10)",
    deviceInfo: "Samsung SM-S918B | Android 14 (API 34)",
    appVersion: "1.0 (1)",
    firebaseConfigured: true,
    timestampMs: 1700000000000,
    createdAt: serverTimestamp(),
  };

  await assertSucceeds(logRef.set(validLog));
  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).collection("diagnosticLogs").get());

  // Immutable update must fail
  await assertFails(logRef.update({ message: "tampered" }));

  // Cross-user read & write must fail
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore(DATABASE_ID);
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("diagnosticLogs").doc("log_001").get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("diagnosticLogs").get());
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("diagnosticLogs").doc("log_002").set({
      ...validLog,
      reportId: "log_002",
    })
  );

  // Unauthenticated read/write must fail
  const unauthDb = testEnv.unauthenticatedContext().firestore(DATABASE_ID);
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("diagnosticLogs").get());
});

test("Authenticated owner can create, list, update, and delete favorites; unauthorized or invalid payloads fail", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore(DATABASE_ID);
  const favCol = aliceDb.collection("users").doc(ALICE_UID).collection("favorites");
  const favDocRef = favCol.doc("ir_musicsweb_101");

  const validFavorite = {
    userId: ALICE_UID,
    favoriteId: "ir_musicsweb_101",
    trackId: "ir_musicsweb_101",
    title: "شبای تهران",
    artist: "علیرضا طلیسچی",
    album: "ترند اینستا",
    durationMs: 215000,
    mediaUri: "https://dl.musicsweb.ir/music/shabaye-tehran-320.mp3",
    artworkUri: "https://musicsweb.ir/uploads/cover.jpg",
    importedTrack: false,
    onlineTrack: true,
    remixTrack: false,
    instaTrend: true,
    fullTrack: true,
    addedAtMs: 1700000000000,
    createdAt: serverTimestamp(),
    updatedAt: serverTimestamp(),
  };

  // Owner create succeeds
  await assertSucceeds(favDocRef.set(validFavorite));

  // Owner get & list succeed
  await assertSucceeds(favDocRef.get());
  await assertSucceeds(favCol.orderBy("addedAtMs", "desc").get());

  // Owner partial metadata update succeeds while preserving immutable fields
  await assertSucceeds(
    favDocRef.update({
      title: "شبای تهران (ریمیکس)",
      remixTrack: true,
      updatedAt: serverTimestamp(),
    })
  );

  // Immutable trackId or addedAtMs tamper on update must fail
  await assertFails(
    favDocRef.update({
      trackId: "tampered_id",
      updatedAt: serverTimestamp(),
    })
  );
  await assertFails(
    favDocRef.update({
      addedAtMs: 1800000000000,
      updatedAt: serverTimestamp(),
    })
  );

  // Shadow/ghost field injection must fail on create and update
  await assertFails(
    favCol.doc("ir_musicsweb_102").set({
      ...validFavorite,
      favoriteId: "ir_musicsweb_102",
      isAdmin: true,
    })
  );
  await assertFails(
    favDocRef.update({
      ghostField: "hack",
      updatedAt: serverTimestamp(),
    })
  );

  // Mismatched favoriteId / userId must fail
  await assertFails(
    favCol.doc("ir_musicsweb_103").set({
      ...validFavorite,
      favoriteId: "different_id",
    })
  );
  await assertFails(
    favCol.doc("ir_musicsweb_104").set({
      ...validFavorite,
      favoriteId: "ir_musicsweb_104",
      userId: BOB_UID,
    })
  );

  // Invalid document ID characters must fail
  await assertFails(
    favCol.doc("bad.id:with$chars").set({
      ...validFavorite,
      favoriteId: "bad.id:with$chars",
    })
  );

  // Future client timestamp (spoofing serverTimestamp) must fail
  await assertFails(
    favCol.doc("ir_musicsweb_105").set({
      ...validFavorite,
      favoriteId: "ir_musicsweb_105",
      createdAt: new Date(Date.now() + 600_000),
      updatedAt: new Date(Date.now() + 600_000),
    })
  );

  // Oversized title > 300 chars must fail
  await assertFails(
    favCol.doc("ir_musicsweb_106").set({
      ...validFavorite,
      favoriteId: "ir_musicsweb_106",
      title: "A".repeat(301),
    })
  );

  // Cross-user read/list/write/delete must fail
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore(DATABASE_ID);
  const bobTargetsAlice = bobDb.collection("users").doc(ALICE_UID).collection("favorites");
  await assertFails(bobTargetsAlice.doc("ir_musicsweb_101").get());
  await assertFails(bobTargetsAlice.get());
  await assertFails(
    bobTargetsAlice.doc("ir_bob_hack").set({
      ...validFavorite,
      userId: BOB_UID,
      favoriteId: "ir_bob_hack",
    })
  );
  await assertFails(bobTargetsAlice.doc("ir_musicsweb_101").delete());

  // Unauthenticated access must fail
  const unauthDb = testEnv.unauthenticatedContext().firestore(DATABASE_ID);
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("favorites").get());
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("favorites").doc("ir_musicsweb_101").get());

  // Owner delete succeeds
  await assertSucceeds(favDocRef.delete());
});

test("Track reactions: authenticated users can read, create, switch emoji, and remove own reaction; cross-user or invalid writes fail", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore(DATABASE_ID);
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore(DATABASE_ID);
  const trackKey = "ir_musicsweb_101";

  const aliceReactionRef = aliceDb.collection("tracks").doc(trackKey).collection("reactions").doc(ALICE_UID);
  const validReaction = {
    userId: ALICE_UID,
    trackKey,
    trackTitle: "شبای تهران",
    emojiType: "FIRE",
    authorUsername: "Ali1",
    createdAt: serverTimestamp(),
    updatedAt: serverTimestamp(),
  };

  // Alice creates reaction
  await assertSucceeds(aliceReactionRef.set(validReaction));

  // Bob can read/list all reactions on the track
  await assertSucceeds(bobDb.collection("tracks").doc(trackKey).collection("reactions").get());

  // Alice switches her reaction emoji via partial update preserving immutable fields
  await assertSucceeds(
    aliceReactionRef.update({
      emojiType: "LOVE",
      authorUsername: "Ali1",
      trackTitle: "شبای تهران",
      updatedAt: serverTimestamp(),
    })
  );

  // Immutable trackKey tamper on update must fail
  await assertFails(
    aliceReactionRef.update({
      trackKey: "other_track",
      updatedAt: serverTimestamp(),
    })
  );

  // Invalid emoji enum must fail
  await assertFails(
    bobDb.collection("tracks").doc(trackKey).collection("reactions").doc(BOB_UID).set({
      ...validReaction,
      userId: BOB_UID,
      authorUsername: "Bob",
      emojiType: "INVALID_EMOJI",
    })
  );

  // Bob cannot overwrite or delete Alice's reaction
  await assertFails(
    bobDb.collection("tracks").doc(trackKey).collection("reactions").doc(ALICE_UID).set({
      ...validReaction,
      userId: BOB_UID,
    })
  );
  await assertFails(
    bobDb.collection("tracks").doc(trackKey).collection("reactions").doc(ALICE_UID).delete()
  );

  // Unauthenticated cannot read or write reactions
  const unauthDb = testEnv.unauthenticatedContext().firestore(DATABASE_ID);
  await assertFails(unauthDb.collection("tracks").doc(trackKey).collection("reactions").get());

  // Alice can delete her own reaction
  await assertSucceeds(aliceReactionRef.delete());
});

test("Track comments: authenticated users can read and post comments; only owner can edit/delete own comments", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore(DATABASE_ID);
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore(DATABASE_ID);
  const trackKey = "ir_musicsweb_101";
  const commentId = "cmt_alice_001";

  const aliceCommentRef = aliceDb.collection("tracks").doc(trackKey).collection("comments").doc(commentId);
  const validComment = {
    commentId,
    trackKey,
    trackTitle: "شبای تهران",
    userId: ALICE_UID,
    authorUsername: "Ali1",
    text: "این آهنگ فوق‌العاده‌ست برای رانندگی شبانه!",
    createdAt: serverTimestamp(),
    updatedAt: serverTimestamp(),
  };

  // Alice creates comment
  await assertSucceeds(aliceCommentRef.set(validComment));

  // Bob can list comments ordered by createdAt desc
  await assertSucceeds(
    bobDb.collection("tracks").doc(trackKey).collection("comments").orderBy("createdAt", "desc").get()
  );

  // Owner can edit text while preserving immutable fields
  await assertSucceeds(
    aliceCommentRef.update({
      text: "فوق‌العاده عالی برای ماشین!",
      updatedAt: serverTimestamp(),
    })
  );

  // Bob cannot update or delete Alice's comment
  await assertFails(
    bobDb.collection("tracks").doc(trackKey).collection("comments").doc(commentId).update({
      text: "هک شد",
      updatedAt: serverTimestamp(),
    })
  );
  await assertFails(
    bobDb.collection("tracks").doc(trackKey).collection("comments").doc(commentId).delete()
  );

  // Spoofed userId on create fails
  await assertFails(
    bobDb.collection("tracks").doc(trackKey).collection("comments").doc("cmt_bob_spoof").set({
      ...validComment,
      commentId: "cmt_bob_spoof",
      userId: ALICE_UID,
    })
  );

  // Oversized text > 500 chars fails
  await assertFails(
    bobDb.collection("tracks").doc(trackKey).collection("comments").doc("cmt_bob_long").set({
      ...validComment,
      commentId: "cmt_bob_long",
      userId: BOB_UID,
      authorUsername: "Bob",
      text: "A".repeat(501),
    })
  );

  // Future client timestamp fails
  await assertFails(
    bobDb.collection("tracks").doc(trackKey).collection("comments").doc("cmt_bob_future").set({
      ...validComment,
      commentId: "cmt_bob_future",
      userId: BOB_UID,
      authorUsername: "Bob",
      createdAt: new Date(Date.now() + 600_000),
      updatedAt: new Date(Date.now() + 600_000),
    })
  );

  // Unauthenticated read/write fails
  const unauthDb = testEnv.unauthenticatedContext().firestore(DATABASE_ID);
  await assertFails(unauthDb.collection("tracks").doc(trackKey).collection("comments").get());

  // Alice deletes her own comment
  await assertSucceeds(aliceCommentRef.delete());
});


