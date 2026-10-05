package com.vls.tristar

import java.time.Instant


static void main(String[] args) {
    println "Starting test!"

    int usersNumber = Integer.parseInt(System.getenv("USERS_NUMBER") ?: "200")
    int statUsersNumber = 3
    int addUsersDelay = 0
    int addUsersNumber = 2
    int testTotalTime = 10
    boolean testRiskManagement = false

    List<Thread> threads = []

    List<TristarUserSimulator> simulators = addUsers(statUsersNumber)

    println "Users ${simulators.size()} are initialized"

    println "Start running test"

    simulators.each {
        Thread thread = new Thread(it)
        thread.start()
        threads << thread
    }

    println "All users are running"


    Instant nextCheck = Instant.now().plusSeconds(addUsersDelay)
    Instant finish = Instant.now().plusSeconds(testTotalTime * 60)
    while (true) {
        Thread.yield()
        if (simulators.size() < usersNumber && nextCheck.isBefore(Instant.now())) {

            def newSimulations = addUsers(addUsersNumber)
            newSimulations.each {
                Thread thread = new Thread(it)
                thread.start()
                threads << thread
            }
            simulators.addAll(newSimulations)
            println("Added ${addUsersNumber} users")

            nextCheck = Instant.now().plusSeconds(addUsersDelay)
        }
        if (finish.isBefore(Instant.now())) {
            break
        }
    }

    simulators.forEach {
        it.stop()
    }
    threads.each { it.join() }

    Reports.getInstance().printReport()
}


def addUsers(int number) {
    List<TristarUserSimulator> simulators = []
    def userNameTemplate = "tr-di-load-test-user-1-"
    for (int i = 0; i < number; i++) {
        def userSimualtor = new TristarUserSimulator(name: userNameTemplate + Counter.getAndIncrement())
        try {
            if (userSimualtor.init()) {
                simulators << userSimualtor
            }
        } catch (Exception e) {
            println "Canno create user in simulator"
        }
    }
    return simulators
}

class Counter {
    static int counter = 0

    static int getAndIncrement() {
        int v = counter
        counter ++
        return v
    }
}