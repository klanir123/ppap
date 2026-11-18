# UC-10 Datenaustausch überwachen (Connector-Cockpit)

**Primärakteur:** Connector-Administrator (CA)
**Ziel:** Transparenz über den eigenen EDC und alle Austauschvorgänge; Fehlerbehebung ohne Kommandozeile.

## Hauptablauf
1. CA öffnet „Connector-Cockpit“.
2. Übersicht: Erreichbarkeit Control/Data Plane, DTR, Identität (Wallet), Version.
3. Reiter **Assets / Policies / Contract Definitions** (gelesen aus der Management API, mit Kennzeichnung „vom Connector verwaltet“).
4. Reiter **Partner-Katalog**: Partner wählen → Katalog abrufen → angebotene Assets anzeigen.
5. Reiter **Negotiations / Transfers / EDRs** mit Status.
6. Reiter **Outbox/Inbox**: Nachrichten mit Status, Versuche, letzter Fehler; **Retry** und **Verwerfen**.
7. **Live-Log** (SSE) der Austauschereignisse mit Korrelations-ID.
8. **Policies je Partnerbeziehung** konfigurieren (Policy-Profil: Access = BPN/Membership; Usage = FrameworkAgreement, UsagePurpose …); Änderungen werden im EDC nachgezogen.

## Akzeptanzkriterien
- AK-10-1 Fehlgeschlagene Outbox-Nachricht kann per Klick erneut gesendet werden; Ergebnis live sichtbar.
- AK-10-2 Partner-Katalog wird live über DSP abgerufen (kein Cache-Mock).
- AK-10-3 Policy-Änderung in der UI erzeugt/aktualisiert Policy- und Contract-Definition im EDC.
