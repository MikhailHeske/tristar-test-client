package com.vls.tristar.model

import com.fasterxml.jackson.annotation.JsonFormat
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import groovy.transform.ToString

import java.time.Instant

@ToString
@JsonIgnoreProperties(ignoreUnknown = true)
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
    Instant marketCloseTime;
    List<Selection> selections;
    List<TimeRange> bettingTimes
    String status;

    static class TimeRange {
        Instant start;
        Instant end;
    }

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
