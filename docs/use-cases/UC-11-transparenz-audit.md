# UC-11 Transparenz und Audit

**Primärakteur:** alle fachlichen Rollen
**Ziel:** Überblick über offene Vorgänge und lückenlose Nachvollziehbarkeit.

## Hauptablauf
1. Dashboard: KPIs (offene Vorgänge, überfällige Termine, Ø Durchlaufzeit Anforderung → Entscheidung, Freigabequote), Aktivitätsfeed, Austauschstatus je Partner.
2. Vorgangsdetail → Timeline und Audit-Trail: wer, wann, was, welcher Transfer, welcher Hash.
3. Export des Audit-Trails eines Vorgangs (JSON/PDF-Bericht).

## Akzeptanzkriterien
- AK-11-1 Audit-Events sind append-only (keine Update/Delete-Rechte für den Applikations-DB-User auf der Tabelle; Hash-Verkettung je Vorgang).
- AK-11-2 Jeder ExchangeRecord ist vom zugehörigen Audit-Event aus verlinkt.
- AK-11-3 KPIs werden aus der Datenbank berechnet, keine statischen Werte.
