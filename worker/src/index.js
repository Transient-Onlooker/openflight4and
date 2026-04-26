const GOOGLE_CERTS_URL = "https://www.googleapis.com/oauth2/v3/certs";
const USER_CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

let cachedGoogleKeys = null;
let cachedGoogleKeysUntil = 0;

export default {
  async fetch(request, env) {
    const url = new URL(request.url);
    try {
      if (request.method === "OPTIONS") return jsonResponse({ ok: true });
      if (request.method === "GET" && url.pathname === "/version") return handleVersion(env);
      if (request.method === "POST" && url.pathname === "/auth/session") return handleSession(request, env);
      if (request.method === "GET" && url.pathname === "/me") return handleMe(request, env);
      if (request.method === "POST" && url.pathname === "/tickets/merge") return handleTicketMerge(request, env);
      if (request.method === "POST" && url.pathname === "/tickets/events") return handleTicketEvent(request, env);
      if (request.method === "POST" && url.pathname === "/tickets/transfer") return handleTicketTransfer(request, env);
      if (request.method === "POST" && url.pathname === "/redeem") return handleRedeem(request, env);
      return jsonResponse({ ok: false, error: "Not found" }, 404);
    } catch (error) {
      return jsonResponse({ ok: false, error: error.message || "Internal error" }, 500);
    }
  }
};

async function handleVersion(env) {
  const row = await env.DB.prepare(
    "SELECT allowed_version, recent_version FROM app_versions WHERE platform = ?"
  ).bind("android").first();
  if (!row) return jsonResponse({ ok: false, error: "Version row not found" }, 404);
  return jsonResponse({
    ok: true,
    allowedVersion: row.allowed_version,
    recentVersion: row.recent_version
  });
}

async function handleSession(request, env) {
  const uid = await requireFirebaseUid(request, env);
  const user = await ensureUser(env, uid);
  const tickets = await ensureTicketRow(env, uid);
  return jsonResponse({ ok: true, firebaseUid: uid, userCode: user.user_code, ticketCount: tickets.ticket_count });
}

async function handleMe(request, env) {
  const uid = await requireFirebaseUid(request, env);
  const user = await ensureUser(env, uid);
  const tickets = await ensureTicketRow(env, uid);
  return jsonResponse({ ok: true, firebaseUid: uid, userCode: user.user_code, ticketCount: tickets.ticket_count });
}

async function handleTicketMerge(request, env) {
  const uid = await requireFirebaseUid(request, env);
  await ensureUser(env, uid);
  const body = await request.json().catch(() => ({}));
  const localTicketCount = safeInt(body.localTicketCount, 0);
  const current = await ensureTicketRow(env, uid);
  const ticketCount = Math.max(current.ticket_count, localTicketCount);
  const clientEventId = `merge:${uid}:${Date.now()}`;
  const delta = ticketCount - current.ticket_count;
  const statements = [
    env.DB.prepare("UPDATE user_tickets SET ticket_count = ?, updated_at = CURRENT_TIMESTAMP WHERE firebase_uid = ?")
      .bind(ticketCount, uid)
  ];
  if (delta !== 0) {
    statements.push(
      env.DB.prepare(
        "INSERT INTO ticket_events (firebase_uid, event_code, delta, balance_after, client_event_id, detail) VALUES (?, 'I', ?, ?, ?, ?)"
      ).bind(uid, delta, ticketCount, clientEventId, `Login merge local=${localTicketCount} server=${current.ticket_count}`)
    );
  }
  await env.DB.batch(statements);
  return jsonResponse({ ok: true, ticketCount });
}

async function handleTicketEvent(request, env) {
  const uid = await requireFirebaseUid(request, env);
  await ensureUser(env, uid);
  const body = await request.json().catch(() => ({}));
  const clientEventId = String(body.clientEventId || "");
  const eventCode = String(body.eventCode || "");
  const delta = safeInt(body.delta, 0);
  if (!clientEventId || !eventCode || delta === 0) {
    return jsonResponse({ ok: false, error: "Invalid ticket event" }, 400);
  }

  const existing = await env.DB.prepare(
    "SELECT balance_after FROM ticket_events WHERE client_event_id = ?"
  ).bind(clientEventId).first();
  if (existing) return jsonResponse({ ok: true, ticketCount: existing.balance_after });

  const current = await ensureTicketRow(env, uid);
  const ticketCount = Math.max(0, current.ticket_count + delta);
  await env.DB.batch([
    env.DB.prepare("UPDATE user_tickets SET ticket_count = ?, updated_at = CURRENT_TIMESTAMP WHERE firebase_uid = ?")
      .bind(ticketCount, uid),
    env.DB.prepare(
      "INSERT INTO ticket_events (firebase_uid, event_code, delta, balance_after, client_event_id, detail, client_created_at) VALUES (?, ?, ?, ?, ?, ?, ?)"
    ).bind(uid, eventCode, delta, ticketCount, clientEventId, String(body.detail || ""), safeInt(body.createdAt, 0))
  ]);
  return jsonResponse({ ok: true, ticketCount });
}

async function handleTicketTransfer(request, env) {
  const uid = await requireFirebaseUid(request, env);
  await ensureUser(env, uid);
  const body = await request.json().catch(() => ({}));
  const amount = safeInt(body.amount, 0);
  const targetUserCode = String(body.targetUserCode || "").trim().toUpperCase();
  if (amount <= 0 || !targetUserCode) return jsonResponse({ ok: false, error: "Invalid transfer" }, 400);

  const target = await env.DB.prepare("SELECT firebase_uid FROM users WHERE user_code = ?").bind(targetUserCode).first();
  if (!target || target.firebase_uid === uid) return jsonResponse({ ok: false, error: "Invalid target user" }, 400);

  const senderTickets = await ensureTicketRow(env, uid);
  if (senderTickets.ticket_count < amount) return jsonResponse({ ok: false, error: "Not enough tickets" }, 400);
  const receiverTickets = await ensureTicketRow(env, target.firebase_uid);
  const senderAfter = senderTickets.ticket_count - amount;
  const receiverAfter = receiverTickets.ticket_count + amount;
  const clientEventId = String(body.clientEventId || crypto.randomUUID());

  await env.DB.batch([
    env.DB.prepare("UPDATE user_tickets SET ticket_count = ?, updated_at = CURRENT_TIMESTAMP WHERE firebase_uid = ?").bind(senderAfter, uid),
    env.DB.prepare("UPDATE user_tickets SET ticket_count = ?, updated_at = CURRENT_TIMESTAMP WHERE firebase_uid = ?").bind(receiverAfter, target.firebase_uid),
    env.DB.prepare("INSERT OR IGNORE INTO ticket_events (firebase_uid, event_code, delta, balance_after, client_event_id, counterparty_user_code, detail) VALUES (?, 'B1', ?, ?, ?, ?, ?)")
      .bind(uid, -amount, senderAfter, clientEventId, targetUserCode, `Transfer to ${targetUserCode}`),
    env.DB.prepare("INSERT OR IGNORE INTO ticket_events (firebase_uid, event_code, delta, balance_after, client_event_id, counterparty_user_code, detail) VALUES (?, 'B2', ?, ?, ?, ?, ?)")
      .bind(target.firebase_uid, amount, receiverAfter, `${clientEventId}:receiver`, await getUserCode(env, uid), `Transfer from ${await getUserCode(env, uid)}`)
  ]);
  return jsonResponse({ ok: true, ticketCount: senderAfter });
}

async function getUserCode(env, uid) {
  const user = await env.DB.prepare("SELECT user_code FROM users WHERE firebase_uid = ?").bind(uid).first();
  return user?.user_code || "";
}

async function handleRedeem(request, env) {
  const authUid = await optionalFirebaseUid(request, env);
  const body = await request.json().catch(() => ({}));
  const code = String(body.code || "").trim().toUpperCase();
  if (!code) return jsonResponse({ ok: false, error: "Code is required" }, 400);

  const rewardAmount = code === "ADMIN100" ? 100 : code === "ADMIN10" ? 10 : code === "ADMIN" ? 1 : 0;
  if (rewardAmount <= 0) return jsonResponse({ ok: false, error: "Invalid code" }, 404);

  if (!authUid) return jsonResponse({ ok: true, rewardAmount });
  await ensureUser(env, authUid);
  const current = await ensureTicketRow(env, authUid);
  const ticketCount = current.ticket_count + rewardAmount;
  const clientEventId = `redeem:${authUid}:${code}`;
  await env.DB.batch([
    env.DB.prepare("UPDATE user_tickets SET ticket_count = ?, updated_at = CURRENT_TIMESTAMP WHERE firebase_uid = ?").bind(ticketCount, authUid),
    env.DB.prepare("INSERT OR IGNORE INTO ticket_events (firebase_uid, event_code, delta, balance_after, client_event_id, detail) VALUES (?, 'C', ?, ?, ?, ?)")
      .bind(authUid, rewardAmount, ticketCount, clientEventId, `Redeem ${code}`)
  ]);
  return jsonResponse({ ok: true, rewardAmount, ticketCount });
}

async function ensureUser(env, uid) {
  let user = await env.DB.prepare("SELECT firebase_uid, user_code FROM users WHERE firebase_uid = ?").bind(uid).first();
  if (user) return user;
  for (let i = 0; i < 8; i++) {
    const userCode = generateUserCode();
    try {
      await env.DB.prepare("INSERT INTO users (firebase_uid, user_code) VALUES (?, ?)").bind(uid, userCode).run();
      return { firebase_uid: uid, user_code: userCode };
    } catch (_) {
      // Retry on rare user_code collision.
    }
  }
  throw new Error("Could not generate user code");
}

async function ensureTicketRow(env, uid) {
  let row = await env.DB.prepare("SELECT ticket_count FROM user_tickets WHERE firebase_uid = ?").bind(uid).first();
  if (row) return row;
  await env.DB.prepare("INSERT INTO user_tickets (firebase_uid, ticket_count) VALUES (?, 0)").bind(uid).run();
  return { ticket_count: 0 };
}

async function requireFirebaseUid(request, env) {
  const uid = await optionalFirebaseUid(request, env);
  if (!uid) throw new Error("Unauthorized");
  return uid;
}

async function optionalFirebaseUid(request, env) {
  const header = request.headers.get("Authorization") || "";
  if (!header.startsWith("Bearer ")) return null;
  const token = header.slice("Bearer ".length).trim();
  const payload = await verifyFirebaseToken(token, env);
  return payload?.user_id || payload?.sub || null;
}

async function verifyFirebaseToken(token, env) {
  const projectId = env.FIREBASE_PROJECT_ID;
  if (!projectId) throw new Error("FIREBASE_PROJECT_ID is not configured");
  const [encodedHeader, encodedPayload, encodedSignature] = token.split(".");
  if (!encodedHeader || !encodedPayload || !encodedSignature) return null;
  const header = JSON.parse(base64UrlDecodeToString(encodedHeader));
  const payload = JSON.parse(base64UrlDecodeToString(encodedPayload));
  if (payload.aud !== projectId || payload.iss !== `https://securetoken.google.com/${projectId}`) return null;
  if (safeInt(payload.exp, 0) * 1000 <= Date.now()) return null;

  const jwk = await getGoogleJwk(header.kid);
  if (!jwk) return null;
  const key = await crypto.subtle.importKey(
    "jwk",
    jwk,
    { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" },
    false,
    ["verify"]
  );
  const verified = await crypto.subtle.verify(
    "RSASSA-PKCS1-v1_5",
    key,
    base64UrlToBytes(encodedSignature),
    new TextEncoder().encode(`${encodedHeader}.${encodedPayload}`)
  );
  return verified ? payload : null;
}

async function getGoogleJwk(kid) {
  if (!cachedGoogleKeys || cachedGoogleKeysUntil <= Date.now()) {
    const response = await fetch(GOOGLE_CERTS_URL);
    const cacheControl = response.headers.get("cache-control") || "";
    const maxAge = Number(cacheControl.match(/max-age=(\d+)/)?.[1] || 3600);
    cachedGoogleKeys = await response.json();
    cachedGoogleKeysUntil = Date.now() + maxAge * 1000;
  }
  return cachedGoogleKeys.keys.find((key) => key.kid === kid);
}

function generateUserCode() {
  const bytes = new Uint8Array(8);
  crypto.getRandomValues(bytes);
  const text = Array.from(bytes, (value) => USER_CODE_ALPHABET[value % USER_CODE_ALPHABET.length]).join("");
  return `OF-${text.slice(0, 4)}-${text.slice(4)}`;
}

function safeInt(value, fallback) {
  const parsed = Number.parseInt(value, 10);
  return Number.isFinite(parsed) ? parsed : fallback;
}

function jsonResponse(body, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: {
      "Content-Type": "application/json; charset=utf-8",
      "Access-Control-Allow-Origin": "*",
      "Access-Control-Allow-Headers": "Content-Type, Authorization",
      "Access-Control-Allow-Methods": "GET, POST, OPTIONS"
    }
  });
}

function base64UrlToBytes(value) {
  const normalized = value.replace(/-/g, "+").replace(/_/g, "/").padEnd(Math.ceil(value.length / 4) * 4, "=");
  const binary = atob(normalized);
  const bytes = new Uint8Array(binary.length);
  for (let i = 0; i < binary.length; i += 1) bytes[i] = binary.charCodeAt(i);
  return bytes;
}

function base64UrlDecodeToString(value) {
  return new TextDecoder().decode(base64UrlToBytes(value));
}
