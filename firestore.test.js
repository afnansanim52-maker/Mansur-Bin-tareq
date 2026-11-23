const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const HOST_UID = "host_user_001";
const VIEWER_UID = "viewer_user_002";
const INTRUDER_UID = "intruder_user_999";

const HOST_CODE = "VR-102-304";
const VIEWER_CODE = "VR-506-708";

const [emulatorHost, emulatorPortStr] = (
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085"
).split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

const pastTime = (offsetMs = 5000) => new Date(Date.now() - offsetMs);
const futureTime = (offsetMs = 30 * 60 * 1000) => new Date(Date.now() + offsetMs);

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

async function seedDevices() {
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    const t = pastTime(10000);
    await db.collection("devices").doc(HOST_CODE).set({
      ownerUid: HOST_UID,
      deviceName: "Pixel 9 Pro",
      ownerName: "Host Alice",
      online: true,
      sharingActive: false,
      platformVersion: "Android 15",
      createdAt: t,
      updatedAt: t,
    });
    await db.collection("devices").doc(VIEWER_CODE).set({
      ownerUid: VIEWER_UID,
      deviceName: "Galaxy S24",
      ownerName: "Viewer Bob",
      online: true,
      sharingActive: false,
      platformVersion: "Android 15",
      createdAt: t,
      updatedAt: t,
    });
  });
}

async function seedAcceptedPairing(pairingId = "pair_001") {
  await seedDevices();
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    const t = pastTime(8000);
    await db.collection("pairingRequests").doc(pairingId).set({
      requesterUid: VIEWER_UID,
      requesterDeviceCode: VIEWER_CODE,
      requesterDeviceName: "Galaxy S24",
      requesterName: "Viewer Bob",
      targetOwnerUid: HOST_UID,
      targetDeviceCode: HOST_CODE,
      targetDeviceName: "Pixel 9 Pro",
      targetOwnerName: "Host Alice",
      participantUids: [VIEWER_UID, HOST_UID],
      status: "ACCEPTED",
      createdAt: t,
      updatedAt: t,
    });
  });
}

// --- 1. UNAUTHENTICATED ACCESS DENIAL ---

test("Unauthenticated users cannot read or write any collection", async () => {
  await seedAcceptedPairing();
  const unauth = testEnv.unauthenticatedContext().firestore();

  await assertFails(unauth.collection("users").doc(HOST_UID).get());
  await assertFails(unauth.collection("devices").doc(HOST_CODE).get());
  await assertFails(unauth.collection("devices").get());
  await assertFails(unauth.collection("pairingRequests").get());
  await assertFails(unauth.collection("sessions").get());
});

// --- 2. PRIVATE USER PROFILES (PII ISOLATION) ---

test("User profile enforces strict owner-only PII isolation and schema bounds", async () => {
  const hostDb = testEnv.authenticatedContext(HOST_UID).firestore();
  const viewerDb = testEnv.authenticatedContext(VIEWER_UID).firestore();
  const t = pastTime(5000);

  await assertSucceeds(
    hostDb.collection("users").doc(HOST_UID).set({
      userId: HOST_UID,
      displayName: "Host Alice",
      email: "alice@example.com",
      preferredLanguage: "bn",
      createdAt: t,
      updatedAt: t,
    })
  );

  // Partial update succeeds
  await assertSucceeds(
    hostDb.collection("users").doc(HOST_UID).update({
      preferredLanguage: "en",
      updatedAt: pastTime(1000),
    })
  );

  // Non-owner cannot read private PII
  await assertFails(viewerDb.collection("users").doc(HOST_UID).get());
  // List users is forbidden
  await assertFails(hostDb.collection("users").get());
  // Shadow/ghost field rejected
  await assertFails(
    hostDb.collection("users").doc(HOST_UID).update({
      isAdmin: true,
      updatedAt: pastTime(500),
    })
  );
  // Future timestamp rejected
  await assertFails(
    hostDb.collection("users").doc(HOST_UID).update({
      displayName: "Tampered",
      updatedAt: futureTime(60000),
    })
  );
});

// --- 3. DEVICE REGISTRY ---

test("Device registry allows owner CRUD, exact-code lookup by signed-in peer, and denies unfiltered scraping", async () => {
  const hostDb = testEnv.authenticatedContext(HOST_UID).firestore();
  const viewerDb = testEnv.authenticatedContext(VIEWER_UID).firestore();
  const t = pastTime(5000);

  await assertSucceeds(
    hostDb.collection("devices").doc(HOST_CODE).set({
      ownerUid: HOST_UID,
      deviceName: "Pixel 9 Pro",
      ownerName: "Host Alice",
      online: true,
      sharingActive: false,
      platformVersion: "Android 15",
      createdAt: t,
      updatedAt: t,
    })
  );

  // Signed-in peer can look up exact device code (no PII stored)
  await assertSucceeds(viewerDb.collection("devices").doc(HOST_CODE).get());

  // Unfiltered list is rejected; owner-scoped query succeeds
  await assertFails(viewerDb.collection("devices").get());
  await assertSucceeds(
    hostDb.collection("devices").where("ownerUid", "==", HOST_UID).get()
  );

  // Peer cannot update or hijack host device
  await assertFails(
    viewerDb.collection("devices").doc(HOST_CODE).update({
      online: false,
      updatedAt: pastTime(1000),
    })
  );

  // Owner partial update succeeds
  await assertSucceeds(
    hostDb.collection("devices").doc(HOST_CODE).update({
      sharingActive: true,
      updatedAt: pastTime(1000),
    })
  );
});

// --- 4. CONSENT-BASED PAIRING REQUESTS ---

test("Pairing workflow enforces explicit owner approval, terminal state locking, and participant-scoped queries", async () => {
  await seedDevices();
  const hostDb = testEnv.authenticatedContext(HOST_UID).firestore();
  const viewerDb = testEnv.authenticatedContext(VIEWER_UID).firestore();
  const intruderDb = testEnv.authenticatedContext(INTRUDER_UID).firestore();
  const t = pastTime(4000);

  const validRequest = {
    requesterUid: VIEWER_UID,
    requesterDeviceCode: VIEWER_CODE,
    requesterDeviceName: "Galaxy S24",
    requesterName: "Viewer Bob",
    targetOwnerUid: HOST_UID,
    targetDeviceCode: HOST_CODE,
    targetDeviceName: "Pixel 9 Pro",
    targetOwnerName: "Host Alice",
    participantUids: [VIEWER_UID, HOST_UID],
    status: "PENDING",
    createdAt: t,
    updatedAt: t,
  };

  // Viewer cannot bypass consent by creating directly as ACCEPTED
  await assertFails(
    viewerDb.collection("pairingRequests").doc("pair_bypass").set({
      ...validRequest,
      status: "ACCEPTED",
    })
  );

  // Viewer creates PENDING pairing request targeting existing Host device
  await assertSucceeds(
    viewerDb.collection("pairingRequests").doc("pair_001").set(validRequest)
  );

  // Viewer cannot self-approve their own pending request
  await assertFails(
    viewerDb.collection("pairingRequests").doc("pair_001").update({
      status: "ACCEPTED",
      updatedAt: pastTime(1000),
    })
  );

  // Intruder cannot read or list pairings
  await assertFails(intruderDb.collection("pairingRequests").doc("pair_001").get());
  await assertFails(intruderDb.collection("pairingRequests").get());

  // Scoped Kotlin query succeeds for both participants
  await assertSucceeds(
    hostDb.collection("pairingRequests").where("participantUids", "array-contains", HOST_UID).get()
  );
  await assertSucceeds(
    viewerDb.collection("pairingRequests").where("participantUids", "array-contains", VIEWER_UID).get()
  );

  // Host explicitly accepts
  await assertSucceeds(
    hostDb.collection("pairingRequests").doc("pair_001").update({
      status: "ACCEPTED",
      updatedAt: pastTime(2000),
    })
  );

  // Either participant can revoke an accepted pairing
  await assertSucceeds(
    viewerDb.collection("pairingRequests").doc("pair_001").update({
      status: "REVOKED",
      updatedAt: pastTime(1000),
    })
  );

  // Terminal state locking: REVOKED cannot be re-accepted
  await assertFails(
    hostDb.collection("pairingRequests").doc("pair_001").update({
      status: "ACCEPTED",
      updatedAt: pastTime(500),
    })
  );
});

// --- 5. SCREEN SHARING SESSIONS & WEBRTC SIGNALING ---

test("Screen share sessions require accepted pairing, explicit host approval, and participant-only ICE signaling", async () => {
  await seedAcceptedPairing("pair_001");
  const hostDb = testEnv.authenticatedContext(HOST_UID).firestore();
  const viewerDb = testEnv.authenticatedContext(VIEWER_UID).firestore();
  const intruderDb = testEnv.authenticatedContext(INTRUDER_UID).firestore();
  const t = pastTime(4000);

  const sessionPayload = {
    pairingId: "pair_001",
    hostUid: HOST_UID,
    hostDeviceCode: HOST_CODE,
    hostDeviceName: "Pixel 9 Pro",
    viewerUid: VIEWER_UID,
    viewerDeviceCode: VIEWER_CODE,
    viewerDeviceName: "Galaxy S24",
    participantUids: [HOST_UID, VIEWER_UID],
    status: "REQUESTED",
    screenWidth: 0,
    screenHeight: 0,
    qualityProfile: "AUTO",
    expiresAt: futureTime(30 * 60 * 1000),
    createdAt: t,
    updatedAt: t,
  };

  // Viewer cannot create an already-ACTIVE session without Host approval
  await assertFails(
    viewerDb.collection("sessions").doc("sess_bypass").set({
      ...sessionPayload,
      status: "ACTIVE",
    })
  );

  // Viewer requests screen share session
  await assertSucceeds(
    viewerDb.collection("sessions").doc("sess_001").set(sessionPayload)
  );

  // ICE candidates blocked while session is still REQUESTED (not yet approved!)
  await assertFails(
    viewerDb.collection("sessions").doc("sess_001").collection("iceCandidates").doc("ice_early").set({
      sessionId: "sess_001",
      participantUids: [HOST_UID, VIEWER_UID],
      senderUid: VIEWER_UID,
      role: "VIEWER",
      sdpMid: "0",
      sdpMLineIndex: 0,
      sdp: "candidate:1 1 UDP 2122252543 192.168.1.5 54321 typ host",
      createdAt: pastTime(1000),
    })
  );

  // Host explicitly approves the session
  await assertSucceeds(
    hostDb.collection("sessions").doc("sess_001").update({
      status: "APPROVED",
      updatedAt: pastTime(3000),
    })
  );

  // Host publishes WebRTC SDP Offer and screen resolution
  await assertSucceeds(
    hostDb.collection("sessions").doc("sess_001").update({
      offerSdp: "v=0\r\no=- 12345 2 IN IP4 127.0.0.1\r\ns=-\r\n",
      screenWidth: 720,
      screenHeight: 1600,
      status: "ACTIVE",
      updatedAt: pastTime(2000),
    })
  );

  // Viewer publishes WebRTC SDP Answer
  await assertSucceeds(
    viewerDb.collection("sessions").doc("sess_001").update({
      answerSdp: "v=0\r\no=- 67890 2 IN IP4 127.0.0.1\r\ns=-\r\n",
      qualityProfile: "BALANCED",
      updatedAt: pastTime(1500),
    })
  );

  // Both peers exchange ICE candidates
  await assertSucceeds(
    hostDb.collection("sessions").doc("sess_001").collection("iceCandidates").doc("ice_host_1").set({
      sessionId: "sess_001",
      participantUids: [HOST_UID, VIEWER_UID],
      senderUid: HOST_UID,
      role: "HOST",
      sdpMid: "0",
      sdpMLineIndex: 0,
      sdp: "candidate:1 1 UDP 2122252543 10.0.0.2 50000 typ host",
      createdAt: pastTime(1000),
    })
  );

  // Scoped Kotlin query on sessions and iceCandidates succeeds
  await assertSucceeds(
    viewerDb.collection("sessions").where("participantUids", "array-contains", VIEWER_UID).get()
  );
  await assertSucceeds(
    viewerDb.collection("sessions").doc("sess_001").collection("iceCandidates")
      .where("participantUids", "array-contains", VIEWER_UID).get()
  );

  // Intruder cannot read session or ICE candidates
  await assertFails(intruderDb.collection("sessions").doc("sess_001").get());
  await assertFails(
    intruderDb.collection("sessions").doc("sess_001").collection("iceCandidates").doc("ice_host_1").get()
  );

  // Host immediately stops sharing
  await assertSucceeds(
    hostDb.collection("sessions").doc("sess_001").update({
      status: "ENDED",
      updatedAt: pastTime(500),
    })
  );

  // Terminal state locking: ENDED session cannot be restarted
  await assertFails(
    viewerDb.collection("sessions").doc("sess_001").update({
      status: "ACTIVE",
      updatedAt: pastTime(100),
    })
  );
});

// --- 6. PAIRED CHAT & SESSION EVENT MESSAGES ---

test("Chat messages are restricted to accepted pairing participants and append-only", async () => {
  await seedAcceptedPairing("pair_001");
  const hostDb = testEnv.authenticatedContext(HOST_UID).firestore();
  const viewerDb = testEnv.authenticatedContext(VIEWER_UID).firestore();
  const intruderDb = testEnv.authenticatedContext(INTRUDER_UID).firestore();

  await assertSucceeds(
    viewerDb.collection("pairingRequests").doc("pair_001").collection("messages").doc("msg_1").set({
      pairingId: "pair_001",
      participantUids: [VIEWER_UID, HOST_UID],
      senderUid: VIEWER_UID,
      senderName: "Viewer Bob",
      text: "হ্যালো, আমি কি স্ক্রিন দেখতে পারি?",
      messageType: "TEXT",
      createdAt: pastTime(1000),
    })
  );

  // Host reads messages with participant-scoped query
  await assertSucceeds(
    hostDb.collection("pairingRequests").doc("pair_001").collection("messages")
      .where("participantUids", "array-contains", HOST_UID).get()
  );

  // Append-only: updating or deleting messages is forbidden
  await assertFails(
    viewerDb.collection("pairingRequests").doc("pair_001").collection("messages").doc("msg_1").update({
      text: "Edited",
    })
  );
  await assertFails(
    intruderDb.collection("pairingRequests").doc("pair_001").collection("messages").doc("msg_1").get()
  );
});
