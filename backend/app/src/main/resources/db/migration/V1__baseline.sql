-- Baseline schema of the msg CX PPAP Connector.

-- ---------------------------------------------------------------------------------------------
-- Spring Modulith event publication registry (JPA, schema v2 of spring-modulith-events 2.1).
-- DDL taken from spring-modulith-events-jdbc 2.1.2: schemas/v2/schema-postgresql(-archive).sql
-- ---------------------------------------------------------------------------------------------
CREATE TABLE event_publication
(
    id                     UUID                     NOT NULL,
    listener_id            TEXT                     NOT NULL,
    event_type             TEXT                     NOT NULL,
    serialized_event       TEXT                     NOT NULL,
    publication_date       TIMESTAMP WITH TIME ZONE NOT NULL,
    completion_date        TIMESTAMP WITH TIME ZONE,
    status                 TEXT,
    completion_attempts    INT,
    last_resubmission_date TIMESTAMP WITH TIME ZONE,
    PRIMARY KEY (id)
);
CREATE INDEX event_publication_serialized_event_hash_idx ON event_publication USING hash (serialized_event);
CREATE INDEX event_publication_by_completion_date_idx ON event_publication (completion_date);

CREATE TABLE event_publication_archive
(
    id                     UUID                     NOT NULL,
    listener_id            TEXT                     NOT NULL,
    event_type             TEXT                     NOT NULL,
    serialized_event       TEXT                     NOT NULL,
    publication_date       TIMESTAMP WITH TIME ZONE NOT NULL,
    completion_date        TIMESTAMP WITH TIME ZONE,
    status                 TEXT,
    completion_attempts    INT,
    last_resubmission_date TIMESTAMP WITH TIME ZONE,
    PRIMARY KEY (id)
);
CREATE INDEX event_publication_archive_serialized_event_hash_idx ON event_publication_archive USING hash (serialized_event);
CREATE INDEX event_publication_archive_by_completion_date_idx ON event_publication_archive (completion_date);

-- ---------------------------------------------------------------------------------------------
-- Audit log (module "audit"): append-only, hash-chained per tenant.
-- ---------------------------------------------------------------------------------------------
CREATE TABLE audit_event
(
    id          UUID                     NOT NULL,
    tenant_id   VARCHAR(16)              NOT NULL,
    occurred_at TIMESTAMP WITH TIME ZONE NOT NULL,
    actor       VARCHAR(255)             NOT NULL,
    action      VARCHAR(100)             NOT NULL,
    object_type VARCHAR(100)             NOT NULL,
    object_id   VARCHAR(255)             NOT NULL,
    details     JSONB,
    prev_hash   CHAR(64),
    hash        CHAR(64)                 NOT NULL,
    CONSTRAINT pk_audit_event PRIMARY KEY (id),
    CONSTRAINT uq_audit_event_hash UNIQUE (tenant_id, hash)
);
CREATE INDEX ix_audit_event_tenant_occurred ON audit_event (tenant_id, occurred_at);
CREATE INDEX ix_audit_event_object ON audit_event (tenant_id, object_type, object_id);

COMMENT ON TABLE audit_event IS
    'Append-only audit log. Each event is hash-chained per tenant: hash = SHA-256 over the RFC 8785 canonical JSON of the event including prev_hash.';
COMMENT ON COLUMN audit_event.tenant_id IS 'BPNL of the tenant (ADR-0004).';
COMMENT ON COLUMN audit_event.actor IS 'User id (sub) or technical principal that caused the event.';
COMMENT ON COLUMN audit_event.details IS 'Event-specific payload (JSON).';
COMMENT ON COLUMN audit_event.prev_hash IS 'Hash of the previous event of the same tenant (NULL for the first event).';
COMMENT ON COLUMN audit_event.hash IS 'Hex-encoded SHA-256 of this event.';

-- Audit events are immutable: reject UPDATE and DELETE at database level.
CREATE FUNCTION audit_event_reject_modification() RETURNS trigger
    LANGUAGE plpgsql AS
$$
BEGIN
    RAISE EXCEPTION 'audit_event is append-only (% not allowed)', TG_OP;
END;
$$;

CREATE TRIGGER audit_event_append_only
    BEFORE UPDATE OR DELETE
    ON audit_event
    FOR EACH ROW
EXECUTE FUNCTION audit_event_reject_modification();
