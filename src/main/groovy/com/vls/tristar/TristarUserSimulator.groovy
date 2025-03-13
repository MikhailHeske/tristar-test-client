package com.vls.tristar

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.vls.tristar.model.Bet
import com.vls.tristar.model.BetsStatetsDTO
import com.vls.tristar.model.Market
import com.vls.tristar.model.PlaceBet
import com.vls.tristar.model.Round
import com.vls.tristar.model.Winning
import com.vls.tristar.service.JWTService
import groovy.util.logging.Slf4j
import jakarta.websocket.ContainerProvider
import jakarta.websocket.WebSocketContainer
import org.apache.commons.logging.LogAdapter
import org.apache.commons.logging.LogFactory
import org.springframework.lang.Nullable
import org.springframework.messaging.converter.MappingJackson2MessageConverter
import org.springframework.messaging.simp.stomp.StompCommand
import org.springframework.messaging.simp.stomp.StompFrameHandler
import org.springframework.messaging.simp.stomp.StompHeaders
import org.springframework.messaging.simp.stomp.StompSession
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter
import org.springframework.web.socket.WebSocketHttpHeaders
import org.springframework.web.socket.WebSocketMessage
import org.springframework.web.socket.WebSocketSession
import org.springframework.web.socket.client.WebSocketClient
import org.springframework.web.socket.client.standard.StandardWebSocketClient
import org.springframework.web.socket.handler.LoggingWebSocketHandlerDecorator
import org.springframework.web.socket.messaging.WebSocketStompClient

import java.lang.reflect.Type
import java.math.MathContext
import java.math.RoundingMode
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalUnit
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.logging.Logger

@Slf4j
class TristarUserSimulator implements Runnable {

    static ObjectMapper mapper = new ObjectMapper()
    static JWTService jwtService = new JWTService("MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAqfFXMN/awNNbfIfXX8nrheZTi3V1YoB60vKMjkCm+YMUZfUEILc2xhjsdV6Bcf1cICmsOifZjxjSIYLeoqRYpdaNP3k/AJ6/nUfd7fWBOuVP34DwJ2U8L7xaOD8/IJWeFjguWOuFaES6JvpW+qY7PWiL27og0nMcUNmmaFqsHzXIc7vHnQoSkKvs/X1Yw7C3Nsf3SIYoSaGmzEGht8qLnl2L6/f0qPpRiYhtn2yxb/SpmVJWGso1GMy7CNBHNWYYFAw+64/AgkZuKCInfPwoaADABAkORsBm5EL2a2V6QuFnTC4CFRog+7IeY20JgpQY7DdO4TVTiGqhsOM25ApDYwIDAQAB")

    private String name
    private String userId
    private String gameId = "1"
    private HttpClient httpClient = HttpClient.newHttpClient();

    private WebSocketClient client
    private WebSocketStompClient stompClient
    private StompSession session

    boolean shouldStop = false

    void stop() {
        shouldStop = true
    }

    boolean init() {
        createUserInSimulator()
        balanceBefore = fetchBalance()
        return connect()
    }

    void createUserInSimulator() {
        Map body = [
                name    : name,
                amount  : 10000000,
                currency: "USD"
        ]

        HttpRequest userUpdateRequest = HttpRequest.newBuilder()
                .uri(URI.create(Constants.createUserSimulatorEndpoint))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(
                        mapper.writeValueAsString(body)
                ))
                .build()

        HttpResponse response = httpClient.send(userUpdateRequest, HttpResponse.BodyHandlers.ofString())
        if (response.statusCode() != 200) {
            throw new RuntimeException("Failed to create user in simulator: " + response.body())
        }
    }

    boolean connect() {
        try {
            log.info("Connection for user {}", name)

            String gameUrlLink = getGameUrlLink()
            log.info("Got game url link {}", gameUrlLink)
            String oneTimeToken = extractOneTimeLoginToken(gameUrlLink)
            log.info("Extracted one time token {}", oneTimeToken)
            String jwtToken = login(oneTimeToken)
            log.info("Got JWT token {}", jwtToken)

            WebSocketContainer container = ContainerProvider.getWebSocketContainer()
            container.setDefaultMaxSessionIdleTimeout(-1)
            container.setDefaultMaxBinaryMessageBufferSize(10 * 1024 * 1024)
            client = new StandardWebSocketClient();

            stompClient = new WebSocketStompClient(client);

            MappingJackson2MessageConverter converter = new MappingJackson2MessageConverter()
            converter.objectMapper.registerModule(new JavaTimeModule());
            stompClient.setInboundMessageSizeLimit(10 * 1024*1024)
            stompClient.setMessageConverter(converter);

            WebSocketHttpHeaders headers = new WebSocketHttpHeaders()

            StompHeaders stompHeaders = new StompHeaders()
            stompHeaders.add("x-authorization", jwtToken)

            session = stompClient.connectAsync(Constants.gamingEndpoint, headers, stompHeaders, new CustomStompSessionHandlerAdapter()).get(1, TimeUnit.SECONDS);

            log.info("Connected to WebSocket")

            return true;

        } catch (Exception e) {
            log.error("Failed to connect to TriStar User Simulator: {}", e.getMessage());
            return false;
        }
    }


    private String getGameUrlLink() {
        Map<String, Object> gameUrlBody = [
                user          : name,
                token         : UUID.randomUUID().toString(),
                sub_partner_id: "casino",
                platform      : "GPL_MOBILE",
                operator_id   : "HUB_TRISTAR",
                lobby_url     : "https://amazing-casino.com/lobby",
                lang          : "en",
                ip            : "142.245.172.168",
                game_code     : "DTL",
                deposit_url   : "https://amazing-casion.com/deposit",
                currency      : "USD",
                game_currency : "USD",
                country       : "EE"
        ]

        HttpRequest gameUrlRequest = HttpRequest.newBuilder()
                .uri(URI.create(Constants.operatorGameUrlEndpoint))
                .header("X-Hub88-Signature", "fridge disarray flock sinuous")
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(
                        mapper.writeValueAsString(gameUrlBody)
                ))
                .timeout(Duration.of(5, ChronoUnit.SECONDS))
                .build()

        HttpResponse<String> gameUrlResponse = httpClient.send(gameUrlRequest, HttpResponse.BodyHandlers.ofString())
        log.info("Got response from game/url {}", gameUrlResponse.body())
        return mapper.readValue(gameUrlResponse.body(), Map).url
    }

    private String login(String oneTimeToken) {
        Map<String, Object> loginBody = [
                token: oneTimeToken
        ]

        HttpRequest oneTimeTokenLoginRequest = HttpRequest.newBuilder()
                .uri(URI.create(Constants.oneTimeTokenLoginEndpoint))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(
                        mapper.writeValueAsString(loginBody)
                ))
                .build()

        HttpResponse<String> loginResponse = httpClient.send(oneTimeTokenLoginRequest, HttpResponse.BodyHandlers.ofString())
        String jwtToken = loginResponse.headers().firstValue("X-Authorization").get()

        Map<String, Object> tokenData = jwtService.decrypt(jwtToken)
        Map<String, Object> userData = tokenData.get("User")
        userId = userData.get("id")

        return jwtToken
    }

    private String extractOneTimeLoginToken(String gameLink) {
        String[] parts = gameLink.split("\\?")
        String[] oneTimeLoginTokenParts = parts[1].split("&")
        return oneTimeLoginTokenParts[0].split("=")[1]
    }


    int counter = 1
    Round currentRound
    List<Market> markets = []
    List<Bet> currentBets = []

    BigDecimal balanceBefore


    @Override
    void run() {
        session.subscribe("/topic/game/" + gameId + "/round", new RoundHandler())
        session.subscribe("/topic/game/" + gameId + "/market", new MarketHandler())
        session.subscribe("/user/" + userId + "/bets", new BetStateHandler())
        session.subscribe("/user/" + userId + "/winning", new WinningHandler())

        Instant nextCheckTime = Instant.now().plusSeconds(1)
        while (true) {
            if (!session.isConnected()) {
                log.error("Connection is closed!!!")
                Reports.getInstance().incrementConnectionLost()
                return
            }
            if (nextCheckTime.isBefore(Instant.now())) {
                def openMarkets = markets.findAll { it.status == "OPEN" }
                if (openMarkets) {
                    def marketForPlaceBet = markets[random.nextInt(openMarkets.size())]
                    log.info("Market are open. User {} wants to place bet on market {} with id {}!", name, marketForPlaceBet.name, marketForPlaceBet.id)
                    PlaceBet placedBet = placeBet(marketForPlaceBet)
                    log.info("Bet {} on {} is placed by user {} for round {}, counter {} ", placedBet.clientBetId, placedBet.amount, name, currentRound?.externalRoundId, counter)
                }

                nextCheckTime = Instant.now().plusSeconds(5)
            }
            Thread.yield()
            if (shouldStop) {
                log.info("Stopping TristarUserSimulator for user {}", name)
                return
            }
        }
    }

    void clearState() {
        currentRound = null
        currentBets = []
        markets = []

        counter++
        log.debug("Ready to next {}th round", counter)
    }

    BigDecimal fetchBalance() {
        Map<String, Object> balanceBody = [
                token: "ssss",
                supplier_user: name,
                request_uuid: "wwww",
                game_code: "DTL",
                gameId: "1"
        ]

        HttpRequest balanceRequest = HttpRequest.newBuilder()
                .uri(URI.create(Constants.balanceEndpoint))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(
                        mapper.writeValueAsString(balanceBody)
                ))
                .build()

        HttpResponse<String> response = httpClient.send(balanceRequest, HttpResponse.BodyHandlers.ofString())
        BigDecimal balance = mapper.readValue(response.body(), Map).balance as BigDecimal
        return balance.divide(100000 as BigDecimal).setScale(2, RoundingMode.UP)

    }

    class RoundHandler implements StompFrameHandler {

        @Override
        Type getPayloadType(StompHeaders headers) {
            return Round
        }

        @Override
        void handleFrame(StompHeaders headers, @Nullable Object payload) {
            log.info("Received round update: {}", payload)

            Round newRound = (Round) payload

            if (currentRound && currentRound.sequenceNumber < newRound.sequenceNumber) {
                clearState()
            }
            currentRound = (Round) payload
        }
    }

    class MarketHandler implements StompFrameHandler {

        static ObjectMapper objectMapper = new ObjectMapper()

        static {
            objectMapper.registerModule(new JavaTimeModule());
        }

        @Override
        Type getPayloadType(StompHeaders headers) {
            return Market
        }

        @Override
        void handleFrame(StompHeaders headers, @Nullable Object payload) {
            log.info("Received market update: {}", payload)
            Market newMarket = (Market) payload

            int indexOfMarket = markets.findIndexOf { it.id == newMarket.id }
            if (indexOfMarket > -1) {
                markets[indexOfMarket] = newMarket
            } else {
                markets << newMarket
            }
        }
    }

    Random random = new Random()

    PlaceBet placeBet(Market market) {
        Market.Selection selection = market.selections[random.nextInt(market.selections.size())]

        PlaceBet placeBet = new PlaceBet(
                clientBetId: UUID.randomUUID().toString(),
                amount: random.nextInt(90) + 10,
                selectionId: selection.id,
                priceId: selection.backPrices[0].id,
                marketId: market.id,
                roundId: market.roundId,
                gameId: market.gameId,
                userPlacedTime: Instant.now(),
                clientId: "test",
                side: "BACK"
        )

        session.send("/app/game/bet", [bet: placeBet])

        return placeBet
    }

    class BetStateHandler implements StompFrameHandler {

        @Override
        Type getPayloadType(StompHeaders headers) {
            return BetsStatetsDTO
        }

        @Override
        void handleFrame(StompHeaders headers, @Nullable Object payload) {
            log.debug("Come bets update")
            try {
                BetsStatetsDTO betsStatetsDTO = (BetsStatetsDTO) payload

                for (Bet newBet : betsStatetsDTO.bets) {
                    log.info("Received bet state update: {}", newBet)
                    boolean isReplaced = false
                    for (int i = 0; i < currentBets.size() && !isReplaced; i++) {
                        if (currentBets[i].id == newBet.id) {
                            currentBets[i] = newBet
                            isReplaced = true
                        }
                    }
                    if (!isReplaced) {
                        currentBets << newBet
                    }
                }
            } catch (Exception e) {
                log.error("Error in BetStateHandler: {}", e.getMessage())
            }
        }
    }

    class WinningHandler implements StompFrameHandler {

        @Override
        Type getPayloadType(StompHeaders headers) {
            return Winning
        }

        @Override
        void handleFrame(StompHeaders headers, @Nullable Object payload) {
            log.debug("Received winning update: {}", payload)

            Winning winning = (Winning) payload

            BigDecimal balanceAfter = fetchBalance()
            UserRoundResultData data = new UserRoundResultData(
                    userId: userId,
                    userName: name,
                    round: currentRound,
                    placedBets: currentBets,
                    userWinning: winning,
                    userBalanceBefore: balanceBefore,
                    userBalanceAfter: balanceAfter
            )

            UserRoundResultValidator validator = new UserRoundResultValidator(data)
            log.info("RoundResultData: {}", data)
            log.info("Is round result right: {}", validator.isCalulationRight())
            Reports.getInstance().addReportData(data)

            balanceBefore = balanceAfter
        }
    }
}

@Slf4j
class CustomStompSessionHandlerAdapter extends StompSessionHandlerAdapter {




    @Override
    void handleFrame(StompHeaders headers, @Nullable Object payload) {
        super.handleFrame(headers, payload)
        log.info("Received STOMP frame: {}", payload)
    }

    @Override
    void handleException(StompSession session, @Nullable StompCommand command, StompHeaders headers, byte[] payload, Throwable exception) {
        super.handleException(session, command, headers, payload, exception)
        log.error("Stomp exception: {}", exception.getMessage())
    }

    @Override
    void handleTransportError(StompSession session, Throwable exception) {
        super.handleTransportError(session, exception)
        log.error("Stomp error: {}, {}", exception.getMessage(), exception.getClass().getName())
    }
}

class CustomWebSocketStompClient extends WebSocketStompClient {

    /**
     * Class constructor. Sets {@link #setDefaultHeartbeat} to "0,0" but will
     * reset it back to the preferred "10000,10000" when a
     * {@link #setTaskScheduler} is configured.
     * @param webSocketClient the WebSocket client to connect with
     */
    CustomWebSocketStompClient(WebSocketClient webSocketClient) {
        super(webSocketClient)
    }
}