-- Received requests whose part cannot be mapped are kept in a clarification list (UC-04 A1,
-- docs/api/exchange-api.md "Klärungsliste"): part_number stays empty until the supplier assigns the part.

ALTER TABLE ap_case ALTER COLUMN part_number DROP NOT NULL;
ALTER TABLE ap_case ADD COLUMN clarification_reason VARCHAR(32);
ALTER TABLE ap_case ADD COLUMN clarification_partner_part_number VARCHAR(64);
ALTER TABLE ap_case ADD CONSTRAINT ck_ap_case_clarification CHECK (clarification_reason IS NULL OR clarification_reason IN ('PART_NOT_MAPPED'));
ALTER TABLE ap_case ADD CONSTRAINT ck_ap_case_part CHECK (part_number IS NOT NULL OR clarification_reason IS NOT NULL);
CREATE INDEX ix_ap_case_clarification ON ap_case (tenant_id) WHERE clarification_reason IS NOT NULL;
