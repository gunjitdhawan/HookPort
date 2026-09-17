package io.hookport.endpoint;

import io.hookport.shared.PageResponse;
import jakarta.validation.Valid;
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
                .body(response);
    }

    @GetMapping("/{endpointId}")
    public EndpointResponse getById(
            @PathVariable UUID endpointId
    ) {
        return service.getById(endpointId);
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
}