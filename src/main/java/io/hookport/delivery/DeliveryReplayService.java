package io.hookport.delivery;

import io.hookport.endpoint.EndpointStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@Service
public class DeliveryReplayService {

    private final WebhookDeliveryRepository deliveryRepository;
    private static final Logger log =
            LoggerFactory.getLogger(DeliveryReplayService.class);

    public DeliveryReplayService(
            WebhookDeliveryRepository deliveryRepository
    ) {
        this.deliveryRepository = deliveryRepository;
    }

    @Transactional
    public ReplayDeliveryResponse replay(UUID deliveryId) {
        WebhookDelivery original = deliveryRepository
                .findById(deliveryId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Delivery not found"
                ));

        if (!original.canReplay()) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Only failed or exhausted deliveries can be replayed"
            );
        }

        if (original.getEndpoint().getStatus()
                != EndpointStatus.ACTIVE) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Cannot replay delivery for a disabled endpoint"
            );
        }

        WebhookDelivery replay =
                WebhookDelivery.replayOf(original);

        deliveryRepository.save(replay);

        log.info(
                "Created delivery replay: originalDeliveryId={}, replayDeliveryId={}",
                original.getId(),
                replay.getId()
        );

        return new ReplayDeliveryResponse(
                original.getId(),
                replay.getId(),
                replay.getStatus()
        );
    }
}