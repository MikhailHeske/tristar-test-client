package com.vls.tristar.model

import groovy.transform.ToString

import java.time.Instant

@ToString
class Bet {
    String id;
    long systemId;
    String clientBetId;
    long userId;
    BigDecimal amount;
    BigDecimal amountF;
    String selectionId;
    String priceId;
    String marketId;
    String roundId;
    String gameId;
    BigDecimal price;
    Instant userPlacedTime;
    String status;
    boolean invalid;
    String externalId;
    String side;
    List<ErrorInfo> errors;
    String clientId;
    String connectSessionId;
    String operatorId;
    String sessionId;
    String userCurrencyCode;
    String outcome;
    BigDecimal outcomeAmount;
    BigDecimal userCurrencyOutcomeAmount;
    BigDecimal winAmount;
    BigDecimal userCurrencyWinAmount;
    Instant settleTime;
    BigDecimal settleOdds;
    String position;
}
