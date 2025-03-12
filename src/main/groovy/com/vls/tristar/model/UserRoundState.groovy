package com.vls.tristar.model

import groovy.transform.ToString

@ToString
class UserRoundState {
    String roundId
    BigDecimal placedAmount = 0;
    BigDecimal expectedWinAmount = 0;
    BigDecimal expectedPnlAmount = 0;
    BigDecimal actualWinAmount = 0;
    BigDecimal actualPnlAmount = 0;
    String outcome;

    boolean isCalculationRight() {
        if (outcome.equalsIgnoreCase("win")) {
            return actualPnlAmount == expectedPnlAmount;
        } else {
            return actualPnlAmount == placedAmount.negate();
        }
    }
}
