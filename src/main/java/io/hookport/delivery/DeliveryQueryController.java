package io.hookport.delivery;

import io.hookport.shared.PagedResponse;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
public class DeliveryQueryController {

    private final DeliveryQueryService queryService;

    public DeliveryQueryController(
            DeliveryQueryService queryService
    ) {
        this.queryService = queryService;
    }

    @GetMapping("/deliveries/{deliveryId}")
    public DeliveryResponse getDelivery(
            @PathVariable UUID deliveryId
    ) {
        return queryService.findDelivery(deliveryId);
    }

    @GetMapping("/deliveries/{deliveryId}/attempts")
    public PagedResponse<DeliveryAttemptDetailResponse> getAttempts(
            @PathVariable UUID deliveryId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return queryService.findAttempts(
                deliveryId,
                page,
                size
        );
    }

    @GetMapping("/endpoints/{endpointId}/deliveries")
    public PagedResponse<DeliveryResponse> getEndpointDeliveries(
            @PathVariable UUID endpointId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return queryService.findEndpointDeliveries(
                endpointId,
                page,
                size
        );
    }
}