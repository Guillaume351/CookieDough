package com.cookiebuild.cookiedough.retention;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

/** Prevents duplicate chat delivery while a displayed response is awaiting its database ACK. */
final class RallyResponseDeliveryTracker {
    record Delivery(RallyRepository.Response response, boolean firstDisplay) { }

    private final Set<UUID> displayedAwaitingAck = ConcurrentHashMap.newKeySet();

    List<Delivery> ready(
            List<RallyRepository.Response> responses, Predicate<UUID> targetOnline) {
        return responses.stream()
                .filter(response -> targetOnline.test(response.targetPlayerId()))
                .map(response -> new Delivery(response, displayedAwaitingAck.add(response.id())))
                .toList();
    }

    void acknowledged(UUID responseId) {
        displayedAwaitingAck.remove(responseId);
    }

    void displayFailed(UUID responseId) {
        displayedAwaitingAck.remove(responseId);
    }
}
