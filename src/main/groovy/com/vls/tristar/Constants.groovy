package com.vls.tristar

class Constants {

    static final String simulatorHost = 'http://tristar-third-party-simulator:8080'
    static final String playerHost = 'ws://tristar-client.qa.tristaar.com/api/player'
    static final String authHost = 'https://tristar-client.qa.tristaar.com/api/auth'
    static final String operatorHost = 'http://tristar-operator-service:3000'

    static final String createUserSimulatorEndpoint = simulatorHost + '/api/v1/users'
    static final String operatorGameUrlEndpoint = operatorHost + '/game/url'
    static final String oneTimeTokenLoginEndpoint = authHost + '/v1/one-time-token-login'
    static final String gamingEndpoint = playerHost + '/gaming'
    static final String balanceEndpoint = simulatorHost + '/supplier/generic/v2/user/balance'
}
