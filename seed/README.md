# Seed-Daten

Fiktive, aber realistische Stammdaten für die Demo-Teilnehmer (keine echten Firmen, Sachnummern oder Personendaten).
Format und Ladevorgang: [docs/api/masterdata-api.md](../docs/api/masterdata-api.md#seed-daten).

| Ordner | Inhalt |
|---|---|
| `common/catalog.json` | Elementkatalog (24 Elemente), Vorlagestufen (VDA 1–3, AIAG 1–5, kundenspezifisch), Vorlagegründe – Orientierung an VDA Band 2 / AIAG PPAP, `TODO(EG)` |
| `oem/` | 45 Teile, Lieferanten Tier-1 (Catena-X-fähig) + 2 Lieferanten ohne EDC, 2 Werke |
| `tier1/` | 42 Teile (30 an OEM, 12 Zukaufteile von Tier-2), Kunden OEM + Nutzfahrzeuge (ohne EDC), Lieferant Tier-2 |
| `tier2/` | 32 Teile (12 an Tier-1), Kunde Tier-1, Werkzeugbauer (ohne EDC) |

Die Teilezuordnungen sind auf beiden Seiten jeder Beziehung **spiegelgleich** (OEM-Kundensachnummer ↔ Tier-1-Sachnummer, Tier-1 ↔ Tier-2), damit eingehende Anforderungen automatisch zugeordnet werden (UC-04).

**Nicht von Hand editieren** – die Dateien werden erzeugt:

```bash
python3 seed/generate_catalog.py && python3 seed/generate_participants.py
```

Die App lädt die Daten beim Start (`PPAP_SEED_ENABLED=true`) idempotent über dieselben Import-Services wie UI und API. `make demo` setzt die Datenbanken zurück und lädt den Seed neu.
