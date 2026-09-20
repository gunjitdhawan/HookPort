package io.hookport.delivery;

import io.hookport.endpoint.WebhookEndpointRepository;
import io.hookport.shared.PagedResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class DeliveryQueryService {

    private static final int MAX_PAGE_SIZE = 100;

    private final WebhookDeliveryRepository deliveryRepository;
    private final DeliveryAttemptRepository attemptRepository;
    private final WebhookEndpointRepository endpointRepository;

    public DeliveryQueryService(
            WebhookDeliveryRepository deliveryRepository,
            DeliveryAttemptRepository attemptRepository,
            WebhookEndpointRepository endpointRepository
    ) {
        this.deliveryRepository = deliveryRepository;
        this.attemptRepository = attemptRepository;
        this.endpointRepository = endpointRepository;
    }

    public DeliveryResponse findDelivery(UUID deliveryId) {
        WebhookDelivery delivery = deliveryRepository
                .findById(deliveryId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Delivery not found"
                ));

        return DeliveryResponse.from(delivery);
    }

    public PagedResponse<DeliveryResponse> findEndpointDeliveries(
            UUID endpointId,
            int page,
            int size
    ) {
        requireEndpoint(endpointId);

        PageRequest pageRequest = createPageRequest(
                page,
                size,
                "createdAt"
        );

        Page<DeliveryResponse> result = deliveryRepository
                .findByEndpoint_Id(endpointId, pageRequest)
                .map(DeliveryResponse::from);

        return PagedResponse.from(result);
    }

    public PagedResponse<DeliveryAttemptDetailResponse> findAttempts(
            UUID deliveryId,
            int page,
            int size
    ) {
        requireDelivery(deliveryId);

        PageRequest pageRequest = createPageRequest(
                page,
                size,
                "attemptNumber"
        );

        Page<DeliveryAttemptDetailResponse> result = attemptRepository
                .findByDelivery_Id(deliveryId, pageRequest)
                .map(DeliveryAttemptDetailResponse::from);

        return PagedResponse.from(result);
    }

    private PageRequest createPageRequest(
            int page,
            int size,
            String sortProperty
    ) {
        if (page < 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Page must be zero or greater"
            );
        }

        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Size must be between 1 and 100"
            );
        }

        return PageRequest.of(
                page,
                size,
                Sort.by(
                        Sort.Direction.DESC,
                        sortProperty
                )
        );
    }

    private void requireEndpoint(UUID endpointId) {
        if (!endpointRepository.existsById(endpointId)) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Endpoint not found"
            );
        }
    }

    private void requireDelivery(UUID deliveryId) {
        if (!deliveryRepository.existsById(deliveryId)) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Delivery not found"
            );
        }
    }
}