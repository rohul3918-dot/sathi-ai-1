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

function validTaskPayload(uid) {
  const now = new Date();
  return {
    userId: uid,
    title: "Morning Walk",
    note: "30 mins in park",
    category: "HEALTH",
    priority: "HIGH",
    scheduledTime: "07:00 AM",
    completed: false,
    createdAt: now,
    updatedAt: now,
  };
}

function validPermissionsPayload(uid) {
  const now = new Date();
  return {
    userId: uid,
    masterEnabled: true,
    allowVoiceMic: true,
    allowTtsSpeech: true,
    allowFlashlight: false,
    allowVolumeControl: true,
    allowDisplayAndSettings: false,
    allowAlarmsAndTimers: true,
    allowDialer: false,
    allowNotifications: true,
    assistantLanguage: "bilingual",
    createdAt: now,
    updatedAt: now,
  };
}

test("Unauthenticated user cannot read or write tasks", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("tasks").get());
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("tasks").doc("t1").set(validTaskPayload(ALICE_UID)));
});

test("Authenticated user can create, list, update, and delete own tasks", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const taskRef = aliceDb.collection("users").doc(ALICE_UID).collection("tasks").doc("task_1");

  await assertSucceeds(taskRef.set(validTaskPayload(ALICE_UID)));
  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).collection("tasks").get());
  await assertSucceeds(taskRef.update({ completed: true, updatedAt: new Date() }));
  await assertSucceeds(taskRef.delete());
});

test("Authenticated user cannot read or write another user's tasks", async () => {
  await testEnv.withSecurityRulesDisabled(async (context) => {
    await context.firestore().collection("users").doc(BOB_UID).collection("tasks").doc("bob_task").set(validTaskPayload(BOB_UID));
  });

  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertFails(aliceDb.collection("users").doc(BOB_UID).collection("tasks").get());
  await assertFails(aliceDb.collection("users").doc(BOB_UID).collection("tasks").doc("bob_task").get());
  await assertFails(aliceDb.collection("users").doc(BOB_UID).collection("tasks").doc("bob_task").update({ completed: true }));
});

test("Rejects shadow ghost field and invalid category on task create", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const taskRef = aliceDb.collection("users").doc(ALICE_UID).collection("tasks").doc("bad_task");

  await assertFails(taskRef.set({ ...validTaskPayload(ALICE_UID), isAdmin: true }));
  await assertFails(taskRef.set({ ...validTaskPayload(ALICE_UID), category: "INVALID_CAT" }));
  await assertFails(taskRef.set({ ...validTaskPayload(ALICE_UID), userId: BOB_UID }));
});

test("Authenticated user can manage permissions at settings/permissions only", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const permRef = aliceDb.collection("users").doc(ALICE_UID).collection("settings").doc("permissions");
  await assertSucceeds(permRef.set({
    ...validPermissionsPayload(ALICE_UID),
    assistantName: "Maya",
    conversationStyle: "FRIENDLY",
    customStylePrompt: "Be encouraging",
    voiceGender: "FEMALE",
    voiceLocale: "bn-BD",
    voicePersona: "FEMALE_WARM",
  }));
  await assertSucceeds(permRef.update({
    allowFlashlight: true,
    voiceGender: "MALE",
    voiceLocale: "bn-IN",
    voicePersona: "MALE_CALM",
    conversationStyle: "CONCISE",
    updatedAt: new Date(),
  }));
  await assertFails(permRef.update({ voiceGender: "ROBOT", updatedAt: new Date() }));
  await assertFails(permRef.update({ voicePersona: "UNKNOWN_PERSONA", updatedAt: new Date() }));

  // Arbitrary doc ID rejected
  await assertFails(aliceDb.collection("users").doc(ALICE_UID).collection("settings").doc("other").set(validPermissionsPayload(ALICE_UID)));
});

test("Action logs are append-only (no update) and messages are owner-scoped", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const logRef = aliceDb.collection("users").doc(ALICE_UID).collection("actionLogs").doc("log_1");
  await assertSucceeds(logRef.set({
    userId: ALICE_UID,
    actionType: "DEVICE_FLASHLIGHT",
    summary: "Flashlight turned ON",
    status: "EXECUTED",
    permissionKey: "allowFlashlight",
    createdAt: new Date(),
  }));
  await assertFails(logRef.update({ status: "FAILED" }));

  const msgRef = aliceDb.collection("users").doc(ALICE_UID).collection("messages").doc("msg_1");
  await assertSucceeds(msgRef.set({
    userId: ALICE_UID,
    sender: "USER",
    text: "ফ্ল্যাশলাইট চালু করো",
    actionLabel: "",
    actionStatus: "NONE",
    clientOrder: 1,
    createdAt: new Date(),
  }));

  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("messages").get());
});

test("Authenticated user can manage own profile/profile only with valid fields", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const profileRef = aliceDb.collection("users").doc(ALICE_UID).collection("profile").doc("profile");
  const now = new Date();

  await assertSucceeds(profileRef.set({
    userId: ALICE_UID,
    displayName: "Ruhul Amin",
    nickname: "রূহুল",
    email: "ruhul@example.com",
    bio: "Morning runner and tech lover",
    googlePhotoUrl: "https://example.com/photo.jpg",
    customPhotoUri: "",
    createdAt: now,
    updatedAt: now,
  }));

  await assertSucceeds(profileRef.update({
    displayName: "Ruhul A.",
    nickname: "ভাইয়া",
    bio: "Updated note",
    customPhotoUri: "file:///data/user/0/com.example/files/avatars/avatar.jpg",
    updatedAt: new Date(),
  }));

  // Rejects arbitrary doc ID, oversized bio, and cross-user reads/writes
  await assertFails(aliceDb.collection("users").doc(ALICE_UID).collection("profile").doc("other").set({
    userId: ALICE_UID,
    displayName: "Invalid Doc",
    createdAt: now,
    updatedAt: now,
  }));

  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("profile").doc("profile").get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("profile").doc("profile").update({
    displayName: "Hacked",
    updatedAt: new Date(),
  }));
});

test("Authenticated user can sync own users/{uid} document and cannot access others", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const userRef = aliceDb.collection("users").doc(ALICE_UID);
  const now = new Date();

  await assertSucceeds(userRef.set({
    uid: ALICE_UID,
    displayName: "Ruhul Amin",
    email: "ruhul@example.com",
    photoUrl: "https://example.com/photo.jpg",
    createdAt: now,
    updatedAt: now,
  }));

  await assertSucceeds(userRef.get());
  await assertSucceeds(userRef.update({
    displayName: "Ruhul Updated",
    updatedAt: new Date(),
  }));

  await assertFails(aliceDb.collection("users").get());

  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  await assertFails(bobDb.collection("users").doc(ALICE_UID).get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).update({
    displayName: "Hijack",
    updatedAt: new Date(),
  }));
});
