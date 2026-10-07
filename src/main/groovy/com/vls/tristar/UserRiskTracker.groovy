package com.vls.tristar

import com.vls.tristar.model.Bet
import com.vls.tristar.model.Market
import com.vls.tristar.model.Winning

import java.math.RoundingMode
import java.sql.Connection
import java.sql.DriverManager
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.sql.SQLException
import java.sql.Timestamp
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.ConcurrentHashMap

/**
 * Per-user turnover, wallet winning and PnL collected during a load test and
 * checked against the bet-service consolidators.
 *
 * Turnover is the stake of a bet the first time bet-state reports it as accepted.
 * Table games publish round net outcome on {@code /winning}. Wallet credit is
 * stake + outcome, and that credit is {@code total_win}. PnL = winning - turnover.
 *
 * {@code user_current_consolidator} receives every settled bet in the open window.
 * The day and market tables receive a bet only when that round has at least two
 * bets on the same market type.
 */
class UserRiskTracker {

    static final BigDecimal MONEY_TOLERANCE = new BigDecimal("0.01")
    static final ZoneId zone = ZoneId.of(System.getenv("RISK_ZONE") ?: "UTC")

    static final String CURRENT = "user_current_consolidator"
    static final String USER_DAY = "user_day_consolidator"
    static final String USER_GAME_DAY = "user_game_day_consolidator"
    static final String USER_MARKET_DAY = "user_market_day_consolidator"
    static final String USER_MARKET = "user_market_consolidator"

    private static final UserRiskTracker INSTANCE = new UserRiskTracker()

    static UserRiskTracker getInstance() {
        return INSTANCE
    }

    static void configure(boolean enabled) {
        INSTANCE.enabled = enabled
        if (!enabled) {
            println "User risk management check is off (TEST_RISK_MANAGEMENT=false)"
            return
        }
        println "User risk management check is on. Bet DB ${Constants.betDbUrl()} as ${Constants.betDbUser()}"
    }

    boolean enabled = false

    private final Map<Long, UserRiskStats> users = new ConcurrentHashMap<>()
    private final Map<String, String> marketTypes = new ConcurrentHashMap<>()
    private final Set<String> missingTables = Collections.synchronizedSet(new HashSet<>())
    private final RiskManagementDb db = new RiskManagementDb()
    private boolean connectionErrorPrinted = false

    void onMarket(Market market) {
        if (!enabled || market?.id == null || market.type == null) {
            return
        }
        String type = market.type.toString()
        String previous = marketTypes.put(market.id, type)
        if (previous == type) {
            return
        }
        users.values().each { it.noteMarket(market.id, type) }
    }

    static String marketType(String marketId) {
        return marketId == null ? null : INSTANCE.marketTypes.get(marketId)
    }

    void register(String userId, String userName) {
        if (!enabled) {
            return
        }
        Long id = parseUserId(userId)
        if (id == null) {
            return
        }
        UserRiskStats stats = new UserRiskStats(id, userName)
        users.put(id, stats)
        try {
            if (missingTables.contains(CURRENT)) {
                stats.currentMissing = true
            } else {
                try {
                    UserCurrentConsolidatorRow row = db.findByUserId(id)
                    stats.captureBaseline(row)
                    if (row == null) {
                        println "Risk baseline user ${id} (${userName}): no ${CURRENT} row"
                    } else {
                        println "Risk baseline user ${id} (${userName}): total_bets=${money(row.totalBets)} total_win=${money(row.totalWin)} windowActive=${stats.activeWindowAtStart}"
                    }
                } catch (MissingConsolidatorTable missing) {
                    missingTables.add(missing.table)
                    stats.currentMissing = true
                    println "Risk table ${missing.table} is not in this database, check skipped"
                }
            }
            stats.dayBaseline = loadBaseline(USER_DAY) { db.loadUserDay([id]).get(id) }
            stats.gameDayBaseline = loadBaseline(USER_GAME_DAY) { db.loadUserGameDay([id]).get(id) }
            stats.marketDayBaseline = loadBaseline(USER_MARKET_DAY) { db.loadUserMarketDay([id]).get(id) }
            stats.marketBaseline = loadBaseline(USER_MARKET) { db.loadUserMarket([id]).get(id) }
        } catch (Exception e) {
            stats.baselineError = e.message
            if (!connectionErrorPrinted) {
                connectionErrorPrinted = true
                println "Failed to read risk consolidators: ${e.message}"
                e.printStackTrace()
            }
        }
    }

    private Map<String, MoneyPair> loadBaseline(String table, Closure<Map<String, MoneyPair>> query) {
        if (missingTables.contains(table)) {
            return [:]
        }
        try {
            return query() ?: [:]
        } catch (MissingConsolidatorTable missing) {
            missingTables.add(missing.table)
            println "Risk table ${missing.table} is not in this database, check skipped"
            return [:]
        }
    }

    void onBet(String userId, Bet bet) {
        if (!enabled || bet == null) {
            return
        }
        UserRiskStats stats = statsFor(userId, bet.userId)
        stats?.onBet(bet)
    }

    void onWinning(String userId, Winning winning) {
        if (!enabled || winning == null) {
            return
        }
        long hintedId = winning.userId != null ? winning.userId : 0L
        UserRiskStats stats = statsFor(userId, hintedId)
        stats?.onWinning(winning)
    }

    void awaitAndVerify() {
        if (!enabled) {
            return
        }
        if (users.isEmpty()) {
            println "User risk management: no users to check"
            return
        }

        long timeoutSec = Long.parseLong(System.getenv("RISK_VERIFY_TIMEOUT_SECONDS") ?: "120")
        long deadline = System.currentTimeMillis() + timeoutSec * 1000L
        RiskReport report = evaluate()
        boolean canCompare = users.values().any { it.baselineError == null }
        while (canCompare && !report.passed && System.currentTimeMillis() < deadline) {
            BigDecimal pending = sumMoney(report.users) { it.pending }
            int mismatches = report.users.count { !it.matched } + report.tables.count { !it.matched && !it.skipped }
            println "Risk check waiting: pendingTurnover=${money(pending)} mismatches=${mismatches}"
            try {
                Thread.sleep(3000)
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt()
                break
            }
            report = evaluate()
        }
        printReport(report)
        db.close()
    }

    private UserRiskStats statsFor(String userId, long betUserId) {
        Long id = parseUserId(userId)
        if (id != null && users.containsKey(id)) {
            return users.get(id)
        }
        if (betUserId > 0 && users.containsKey(betUserId)) {
            return users.get(betUserId)
        }
        return null
    }

    private RiskReport evaluate() {
        List<Long> ids = new ArrayList<>(users.keySet())
        Map<Long, RiskSnapshot> snapshots = [:]
        users.values().each { UserRiskStats stats -> snapshots.put(stats.userId, stats.snapshot()) }

        RiskReport report = new RiskReport()
        report.users = buildCurrentChecks(ids, snapshots)
        report.tables = [
                buildTable(USER_DAY, ids, snapshots) { RiskSnapshot snap, UserRiskStats stats -> [snap.day, stats.dayBaseline] },
                buildTable(USER_GAME_DAY, ids, snapshots) { RiskSnapshot snap, UserRiskStats stats -> [snap.gameDay, stats.gameDayBaseline] },
                buildTable(USER_MARKET_DAY, ids, snapshots) { RiskSnapshot snap, UserRiskStats stats -> [snap.marketDay, stats.marketDayBaseline] },
                buildTable(USER_MARKET, ids, snapshots) { RiskSnapshot snap, UserRiskStats stats -> [snap.market, stats.marketBaseline] }
        ]
        boolean currentOk = report.users.every { it.matched }
        boolean tablesOk = report.tables.every { it.matched }
        report.passed = currentOk && tablesOk
        return report
    }

    private List<UserRiskCheck> buildCurrentChecks(List<Long> ids, Map<Long, RiskSnapshot> snapshots) {
        Map<Long, UserCurrentConsolidatorRow> rows = [:]
        String dbError = null
        boolean missing = missingTables.contains(CURRENT)
        if (!missing) {
            try {
                rows = db.findByUserIds(ids)
            } catch (MissingConsolidatorTable missingTable) {
                missingTables.add(missingTable.table)
                missing = true
            } catch (Exception e) {
                dbError = e.message
                rememberDbError(e)
            }
        }
        boolean skipped = missing
        return users.values().sort { it.userId }.collect { UserRiskStats stats ->
            toCheck(stats, snapshots.get(stats.userId), skipped, rows.get(stats.userId), dbError)
        }
    }

    private TableCheck buildTable(String table, List<Long> ids, Map<Long, RiskSnapshot> snapshots, Closure<List> slicesOf) {
        TableCheck check = new TableCheck(name: table)
        if (missingTables.contains(table)) {
            check.skipped = true
            check.matched = true
            return check
        }
        if (users.values().any { it.baselineError }) {
            check.matched = false
            check.failures = ["baseline was not saved: ${users.values().find { it.baselineError }?.baselineError}"]
            return check
        }
        Map<Long, Map<String, MoneyPair>> actual
        try {
            actual = loadTable(table, ids)
        } catch (MissingConsolidatorTable missingTable) {
            missingTables.add(missingTable.table)
            check.skipped = true
            check.matched = true
            return check
        } catch (Exception e) {
            rememberDbError(e)
            check.matched = false
            check.failures = [e.message]
            return check
        }
        List<String> failures = []
        BigDecimal addedBets = BigDecimal.ZERO
        BigDecimal addedWins = BigDecimal.ZERO
        users.values().each { UserRiskStats stats ->
            List parts = slicesOf(snapshots.get(stats.userId), stats)
            Map<String, MoneyPair> delta = parts[0] ?: [:]
            Map<String, MoneyPair> baseline = parts[1] ?: [:]
            delta.values().each {
                addedBets = addedBets.add(it.bets)
                addedWins = addedWins.add(it.wins)
            }
            diffMoneyMaps(baseline, delta, actual.get(stats.userId) ?: [:]).each { failures << "user=${stats.userId} ${it}" }
        }
        check.addedBets = addedBets
        check.addedWins = addedWins
        boolean settled = snapshots.values().every { moneyClose(it.slicePending, BigDecimal.ZERO) && moneyClose(it.pending, BigDecimal.ZERO) }
        if (!settled && failures.isEmpty()) {
            failures << "waiting for unsettled rounds"
        }
        check.failures = failures.size() > 20 ? failures.subList(0, 20) + ["... ${failures.size() - 20} more"] : failures
        check.matched = failures.isEmpty()
        return check
    }

    private Map<Long, Map<String, MoneyPair>> loadTable(String table, List<Long> ids) {
        switch (table) {
            case USER_DAY: return db.loadUserDay(ids)
            case USER_GAME_DAY: return db.loadUserGameDay(ids)
            case USER_MARKET_DAY: return db.loadUserMarketDay(ids)
            case USER_MARKET: return db.loadUserMarket(ids)
            default: return [:]
        }
    }

    private void rememberDbError(Exception e) {
        if (!connectionErrorPrinted) {
            connectionErrorPrinted = true
            println "Failed to read risk consolidators: ${e.message}"
            e.printStackTrace()
        }
    }

    private static UserRiskCheck toCheck(UserRiskStats stats, RiskSnapshot snap, boolean skipped, UserCurrentConsolidatorRow row, String dbError) {
        BigDecimal slicePending = snap?.slicePending ?: BigDecimal.ZERO
        UserRiskCheck check = new UserRiskCheck(
                userId: stats.userId,
                userName: stats.userName,
                turnover: snap.turnover,
                winning: snap.winning,
                pnl: snap.pnl,
                pending: snap.pending.add(slicePending),
                acceptedBets: snap.acceptedBets
        )
        boolean pending = !moneyClose(check.pending, BigDecimal.ZERO)
        if (skipped || stats.currentMissing) {
            check.skipped = true
            check.matched = !pending && stats.baselineError == null && dbError == null
            if (pending) {
                check.detail = "pending turnover ${money(check.pending)} is not settled yet"
            }
            return check
        }
        if (stats.baselineError != null || dbError != null) {
            check.matched = false
            check.detail = stats.baselineError ?: dbError
            return check
        }

        BigDecimal dbBets = row?.totalBets ?: BigDecimal.ZERO
        BigDecimal dbWins = row?.totalWin ?: BigDecimal.ZERO
        Map expected = expectedConsolidator(stats.activeWindowAtStart, stats.baseBets, stats.baseWins, snap.turnover, snap.winning)
        check.dbBets = dbBets
        check.dbWins = dbWins
        check.dbPnl = dbWins.subtract(dbBets)
        check.expectedBets = expected.totalBets as BigDecimal
        check.expectedWins = expected.totalWin as BigDecimal
        check.baselineBets = stats.baseBets
        check.baselineWins = stats.baseWins
        check.windowActiveAtStart = stats.activeWindowAtStart
        boolean betsOk = moneyClose(check.expectedBets, dbBets)
        boolean winsOk = moneyClose(check.expectedWins, dbWins)
        boolean pnlOk = moneyClose(check.expectedWins.subtract(check.expectedBets), check.dbPnl, new BigDecimal("0.02"))
        check.matched = !pending && betsOk && winsOk && pnlOk
        if (pending) {
            check.detail = "pending turnover ${money(check.pending)} is not settled yet"
        } else if (!check.matched) {
            check.detail = "expected total_bets=${money(check.expectedBets)} total_win=${money(check.expectedWins)}, db total_bets=${money(dbBets)} total_win=${money(dbWins)}"
        }
        return check
    }

    private static void printReport(RiskReport report) {
        println "----- User Risk Management -----"
        println "turnover = accepted stakes, winning = amount credited to the wallet, pnl = winning - turnover"
        if (report.users.every { it.skipped }) {
            println "[SKIP] ${CURRENT} is not in this database"
        } else {
            println "[${CURRENT}]"
            report.users.each { UserRiskCheck check ->
                String mark = check.skipped ? "SKIP" : check.matched ? "OK" : "FAIL"
                BigDecimal dbTurnover = comparableDbTurnover(check)
                BigDecimal dbWinning = comparableDbWinning(check)
                println "[${mark}] user=${check.userId} ${check.userName} bets=${check.acceptedBets} turnover=${money(check.turnover)} winning=${money(check.winning)} pnl=${money(check.pnl)} | db turnover=${money(dbTurnover)} winning=${money(dbWinning)} pnl=${money(dbWinning.subtract(dbTurnover))}"
                if (!check.matched && check.detail) {
                    println "       ${check.detail}"
                }
            }
        }
        report.tables.each { TableCheck table ->
            if (table.skipped) {
                println "[SKIP] ${table.name} is not in this database"
                return
            }
            String mark = table.matched ? "OK" : "FAIL"
            println "[${mark}] ${table.name} turnover=${money(table.addedBets)} winning=${money(table.addedWins)} pnl=${money((table.addedWins ?: BigDecimal.ZERO).subtract(table.addedBets ?: BigDecimal.ZERO))}"
            table.failures?.each { println "       ${it}" }
        }
        println report.passed ? "USER RISK MANAGEMENT: PASSED" : "USER RISK MANAGEMENT: FAILED"
    }

    /**
     * Absolute totals we expect to read after the test.
     * An open window keeps the saved baseline and adds this run.
     * A missing or expired window is replaced by the service, so only this run is stored.
     * With no settled stakes and no wallet credit the row must stay as it was.
     */
    static Map expectedConsolidator(boolean activeWindow, BigDecimal baseBets, BigDecimal baseWins,
                                     BigDecimal turnover, BigDecimal winning) {
        BigDecimal bets = baseBets ?: BigDecimal.ZERO
        BigDecimal wins = baseWins ?: BigDecimal.ZERO
        BigDecimal turn = turnover ?: BigDecimal.ZERO
        BigDecimal win = winning ?: BigDecimal.ZERO
        if (turn.signum() == 0 && win.signum() == 0) {
            return [totalBets: bets, totalWin: wins]
        }
        if (activeWindow) {
            return [totalBets: bets.add(turn), totalWin: wins.add(win)]
        }
        return [totalBets: turn, totalWin: win]
    }

    static List<String> diffMoneyMaps(Map<String, MoneyPair> baseline, Map<String, MoneyPair> delta, Map<String, MoneyPair> actual) {
        Set<String> keys = new LinkedHashSet<>()
        keys.addAll(baseline?.keySet() ?: [])
        keys.addAll(delta?.keySet() ?: [])
        keys.addAll(actual?.keySet() ?: [])
        List<String> failures = []
        keys.each { String key ->
            MoneyPair base = baseline?.get(key) ?: MoneyPair.zero()
            MoneyPair add = delta?.get(key) ?: MoneyPair.zero()
            MoneyPair expected = base.plus(add)
            MoneyPair got = actual?.get(key) ?: MoneyPair.zero()
            if (!moneyClose(expected.bets, got.bets) || !moneyClose(expected.wins, got.wins)) {
                failures << "${key} expected bets=${money(expected.bets)} win=${money(expected.wins)} db bets=${money(got.bets)} win=${money(got.wins)}"
            }
        }
        return failures
    }

    static boolean moneyClose(BigDecimal left, BigDecimal right, BigDecimal tolerance = MONEY_TOLERANCE) {
        BigDecimal a = left ?: BigDecimal.ZERO
        BigDecimal b = right ?: BigDecimal.ZERO
        return a.subtract(b).abs() <= tolerance
    }

    private static BigDecimal comparableDbTurnover(UserRiskCheck check) {
        if (replacedWindow(check)) {
            return check.dbBets ?: BigDecimal.ZERO
        }
        return (check.dbBets ?: BigDecimal.ZERO).subtract(check.baselineBets ?: BigDecimal.ZERO)
    }

    private static BigDecimal comparableDbWinning(UserRiskCheck check) {
        if (replacedWindow(check)) {
            return check.dbWins ?: BigDecimal.ZERO
        }
        return (check.dbWins ?: BigDecimal.ZERO).subtract(check.baselineWins ?: BigDecimal.ZERO)
    }

    private static boolean replacedWindow(UserRiskCheck check) {
        boolean hadActivity = (check.turnover ?: BigDecimal.ZERO).signum() != 0 || (check.winning ?: BigDecimal.ZERO).signum() != 0
        return !check.windowActiveAtStart && hadActivity
    }

    private static BigDecimal sumMoney(List<UserRiskCheck> checks, Closure<BigDecimal> value) {
        BigDecimal sum = BigDecimal.ZERO
        checks.each { sum = sum.add(value(it) ?: BigDecimal.ZERO) }
        return sum
    }

    static String money(BigDecimal value) {
        return (value ?: BigDecimal.ZERO).setScale(6, RoundingMode.HALF_UP).toPlainString()
    }

    static Long parseUserId(String userId) {
        if (!userId) {
            return null
        }
        try {
            return Long.valueOf(userId.trim())
        } catch (NumberFormatException ignored) {
            println "Risk tracker: cannot parse userId ${userId}"
            return null
        }
    }
}

class UserRiskStats {
    final long userId
    final String userName

    BigDecimal baseBets = BigDecimal.ZERO
    BigDecimal baseWins = BigDecimal.ZERO
    boolean activeWindowAtStart = false
    boolean currentMissing = false
    String baselineError
    Map<String, MoneyPair> dayBaseline = [:]
    Map<String, MoneyPair> gameDayBaseline = [:]
    Map<String, MoneyPair> marketDayBaseline = [:]
    Map<String, MoneyPair> marketBaseline = [:]

    private BigDecimal turnover = BigDecimal.ZERO
    private BigDecimal outcomeTotal = BigDecimal.ZERO
    private final Map<String, BigDecimal> openRoundTurnover = new LinkedHashMap<>()
    private final Set<String> settledRounds = new HashSet<>()
    private final Set<String> cancelledRounds = new HashSet<>()
    private final Set<String> allocatedRounds = new HashSet<>()
    private final Map<String, CountedBet> byServerId = new LinkedHashMap<>()
    private final Map<String, CountedBet> byClientId = new LinkedHashMap<>()
    private final Set<CountedBet> countedBets = new LinkedHashSet<>()
    private final Map<String, MoneyPair> dayDelta = new LinkedHashMap<>()
    private final Map<String, MoneyPair> gameDayDelta = new LinkedHashMap<>()
    private final Map<String, MoneyPair> marketDayDelta = new LinkedHashMap<>()
    private final Map<String, MoneyPair> marketDelta = new LinkedHashMap<>()

    UserRiskStats(long userId, String userName) {
        this.userId = userId
        this.userName = userName
    }

    void captureBaseline(UserCurrentConsolidatorRow row) {
        activeWindowAtStart = row != null && row.windowActive()
        baseBets = row?.totalBets ?: BigDecimal.ZERO
        baseWins = row?.totalWin ?: BigDecimal.ZERO
    }

    synchronized void noteMarket(String marketId, String type) {
        boolean changed = false
        countedBets.each { CountedBet counted ->
            if (counted.marketId == marketId && counted.marketType != type) {
                counted.marketType = type
                changed = true
            }
        }
        if (changed) {
            new ArrayList<>(settledRounds).each { tryAllocate(it) }
        }
    }

    synchronized void onBet(Bet bet) {
        String status = normalizeStatus(bet.status)
        if (!status) {
            return
        }
        CountedBet existing = find(bet)
        if (status in ["CANCELLED", "REJECTED"]) {
            if (existing?.roundId && allocatedRounds.contains(existing.roundId)) {
                return
            }
            removeBet(existing)
            return
        }
        if (!(status in ["ACCEPTED", "SETTLED", "VOIDED"])) {
            return
        }
        if (existing != null) {
            refresh(existing, bet)
            tryAllocate(existing.roundId)
            return
        }
        String roundId = bet.roundId ?: "unknown"
        if (cancelledRounds.contains(roundId)) {
            return
        }
        BigDecimal stake = stakeOf(bet)
        CountedBet counted = new CountedBet(
                id: bet.id,
                clientBetId: bet.clientBetId,
                roundId: roundId,
                gameId: bet.gameId,
                marketId: bet.marketId,
                marketType: bet.marketId ? UserRiskTracker.marketType(bet.marketId) : null,
                stake: stake
        )
        refresh(counted, bet)
        remember(counted)
        turnover = turnover.add(stake)
        if (!settledRounds.contains(roundId)) {
            openRoundTurnover[roundId] = (openRoundTurnover[roundId] ?: BigDecimal.ZERO).add(stake)
        }
        tryAllocate(roundId)
    }

    synchronized void onWinning(Winning winning) {
        if (!winning.roundId || settledRounds.contains(winning.roundId) || cancelledRounds.contains(winning.roundId)) {
            return
        }
        String status = normalizeStatus(winning.status)
        if (status == "CANCELLED") {
            cancelledRounds.add(winning.roundId)
            removeRound(winning.roundId)
            return
        }
        settledRounds.add(winning.roundId)
        openRoundTurnover.remove(winning.roundId)
        outcomeTotal = outcomeTotal.add(winning.winAmount ?: BigDecimal.ZERO)
        tryAllocate(winning.roundId)
    }

    synchronized RiskSnapshot snapshot() {
        new ArrayList<>(settledRounds).each { tryAllocate(it) }
        BigDecimal pending = BigDecimal.ZERO
        openRoundTurnover.values().each { pending = pending.add(it ?: BigDecimal.ZERO) }
        BigDecimal winning = turnover.subtract(pending).add(outcomeTotal)
        return new RiskSnapshot(
                turnover: turnover,
                winning: winning,
                pending: pending,
                slicePending: unfinishedSliceTurnover(),
                pnl: winning.subtract(turnover),
                acceptedBets: countedBets.size(),
                day: copyPairs(dayDelta),
                gameDay: copyPairs(gameDayDelta),
                marketDay: copyPairs(marketDayDelta),
                market: copyPairs(marketDelta)
        )
    }

    private void refresh(CountedBet counted, Bet bet) {
        if (bet.marketId) {
            counted.marketId = bet.marketId
        }
        if (bet.gameId) {
            counted.gameId = bet.gameId
        }
        if (!counted.marketType && counted.marketId) {
            counted.marketType = UserRiskTracker.marketType(counted.marketId)
        }
        String status = normalizeStatus(bet.status)
        String outcome = normalizeStatus(bet.outcome)
        if (status == "VOIDED" || outcome == "VOID") {
            counted.wallet = counted.stake
            return
        }
        if (!(status in ["SETTLED", "VOIDED"] || bet.outcome)) {
            return
        }
        if (bet.winAmount != null) {
            counted.wallet = bet.winAmount
        } else if (bet.outcomeAmount != null) {
            counted.wallet = counted.stake.add(bet.outcomeAmount)
        }
    }

    private void tryAllocate(String roundId) {
        if (!roundId || !settledRounds.contains(roundId) || allocatedRounds.contains(roundId) || cancelledRounds.contains(roundId)) {
            return
        }
        List<CountedBet> bets = new ArrayList<>(countedBets).findAll { it.roundId == roundId }
        if (!bets) {
            allocatedRounds.add(roundId)
            return
        }
        if (bets.any { !it.marketType } && bets.size() > 1) {
            return
        }
        List<CountedBet> qualifying = []
        bets.findAll { it.marketType }.groupBy { it.marketType }.values().each { List<CountedBet> group ->
            if (group.size() > 1) {
                qualifying.addAll(group)
            }
        }
        if (qualifying.any { it.wallet == null }) {
            return
        }
        LocalDate date = LocalDate.now(UserRiskTracker.zone)
        qualifying.each { CountedBet bet ->
            String dayKey = "${date.dayOfMonth}|${date.monthValue}"
            addSlice(dayDelta, dayKey, bet)
            addSlice(gameDayDelta, "${bet.gameId}|${dayKey}", bet)
            addSlice(marketDayDelta, "${bet.gameId}|${bet.marketType}|${dayKey}", bet)
            addSlice(marketDelta, "${bet.gameId}|${bet.marketType}", bet)
        }
        allocatedRounds.add(roundId)
    }

    private static void addSlice(Map<String, MoneyPair> target, String key, CountedBet bet) {
        MoneyPair pair = target.get(key)
        if (pair == null) {
            pair = new MoneyPair()
            target.put(key, pair)
        }
        pair.add(bet.stake, bet.wallet)
    }

    private BigDecimal unfinishedSliceTurnover() {
        BigDecimal pending = BigDecimal.ZERO
        Map<String, List<CountedBet>> byRound = [:]
        countedBets.each { CountedBet bet ->
            if (settledRounds.contains(bet.roundId) && !allocatedRounds.contains(bet.roundId)) {
                byRound.computeIfAbsent(bet.roundId) { [] }.add(bet)
            }
        }
        byRound.values().each { List<CountedBet> bets ->
            if (bets.any { !it.marketType } && bets.size() > 1) {
                bets.each { pending = pending.add(it.stake) }
                return
            }
            bets.groupBy { it.marketType }.values().each { List<CountedBet> group ->
                if (group.size() > 1 && group.any { it.wallet == null }) {
                    group.each { pending = pending.add(it.stake) }
                }
            }
        }
        return pending
    }

    private static Map<String, MoneyPair> copyPairs(Map<String, MoneyPair> source) {
        Map<String, MoneyPair> copy = [:]
        source.each { String key, MoneyPair value -> copy.put(key, value.copy()) }
        return copy
    }

    private void removeRound(String roundId) {
        new ArrayList<>(countedBets).findAll { it.roundId == roundId }.each { removeBet(it) }
        openRoundTurnover.remove(roundId)
    }

    private void removeBet(CountedBet counted) {
        if (counted == null || !countedBets.contains(counted)) {
            return
        }
        countedBets.remove(counted)
        if (counted.id) {
            byServerId.remove(counted.id)
        }
        if (counted.clientBetId) {
            byClientId.remove(counted.clientBetId)
        }
        turnover = turnover.subtract(counted.stake)
        if (counted.roundId && openRoundTurnover.containsKey(counted.roundId)) {
            BigDecimal left = openRoundTurnover[counted.roundId].subtract(counted.stake)
            if (left.signum() == 0) {
                openRoundTurnover.remove(counted.roundId)
            } else {
                openRoundTurnover[counted.roundId] = left
            }
        }
    }

    private void remember(CountedBet counted) {
        countedBets.add(counted)
        if (counted.id) {
            byServerId[counted.id] = counted
        }
        if (counted.clientBetId) {
            byClientId[counted.clientBetId] = counted
        }
    }

    private CountedBet find(Bet bet) {
        if (bet.id && byServerId.containsKey(bet.id)) {
            return byServerId[bet.id]
        }
        if (bet.clientBetId && byClientId.containsKey(bet.clientBetId)) {
            return byClientId[bet.clientBetId]
        }
        return null
    }

    private static BigDecimal stakeOf(Bet bet) {
        if (bet.amount != null) {
            return bet.amount
        }
        return bet.amountF ?: BigDecimal.ZERO
    }

    private static String normalizeStatus(Object status) {
        if (status == null) {
            return null
        }
        String name = status instanceof Enum ? ((Enum) status).name() : status.toString()
        String normalized = name.trim().toUpperCase()
        return normalized ? normalized : null
    }
}

class CountedBet {
    String id
    String clientBetId
    String roundId
    String gameId
    String marketId
    String marketType
    BigDecimal stake
    BigDecimal wallet
}

class RiskSnapshot {
    BigDecimal turnover
    BigDecimal winning
    BigDecimal pending = BigDecimal.ZERO
    BigDecimal slicePending = BigDecimal.ZERO
    BigDecimal pnl
    int acceptedBets
    Map<String, MoneyPair> day = [:]
    Map<String, MoneyPair> gameDay = [:]
    Map<String, MoneyPair> marketDay = [:]
    Map<String, MoneyPair> market = [:]
}

class UserRiskCheck {
    long userId
    String userName
    BigDecimal turnover
    BigDecimal winning
    BigDecimal pnl
    BigDecimal pending
    int acceptedBets
    boolean skipped
    BigDecimal dbBets = BigDecimal.ZERO
    BigDecimal dbWins = BigDecimal.ZERO
    BigDecimal dbPnl = BigDecimal.ZERO
    BigDecimal expectedBets = BigDecimal.ZERO
    BigDecimal expectedWins = BigDecimal.ZERO
    BigDecimal baselineBets = BigDecimal.ZERO
    BigDecimal baselineWins = BigDecimal.ZERO
    boolean windowActiveAtStart
    boolean matched
    String detail
}

class RiskReport {
    List<UserRiskCheck> users = []
    List<TableCheck> tables = []
    boolean passed
}

class TableCheck {
    String name
    boolean skipped
    boolean matched
    BigDecimal addedBets = BigDecimal.ZERO
    BigDecimal addedWins = BigDecimal.ZERO
    List<String> failures = []
}

class MoneyPair {
    BigDecimal bets = BigDecimal.ZERO
    BigDecimal wins = BigDecimal.ZERO

    static MoneyPair zero() {
        return new MoneyPair()
    }

    void add(BigDecimal stake, BigDecimal wallet) {
        bets = bets.add(stake ?: BigDecimal.ZERO)
        wins = wins.add(wallet ?: BigDecimal.ZERO)
    }

    MoneyPair plus(MoneyPair other) {
        return new MoneyPair(
                bets: bets.add(other?.bets ?: BigDecimal.ZERO),
                wins: wins.add(other?.wins ?: BigDecimal.ZERO)
        )
    }

    MoneyPair copy() {
        return new MoneyPair(bets: bets, wins: wins)
    }
}

class MissingConsolidatorTable extends RuntimeException {
    final String table

    MissingConsolidatorTable(String table, Throwable cause) {
        super(table, cause)
        this.table = table
    }
}

class UserCurrentConsolidatorRow {
    long userId
    BigDecimal totalBets
    BigDecimal totalWin
    Instant startTime
    Instant endTime

    boolean windowActive(Instant now = Instant.now()) {
        return endTime != null && endTime.isAfter(now)
    }
}

class RiskManagementDb implements Closeable {
    private Connection connection

    UserCurrentConsolidatorRow findByUserId(long userId) {
        Map<Long, UserCurrentConsolidatorRow> rows = findByUserIds([userId])
        return rows.get(userId)
    }

    Map<Long, UserCurrentConsolidatorRow> findByUserIds(Collection<Long> userIds) {
        if (!userIds) {
            return [:]
        }
        try {
            PreparedStatement statement = prepare("""
                SELECT user_id, total_win, total_bets, start_time, end_time
                FROM user_current_consolidator
                WHERE user_id IN (%IDS%)
            """, userIds)
            ResultSet resultSet = statement.executeQuery()
            try {
                Map<Long, UserCurrentConsolidatorRow> rows = [:]
                while (resultSet.next()) {
                    UserCurrentConsolidatorRow row = read(resultSet)
                    rows.put(row.userId, row)
                }
                return rows
            } finally {
                resultSet.close()
                statement.close()
            }
        } catch (Exception e) {
            throw missingOrSame(UserRiskTracker.CURRENT, e)
        }
    }

    Map<Long, Map<String, MoneyPair>> loadUserDay(Collection<Long> userIds) {
        return loadGroups(UserRiskTracker.USER_DAY, userIds, """
            SELECT user_id, day, month, total_bets, total_win
            FROM user_day_consolidator
            WHERE user_id IN (%IDS%)
        """) { ResultSet rs -> "${rs.getInt("day")}|${rs.getInt("month")}" }
    }

    Map<Long, Map<String, MoneyPair>> loadUserGameDay(Collection<Long> userIds) {
        return loadGroups(UserRiskTracker.USER_GAME_DAY, userIds, """
            SELECT user_id, game_id, day, month, total_bets, total_win
            FROM user_game_day_consolidator
            WHERE user_id IN (%IDS%)
        """) { ResultSet rs -> "${rs.getString("game_id")}|${rs.getInt("day")}|${rs.getInt("month")}" }
    }

    Map<Long, Map<String, MoneyPair>> loadUserMarketDay(Collection<Long> userIds) {
        return loadGroups(UserRiskTracker.USER_MARKET_DAY, userIds, """
            SELECT user_id, game_id, market_type, day, month, total_bets, total_win
            FROM user_market_day_consolidator
            WHERE user_id IN (%IDS%)
        """) { ResultSet rs -> "${rs.getString("game_id")}|${rs.getString("market_type")}|${rs.getInt("day")}|${rs.getInt("month")}" }
    }

    Map<Long, Map<String, MoneyPair>> loadUserMarket(Collection<Long> userIds) {
        return loadGroups(UserRiskTracker.USER_MARKET, userIds, """
            SELECT user_id, game_id, market_type, total_bets, total_win
            FROM user_market_consolidator
            WHERE user_id IN (%IDS%)
        """) { ResultSet rs -> "${rs.getString("game_id")}|${rs.getString("market_type")}" }
    }

    private Map<Long, Map<String, MoneyPair>> loadGroups(String table, Collection<Long> userIds, String sql, Closure<String> keyOf) {
        if (!userIds) {
            return [:]
        }
        try {
            PreparedStatement statement = prepare(sql, userIds)
            ResultSet resultSet = statement.executeQuery()
            try {
                Map<Long, Map<String, MoneyPair>> byUser = [:]
                while (resultSet.next()) {
                    long userId = resultSet.getLong("user_id")
                    byUser.computeIfAbsent(userId) { [:] }.put(keyOf(resultSet), new MoneyPair(
                            bets: resultSet.getBigDecimal("total_bets") ?: BigDecimal.ZERO,
                            wins: resultSet.getBigDecimal("total_win") ?: BigDecimal.ZERO
                    ))
                }
                return byUser
            } finally {
                resultSet.close()
                statement.close()
            }
        } catch (Exception e) {
            throw missingOrSame(table, e)
        }
    }

    private PreparedStatement prepare(String sql, Collection<Long> userIds) {
        connect()
        String placeholders = userIds.collect { "?" }.join(",")
        PreparedStatement statement = connection.prepareStatement(sql.replace("%IDS%", placeholders))
        int index = 1
        for (Long id : userIds) {
            statement.setLong(index++, id)
        }
        return statement
    }

    private static Exception missingOrSame(String table, Exception e) {
        SQLException sql = unwrap(e)
        if (sql != null && (sql.SQLState == "42P01" || sql.message?.contains("does not exist"))) {
            return new MissingConsolidatorTable(table, e)
        }
        return e
    }

    private static SQLException unwrap(Throwable error) {
        Throwable current = error
        while (current != null) {
            if (current instanceof SQLException) {
                return (SQLException) current
            }
            current = current.cause
        }
        return null
    }

    private void connect() {
        if (connection != null && !connection.isClosed()) {
            return
        }
        Properties properties = new Properties()
        properties.setProperty("user", Constants.betDbUser())
        properties.setProperty("password", Constants.betDbPassword())
        connection = DriverManager.getConnection(Constants.betDbUrl(), properties)
        connection.setReadOnly(true)
        connection.setAutoCommit(true)
        connection.createStatement().withCloseable { it.execute("SET TIME ZONE 'UTC'") }
    }

    private static UserCurrentConsolidatorRow read(ResultSet resultSet) {
        return new UserCurrentConsolidatorRow(
                userId: resultSet.getLong("user_id"),
                totalWin: resultSet.getBigDecimal("total_win") ?: BigDecimal.ZERO,
                totalBets: resultSet.getBigDecimal("total_bets") ?: BigDecimal.ZERO,
                startTime: readInstant(resultSet, "start_time"),
                endTime: readInstant(resultSet, "end_time")
        )
    }

    private static Instant readInstant(ResultSet resultSet, String column) {
        Calendar calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        Timestamp timestamp = resultSet.getTimestamp(column, calendar)
        return timestamp?.toInstant()
    }

    @Override
    void close() {
        if (connection != null) {
            connection.close()
            connection = null
        }
    }
}
