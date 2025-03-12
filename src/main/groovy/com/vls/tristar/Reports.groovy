package com.vls.tristar

import com.vls.tristar.model.Round

import java.util.concurrent.atomic.AtomicInteger;

class Reports {

    private static final Reports reports = new Reports()

    static Reports getInstance() {
        return reports;
    }


    AtomicInteger connectionLost = new AtomicInteger(0)
    List<UserRoundResultData> allData = new ArrayList<>()

    void incrementConnectionLost() {
        connectionLost.incrementAndGet()
    }

    synchronized void addReportData(UserRoundResultData data) {
        allData << data
    }

    void printReport() {
        println("Total lost connections: " + connectionLost.get())
        println("Calculating report...")

        int totalBets = 0
        Set<String> roundId = []
        int rightCalculations = 0
        int wrongCalculations = 0

        List<UserRoundResultData> wrong = []

        allData.each { UserRoundResultData data ->
            totalBets += data.placedBets.size()
            roundId << data.round.id

            if (new UserRoundResultValidator(data).isCalulationRight()) {
                rightCalculations ++
            } else {
                wrongCalculations ++
                wrong << data
            }
        }

        println("Total bets: " + totalBets)
        println("Total rounds: " + roundId.size())
        println("Total right calculations: " + rightCalculations)
        println("Total wrong calculations: " + wrongCalculations)
        println("-----------")
        wrong.forEach {
            println("Wrong calculation: " + it.round.id + " and user " + it.userId)
            println(it)
            println()
        }



    }
}
