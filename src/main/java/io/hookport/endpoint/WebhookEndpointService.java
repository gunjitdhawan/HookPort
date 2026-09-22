package io.hookport.endpoint;

import io.hookport.security.TargetUrlValidator;
import io.hookport.shared.PageResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.net.URISyntaxException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Set;
import java.util.UUID;

@Service
public class WebhookEndpointService {

    private static final Set<String> ALLOWED_SCHEMES =
            Set.of("http", "https");

    private final WebhookEndpointRepository repository;
    private final SecureRandom secureRandom = new SecureRandom();
    private final TargetUrlValidator targetUrlValidator;
    private final EndpointRateBucketRepository buckets;

    public WebhookEndpointService(WebhookEndpointRepository repository,
                                  TargetUrlValidator targetUrlValidator,
                                  EndpointRateBucketRepository bucketRepository) {
        this.repository = repository;
        this.targetUrlValidator = targetUrlValidator;
        this.buckets = bucketRepository;
    }

    @Transactional
    public EndpointResponse update(UUID endpointId, UpdateEndpointRequest request, long expectedVersion) {
        if (request.name() == null &&
                request.targetUrl() == null &&
                request.status() == null &&
                request.bucketCapacity() == null &&
                request.refillPerSecond() == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "At least one field must be supplied"
            );
        }

        WebhookEndpoint endpoint = repository.findById(endpointId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Webhook endpoint not found"
                ));

        if (!endpoint.getVersion().equals(expectedVersion)) {
            throw new ResponseStatusException(
                    HttpStatus.PRECONDITION_FAILED,
                    "The endpoint was modified after it was retrieved"
            );
        }

        String updatedName = endpoint.getName();
        String updatedTargetUrl = endpoint.getTargetUrl();
        EndpointStatus updatedStatus = endpoint.getStatus();

        if(request.name()!=null) {
            updatedName = request.name().trim();
        }

        if(updatedName.isBlank()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "name must not be blank");

        if (!updatedName.equals(endpoint.getName()) &&
                repository.existsByNameAndIdNot(
                        updatedName,
                        endpointId
                )) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "An endpoint with this name already exists"
            );
        }

        if (request.targetUrl() != null) {
            updatedTargetUrl =
                    validateAndNormalizeUrl(request.targetUrl());
            targetUrlValidator.validate(updatedTargetUrl);
        }

        if (request.status() != null) {
            updatedStatus = request.status();
        }

        EndpointRateBucket bucket;

        if (request.bucketCapacity() != null
                || request.refillPerSecond() != null) {
            bucket = buckets.findForSettingsUpdate(endpointId)
                    .orElseThrow(() -> new IllegalStateException(
                            "Endpoint has no rate bucket: " + endpointId
                    ));

            int capacity = request.bucketCapacity() == null
                    ? bucket.getCapacity()
                    : request.bucketCapacity();

            int refillRate = request.refillPerSecond() == null
                    ? bucket.getRefillPerSecond()
                    : request.refillPerSecond();

            bucket.changeSettings(
                    capacity,
                    refillRate,
                    Instant.now()
            );
        } else {
            bucket = bucketFor(endpointId);
        }

        endpoint.update(
                updatedName,
                updatedTargetUrl,
                updatedStatus
        );

        /*
         * The entity is managed, so save() is not required.
         * flush() executes the SQL now and updates the version
         * before we create the response.
         */
        buckets.flush();
        repository.flush();

        return EndpointResponse.from(endpoint, bucketFor(endpointId));
    }

    @Transactional
    public CreateEndpointResponse create(CreateEndpointRequest request) {
        String name = request.name().trim();

        if (repository.existsByName(name)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "An endpoint with this name already exists"
            );
        }

        String targetUrl = validateAndNormalizeUrl(request.targetUrl());
        int capacity = request.bucketCapacity() == null
                ? 5 : request.bucketCapacity();
        int refillRate = request.refillPerSecond() == null
                ? 5 : request.refillPerSecond();


        String signingSecret = generateSigningSecret();
        targetUrlValidator.validate(request.targetUrl());
        WebhookEndpoint endpoint = WebhookEndpoint.create(
                name,
                targetUrl,
                signingSecret
        );

        WebhookEndpoint savedEndpoint = repository.save(endpoint);
        buckets.save(EndpointRateBucket.full(
                savedEndpoint.getId(),
                capacity,
                refillRate,
                Instant.now()
        ));

        return new CreateEndpointResponse(
                savedEndpoint.getId(),
                savedEndpoint.getName(),
                savedEndpoint.getTargetUrl(),
                savedEndpoint.getStatus(),
                savedEndpoint.getSigningSecret(),
                savedEndpoint.getCreatedAt(),
                savedEndpoint.getVersion(),
                capacity,
                refillRate

        );
    }

    private String validateAndNormalizeUrl(String value) {
        try {
            URI uri = new URI(value.trim());

            String scheme = uri.getScheme();

            if (scheme == null ||
                    !ALLOWED_SCHEMES.contains(scheme.toLowerCase()) ||
                    uri.getHost() == null) {
                throw invalidTargetUrl();
            }

            return uri.normalize().toString();

        } catch (URISyntaxException exception) {
            throw invalidTargetUrl();
        }
    }

    private ResponseStatusException invalidTargetUrl() {
        return new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "targetUrl must be a valid HTTP or HTTPS URL"
        );
    }

    private String generateSigningSecret() {
        byte[] randomBytes = new byte[32];
        secureRandom.nextBytes(randomBytes);

        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(randomBytes);
    }

    @Transactional(readOnly = true)
    public EndpointResponse getById(UUID endpointId) {
        WebhookEndpoint endpoint = repository.findById(endpointId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Webhook endpoint not found"
                ));

        return EndpointResponse.from(endpoint, bucketFor(endpointId));
    }

    @Transactional(readOnly = true)
    public PageResponse<EndpointResponse> getAll(int page, int size) {
        int safeSize = Math.min(size, 100);

        PageRequest pageRequest = PageRequest.of(
                page,
                safeSize,
                Sort.by(Sort.Direction.DESC, "createdAt")
        );

        Page<EndpointResponse> result = repository
                .findAll(pageRequest)
                .map(endpoint -> EndpointResponse.from(endpoint, bucketFor(endpoint.getId())));

        return PageResponse.from(result);
    }

    private EndpointRateBucket bucketFor(UUID endpointId) {
        return buckets.findById(endpointId)
                .orElseThrow(() -> new IllegalStateException(
                        "Endpoint has no rate bucket: " + endpointId
                ));
    }
}