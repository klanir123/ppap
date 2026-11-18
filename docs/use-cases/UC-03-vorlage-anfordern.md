# UC-03 PPF-Vorlage anfordern (Kunde)

**Primärakteur:** Qualitätsingenieur Kunde (QI-K); SYS per API
**Ziel:** Ein Lieferant erhält eine eindeutige, strukturierte PPF-Anforderung für ein Teil.

## Vorbedingungen
- Rolle `CUSTOMER_QUALITY_ENGINEER`.
- Teil existiert, Beziehung zum Lieferanten mit Teilezuordnung existiert, Partner hat DSP-Endpoint.

## Hauptablauf (Wizard)
1. **Teil wählen:** QI-K sucht nach Kundensachnummer/Bezeichnung.
2. **Lieferant:** System schlägt Lieferant(en) aus der Beziehung vor (inkl. Lieferantensachnummer aus `PartMapping`, sofern bekannt).
3. **Vorlagegrund und Vorlagestufe** wählen.
4. **Elemente:** System belegt die geforderten Elemente gemäß Stufe vor; QI-K kann ergänzen/abwählen und je Element Hinweise hinterlegen.
5. **Termin und Bemerkungen**, optional Anhänge (z. B. Zeichnung, Prüfvorschrift; werden per Pull bereitgestellt).
6. **Zusammenfassung** → „Senden“.
7. System legt `ApprovalCase` (Status `ENTWURF` → `ANGEFORDERT`) und `ApprovalRequest` an, erzeugt Outbox-Nachricht `PpapRequest` (validiert gegen JSON-Schema) und versendet asynchron über EDC an den Lieferanten (UC-04 beim Partner).
8. UI zeigt Fortschritt im Tab „Datenaustausch“ (Katalog → Negotiation → Transfer/EDR → Zustellung).

## Alternativabläufe
- **A1 Entwurf speichern:** Vorgang bleibt `ENTWURF`, nicht versendet.
- **A2 Zustellung schlägt fehl:** Outbox wiederholt mit Backoff; nach max. Versuchen Status der Nachricht `FAILED`, Vorgang bleibt `ANGEFORDERT` mit Warnhinweis „nicht zugestellt“, manueller Retry im Cockpit.
- **A3 API:** SYS ruft `POST /api/v1/approval-cases` + `POST /api/v1/approval-cases/{id}/request:send`.

## Nachbedingungen
- Vorgang `ANGEFORDERT`; `ExchangeRecord`(s) zeigen Zustellstatus; Audit-Eintrag vorhanden.

## Akzeptanzkriterien
- AK-03-1 Ohne vorhandene Beziehung kann kein Lieferant gewählt werden.
- AK-03-2 Payload `PpapRequest` validiert gegen das generierte JSON-Schema, sonst kein Versand.
- AK-03-3 Versand ist idempotent (Message-ID); Doppelversand führt beim Empfänger nicht zu Doppelanlage.
- AK-03-4 Zustellbestätigung des Empfängers (HTTP 2xx über Data Plane) setzt Austauschstatus `DELIVERED`.
- AK-03-5 Snapshot der Elementdefinitionen ist Teil der Anforderung.
