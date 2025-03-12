package com.vls.tristar.model

import groovy.transform.ToString

import java.time.Instant

@ToString
class Market {
    String id;
    String configId;
    String name;
    String reference;
    String marketGroupName;
    String gameId;
    String roundId;
    String type;
    Integer sortOrder;
    Instant marketOpenTime;
    //Instant marketCloseTime;
    List<Selection> selections;
    String status;

    @ToString
    static class Selection {
        String id;
        String name;
        String reference;
        List<Price> backPrices;
        List<Price> layPrices;
        String status;
    }

    @ToString
    static class Price {
        String id;
        String side;
        BigDecimal value;
    }
}
