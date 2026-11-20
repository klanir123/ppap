package com.msg.ppap.exchange.application;

import com.msg.ppap.exchange.api.CaseExchangeRecords;
import com.msg.ppap.exchange.domain.ExchangeRecord;
import com.msg.ppap.exchange.domain.ExchangeStep;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;

/** {@link CaseExchangeRecords} for the audit export. */
@Service
public class CaseExchangeRecordsService implements CaseExchangeRecords {

    private final ExchangeContext context;

    public CaseExchangeRecordsService(ExchangeContext context) {
        this.context = context;
    }

    @Override
    public List<Map<String, Object>> recordsOfCase(UUID caseId) {
        List<ExchangeRecord> records = new ArrayList<>(context.store().findByCase(caseId));
        records.sort(Comparator.comparing(ExchangeRecord::createdAt, Comparator.nullsLast(Comparator.naturalOrder())));
        return records.stream().map(CaseExchangeRecordsService::record).toList();
    }

    static Map<String, Object> record(ExchangeRecord r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", r.id().toString());
        put(m, "messageId", r.messageId());
        m.put("direction", r.direction().name());
        put(m, "messageType", r.messageType());
        put(m, "partnerBpnl", r.partnerBpnl());
        m.put("status", r.status().name());
        m.put("attempts", r.attempts());
        put(m, "payloadHash", r.payloadHash());
        put(m, "correlationId", r.correlationId());
        put(m, "createdAt", r.createdAt());
        put(m, "completedAt", r.completedAt());
        put(m, "errorCode", r.errorCode());
        put(m, "lastError", r.lastError());
        m.put("steps", r.steps().stream().map(CaseExchangeRecordsService::step).toList());
        return m;
    }

    private static Map<String, Object> step(ExchangeStep s) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("attempt", s.attempt());
        m.put("step", s.step().name());
        m.put("status", s.status().name());
        m.put("at", s.at().toString());
        m.put("durationMs", s.durationMs());
        m.put("details", s.details());
        return m;
    }

    private static void put(Map<String, Object> map, String key, Object value) {
        if (value != null) {
            map.put(key, value.toString());
        }
    }
}
