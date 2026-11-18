# UC-12 Nutzung messen

**Primärakteur:** Connector-Administrator
**Ziel:** Zähler „Freigabevorgänge pro Mandant und Monat“ als Grundlage für das Lizenzmodell.

## Definition (Annahme, siehe ASSUMPTIONS.md A-30)
Ein **Freigabevorgang** zählt einmal im Monat der **ersten Einreichung** (Revision 1) eines `ApprovalCase`, und zwar beim einreichenden Lieferanten **und** beim empfangenden Kunden (jeder Teilnehmer misst seine eigene Nutzung). Weitere Revisionen werden separat als „Revisionen“ ausgewiesen, zählen aber nicht als neuer Freigabevorgang.

## Akzeptanzkriterien
- AK-12-1 Zähler wird ereignisbasiert fortgeschrieben (Domain Event `SubmissionSubmitted` / `SubmissionReceived`) und ist aus den Rohdaten reproduzierbar.
- AK-12-2 Anzeige je Monat (12 Monate), Export CSV.
- AK-12-3 Keine Abrechnungslogik.
