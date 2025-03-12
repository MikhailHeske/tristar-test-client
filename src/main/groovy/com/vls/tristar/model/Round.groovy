package com.vls.tristar.model

import groovy.transform.ToString

import java.time.Instant

@ToString
class Round {
    String id;
    String gameId;
    String status;
    Long sequenceNumber;
    String externalRoundId;
    Instant startTime;
    Instant finishedTime;

}