-- Documents (module "documents"): metadata of uploaded files. The content is stored content-addressed in the object
-- storage under storage_key = <tenant_id>/<sha256> (identical content is stored once); every upload gets its own row.
CREATE TABLE doc_document
(
    id          UUID                     NOT NULL,
    tenant_id   VARCHAR(16)              NOT NULL,
    file_name   VARCHAR(255)             NOT NULL,
    media_type  VARCHAR(255)             NOT NULL,
    size_bytes  BIGINT                   NOT NULL,
    sha256      VARCHAR(64)              NOT NULL,
    storage_key VARCHAR(255)             NOT NULL,
    uploaded_at TIMESTAMP WITH TIME ZONE NOT NULL,
    uploaded_by VARCHAR(255)             NOT NULL,
    CONSTRAINT pk_doc_document PRIMARY KEY (id),
    CONSTRAINT ck_doc_document_size CHECK (size_bytes >= 0),
    CONSTRAINT ck_doc_document_sha256 CHECK (sha256 ~ '^[0-9a-f]{64}$')
);
CREATE INDEX ix_doc_document_sha256 ON doc_document (tenant_id, sha256);

-- Document metadata is immutable.
CREATE FUNCTION doc_document_reject_update() RETURNS trigger
    LANGUAGE plpgsql AS
$$
BEGIN
    RAISE EXCEPTION 'doc_document is immutable (% not allowed)', TG_OP;
END;
$$;

CREATE TRIGGER doc_document_immutable
    BEFORE UPDATE
    ON doc_document
    FOR EACH ROW
EXECUTE FUNCTION doc_document_reject_update();
