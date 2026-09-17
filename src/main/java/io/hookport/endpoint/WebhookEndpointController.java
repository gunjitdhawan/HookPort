package io.hookport.endpoint;

import io.hookport.shared.PageResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/endpoints")
public class WebhookEndpointController {

    private final WebhookEndpointService service;

    public WebhookEndpointController(WebhookEndpointService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<CreateEndpointResponse> create(
            @Valid @RequestBody CreateEndpointRequest request
    ) {
        CreateEndpointResponse response = service.create(request);

        URI location = URI.create(
                "/api/v1/endpoints/" + response.id()
        );

        return ResponseEntity
                .created(location)
                .eTag(EndpointEtag.fromVersion(response.version()))
                .body(response);
    }

    @GetMapping("/{endpointId}")
    public ResponseEntity<EndpointResponse> getById(
            @PathVariable UUID endpointId
    ) {
        EndpointResponse response = service.getById(endpointId);
        return ResponseEntity
                .ok()
                .eTag(EndpointEtag.fromVersion(response.version()))
                .body(response);
    }

    @GetMapping
    public PageResponse<EndpointResponse> getAll(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        if (page < 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "page must be zero or greater"
            );
        }

        if (size < 1) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "size must be greater than zero"
            );
        }

        return service.getAll(page, size);
    }

    @PatchMapping("/{endpointId}")
    public ResponseEntity<EndpointResponse> update(
            @PathVariable UUID endpointId,
            @RequestHeader(
                    value = HttpHeaders.IF_MATCH,
                    required = false
            ) String ifMatch,
            @Valid @RequestBody UpdateEndpointRequest request
    ) {
        long expectedVersion = EndpointEtag.parseRequired(ifMatch);
        EndpointResponse response = service.update(endpointId, request, expectedVersion);
        return ResponseEntity
                .ok()
                .eTag(EndpointEtag.fromVersion(response.version()))
                .body(response);

    }
}