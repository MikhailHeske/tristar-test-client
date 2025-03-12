package com.vls.tristar.model

import groovy.transform.ToString

@ToString
class Winning {
    Long userId;
    BigDecimal winAmount;
    BigDecimal winAmountF;
    String userCurrency;
    String roundId;
    String gameId;
    Status status;

    static enum Status {
        WON,
        LOST,
        CANCELLED,
    }
}
