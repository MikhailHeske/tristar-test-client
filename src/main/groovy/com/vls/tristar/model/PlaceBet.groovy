package com.vls.tristar.model

import groovy.transform.ToString

import java.time.Instant;

@ToString
class PlaceBet {
    String clientBetId;
    BigDecimal amount;
    String selectionId;
    String priceId;
    String marketId;
    String roundId;
    String gameId;
    Instant userPlacedTime;
    String clientId;
    String side;
}
