package com.msg.ppap.exchange.adapter.in.rest;

import com.msg.ppap.exchange.adapter.in.rest.ConnectorDtos.RecentMessageDto;
import com.msg.ppap.exchange.adapter.in.rest.ConnectorDtos.TopologyDto;
import com.msg.ppap.exchange.application.DataspaceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Dataspace view (demo mode, E5-06): topology of the own participant and latest messages (all roles). */
@RestController
@RequestMapping("/api/v1/dataspace")
@Tag(name = "dataspace")
public class DataspaceController {

    private final DataspaceService dataspace;

    public DataspaceController(DataspaceService dataspace) {
        this.dataspace = dataspace;
    }

    @GetMapping("/topology")
    @Operation(
            operationId = "getDataspaceTopology",
            summary = "Own participant with connector and its partners with relations (perspective of the tenant)")
    public TopologyDto topology() {
        return ConnectorDtos.topology(dataspace.topology());
    }

    @GetMapping("/recent")
    @Operation(
            operationId = "listRecentExchange",
            summary = "Latest exchanged messages with steps (newest first, without payload) for the animation")
    public List<RecentMessageDto> recent(@RequestParam(defaultValue = "50") int limit) {
        return dataspace.recent(limit).stream().map(ConnectorDtos::recent).toList();
    }
}
