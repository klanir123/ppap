# UC-02 Vorlageelement-Katalog pflegen

**Primärakteur:** Stammdatenpfleger (SDP)
**Ziel:** Vorlageelemente, Vorlagestufen und Vorlagegründe sind als konfigurierbare Daten verfügbar und dienen als Grundlage für Anforderungen und Pakete.

## Vorbedingungen
- Rolle `MASTERDATA_MANAGER`. Seed-Katalog (angelehnt an VDA Band 2 und AIAG PPAP 4th Ed.) ist geladen.

## Hauptablauf
1. SDP öffnet „Stammdaten → Elementkatalog“.
2. SDP pflegt **Vorlageelemente**: Code (z. B. `VDA-04`), Bezeichnung DE/EN, Beschreibung, Referenz (VDA-Band-2-Nr., AIAG-Element-Nr.), Evidenztyp (Dokument, strukturierte Werte, beides), optionales **Werte-Schema** (JSON Schema für strukturierte Werte, z. B. Cpk-Liste), gültig ab/bis, aktiv.
3. SDP pflegt **Vorlagestufen** (z. B. VDA Stufe 0–4 / AIAG Level 1–5) mit Vorbelegung, welche Elemente je Stufe gefordert sind (Matrix Element × Stufe: gefordert / auf Anforderung / nicht gefordert).
4. SDP pflegt **Vorlagegründe** (z. B. Neuteil, Produktänderung, Lieferantenwechsel, Verlagerung, Verfahrensänderung, Unterbrechung > 12 Monate …).
5. System versioniert den Katalog; laufende Vorgänge behalten die Elementdefinition zum Zeitpunkt der Anforderung (Snapshot).

## Alternativabläufe
- **A1** Import/Export des Katalogs als JSON/XLSX (z. B. Austausch eines Standard-Katalogs zwischen Mandanten oder Update auf den Catena-X-Standard).
- **A2** Ein Code wird von einem Partner gesendet, der im eigenen Katalog unbekannt ist → Element wird mit den gesendeten Bezeichnungen als „fremdes Element“ angezeigt (keine Ablehnung).

## Nachbedingungen
- Katalogänderung ist versioniert und auditiert; bestehende Vorgänge unverändert.

## Akzeptanzkriterien
- AK-02-1 Keine Katalogwerte im Java- oder Frontend-Code; Seed liegt unter `seed/`.
- AK-02-2 Vorbelegung der Elemente im Anforderungs-Wizard folgt der Matrix der gewählten Stufe.
- AK-02-3 Deaktivierte Elemente sind für neue Anforderungen nicht wählbar, in Altvorgängen weiterhin sichtbar.
- AK-02-4 Unsichere Inhalte sind im Seed mit `TODO(EG)` markiert.
- AK-02-5 Der Austausch mit Partnern verwendet den Element-**Code** (stabil) plus Bezeichnung als Fallback.
