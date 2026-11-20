-- Phase 6: cascade and transparency (docs/api/cascade-transparency-api.md, UC-09 … UC-12).

-- ---------------------------------------------------------------------------------------------
-- 1. Cascade (UC-09): a sub-supplier request (customer case) points to the supplier case it was created from.
--    Sub-supplier references are part of a revision (draft: editable; submitted: immutable, part of the signed
--    payload; customer side: as received).
-- ---------------------------------------------------------------------------------------------
ALTER TABLE ap_case
    ADD COLUMN parent_case_id UUID;
ALTER TABLE ap_case
    ADD CONSTRAINT fk_ap_case_parent FOREIGN KEY (parent_case_id) REFERENCES ap_case (id);
CREATE INDEX ix_ap_case_parent ON ap_case (tenant_id, parent_case_id) WHERE parent_case_id IS NOT NULL;

ALTER TABLE ap_submission
    ADD COLUMN sub_supplier_references JSONB NOT NULL DEFAULT '[]'::jsonb;
ALTER TABLE ap_submission
    ADD CONSTRAINT ck_ap_submission_references CHECK (jsonb_typeof(sub_supplier_references) = 'array');

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
            NEW.signature_jws, NEW.exchange_mode, NEW.sub_supplier_references)
            IS DISTINCT FROM
           (OLD.tenant_id, OLD.case_id, OLD.revision, OLD.status, OLD.based_on_revision, OLD.elements,
            OLD.submitted_at, OLD.submitted_by, OLD.payload, OLD.payload_hash, OLD.signature_alg, OLD.signature_kid,
            OLD.signature_jws, OLD.exchange_mode, OLD.sub_supplier_references) THEN
            RAISE EXCEPTION 'ap_submission: submitted revision % is immutable (UPDATE)', OLD.revision;
        END IF;
    END IF;
    RETURN CASE WHEN TG_OP = 'DELETE' THEN OLD ELSE NEW END;
END;
$$;

-- ---------------------------------------------------------------------------------------------
-- 3. Policy profiles (UC-10, E5-04): data, not code. The relation references a profile by code.
--    The profile "default" is created here for the instance tenant (Flyway placeholders from ppap.tenant.bpnl and
--    ppap.edc.policy.usage-purpose) and for every tenant that already has relations; afterwards it is maintained
--    via /api/v1/policy-profiles like any other profile.
-- ---------------------------------------------------------------------------------------------
CREATE TABLE md_policy_profile
(
    id                     UUID                     NOT NULL,
    tenant_id              VARCHAR(16)              NOT NULL,
    version                BIGINT                   NOT NULL,
    created_at             TIMESTAMP WITH TIME ZONE NOT NULL,
    created_by             VARCHAR(255)             NOT NULL,
    updated_at             TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_by             VARCHAR(255)             NOT NULL,
    code                   VARCHAR(64)              NOT NULL,
    name                   VARCHAR(255)             NOT NULL,
    description            VARCHAR(4000),
    access_type            VARCHAR(16)              NOT NULL,
    membership_required    BOOLEAN                  NOT NULL,
    framework_agreement    VARCHAR(255)             NOT NULL,
    usage_purposes         JSONB                    NOT NULL,
    additional_constraints JSONB                    NOT NULL DEFAULT '[]'::jsonb,
    active                 BOOLEAN                  NOT NULL,
    CONSTRAINT pk_md_policy_profile PRIMARY KEY (id),
    CONSTRAINT uq_md_policy_profile_code UNIQUE (tenant_id, code),
    CONSTRAINT ck_md_policy_profile_access CHECK (access_type IN ('BPN_GROUP', 'BPN')),
    CONSTRAINT ck_md_policy_profile_purposes CHECK (jsonb_typeof(usage_purposes) = 'array'),
    CONSTRAINT ck_md_policy_profile_constraints CHECK (jsonb_typeof(additional_constraints) = 'array')
);

INSERT INTO md_policy_profile (id, tenant_id, version, created_at, created_by, updated_at, updated_by, code, name,
                               description, access_type, membership_required, framework_agreement, usage_purposes,
                               additional_constraints, active)
SELECT gen_random_uuid(), t.tenant_id, 0, now(), 'system:migration', now(), 'system:migration', 'default',
       'Standard (Catena-X)',
       'Catena-X Standard: Zugriff für Partner mit Beziehung (BPN-Gruppe ppap-partners), Nutzung nach Framework '
           || 'Agreement Data Exchange Governance und Usage Purpose.',
       'BPN_GROUP', TRUE, 'DataExchangeGovernance:1.0', jsonb_build_array('${usage_purpose}'), '[]'::jsonb, TRUE
FROM (SELECT '${tenant_bpnl}' AS tenant_id
      UNION
      SELECT DISTINCT tenant_id FROM md_relation) t;

ALTER TABLE md_relation
    ADD CONSTRAINT fk_md_relation_policy_profile FOREIGN KEY (tenant_id, policy_profile)
        REFERENCES md_policy_profile (tenant_id, code);

-- ---------------------------------------------------------------------------------------------
-- 5. Usage counters (UC-12): event-based, idempotent per (exchange id, revision, metric) via usage_event; the
--    counters are reproducible from the revisions (POST /api/v1/usage/recompute).
-- ---------------------------------------------------------------------------------------------
CREATE TABLE usage_event
(
    tenant_id   VARCHAR(16)              NOT NULL,
    exchange_id UUID                     NOT NULL,
    revision    INTEGER                  NOT NULL,
    metric      VARCHAR(32)              NOT NULL,
    month       CHAR(7)                  NOT NULL,
    case_id     UUID                     NOT NULL,
    occurred_at TIMESTAMP WITH TIME ZONE NOT NULL,
    recorded_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_usage_event PRIMARY KEY (tenant_id, exchange_id, revision, metric),
    CONSTRAINT ck_usage_event_month CHECK (month ~ '^[0-9]{4}-(0[1-9]|1[0-2])$')
);

CREATE TABLE usage_counter
(
    tenant_id VARCHAR(16) NOT NULL,
    month     CHAR(7)     NOT NULL,
    metric    VARCHAR(32) NOT NULL,
    count     BIGINT      NOT NULL,
    CONSTRAINT pk_usage_counter PRIMARY KEY (tenant_id, month, metric),
    CONSTRAINT ck_usage_counter_month CHECK (month ~ '^[0-9]{4}-(0[1-9]|1[0-2])$'),
    CONSTRAINT ck_usage_counter_count CHECK (count >= 0)
);
