-- Audit log (module "audit"): insertion sequence for a deterministic order within the hash chain and for paging.
-- The hash chain is kept per (tenant_id, object_type, object_id).
ALTER TABLE audit_event ADD COLUMN seq BIGINT GENERATED ALWAYS AS IDENTITY;
CREATE UNIQUE INDEX uq_audit_event_seq ON audit_event (seq);
CREATE INDEX ix_audit_event_chain ON audit_event (tenant_id, object_type, object_id, seq);
