package com.vls.tristar

class Constants {

//    static final String simulatorHost = 'https://tristar-third-party-simulator.qa.tristaar.com'
//    static final String playerHost = 'ws://tristar-player-api-service.qa.tristaar.staging/api/player'
//    static final String authHost = 'https://auth-service.qa.tristaar.com/api/auth'
//    static final String operatorHost = 'https://tristar-operator-service.qa.tristaar.com'

    static final String simulatorHost = 'https://tristar-third-party-simulator.preprod.tristaar.com'
    static final String playerHost = 'ws://tristar-player-api-service.preprod.tristaar.staging/api/player'
    static final String playerHttpHost = 'https://tristaar.com/api/player'
    static final String authHost = 'https://preprod.tristaar.com/api/auth'
    static final String operatorHost = 'https://tristar-operator-service.preprod.tristaar.com'

    static final String createUserSimulatorEndpoint = simulatorHost + '/api/v1/users'
    static final String operatorGameUrlEndpoint = operatorHost + '/game/url'
    static final String oneTimeTokenLoginEndpoint = authHost + '/v1/one-time-token-login'
    static final String gamingEndpoint = playerHost + '/gaming'
    static final String historyEndpoint = playerHttpHost + '/v2/user/{userId}/history?page=0&limit=15'
    static final String balanceEndpoint = simulatorHost + '/supplier/generic/v2/user/balance'

    static final String balanceDIEndpoint = simulatorHost + '/supplier/generic/v1/users/{user}/balance'
    static final String operatorGameUrlDIEndpoint = operatorHost + '/api/v1/games/url'

    static String betDbUrl() {
        String configured = System.getenv("BET_DB_URL")
        if (configured) {
            return configured
        }
        String host = System.getenv("POSTGRES_HOST") ?: "postgres.preprod.tristaar.staging:5435"
        return "jdbc:postgresql://${host}/bet"
    }

    static String betDbUser() {
        return System.getenv("BET_DB_USER") ?: System.getenv("POSTGRES_USER") ?: "hzhzhz"
    }

    static String betDbPassword() {
        return System.getenv("BET_DB_PASSWORD") ?: System.getenv("POSTGRES_PASSWORD") ?: "hzhzhz"
    }
}
