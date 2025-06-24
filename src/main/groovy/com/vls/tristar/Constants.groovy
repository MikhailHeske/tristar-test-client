package com.vls.tristar

class Constants {

//    static final String simulatorHost = 'https://tristar-third-party-simulator.qa.tristaar.com'
//    static final String playerHost = 'ws://tristar-player-api-service.qa.tristaar.staging/api/player'
//    static final String authHost = 'https://auth-service.qa.tristaar.com/api/auth'
//    static final String operatorHost = 'https://tristar-operator-service.qa.tristaar.com'

    static final String simulatorHost = 'https://tristar-third-party-simulator.preprod.tristaar.com'
    static final String playerHost = 'ws://tristar-player-api-service.preprod.tristaar.staging/api/player'
    static final String authHost = 'https://preprod.tristaar.com/api/auth'
    static final String operatorHost = 'https://tristar-operator-service.preprod.tristaar.com'

    static final String createUserSimulatorEndpoint = simulatorHost + '/api/v1/users'
    static final String operatorGameUrlEndpoint = operatorHost + '/game/url'
    static final String oneTimeTokenLoginEndpoint = authHost + '/v1/one-time-token-login'
    static final String gamingEndpoint = playerHost + '/gaming'
    static final String balanceEndpoint = simulatorHost + '/supplier/generic/v2/user/balance'
}
