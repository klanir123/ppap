-- Phase 5: pull via Digital Twin Registry and document transfer (docs/api/pull-api.md).

-- Effective exchange mode per revision (supplier: how it was provided; customer: how it was received). Set together
-- with the seal (DRAFT -> SUBMITTED) or on receipt; immutable afterwards like the rest of a submitted revision.
ALTER TABLE ap_submission
    ADD COLUMN exchange_mode VARCHAR(32);
ALTER TABLE ap_submission
    ADD CONSTRAINT ck_ap_submission_exchange_mode
        CHECK (exchange_mode IS NULL OR exchange_mode IN ('PUSH', 'PULL', 'PUSH_NOTIFY_PULL_PAYLOAD'));

CREATE OR REPLACE FUNCTION ap_submission_protect_submitted() RETURNS trigger
    LANGUAGE plpgsql AS
$$
BEGIN
    IF OLD.status = 'SUBMITTED' THEN
        IF TG_OP = 'DELETE' THEN
            RAISE EXCEPTION 'ap_submission: submitted revision % is immutable (DELETE)', OLD.revision;
        END IF;
        IF (NEW.tenant_id, NEW.case_id, NEW.revision, NEW.status, NEW.based_on_revision, NEW.elements,
            NEW.submitted_at, NEW.submitted_by, NEW.payload, NEW.payload_hash, NEW.signature_alg, NEW.signature_kid,
            NEW.signature_jws, NEW.exchange_mode)
            IS DISTINCT FROM
           (OLD.tenant_id, OLD.case_id, OLD.revision, OLD.status, OLD.based_on_revision, OLD.elements,
            OLD.submitted_at, OLD.submitted_by, OLD.payload, OLD.payload_hash, OLD.signature_alg, OLD.signature_kid,
            OLD.signature_jws, OLD.exchange_mode) THEN
            RAISE EXCEPTION 'ap_submission: submitted revision % is immutable (UPDATE)', OLD.revision;
        END IF;
    END IF;
    RETURN CASE WHEN TG_OP = 'DELETE' THEN OLD ELSE NEW END;
END;
$$;

-- New step types (TWIN_REGISTRATION, SIGNATURE_CHECK, ...) are longer than 16 characters.
ALTER TABLE ex_message_step
    ALTER COLUMN step TYPE VARCHAR(32);

-- Pull jobs (PULL_SUBMISSION, PULL_DOCUMENT) are inbound records that are worked off like the outbox
-- (PENDING -> IN_PROGRESS -> PROCESSED | PENDING (retry) | FAILED | REJECTED).
CREATE INDEX ix_ex_message_pull ON ex_message (tenant_id, status, next_attempt_at)
    WHERE direction = 'INBOUND' AND message_type IN ('PULL_SUBMISSION', 'PULL_DOCUMENT');

-- Transfer state of partner documents referenced by a case (evidence of received revisions on the customer side,
-- attachments of received requests on the supplier side). The content is stored content-addressed by the documents
-- module once fetched and verified (document_id).
CREATE TABLE ex_document_transfer
(
    id            UUID                     NOT NULL,
    tenant_id     VARCHAR(16)              NOT NULL,
    case_id       UUID                     NOT NULL,
    exchange_id   UUID                     NOT NULL,
    partner_bpnl  VARCHAR(16)              NOT NULL,
    sha256        VARCHAR(64)              NOT NULL,
    file_name     VARCHAR(255)             NOT NULL,
    media_type    VARCHAR(255)             NOT NULL,
    size_bytes    BIGINT                   NOT NULL,
    status        VARCHAR(16)              NOT NULL,
    document_id   UUID,
    hash_verified BOOLEAN                  NOT NULL DEFAULT FALSE,
    message_ref   UUID,
    last_error    VARCHAR(2000),
    created_at    TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at    TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_ex_document_transfer PRIMARY KEY (id),
    CONSTRAINT uq_ex_document_transfer UNIQUE (tenant_id, case_id, sha256),
    CONSTRAINT ck_ex_document_transfer_status CHECK (status IN ('PENDING', 'FETCHED', 'FAILED', 'NOT_AVAILABLE')),
    CONSTRAINT ck_ex_document_transfer_sha256 CHECK (sha256 ~ '^[0-9a-f]{64}$')
);
CREATE INDEX ix_ex_document_transfer_case ON ex_document_transfer (tenant_id, case_id);

-- Error code of a failed transfer (e.g. DOCUMENT_HASH_MISMATCH, DOCUMENT_NOT_AVAILABLE, FILE_TOO_LARGE).
ALTER TABLE ex_document_transfer
    ADD COLUMN error_code VARCHAR(64);

-- Digital twins registered in the own DTR (module "twin"): the shell descriptor exactly as read back from the DTR
-- after the last registration (diagnosis, GET /api/v1/twins/{exchangeId}).
CREATE TABLE tw_twin
(
    tenant_id     VARCHAR(16)              NOT NULL,
    exchange_id   UUID                     NOT NULL,
    case_id       UUID                     NOT NULL,
    shell_id      VARCHAR(255)             NOT NULL,
    shell         TEXT                     NOT NULL,
    registered_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at    TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_tw_twin PRIMARY KEY (tenant_id, exchange_id)
);
