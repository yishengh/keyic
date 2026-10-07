# Keyic portable backup contract

The filename is historical. The writer produces schema **3**; the reader accepts
schemas **1, 2 and 3** and rejects unknown versions without modifying a vault.

Binary envelope (unchanged): `KEYIC1` (6 ASCII bytes), salt (16 bytes), AES-GCM IV
(12 bytes), then authenticated ciphertext (including its 16-byte tag).
Argon2id uses **32,768 KiB**, 3 iterations, parallelism 2, and a 32-byte output.
The previous documentation incorrectly said 65,536 KiB; the implementation and
this maintenance work use the existing 32,768 KiB format. No KDF downgrade occurs.

The decrypted UTF-8 JSON has `schema`, `exportedAt`, `entries`, and (since v3)
`attachments`. Entries preserve type, credentials, website/package hints, TOTP,
notes, tags, favorite, card expiry/CVV, icon, custom fields and timestamps.
Attachments contain entry ID, attachment ID, filename, MIME type, creation time,
and Base64 bytes. Entry IDs must be unique; attachments must reference an included
entry. Unknown entry types are rejected rather than silently coerced.

Only active entries are exported. The recycle bin is not included. Restore
replaces the current vault (including its recycle bin) after full decryption and
validation. The UI warns to export existing data first. New encrypted attachment
files are staged under new IDs and fsynced before one Room transaction replaces
rows and metadata. SQL failure rolls back rows and deletes only the staged files;
old files are left intact. Interrupted staging may leave encrypted orphan files,
which are not visible as vault entries. There is no automatic destructive recovery.

Documents are limited to 64 MiB, attachments to 5 MiB each / 20 MiB per entry.
The writer rejects a `.keyic` backup that its reader would reject for size. SAF
automatic backups use unique names and retain earlier files. Users manage
retention in their chosen storage provider.

KeePass uses KeePassJava2, not this envelope. Keyic-specific fields are preserved
in the encrypted `Keyic.Entry.v1` custom property; normal KeePass fields remain
readable by other clients. Import generates new entry IDs and adds entries in a
single transaction. Duplicate attachment names cannot be represented faithfully
by KeePassJava2 and cause export to fail; `.keyic` preserves them. CSV is a limited,
plaintext interchange format and is not a complete backup.
