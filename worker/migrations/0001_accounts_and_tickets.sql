CREATE TABLE IF NOT EXISTS users (
  firebase_uid TEXT PRIMARY KEY,
  user_code TEXT UNIQUE NOT NULL,
  created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS user_tickets (
  firebase_uid TEXT PRIMARY KEY,
  ticket_count INTEGER NOT NULL DEFAULT 0,
  updated_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY(firebase_uid) REFERENCES users(firebase_uid)
);

CREATE TABLE IF NOT EXISTS ticket_events (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  firebase_uid TEXT NOT NULL,
  event_code TEXT NOT NULL,
  delta INTEGER NOT NULL,
  balance_after INTEGER NOT NULL,
  client_event_id TEXT NOT NULL UNIQUE,
  counterparty_user_code TEXT,
  detail TEXT,
  created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
  client_created_at INTEGER,
  FOREIGN KEY(firebase_uid) REFERENCES users(firebase_uid)
);

CREATE INDEX IF NOT EXISTS idx_ticket_events_firebase_uid ON ticket_events(firebase_uid);
CREATE INDEX IF NOT EXISTS idx_users_user_code ON users(user_code);
