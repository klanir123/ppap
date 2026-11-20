package com.msg.ppap.masterdata.application;

import com.msg.ppap.masterdata.domain.PolicyProfileValues;
import java.util.List;

/**
 * Raw input for creating or updating a policy profile.
 *
 * @param active desired active flag; {@code null} keeps the current state (new profiles are active)
 */
public record PolicyProfileCommand(
        String code,
        String name,
        String description,
        String accessType,
        Boolean membershipRequired,
        String frameworkAgreement,
        List<String> usagePurposes,
        List<PolicyProfileValues.RawConstraint> additionalConstraints,
        Boolean active) {

    PolicyProfileValues toValues() {
        return PolicyProfileValues.parse(
                code,
                name,
                description,
                accessType,
                membershipRequired,
                frameworkAgreement,
                usagePurposes,
                additionalConstraints);
    }
}
