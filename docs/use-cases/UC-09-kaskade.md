# UC-09 Kaskadierte Freigabe über die Lieferkette

**Primärakteur:** Tier-1 (in Rolle Kunde gegenüber Tier-2 und Lieferant gegenüber OEM)
**Ziel:** Freigaben von Unterteilen werden in der eigenen Einreichung nachvollziehbar referenziert – ohne Weitergabe der Unterlieferanten-Dokumente.

## Hauptablauf
1. Tier-1 empfängt Anforderung des OEM (UC-04).
2. Aus dem Vorgang heraus: „Unterlieferanten-Vorlage anfordern“ → UC-03 gegenüber Tier-2 für ein Unterteil (Verknüpfung Eltern-/Kindvorgang).
3. Tier-2 erstellt und reicht ein (UC-05/06), Tier-1 prüft und entscheidet (UC-07/08).
4. Tier-1 fügt seinem Paket eine `SubSupplierReference` hinzu: Kindvorgangs-ID, Unterlieferant (BPNL; optional pseudonymisiert `TODO(EG)`), Unterteil, Revision, Entscheidungsstatus, Entscheidungsdatum, Hash der Entscheidung.
5. OEM sieht die Referenzen in der Prüfansicht (nur Metadaten).

## Akzeptanzkriterien
- AK-09-1 Referenz kann nur auf eigene, entschiedene Kindvorgänge gesetzt werden.
- AK-09-2 Dokumente des Tier-2 werden nicht an den OEM übertragen.
- AK-09-3 Dashboard des Tier-1 zeigt Eltern-Kind-Beziehung und blockierende offene Kindvorgänge.
