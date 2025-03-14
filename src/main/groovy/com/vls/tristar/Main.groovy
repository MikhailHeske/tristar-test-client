package com.vls.tristar

import java.time.Instant


static void main(String[] args) {
    println "Starting test!"

    int usersNumber = Integer.parseInt(System.getenv("USERS_NUMBER") ?: "704")
    int statUsersNumber = 50
    int addUsersDelay = 30
    int addUsersNumber = 50
    int testTotalTime = 30

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
    def userNameTemplate = "tr-load-test-user-"
    for (int i = 0; i < number; i++) {
        def userSimualtor = new TristarUserSimulator(name: userNameTemplate + Counter.getAndIncrement())
        if (userSimualtor.init()) {
            simulators << userSimualtor
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