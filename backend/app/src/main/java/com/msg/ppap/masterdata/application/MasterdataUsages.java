package com.msg.ppap.masterdata.application;

import com.msg.ppap.masterdata.api.MasterdataUsage;
import java.util.List;
import java.util.function.Predicate;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/** Aggregates all {@link MasterdataUsage} implementations of downstream modules (none in a master-data-only setup). */
@Component
public class MasterdataUsages {

    private final ObjectProvider<MasterdataUsage> usages;

    public MasterdataUsages(ObjectProvider<MasterdataUsage> usages) {
        this.usages = usages;
    }

    /** Whether any downstream module reports a usage. */
    public boolean any(Predicate<MasterdataUsage> check) {
        List<MasterdataUsage> all = usages.orderedStream().toList();
        return all.stream().anyMatch(check);
    }
}
