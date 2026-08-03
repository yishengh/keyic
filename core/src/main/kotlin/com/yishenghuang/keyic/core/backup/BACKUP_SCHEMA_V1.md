/**
 * Keyic encrypted JSON backup — schema v1
 *
 * Binary layout:
 *   MAGIC "KEYIC1" (6 bytes)
 *   salt  (16 bytes)
 *   iv    (12 bytes)
 *   ciphertext (AES-256-GCM of UTF-8 JSON)
 *
 * KDF: Argon2id (m=65536 KiB, t=3, p=2) → 32-byte AES key
 *
 * JSON payload:
 * {
 *   "schema": 1,
 *   "exportedAt": <epochMillis>,
 *   "entries": [ { id, title, username, password, url, packageHints, totpSecret,
 *                  notes, tags, favorite, createdAt, updatedAt, passwordChangedAt } ]
 * }
 *
 * This format is the cross-platform interchange contract for a future iOS client.
 * KeePass .kdbx remains a separate adapter (ImportExportPort) and is not required
 * to understand this schema.
 */
