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
