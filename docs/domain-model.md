# Fachliches Datenmodell (verfeinert)

Stand: Phase 0. Alle fachlichen Entitäten tragen `tenant_id`, `id` (UUIDv7), `version` (optimistische Sperre), `created_at/by`, `updated_at/by`.

## Klassendiagramm

```mermaid
classDiagram
  direction LR
  class Tenant { bpnl; name; signingKeyRef; didRef }
  class Partner { bpnl; name; roles[]; dspEndpoint; did; active }
  class Site { bpns; name; address; ownerBpnl }
  class Part { supplierPartNumber; nameDe; nameEn; drawingNumber; drawingRevision; material; catenaXId }
  class CustomerSupplierRelation { customerBpnl; supplierBpnl; exchangeMode; policyProfileId; active }
  class PartMapping { ownPartId; partnerPartNumber; customerSiteBpns; supplierSiteBpns }
  class PolicyProfile { name; accessConstraints; usageConstraints }
  class ElementCatalogEntry { code; nameDe; nameEn; reference; evidenceType; valueSchema; validFrom; active }
  class SubmissionLevel { code; name; matrix: Map~code,Requirement~ }
  class SubmissionReason { code; nameDe; nameEn }
  class ApprovalCase { caseNumber; ownRole; partnerCaseId; partRef; customerBpnl; supplierBpnl; reason; level; dueDate; status; parentCaseId }
  class ApprovalRequest { requestedElements[]; remarks; attachments[]; sentAt }
  class RequestedElement { code; snapshot; requirement; note }
  class Submission { revision; status; submittedAt; payloadHash; signature }
  class ElementEvidence { code; selfAssessment; values; comment }
  class Document { fileName; mediaType; size; sha256; storageKey }
  class Decision { result; conditions[]; conditionDueDate; elementFindings[]; decidedBy; decidedAt; payloadHash; signature }
  class SubSupplierReference { childCaseId; subSupplierBpnl; partNumber; revision; decisionResult; decisionHash }
  class ExchangeRecord { direction; mode; messageType; partnerBpnl; assetId; negotiationId; agreementId; transferId; status; payloadHash; correlationId; ts }
  class OutboxMessage { messageId; type; payload; attempts; nextAttemptAt; status; lastError }
  class InboxMessage { messageId; senderBpnl; payload; sha256; status }
  class AuditEvent { ts; actor; action; objectRef; details; prevHash; hash }
  class UsageCounter { month; approvalCases; revisions }

  Partner "1" -- "*" Site
  CustomerSupplierRelation "*" -- "1" Partner
  CustomerSupplierRelation "1" -- "*" PartMapping
  CustomerSupplierRelation "*" -- "1" PolicyProfile
  PartMapping "*" -- "1" Part
  SubmissionLevel "*" -- "*" ElementCatalogEntry
  ApprovalCase "1" -- "1" ApprovalRequest
  ApprovalRequest "1" -- "*" RequestedElement
  ApprovalCase "1" -- "*" Submission : Revisionen
  Submission "1" -- "*" ElementEvidence
  ElementEvidence "1" -- "*" Document
  Submission "1" -- "0..1" Decision
  Submission "1" -- "*" SubSupplierReference
  ApprovalCase "1" -- "*" ExchangeRecord
  ApprovalCase "0..1" -- "*" ApprovalCase : Kindvorgänge
```

## Aggregate und Modulzuordnung

| Modul | Aggregate / Entitäten |
|---|---|
| `masterdata` | Partner (+Site), Part, CustomerSupplierRelation (+PartMapping), PolicyProfile, ElementCatalogEntry, SubmissionLevel, SubmissionReason, ExcelTemplate |
| `approval` | **ApprovalCase** (Aggregate Root: ApprovalRequest, Submission, ElementEvidence, Decision, SubSupplierReference) |
| `documents` | Document (Metadaten; Binärdaten im Object Storage) |
| `exchange` | OutboxMessage, InboxMessage, ExchangeRecord, EdrCacheEntry |
| `twin` | TwinRegistration (Shell-ID ↔ ApprovalCase/Revision, Submodel-IDs, Asset-IDs) |
| `semantics` | keine Persistenz; SAMM-Schemas, Mapper, Validierung, Kanonisierung |
| `audit` | AuditEvent (append-only, Hash-Kette je Objekt) |
| `identity` | Tenant, Signaturschlüssel-Referenzen, Benutzerkontext |
| `usage` | UsageCounter |

Module kommunizieren über **Domain Events** (Spring Modulith Event Publication Registry = persistente, transaktionale Events) und über explizite, öffentliche Application-Services (`api`-Package je Modul).

## Zustandsmaschine Freigabevorgang

Die Zustände gelten auf beiden Seiten; jede Seite hält ihren eigenen `ApprovalCase` (Rolle `CUSTOMER` oder `SUPPLIER`), verknüpft über `partnerCaseId`. Übergänge, die vom Partner ausgelöst werden, kommen als Notification.

```mermaid
stateDiagram-v2
  [*] --> ENTWURF : Kunde legt an
  ENTWURF --> ANGEFORDERT : Anforderung senden (Kunde)\n/ empfangen (Lieferant legt direkt in ANGEFORDERT an)
  ANGEFORDERT --> IN_BEARBEITUNG : Lieferant startet Bearbeitung
  IN_BEARBEITUNG --> EINGEREICHT : Revision n einreichen
  EINGEREICHT --> IN_PRUEFUNG : Kunde startet Prüfung
  IN_PRUEFUNG --> FREIGEGEBEN : Freigabe
  IN_PRUEFUNG --> BEDINGT_FREIGEGEBEN : bedingte Freigabe
  IN_PRUEFUNG --> ABGELEHNT : Ablehnung
  ABGELEHNT --> IN_BEARBEITUNG : neue Revision n+1
  BEDINGT_FREIGEGEBEN --> IN_BEARBEITUNG : Nachweis Auflagen (Revision n+1)
  ENTWURF --> ABGEBROCHEN : verwerfen
  ANGEFORDERT --> ABGEBROCHEN : Kunde zieht zurück
  FREIGEGEBEN --> [*]
  ABGEBROCHEN --> [*]
```

Ergänzung zum Prompt: `ABGEBROCHEN` (Rückzug einer Anforderung) – minimal, aber ohne ihn können fehlerhafte Anforderungen nie geschlossen werden. Umsetzung als explizite Übergangstabelle (`Map<Status, Set<Transition>>`) mit Guards (Rolle, Vollständigkeit) – kein Framework (Spring Statemachine wäre für 9 Zustände Overkill). Jeder Übergang erzeugt ein Domain Event (`CaseRequested`, `SubmissionSubmitted`, `DecisionMade` …), auf das `exchange`, `audit`, `usage` reagieren.

## Nachrichtentypen (Austausch)

| messageType | Richtung | Inhalt | Auslöser |
|---|---|---|---|
| `PPAP_REQUEST` | Kunde → Lieferant | `PpapRequest` | UC-03 |
| `PPAP_REQUEST_WITHDRAWN` | Kunde → Lieferant | Vorgangs-ID, Grund | Rückzug |
| `PPAP_STATUS_UPDATE` | beide | Status, Zeitpunkt | IN_BEARBEITUNG / IN_PRÜFUNG (A-15) |
| `PPAP_SUBMISSION` | Lieferant → Kunde | `PpapSubmission` (vollständig) | UC-06, Modus PUSH |
| `PPAP_SUBMISSION_AVAILABLE` | Lieferant → Kunde | Twin-ID, Submodel-ID, Revision, Hash | UC-06, Modus PULL-Notify |
| `PPAP_DECISION` | Kunde → Lieferant | `PpapDecision` + JWS | UC-08 |
