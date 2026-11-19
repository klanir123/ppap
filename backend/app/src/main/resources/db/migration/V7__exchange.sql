-- Dataspace exchange (module "exchange", docs/api/exchange-api.md).
--
-- ex_message is outbox (OUTBOUND), inbox (INBOUND) and visible exchange record at the same time: an outbound row is
-- written in the transaction of the case change (transactional outbox) and delivered by a worker
-- (SELECT ... FOR UPDATE SKIP LOCKED); an inbound row stores the received envelope unchanged before it is processed.
-- Inbound messages are unique per (sender, message id) - duplicates are answered with 200 without effect.

CREATE TABLE ex_message
(
    id              UUID                     NOT NULL,
    tenant_id       VARCHAR(16)              NOT NULL,
    seq             BIGINT GENERATED ALWAYS AS IDENTITY,
    message_id      UUID                     NOT NULL,
    direction       VARCHAR(8)               NOT NULL,
    message_type    VARCHAR(32)              NOT NULL,
    partner_bpnl    VARCHAR(16)              NOT NULL,
    partner_name    VARCHAR(255),
    case_id         UUID,
    case_number     VARCHAR(64),
    exchange_id     UUID,
    status          VARCHAR(16)              NOT NULL,
    attempts        INTEGER                  NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMP WITH TIME ZONE,
    locked_until    TIMESTAMP WITH TIME ZONE,
    payload_hash    VARCHAR(71),
    correlation_id  VARCHAR(128),
    envelope        TEXT                     NOT NULL,
    edc_bpn         VARCHAR(64),
    error_code      VARCHAR(64),
    last_error      VARCHAR(4000),
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    completed_at    TIMESTAMP WITH TIME ZONE,
    CONSTRAINT pk_ex_message PRIMARY KEY (id),
    CONSTRAINT ck_ex_message_direction CHECK (direction IN ('OUTBOUND', 'INBOUND')),
    CONSTRAINT ck_ex_message_status CHECK (status IN ('PENDING', 'IN_PROGRESS', 'DELIVERED', 'FAILED', 'RECEIVED',
                                                      'PROCESSED', 'REJECTED'))
);
CREATE UNIQUE INDEX uq_ex_message_inbound ON ex_message (tenant_id, partner_bpnl, message_id) WHERE direction = 'INBOUND';
CREATE UNIQUE INDEX uq_ex_message_outbound ON ex_message (tenant_id, message_id) WHERE direction = 'OUTBOUND';
CREATE INDEX ix_ex_message_due ON ex_message (tenant_id, direction, status, next_attempt_at);
CREATE INDEX ix_ex_message_exchange ON ex_message (tenant_id, exchange_id, seq);
CREATE INDEX ix_ex_message_case ON ex_message (tenant_id, case_id, seq);
CREATE INDEX ix_ex_message_list ON ex_message (tenant_id, seq DESC);

-- Steps of a message (catalog, negotiation, transfer, EDR, delivery / receipt, validation, processing, key fetch):
-- append only, written as they happen.
CREATE TABLE ex_message_step
(
    id          UUID                     NOT NULL,
    tenant_id   VARCHAR(16)              NOT NULL,
    message_ref UUID                     NOT NULL,
    seq         BIGINT GENERATED ALWAYS AS IDENTITY,
    attempt     INTEGER                  NOT NULL,
    step        VARCHAR(16)              NOT NULL,
    status      VARCHAR(8)               NOT NULL,
    at          TIMESTAMP WITH TIME ZONE NOT NULL,
    duration_ms BIGINT                   NOT NULL,
    details     JSONB                    NOT NULL DEFAULT '{}'::jsonb,
    CONSTRAINT pk_ex_message_step PRIMARY KEY (id),
    CONSTRAINT fk_ex_message_step_message FOREIGN KEY (message_ref) REFERENCES ex_message (id),
    CONSTRAINT ck_ex_message_step_status CHECK (status IN ('OK', 'FAILED', 'SKIPPED'))
);
CREATE INDEX ix_ex_message_step_message ON ex_message_step (message_ref, seq);

CREATE TRIGGER ex_message_step_append_only
    BEFORE UPDATE OR DELETE
    ON ex_message_step
    FOR EACH ROW
EXECUTE FUNCTION ap_reject_modification();

-- Public signature keys of partners, fetched via EDC (asset ppap-public-keys).
CREATE TABLE ex_partner_key
(
    tenant_id  VARCHAR(16)              NOT NULL,
    bpnl       VARCHAR(16)              NOT NULL,
    kid        VARCHAR(255)             NOT NULL,
    jwk        JSONB                    NOT NULL,
    fetched_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_ex_partner_key PRIMARY KEY (tenant_id, bpnl, kid)
);
