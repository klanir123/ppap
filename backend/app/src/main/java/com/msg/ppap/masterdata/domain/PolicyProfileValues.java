package com.msg.ppap.masterdata.domain;

import static com.msg.ppap.masterdata.domain.MasterdataRules.NAME_MAX;
import static com.msg.ppap.masterdata.domain.MasterdataRules.TEXT_MAX;

import com.msg.ppap.platform.domain.Validation;
import com.msg.ppap.platform.domain.ViolationCodes;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Validated attributes of a {@link PolicyProfile} (docs/api/cascade-transparency-api.md §3, CX-0152 2025/9).
 *
 * @param code business key, e.g. {@code default}
 * @param accessType {@code BPN_GROUP} (shared assets: partner group) or {@code BPN} (explicit partner BPNLs)
 * @param membershipRequired access policy additionally requires an active Catena-X membership
 * @param frameworkAgreement {@code FrameworkAgreement} of the usage policy, e.g. {@code DataExchangeGovernance:1.0}
 * @param usagePurposes {@code UsagePurpose} values (at least one), e.g. {@code cx.quality.base:1}
 * @param additionalConstraints further usage constraints (combined with {@code and})
 */
public record PolicyProfileValues(
        String code,
        String name,
        String description,
        AccessType accessType,
        boolean membershipRequired,
        String frameworkAgreement,
        List<String> usagePurposes,
        List<Constraint> additionalConstraints) {

    /** Profile code: letters, digits, dot, underscore, hyphen. */
    public static final Pattern CODE = Pattern.compile("^[A-Za-z0-9][A-Za-z0-9._\\-]{0,63}$");

    /** UsagePurpose / FrameworkAgreement format {@code <name>:<version>} (CX-0152), e.g. {@code cx.quality.base:1}. */
    public static final Pattern VERSIONED_ID = Pattern.compile("^[A-Za-z][A-Za-z0-9._\\-]*:[0-9]+(\\.[0-9]+)*$");

    /** Left operand of additional constraints. */
    public static final Pattern LEFT_OPERAND = Pattern.compile("^[A-Za-z][A-Za-z0-9._:\\-]{0,127}$");

    /** Operators of additional constraints (ODRL). */
    public static final Set<String> OPERATORS =
            Set.of("eq", "neq", "gt", "gteq", "lt", "lteq", "isAnyOf", "isAllOf", "isNoneOf", "isPartOf");

    /** Left operands that are set by the profile itself and must not be repeated as additional constraint. */
    public static final Set<String> RESERVED_OPERANDS = Set.of("FrameworkAgreement", "UsagePurpose");

    /** Access policy type. */
    public enum AccessType {
        BPN_GROUP,
        BPN
    }

    /**
     * Additional atomic usage constraint.
     *
     * @param rightOperand string or list of strings
     */
    public record Constraint(String leftOperand, String operator, Object rightOperand) {

        public Constraint {
            Objects.requireNonNull(leftOperand, "leftOperand");
            Objects.requireNonNull(operator, "operator");
            Objects.requireNonNull(rightOperand, "rightOperand");
            rightOperand = rightOperand instanceof List<?> list ? List.copyOf(list) : rightOperand;
        }
    }

    public PolicyProfileValues {
        usagePurposes = List.copyOf(usagePurposes);
        additionalConstraints = List.copyOf(additionalConstraints);
    }

    /** Parses and validates raw input. */
    public static PolicyProfileValues parse(
            String code,
            String name,
            String description,
            String accessType,
            Boolean membershipRequired,
            String frameworkAgreement,
            List<String> usagePurposes,
            List<RawConstraint> additionalConstraints) {
        var v = new Validation();
        String parsedCode = v.requiredMatching("code", code, 64, CODE, "letters, digits, '.', '_', '-'");
        String parsedName = v.required("name", name, NAME_MAX);
        String parsedDescription = v.optional("description", description, TEXT_MAX);
        AccessType type = v.optionalEnum("access.type", accessType, AccessType.class, AccessType.BPN_GROUP);
        String agreement = v.requiredMatching(
                "usage.frameworkAgreement", frameworkAgreement, NAME_MAX, VERSIONED_ID, "<name>:<version>");
        Set<String> purposes = new LinkedHashSet<>();
        if (usagePurposes == null || usagePurposes.stream().allMatch(p -> Validation.trimToNull(p) == null)) {
            v.add("usage.usagePurposes", ViolationCodes.REQUIRED, "at least one usage purpose required");
        } else {
            for (int i = 0; i < usagePurposes.size(); i++) {
                String purpose = v.optionalMatching(
                        "usage.usagePurposes[" + i + "]",
                        usagePurposes.get(i),
                        NAME_MAX,
                        VERSIONED_ID,
                        "<name>:<version>, e.g. cx.quality.base:1");
                if (purpose != null) {
                    purposes.add(purpose);
                }
            }
        }
        List<Constraint> constraints = new ArrayList<>();
        List<RawConstraint> raw = additionalConstraints == null ? List.of() : additionalConstraints;
        for (int i = 0; i < raw.size(); i++) {
            Constraint constraint = constraint(v, "usage.additionalConstraints[" + i + "]", raw.get(i));
            if (constraint != null) {
                constraints.add(constraint);
            }
        }
        var values = new PolicyProfileValues(
                parsedCode,
                parsedName,
                parsedDescription,
                type,
                membershipRequired == null || membershipRequired,
                agreement,
                List.copyOf(purposes),
                constraints);
        v.throwIfInvalid();
        return values;
    }

    /** Raw additional constraint (right operand: string or list of strings). */
    public record RawConstraint(String leftOperand, String operator, Object rightOperand) {}

    private static Constraint constraint(Validation v, String field, RawConstraint raw) {
        if (raw == null) {
            v.add(field, ViolationCodes.REQUIRED, "must not be null");
            return null;
        }
        String left = v.requiredMatching(field + ".leftOperand", raw.leftOperand(), 128, LEFT_OPERAND, "identifier");
        if (left != null && RESERVED_OPERANDS.contains(left)) {
            v.add(field + ".leftOperand", ViolationCodes.INVALID_VALUE, left + " is set by the profile itself");
            left = null;
        }
        String operator = Validation.trimToNull(raw.operator());
        if (operator == null || !OPERATORS.contains(operator)) {
            v.add(field + ".operator", ViolationCodes.INVALID_VALUE, "one of " + new java.util.TreeSet<>(OPERATORS));
            operator = null;
        }
        Object right = rightOperand(v, field + ".rightOperand", raw.rightOperand());
        return left == null || operator == null || right == null ? null : new Constraint(left, operator, right);
    }

    private static Object rightOperand(Validation v, String field, Object raw) {
        if (raw instanceof String text && Validation.trimToNull(text) != null) {
            return text.strip();
        }
        if (raw instanceof List<?> list && !list.isEmpty()) {
            List<String> values = new ArrayList<>();
            for (Object item : list) {
                if (!(item instanceof String text) || Validation.trimToNull(text) == null) {
                    v.add(field, ViolationCodes.INVALID_VALUE, "list of non-blank strings expected");
                    return null;
                }
                values.add(text.strip());
            }
            return values;
        }
        v.add(field, ViolationCodes.REQUIRED, "string or non-empty list of strings expected");
        return null;
    }
}
