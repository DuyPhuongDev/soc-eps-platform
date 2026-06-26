package com.vdt.soc.collector.handler;

import com.vdt.soc.collector.dto.EventBatchRequest;
import com.vdt.soc.collector.dto.EventBatchResponse;
import com.vdt.soc.collector.dto.EventRequest;
import com.vdt.soc.collector.dto.EventResponse;
import com.vdt.soc.collector.engine.EnforcementEngine;
import com.vdt.soc.common.core.exception.UnauthorizedException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
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

    @ApiResponse(responseCode = "202", description = "Event accepted and forwarded",
            headers = @Header(name = "Retry-After", description = "Not present for 202"))
    @ApiResponse(responseCode = "400", description = "Invalid event payload")
    @ApiResponse(responseCode = "401", description = "Invalid or missing API key")
    @ApiResponse(responseCode = "429", description = "EPS quota exceeded",
            headers = @Header(name = "Retry-After", description = "Seconds before retry"))
    @ApiResponse(responseCode = "503", description = "Kafka unavailable")

    public Mono<EventResponse> ingestEvents(
            @Valid @RequestBody EventRequest request,
            @RequestHeader(value = "X-API-Key", required = false) String apiKey) {

        if (apiKey == null || apiKey.isBlank()) {
            return Mono.error(new UnauthorizedException("Missing X-API-Key header"));
        }

        return enforcementEngine.process(request, apiKey);
    }

    @PostMapping("/events/batch")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Operation(
            summary = "Ingest a batch of events",
            description = """
                    Accepts a batch of up to 1000 log/metric/trace events from a tenant application.
                    Events are validated, rate-limited as a group (multi-token consume),
                    metered, and forwarded to Kafka for downstream processing.

                    Partial acceptance is supported: if the token bucket has fewer tokens
                    than the batch size, all available tokens are consumed and a 202 with
                    status="partial" is returned. Only when zero tokens are available is
                    a 429 returned.
                    """)
    @ApiResponse(responseCode = "202", description = "All or some events accepted",
            headers = @Header(name = "Retry-After", description = "Not present for 202"))
    @ApiResponse(responseCode = "400", description = "Invalid event payload or empty batch")
    @ApiResponse(responseCode = "401", description = "Invalid or missing API key")
    @ApiResponse(responseCode = "429", description = "EPS quota exceeded — no events accepted",
            headers = @Header(name = "Retry-After", description = "Seconds before retry (usually 1)"))
    @ApiResponse(responseCode = "503", description = "Kafka unavailable")
    public Mono<EventBatchResponse> ingestBatch(
            @Valid @RequestBody EventBatchRequest batch,
            @RequestHeader(value = "X-API-Key", required = false) String apiKey) {

        if (apiKey == null || apiKey.isBlank()) {
            return Mono.error(new UnauthorizedException("Missing X-API-Key header"));
        }

        return enforcementEngine.processBatch(batch, apiKey);
    }
}
