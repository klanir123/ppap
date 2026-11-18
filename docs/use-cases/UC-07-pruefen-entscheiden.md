# UC-07 PPF-Paket prüfen und entscheiden (Kunde)

**Primärakteur:** QI-K
**Ziel:** Strukturierte Prüfung und eine digital bestätigte, signierte Entscheidung.

## Vorbedingungen
- Revision empfangen bzw. abgerufen, Hash/Signatur verifiziert. Rolle `CUSTOMER_QUALITY_ENGINEER`.

## Hauptablauf
1. QI-K öffnet den Vorgang (Status `EINGEREICHT`) → „Prüfung starten“ → `IN_PRÜFUNG`.
2. Prüfansicht je Element: Selbstbeurteilung des Lieferanten, Werte, Kommentar, Dokumentvorschau (PDF/Bild; Abruf per EDC, Hash-Prüfung), Unterlieferanten-Referenzen.
3. QI-K setzt je Element ein Prüfergebnis (akzeptiert / nicht akzeptiert / mit Auflage) und Kommentar.
4. Gesamtentscheidung: **FREIGABE**, **BEDINGTE FREIGABE** (Auflagen + Frist, Pflicht), **ABLEHNUNG** (Begründung je abgelehntem Element, Pflicht).
5. **Digitaler Bestätigungsworkflow:** Zusammenfassung → Bestätigung durch angemeldeten Benutzer (Re-Authentisierung über OIDC `max_age`/`prompt=login` konfigurierbar) → Entscheidung wird kanonisiert, gehasht, mit Mandantenschlüssel als JWS signiert (Claims: Vorgang, Revision, Submission-Hash, Ergebnis, Benutzer-ID, Zeitpunkt).
6. Vorgang → `FREIGEGEBEN` | `BEDINGT_FREIGEGEBEN` | `ABGELEHNT`; Outbox → UC-08.

## Alternativabläufe
- **A1 Vier-Augen-Prinzip** (konfigurierbar je Mandant, nicht im MVP-Pflichtumfang): zweite Bestätigung erforderlich.
- **A2 API:** `POST /api/v1/approval-cases/{id}/decision` durch technischen Client nur, wenn Mandant dies erlaubt (Bestätiger = im Payload benannter Benutzer, Audit-Kennzeichnung „via System“).

## Akzeptanzkriterien
- AK-07-1 Entscheidung ohne Pflichtbegründungen ist nicht möglich.
- AK-07-2 Entscheidung ist nach Bestätigung unveränderlich und JWS-signiert; Signatur ist mit dem öffentlichen Mandantenschlüssel (JWKS-Endpunkt bzw. DID-Dokument) prüfbar.
- AK-07-3 Signaturerzeugung liegt hinter einem Port `DecisionSigner`, der später durch Verifiable-Credential-basierte Signatur ersetzt werden kann.
- AK-07-4 Dokumentvorschau zeigt Hash-Prüfstatus (grün/rot).
