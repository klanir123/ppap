# UC-05 PPF-Paket erstellen (Lieferant)

**Primärakteur:** Qualitätsverantwortlicher Lieferant (QV-L); SYS per API
**Ziel:** Für jedes geforderte Element liegen Selbstbeurteilung, ggf. strukturierte Werte und Nachweise vor.

## Vorbedingungen
- Vorgang im Status `ANGEFORDERT` oder `IN_BEARBEITUNG` (Lieferantensicht). Rolle `SUPPLIER_QUALITY_MANAGER`.

## Hauptablauf (Wizard „PPF-Paket erstellen“)
1. QV-L öffnet den Vorgang → „Bearbeitung starten“ → Status `IN_BEARBEITUNG` (wird optional per Notification an den Kunden gemeldet, `TODO(EG)`).
2. Je gefordertem Element:
   - Selbstbeurteilung: `OK` (i. O.) | `NOK` (n. i. O.) | `NA` (nicht zutreffend, Begründung Pflicht)
   - Strukturierte Werte gemäß Werte-Schema des Elements (z. B. Merkmal, Soll, Ist, Cpk), Kommentar
   - Nachweisdokumente hochladen (PDF, Bilder, XLSX, ZIP …; Typ- und Größenprüfung, Virenscan-Hook), SHA-256 wird berechnet
3. Optional **Unterlieferanten-Referenzen** hinzufügen (UC-09).
4. **Vollständigkeitsprüfung** zeigt fehlende Pflichtangaben.
5. Entwurf wird laufend gespeichert (Revision im Status `DRAFT`).

## Alternativabläufe
- **A1 Excel-Formblatt:** QV-L lädt ein ausgefülltes Formblatt (XLSX) hoch → System erkennt die Vorlage (Template-Definition mit Zellbereichen, Datenbankinhalt) → Vorschau mit Feld-Mapping (automatisch erkannt, manuell korrigierbar) → Übernahme in strukturierte Daten; Original-Datei wird als Nachweis mit Hash gespeichert.
- **A2 API:** SYS befüllt Elemente per `PUT /api/v1/approval-cases/{id}/submissions/draft/elements/{code}` und lädt Dokumente per Multipart hoch.
- **A3 Neue Revision nach Ablehnung:** Entwurf wird aus der letzten Revision vorbelegt.

## Akzeptanzkriterien
- AK-05-1 Jedes geforderte Element hat eine Selbstbeurteilung; `NA` und `NOK` verlangen einen Kommentar.
- AK-05-2 Dokument-Upload: max. Größe konfigurierbar (Default 50 MB), Whitelist MIME-Typen mit Magic-Byte-Prüfung, SHA-256 gespeichert.
- AK-05-3 Excel-Import erkennt das Seed-Formblatt „Deckblatt/Selbstbeurteilung“ und überführt mindestens Kopfdaten und Elementbewertungen.
- AK-05-4 Werte werden gegen das Werte-Schema validiert.
