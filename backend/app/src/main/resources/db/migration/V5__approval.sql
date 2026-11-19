-- Approval cases (module "approval", UC-03/05/07).
--
-- One case per side (own_role CUSTOMER | SUPPLIER), linked to the partner's case by exchange_id. Partner, part,
-- reason, level and requested elements are snapshots (UC-02: catalogue changes do not affect running cases).
-- Requested elements, attachments and review findings are JSONB documents: they are always read and written with
-- the case and keyed by stable codes. Submitted revisions and decisions are immutable (domain and triggers below).

CREATE TABLE ap_case
(
    id                  UUID                     NOT NULL,
    tenant_id           VARCHAR(16)              NOT NULL,
    version             BIGINT                   NOT NULL,
    created_at          TIMESTAMP WITH TIME ZONE NOT NULL,
    created_by          VARCHAR(255)             NOT NULL,
    updated_at          TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_by          VARCHAR(255)             NOT NULL,
    case_number         VARCHAR(64)              NOT NULL,
    exchange_id         UUID                     NOT NULL,
    own_role            VARCHAR(16)              NOT NULL,
    source              VARCHAR(16)              NOT NULL,
    status              VARCHAR(32)              NOT NULL,
    partner_bpnl        VARCHAR(16)              NOT NULL,
    partner_name        VARCHAR(255)             NOT NULL,
    relation_id         UUID,
    part_id             UUID,
    part_number         VARCHAR(64)              NOT NULL,
    part_name_de        VARCHAR(255),
    part_name_en        VARCHAR(255),
    partner_part_number VARCHAR(64),
    reason_code         VARCHAR(32)              NOT NULL,
    reason_name_de      VARCHAR(255)             NOT NULL,
    reason_name_en      VARCHAR(255)             NOT NULL,
    level_code          VARCHAR(32)              NOT NULL,
    level_name_de       VARCHAR(255)             NOT NULL,
    level_name_en       VARCHAR(255)             NOT NULL,
    due_date            DATE                     NOT NULL,
    remarks             VARCHAR(4000),
    external_reference  VARCHAR(255),
    requested_at        TIMESTAMP WITH TIME ZONE,
    requested_by        VARCHAR(255),
    request_elements    JSONB                    NOT NULL DEFAULT '[]'::jsonb,
    request_attachments JSONB                    NOT NULL DEFAULT '[]'::jsonb,
    review_findings     JSONB                    NOT NULL DEFAULT '[]'::jsonb,
    current_revision    INTEGER,
    submitted_at        TIMESTAMP WITH TIME ZONE,
    decided_at          TIMESTAMP WITH TIME ZONE,
    last_decision       VARCHAR(32),
    CONSTRAINT pk_ap_case PRIMARY KEY (id),
    CONSTRAINT uq_ap_case_number UNIQUE (tenant_id, case_number),
    CONSTRAINT uq_ap_case_exchange UNIQUE (tenant_id, exchange_id, own_role),
    CONSTRAINT ck_ap_case_role CHECK (own_role IN ('CUSTOMER', 'SUPPLIER')),
    CONSTRAINT ck_ap_case_source CHECK (source IN ('LOCAL', 'MANUAL', 'EXCHANGE', 'LEGACY_IMPORT')),
    CONSTRAINT ck_ap_case_status CHECK (status IN ('DRAFT', 'REQUESTED', 'IN_PROGRESS', 'SUBMITTED', 'IN_REVIEW',
                                                   'APPROVED', 'CONDITIONALLY_APPROVED', 'REJECTED', 'CANCELLED')),
    CONSTRAINT ck_ap_case_elements CHECK (jsonb_typeof(request_elements) = 'array')
);
CREATE INDEX ix_ap_case_status ON ap_case (tenant_id, status, due_date);
CREATE INDEX ix_ap_case_partner ON ap_case (tenant_id, partner_bpnl);
CREATE INDEX ix_ap_case_part ON ap_case (tenant_id, part_id);
CREATE INDEX ix_ap_case_elements ON ap_case USING gin (request_elements jsonb_path_ops);

-- Revisions. SUBMITTED rows carry the canonical exchange payload (TEXT: the exact RFC 8785 bytes that were hashed
-- and signed), its hash and the detached JWS.
CREATE TABLE ap_submission
(
    id                UUID                     NOT NULL,
    tenant_id         VARCHAR(16)              NOT NULL,
    case_id           UUID                     NOT NULL,
    revision          INTEGER                  NOT NULL,
    status            VARCHAR(16)              NOT NULL,
    based_on_revision INTEGER,
    elements          JSONB                    NOT NULL DEFAULT '[]'::jsonb,
    submitted_at      TIMESTAMP WITH TIME ZONE,
    submitted_by      VARCHAR(255),
    payload           TEXT,
    payload_hash      VARCHAR(71),
    signature_alg     VARCHAR(16),
    signature_kid     VARCHAR(255),
    signature_jws     TEXT,
    signature_status  VARCHAR(16),
    updated_at        TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_ap_submission PRIMARY KEY (id),
    CONSTRAINT uq_ap_submission_revision UNIQUE (case_id, revision),
    CONSTRAINT fk_ap_submission_case FOREIGN KEY (case_id) REFERENCES ap_case (id),
    CONSTRAINT ck_ap_submission_status CHECK (status IN ('DRAFT', 'SUBMITTED')),
    CONSTRAINT ck_ap_submission_sealed CHECK (status = 'DRAFT' OR (payload IS NOT NULL AND payload_hash IS NOT NULL
        AND submitted_at IS NOT NULL AND signature_status IS NOT NULL))
);

-- Submitted revisions are immutable: no DELETE, and UPDATE may only change the signature verification status.
CREATE FUNCTION ap_submission_protect_submitted() RETURNS trigger
    LANGUAGE plpgsql AS
$$
BEGIN
    IF OLD.status = 'SUBMITTED' THEN
        IF TG_OP = 'DELETE' THEN
            RAISE EXCEPTION 'ap_submission: submitted revision % is immutable (DELETE)', OLD.revision;
        END IF;
        IF (NEW.tenant_id, NEW.case_id, NEW.revision, NEW.status, NEW.based_on_revision, NEW.elements,
            NEW.submitted_at, NEW.submitted_by, NEW.payload, NEW.payload_hash, NEW.signature_alg, NEW.signature_kid,
            NEW.signature_jws)
            IS DISTINCT FROM
           (OLD.tenant_id, OLD.case_id, OLD.revision, OLD.status, OLD.based_on_revision, OLD.elements,
            OLD.submitted_at, OLD.submitted_by, OLD.payload, OLD.payload_hash, OLD.signature_alg, OLD.signature_kid,
            OLD.signature_jws) THEN
            RAISE EXCEPTION 'ap_submission: submitted revision % is immutable (UPDATE)', OLD.revision;
        END IF;
    END IF;
    RETURN CASE WHEN TG_OP = 'DELETE' THEN OLD ELSE NEW END;
END;
$$;

CREATE TRIGGER ap_submission_immutable
    BEFORE UPDATE OR DELETE
    ON ap_submission
    FOR EACH ROW
EXECUTE FUNCTION ap_submission_protect_submitted();

-- Decisions: insert only.
CREATE TABLE ap_decision
(
    id                 UUID                     NOT NULL,
    tenant_id          VARCHAR(16)              NOT NULL,
    case_id            UUID                     NOT NULL,
    revision           INTEGER                  NOT NULL,
    result             VARCHAR(32)              NOT NULL,
    conditions         JSONB                    NOT NULL DEFAULT '[]'::jsonb,
    condition_due_date DATE,
    comment            VARCHAR(4000),
    element_findings   JSONB                    NOT NULL DEFAULT '[]'::jsonb,
    decided_at         TIMESTAMP WITH TIME ZONE NOT NULL,
    decided_by         VARCHAR(255)             NOT NULL,
    payload            TEXT                     NOT NULL,
    payload_hash       VARCHAR(71)              NOT NULL,
    signature_alg      VARCHAR(16),
    signature_kid      VARCHAR(255),
    signature_jws      TEXT,
    signature_status   VARCHAR(16)              NOT NULL,
    CONSTRAINT pk_ap_decision PRIMARY KEY (id),
    CONSTRAINT uq_ap_decision_revision UNIQUE (case_id, revision),
    CONSTRAINT fk_ap_decision_revision FOREIGN KEY (case_id, revision) REFERENCES ap_submission (case_id, revision),
    CONSTRAINT ck_ap_decision_result CHECK (result IN ('APPROVED', 'CONDITIONALLY_APPROVED', 'REJECTED'))
);

CREATE FUNCTION ap_reject_modification() RETURNS trigger
    LANGUAGE plpgsql AS
$$
BEGIN
    RAISE EXCEPTION '% is append-only (% not allowed)', TG_TABLE_NAME, TG_OP;
END;
$$;

CREATE TRIGGER ap_decision_immutable
    BEFORE UPDATE OR DELETE
    ON ap_decision
    FOR EACH ROW
EXECUTE FUNCTION ap_reject_modification();

-- Timeline of status changes: append only.
CREATE TABLE ap_timeline_entry
(
    id          UUID                     NOT NULL,
    tenant_id   VARCHAR(16)              NOT NULL,
    case_id     UUID                     NOT NULL,
    seq         BIGINT GENERATED ALWAYS AS IDENTITY,
    at          TIMESTAMP WITH TIME ZONE NOT NULL,
    actor       VARCHAR(255)             NOT NULL,
    from_status VARCHAR(32),
    to_status   VARCHAR(32)              NOT NULL,
    action      VARCHAR(32)              NOT NULL,
    comment     VARCHAR(4000),
    CONSTRAINT pk_ap_timeline_entry PRIMARY KEY (id),
    CONSTRAINT fk_ap_timeline_case FOREIGN KEY (case_id) REFERENCES ap_case (id)
);
CREATE INDEX ix_ap_timeline_case ON ap_timeline_entry (case_id, seq);

CREATE TRIGGER ap_timeline_entry_append_only
    BEFORE UPDATE OR DELETE
    ON ap_timeline_entry
    FOR EACH ROW
EXECUTE FUNCTION ap_reject_modification();

-- Case number counter per tenant and year (PPF-<year>-<5 digits>); INSERT … ON CONFLICT DO UPDATE … RETURNING
-- locks the row until commit, so numbers are unique and gap-free per transaction.
CREATE TABLE ap_case_number_counter
(
    tenant_id  VARCHAR(16) NOT NULL,
    year       INTEGER     NOT NULL,
    last_value BIGINT      NOT NULL,
    CONSTRAINT pk_ap_case_number_counter PRIMARY KEY (tenant_id, year)
);

-- Excel form templates (UC-05 A1): data, not code (seed form-templates.json).
CREATE TABLE ap_form_template
(
    id         UUID                     NOT NULL,
    tenant_id  VARCHAR(16)              NOT NULL,
    version    BIGINT                   NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_by VARCHAR(255)             NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_by VARCHAR(255)             NOT NULL,
    code       VARCHAR(64)              NOT NULL,
    name_de    VARCHAR(255)             NOT NULL,
    name_en    VARCHAR(255)             NOT NULL,
    definition JSONB                    NOT NULL,
    active     BOOLEAN                  NOT NULL,
    CONSTRAINT pk_ap_form_template PRIMARY KEY (id),
    CONSTRAINT uq_ap_form_template_code UNIQUE (tenant_id, code)
);
