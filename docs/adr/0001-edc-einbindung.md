# ADR-0001 EDC-Einbindung über Port/Adapter (EDC-agnostisch)

## Kontext
Der Connector muss heute mit einem mitgelieferten Tractus-X EDC laufen und später mit extern betriebenen EDCs (Managed EDC eines Betreibers, ARENA2036, Kunden-EDC) – ggf. in anderen Versionen. Die Management API ändert sich zwischen Minor-Versionen (z. B. `/v2/edrs` → `/v3/edrs` von 0.12 → 0.13, Participant-ID = DID ab 0.13, umbenannte Identity-Keys).

## Entscheidung
1. **Port `DataspaceConnector`** (Modul `exchange`, Application-Schicht) mit fachneutralen Operationen:
   - Provisionierung: `ensureAsset(AssetSpec)`, `ensurePolicy(PolicySpec)`, `ensureContractDefinition(...)`, `removeAsset(...)` – **idempotent, deklarativ** (Reconciliation beim Start und bei Policy-Änderung)
   - Consumer: `requestCatalog(partner, filter)`, `obtainEndpoint(partner, assetId, policy) → EndpointDataReference` (intern: Negotiation + Transfer bzw. `POST /v3/edrs`, EDR-Cache mit Refresh)
   - Monitoring: `listNegotiations`, `listTransfers`, `health`
2. **Adapter `TractusXEdcV3Adapter`** (`adapter.out.edc`) für Management API v3 der Tractus-X EDC 0.12/0.13, Versionsunterschiede über Konfiguration `ppap.edc.api-flavor=txedc-0.13|txedc-0.12`. Weitere Adapter (anderer Hersteller, Management API v4) können ergänzt werden.
3. **EDC läuft als eigener Container je Teilnehmer** (Control Plane + Data Plane, eigene Datenbank in der gemeinsamen Postgres-Instanz, eigenes Secret Store). Das Backend ist **nicht** als EDC-Extension implementiert. Kommunikation Backend → EDC nur über Management API (API-Key, später OAuth2), EDC → Backend nur über die im Asset hinterlegte `HttpData`-Basis-URL (Notification-Empfang, Submodel/Dokumente) mit Shared Secret (`authKey`/`authCode` im DataAddress, Secret-Alias im Vault).
4. **Version:** Tractus-X EDC **0.13.0** (Tractus-X 26.09); **Spike** am Anfang von Phase 1 mit Wallet-Stub 0.1.0. Bei Inkompatibilität **0.12.3** (Umbrella-26.06-Konstellation). Images per Env (`EDC_VERSION`) umschaltbar.
5. **Secret Store:** Die Tractus-X-Images erwarten die HashiCorp-Vault-API. Lokal **OpenBao** (MPL-2.0, API-kompatibel) im Dev-Modus; falls Inkompatibilitäten auftreten, `hashicorp/vault` (BSL; für lokale Entwicklung zulässig). Produktiv entscheidet der Betreiber.
6. **Identität lokal:** Wallet-Stub (STS, Credential Service, DID-Dokumente `did:web:wallet-stub:<BPNL>`, BPN-Directory statt eigenem BDRS). Konfiguration über Env (`EDC_IAM_*`, `TX_EDC_IAM_*`).
7. **Integrationstests** des Adapters gegen echte EDC-Container (Testcontainers Compose-Modul, Tag `@EdcIntegration`, in CI nightly + vor Merge auf `main`).

## Alternativen
- *Backend als EDC-Extension (im EDC-Prozess)*: weniger Hops, aber harte Kopplung an EDC-Version/Framework, kein Managed-EDC-Betrieb möglich → verworfen.
- *Generierter Client aus EDC-OpenAPI*: JSON-LD/ODRL-Payloads sind in OpenAPI schwach typisiert; generierte Typen ändern sich je Version. Stattdessen schlanke handgeschriebene Requests (Jackson 3 + JSON-LD-Kontext-Konstanten) mit Contract-Tests.
- *Tractus-X SDK / Connector-Lib anderer Projekte*: nach Recherche keine stabile, gepflegte Java-Client-Bibliothek für 0.13 gefunden → eigener Adapter.

## Konsequenzen
+ Austauschbarkeit des EDC, Betrieb mit Managed EDC möglich; Domäne frei von EDC-Details.
+ Reconciliation macht Provisionierung robust gegen EDC-Neustarts (In-Memory-Teile, DB-Resets).
− Eigener Adapter muss mit EDC-Releases gepflegt werden (Contract-Tests je unterstützter Version).

## Umsetzung (Phase 4, 2026-11-19)
- **Port** `exchange.application.port.out.DataspaceConnector`: `ensureAsset`, `ensurePolicy`, `ensureContractDefinition`, `ensureBusinessPartnerGroup`, `requestCatalog`, `obtainEndpoint` (mit Schritt-Listener für das ExchangeRecord), `invalidateEndpoint`, `call` (Data Plane), `listNegotiations`, `listTransfers`, `health`. Adapter `TractusXEdcAdapter` (ein Adapter für 0.13; `api-flavor` noch nicht nötig), handgeschriebene JSON-LD-Requests (Jackson 3), Contract-Tests gegen aufgezeichnete Antworten (`src/test/resources/edc`).
- **Provisionierung** (`ProvisioningService`): beim Start (Retry alle 15 s bis der EDC erreichbar ist), nach Partner-/Beziehungsänderungen (`MasterdataChanged`) und alle 10 min. Assets per `POST`, bei `409` `PUT`.
- **Policies sind inhaltsadressiert:** EDC 0.13 erlaubt keine Änderung einer Policy Definition, die von einer Contract Definition referenziert wird (und antwortet auf ein erneutes `POST` mit `400 "Policy Definition is referenced by a Contract Definition"` statt `409`). Policy-IDs sind daher `<basis>-<8 Hex SHA-256 des Inhalts>` (z. B. `ppap-usage-1a2b3c4d`); ändert sich die Policy (z. B. UsagePurpose), entsteht eine neue ID und die Contract Definitions werden per `PUT` umgehängt.
- **Shared Secret:** im Vault des Teilnehmers (KV v2, `secret/ppap-edc-callback-secret`, Feld `content`), im Asset nur `authKey=X-Ppap-Edc-Secret` + `secretName`. Im Quellcode der 0.13-Data-Plane verifiziert (`BaseCommonHttpParamsDecorator.extractAuthCode`: `authCode` oder `vault.resolveSecret(secretName)`). Ohne Vault-Konfiguration Fallback `authCode` (WARN-Log).
- **EDR-Caching:** Der EDC speichert EDRs selbst; vor jeder Zustellung wird `POST /v3/edrs/request` (Filter `assetId` + `providerId` = Partner-DID) abgefragt und der neueste EDR mit `GET …/dataaddress?auto_refresh=true` wiederverwendet (Katalog/Verhandlung/Transfer = `SKIPPED`). Antwortet die Data Plane `401/403`, werden die EDRs gelöscht und beim nächsten Versuch neu verhandelt. Gemessen lokal: Erstkontakt ≈ 8 s (Verhandlung ≈ 5 s), danach ≈ 0,3–0,6 s je Nachricht Ende-zu-Ende.
- Beobachtet (0.13 Data Plane, `ProxyHttpDataSource`): `202` des Backends kommt beim Konsumenten als `200` an, ein `401` des Backends als `500 {"errors":[]}` (`NOT_AUTHORIZED` im Data-Plane-Log). Der Sender behandelt daher alle Nicht-2xx außer `400/422` als wiederholbar.
