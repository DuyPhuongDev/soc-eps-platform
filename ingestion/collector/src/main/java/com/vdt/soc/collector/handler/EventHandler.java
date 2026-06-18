package com.vdt.soc.collector.handler;

import com.vdt.soc.collector.dto.EventRequest;
import com.vdt.soc.collector.dto.EventResponse;
import com.vdt.soc.collector.engine.EnforcementEngine;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/v1")
@RequiredArgsConstructor
@Tag(name = "Events", description = "Event ingestion and forwarding APIs")
public class EventHandler {

    private final EnforcementEngine enforcementEngine;

    @PostMapping("/events")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Operation(
            summary = "Ingest a log event",
            description = """
                    Accepts a log/metric/trace event from a tenant application.
                    The event is validated, rate-limited, metered, and forwarded to Kafka
                    for downstream processing by the Aggregate Service.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "202", description = "Event accepted and forwarded",
                    headers = @Header(name = "Retry-After", description = "Not present for 202")),
            @ApiResponse(responseCode = "400", description = "Invalid event payload"),
            @ApiResponse(responseCode = "401", description = "Invalid or missing API key"),
            @ApiResponse(responseCode = "429", description = "EPS quota exceeded",
                    headers = @Header(name = "Retry-After", description = "Seconds before retry")),
            @ApiResponse(responseCode = "503", description = "Kafka unavailable")
    })
    public Mono<EventResponse> ingestEvents(
            @Valid @RequestBody EventRequest request,
            @RequestHeader(value = "X-API-Key", required = false) String apiKey) {

        if (apiKey == null || apiKey.isBlank()) {
            return Mono.error(new com.vdt.soc.collector.exception.UnauthorizedException("Missing X-API-Key header"));
        }

        return enforcementEngine.process(request, apiKey);
    }
}
