# ADR-0008 Digitale Bestätigung und Signatur

## Kontext
Partner wollen keine nasse Unterschrift, sondern einen nachvollziehbaren digitalen Bestätigungsworkflow. Später soll die Absicherung über Verifiable Credentials / digitale Identitäten in Catena-X erfolgen.

## Entscheidung
- **Bestätigung:** explizite Benutzeraktion im UI (Zusammenfassung + Bestätigungsdialog), optional OIDC-Re-Authentisierung (`max_age`), Benutzer-ID und Zeitpunkt werden Teil der signierten Daten.
- **Signatur:** Einreichungen (`PpapSubmission`) und Entscheidungen (`PpapDecision`) werden als **JWS (detached, ES256)** über das RFC-8785-kanonisierte JSON mit dem **Mandantenschlüssel** signiert (Nimbus JOSE+JWT). Der private Schlüssel liegt im Vault/OpenBao (Transit-Engine perspektivisch), der öffentliche Schlüssel ist über einen JWKS-Endpunkt des Connectors **und** als Verification Method im DID-Dokument (Wallet-Stub) auflösbar.
- **Port** `PayloadSigner` / `SignatureVerifier`; Implementierung `JwsTenantKeySigner`. Spätere Implementierung `VerifiableCredentialSigner` (z. B. Entscheidung als VC, ausgestellt vom Teilnehmer-DID) ohne Änderung der Domäne.
- Hash + Signatur werden im `AuditEvent` und `ExchangeRecord` gespeichert und beim Empfang verifiziert.

## Konsequenzen
+ Unabstreitbarkeit auf Unternehmensebene, Integrität end-to-end (unabhängig vom Transportkanal).
− Keine personengebundene, qualifizierte Signatur (bewusst nicht im Scope, A-19).

## Umsetzung (Phase 3)
- **JWS-Variante:** ES256, *detached* Compact Serialization mit **unkodiertem Payload nach RFC 7797** (`b64: false`, `crit: ["b64"]`): `<base64url(Header)>..<base64url(Signatur)>`. Signiert wird `ASCII(base64url(Header)) || '.' || kanonische Payload-Bytes` – also genau die gespeicherten und übertragenen RFC-8785-Bytes, ohne zweite Base64url-Kodierung. Prüfer brauchen die Payload-Bytes und müssen `b64` verstehen (Nimbus: `ECDSAVerifier(key, Set.of("b64"))`).
- **`kid`** = JWK-Thumbprint nach RFC 7638 (SHA-256, base64url) des öffentlichen Schlüssels; öffentlich unter `GET /.well-known/jwks.json`.
- **Ports:** `PayloadSigner` / `SignatureVerifier` (Modul `identity`, Implementierung `JwsTenantKeySigner`), Schlüsselquelle `SigningKeyProvider` mit Adaptern `vault` (OpenBao/Vault KV v2, `secret/ppap-signing-key`, Feld `content`, PEM PKCS#8 EC P-256), `file` (`PPAP_SIGNING_KEY_FILE`) und `generated` (nur Entwicklung/Tests, WARN-Log). Kann der Schlüssel nicht geladen werden, startet die Anwendung nicht (fail fast).
- **Verifikation fremder Signaturen:** Phase 3 kennt nur den eigenen Schlüssel; Signaturen mit unbekanntem `kid` erhalten den Status `UNVERIFIED` (Auflösung über DID/JWKS des Partners: Phase 4). `INVALID` wird beim Empfang und vor Prüfungsbeginn abgewiesen.
