# UC-01 Stammdaten verwalten

**Primärakteur:** Stammdatenpfleger (SDP); Systemintegration (SYS) per API
**Ziel:** Teile, Partner, Standorte und Kunden-Lieferanten-Beziehungen sind vollständig und aktuell gepflegt, damit Freigabevorgänge korrekt zugeordnet und über EDC adressiert werden können.

## Vorbedingungen
- Benutzer ist angemeldet und hat die Rolle `MASTERDATA_MANAGER` (oder technischer Client mit Scope `masterdata:write`).
- Eigener Mandant (BPNL) ist konfiguriert.

## Hauptablauf (UI)
1. SDP öffnet „Stammdaten“ und wählt den Reiter (Teile | Partner | Standorte | Beziehungen).
2. SDP legt einen Datensatz an oder bearbeitet ihn inline.
   - **Teil:** Lieferantensachnummer (eigene Sachnummer), Bezeichnung DE/EN, Zeichnungsnummer, Änderungsstand (Zeichnungsindex), Werkstoff, optional Catena-X-ID (`urn:uuid`) für Wiederverwendung im Digital Twin.
   - **Partner:** BPNL, Name, Rolle(n) (Kunde/Lieferant), DSP-Endpoint des Partner-EDC, DID, Kontakt (Funktionspostfach, keine Personendaten im Seed).
   - **Standort:** BPNS, Name, Adresse, zugehöriger Partner oder eigener Mandant.
   - **Beziehung:** Kunde, Lieferant (einer davon ist immer der eigene Mandant), Austauschmodus (PUSH | PULL | PUSH_NOTIFY_PULL_PAYLOAD), Policy-Profil, Teilezuordnungen (`PartMapping`: eigene Sachnummer ↔ Sachnummer des Partners, ggf. Lieferwerk/Abnahmewerk).
3. System validiert (Pflichtfelder, BPNL-/BPNS-Format `BPNL[0-9A-Z]{12}`, Eindeutigkeit, URL-Format).
4. System speichert und protokolliert den Audit-Eintrag.

## Alternativabläufe
- **A1 Import CSV/Excel:** SDP lädt Datei hoch → System zeigt Vorschau mit Spaltenzuordnung und Validierungsbericht je Zeile (OK / Warnung / Fehler) → SDP bestätigt → gültige Zeilen werden importiert (Upsert über fachlichen Schlüssel), fehlerhafte als Bericht herunterladbar. Import ist idempotent.
- **A2 REST-API:** SYS ruft `PUT /api/v1/parts/{supplierPartNumber}` bzw. Bulk-Endpunkte auf (Upsert, idempotent, ETag/`If-Match` für optimistische Sperre).
- **A3 Löschen:** Nur möglich, wenn kein offener Freigabevorgang referenziert; sonst Deaktivierung (Soft Delete).

## Nachbedingungen
- Daten sind persistiert, versioniert (`updated_at`, `version`) und im Audit-Trail nachvollziehbar.

## Akzeptanzkriterien
- AK-01-1 Teil, Partner, Standort, Beziehung können per UI angelegt, geändert, deaktiviert werden.
- AK-01-2 CSV- und XLSX-Import mit Vorschau, Spaltenmapping und zeilengenauem Validierungsbericht; 500 Zeilen < 5 s.
- AK-01-3 Upsert über REST-API ist idempotent; Ergebnis identisch zum UI-Pfad (gleiche Validierung).
- AK-01-4 Ungültige BPNL/BPNS werden abgelehnt mit verständlicher Meldung (DE/EN).
- AK-01-5 Ein Partner ohne DSP-Endpoint kann gespeichert, aber nicht für den Versand genutzt werden (Hinweis in UI).
- AK-01-6 Stammdaten eines Mandanten sind für andere Mandanten nicht sichtbar.
