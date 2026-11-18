# UC-06 PPF-Paket einreichen und bereitstellen

**Primärakteur:** QV-L; SYS per API
**Ziel:** Eine unveränderliche Revision ist eingereicht und dem Kunden über EDC verfügbar.

## Vorbedingungen
- Entwurf ist vollständig (UC-05). Beziehung definiert den Austauschmodus.

## Hauptablauf
1. QV-L klickt „Einreichen“ und bestätigt (digitaler Bestätigungsdialog).
2. System friert die Revision ein: `Submission` (Revision n, Status `SUBMITTED`), kanonisches JSON `PpapSubmission` wird erzeugt, gegen Schema validiert, gehasht (SHA-256 über RFC-8785-kanonisiertes JSON), mit Mandantenschlüssel signiert (JWS, detached).
3. Vorgang → `EINGEREICHT`.
4. Bereitstellung je Modus:
   - **PUSH:** Outbox sendet das vollständige `PpapSubmission` (inkl. Dokument-Metadaten; Dokumente selbst per Pull, da Binärdaten) als Notification.
   - **PULL / PUSH_NOTIFY_PULL_PAYLOAD (Standard):** System registriert/aktualisiert den Digital Twin des Freigabevorgangs in der eigenen DTR mit Submodel-Deskriptoren (`PpapSubmission`, Dokumente), legt EDC-Assets mit Access-Policy „nur dieser Kunde (BPNL)“ an und sendet eine schlanke Notification „SubmissionAvailable“ (Twin-/Submodel-IDs, Hash).
5. Kunde ruft ab (beim Kunden: Status `IN_PRÜFUNG` sobald Prüfung begonnen wird; Rückmeldung optional).

## Alternativabläufe
- **A1** Zustellung/Bereitstellung scheitert → Outbox-Retry; Revision bleibt eingereicht, UI zeigt Warnung.
- **A2** Im reinen PULL-Modus ohne Notification findet der Kunde neue Einreichungen per periodischer DTR-Suche (`specificAssetIds`), konfigurierbares Intervall.

## Akzeptanzkriterien
- AK-06-1 Eingereichte Revisionen sind unveränderlich (DB-Constraint/Trigger + Domänenprüfung); jede Änderung erzeugt eine neue Revision.
- AK-06-2 Hash im Payload, in der Notification und im `ExchangeRecord` stimmen überein; Kunde verifiziert Hash und Signatur.
- AK-06-3 Dokumente sind nur für den berechtigten Kunden über EDC abrufbar (Policy je BPNL).
- AK-06-4 Moduswahl je Beziehung wirkt ohne Neustart.
- AK-06-5 Eine Einreichung zählt als ein Freigabevorgang im Nutzungszähler (UC-12), Revisionen werden separat ausgewiesen.
