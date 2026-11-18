# UC-08 Entscheidung zurückmelden

**Primärakteur:** System
**Ziel:** Der Lieferant erhält die Entscheidung inkl. Begründungen strukturiert über EDC.

## Hauptablauf
1. Outbox sendet `PpapDecision` (inkl. JWS) als Notification an den Lieferanten.
2. Lieferant: Empfang analog UC-04 (Idempotenz, Inbox, Schema, Signaturprüfung gegen Kunden-Schlüssel).
3. Lieferanten-Vorgang übernimmt Status und Begründungen; Benachrichtigung in der UI.
4. Bei `ABGELEHNT` oder `BEDINGT_FREIGEGEBEN` kann der Lieferant eine **neue Revision** anlegen → `IN_BEARBEITUNG` (bei Auflagen: Nachweis der Auflagenerfüllung als neue Revision).

## Akzeptanzkriterien
- AK-08-1 Status beim Lieferanten entspricht nach Zustellung dem beim Kunden.
- AK-08-2 Ungültige Signatur → Nachricht wird markiert, Status nicht übernommen, CA-Hinweis.
- AK-08-3 Neue Revision referenziert die Vorgänger-Revision und die Entscheidung.
