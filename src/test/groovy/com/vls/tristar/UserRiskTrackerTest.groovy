package com.vls.tristar

import com.vls.tristar.model.Bet
import com.vls.tristar.model.Winning
import org.junit.jupiter.api.Test

import java.time.Instant
import java.time.LocalDate

import static org.junit.jupiter.api.Assertions.assertEquals
import static org.junit.jupiter.api.Assertions.assertFalse
import static org.junit.jupiter.api.Assertions.assertTrue

class UserRiskTrackerTest {

    @Test
    void acceptedStakeIsTurnoverUntilTheRoundSettles() {
        UserRiskStats stats = new UserRiskStats(7L, "user")
        stats.onBet(bet("b1", "c1", "r1", "ACCEPTED", "50"))
        stats.onBet(bet("b1", "c1", "r1", "ACCEPTED", "50"))

        RiskSnapshot open = stats.snapshot()
        assertMoney("50", open.turnover)
        assertMoney("0", open.winning)
        assertMoney("50", open.pending)
        assertMoney("-50", open.pnl)
        assertEquals(1, open.acceptedBets)

        stats.onWinning(winning("r1", Winning.Status.LOST, "-50"))

        RiskSnapshot settled = stats.snapshot()
        assertMoney("50", settled.turnover)
        assertMoney("0", settled.winning)
        assertMoney("0", settled.pending)
        assertMoney("-50", settled.pnl)
    }

    @Test
    void winCreditsStakePlusOutcome() {
        UserRiskStats stats = new UserRiskStats(7L, "user")
        stats.onBet(bet("b1", "c1", "r1", "ACCEPTED", "50"))
        stats.onWinning(winning("r1", Winning.Status.WON, "49"))

        RiskSnapshot snap = stats.snapshot()
        assertMoney("50", snap.turnover)
        assertMoney("99", snap.winning)
        assertMoney("49", snap.pnl)
    }

    @Test
    void severalBetsInOneRoundShareOneOutcome() {
        UserRiskStats stats = new UserRiskStats(7L, "user")
        stats.onBet(bet("b1", "c1", "r1", "ACCEPTED", "10"))
        stats.onBet(bet("b2", "c2", "r1", "ACCEPTED", "20"))
        stats.onWinning(winning("r1", Winning.Status.WON, "-5"))

        RiskSnapshot snap = stats.snapshot()
        assertMoney("30", snap.turnover)
        assertMoney("25", snap.winning)
        assertMoney("-5", snap.pnl)
        assertEquals(2, snap.acceptedBets)
    }

    @Test
    void cancelledBetAndCancelledRoundLeaveTurnover() {
        UserRiskStats stats = new UserRiskStats(7L, "user")
        stats.onBet(bet("b1", "c1", "r1", "ACCEPTED", "40"))
        stats.onBet(bet("b1", "c1", "r1", "CANCELLED", "40"))
        stats.onBet(bet("b2", "c2", "r2", "ACCEPTED", "15"))
        stats.onWinning(winning("r2", Winning.Status.CANCELLED, "0"))

        RiskSnapshot snap = stats.snapshot()
        assertMoney("0", snap.turnover)
        assertMoney("0", snap.winning)
        assertMoney("0", snap.pnl)
        assertEquals(0, snap.acceptedBets)
    }

    @Test
    void settledUpdateCountsWhenAcceptedWasMissed() {
        UserRiskStats stats = new UserRiskStats(7L, "user")
        stats.onBet(bet(null, "c1", "r1", "PLACED", "10"))
        stats.onBet(bet("b1", "c1", "r1", "SETTLED", "10"))
        stats.onWinning(winning("r1", Winning.Status.LOST, "-10"))

        RiskSnapshot snap = stats.snapshot()
        assertMoney("10", snap.turnover)
        assertMoney("0", snap.winning)
        assertEquals(1, snap.acceptedBets)
    }

    @Test
    void voidReturnsStakeAndPnlStaysZero() {
        UserRiskStats stats = new UserRiskStats(7L, "user")
        stats.onBet(bet("b1", "c1", "r1", "VOIDED", "25"))
        stats.onWinning(winning("r1", Winning.Status.WON, "0"))

        RiskSnapshot snap = stats.snapshot()
        assertMoney("25", snap.turnover)
        assertMoney("25", snap.winning)
        assertMoney("0", snap.pnl)
    }

    @Test
    void stakeThatArrivesAfterWinningStillCounts() {
        UserRiskStats stats = new UserRiskStats(7L, "user")
        stats.onWinning(winning("r1", Winning.Status.LOST, "-50"))
        stats.onBet(bet("b1", "c1", "r1", "ACCEPTED", "50"))

        RiskSnapshot snap = stats.snapshot()
        assertMoney("50", snap.turnover)
        assertMoney("0", snap.winning)
        assertMoney("0", snap.pending)
        assertMoney("-50", snap.pnl)
    }

    @Test
    void duplicateWinningDoesNotChangeTotals() {
        UserRiskStats stats = new UserRiskStats(7L, "user")
        stats.onBet(bet("b1", "c1", "r1", "ACCEPTED", "10"))
        stats.onWinning(winning("r1", Winning.Status.WON, "5"))
        stats.onWinning(winning("r1", Winning.Status.WON, "5"))

        assertMoney("15", stats.snapshot().winning)
    }

    @Test
    void activeWindowKeepsBaselineAndAddsThisRun() {
        Map expected = UserRiskTracker.expectedConsolidator(true, new BigDecimal("100"), new BigDecimal("40"),
                new BigDecimal("50"), new BigDecimal("10"))
        assertMoney("150", expected.totalBets as BigDecimal)
        assertMoney("50", expected.totalWin as BigDecimal)
    }

    @Test
    void expiredWindowIsReplacedByThisRun() {
        Map expected = UserRiskTracker.expectedConsolidator(false, new BigDecimal("100"), new BigDecimal("40"),
                new BigDecimal("50"), new BigDecimal("10"))
        assertMoney("50", expected.totalBets as BigDecimal)
        assertMoney("10", expected.totalWin as BigDecimal)
    }

    @Test
    void noActivityKeepsTheSavedRow() {
        Map expected = UserRiskTracker.expectedConsolidator(false, new BigDecimal("100"), new BigDecimal("40"),
                BigDecimal.ZERO, BigDecimal.ZERO)
        assertMoney("100", expected.totalBets as BigDecimal)
        assertMoney("40", expected.totalWin as BigDecimal)
    }

    @Test
    void moneyComparisonAllowsServiceRounding() {
        assertTrue(UserRiskTracker.moneyClose(new BigDecimal("10.00"), new BigDecimal("10.009")))
        assertFalse(UserRiskTracker.moneyClose(new BigDecimal("10.00"), new BigDecimal("10.02")))
    }

    @Test
    void repeatedMarketBetsAreAddedToEveryConsolidatorSlice() {
        UserRiskStats stats = new UserRiskStats(7L, "user")
        stats.onBet(bet("b1", "c1", "r1", "ACCEPTED", "10", "m1", "103"))
        stats.onBet(bet("b2", "c2", "r1", "ACCEPTED", "20", "m1", "103"))
        stats.noteMarket("m1", "WHO_WIN")
        stats.onBet(bet("b1", "c1", "r1", "SETTLED", "10", "m1", "103", "0", "LOSE"))
        stats.onBet(bet("b2", "c2", "r1", "SETTLED", "20", "m1", "103", "59", "WIN"))
        stats.onWinning(winning("r1", Winning.Status.WON, "29"))

        RiskSnapshot snap = stats.snapshot()
        String day = dayKey()
        assertMoney("30", snap.day[day].bets)
        assertMoney("59", snap.day[day].wins)
        assertMoney("30", snap.gameDay["103|${day}"].bets)
        assertMoney("59", snap.gameDay["103|${day}"].wins)
        assertMoney("30", snap.marketDay["103|WHO_WIN|${day}"].bets)
        assertMoney("59", snap.market["103|WHO_WIN"].wins)
        assertMoney("0", snap.slicePending)
    }

    @Test
    void singleBetAndDifferentMarketsStayOutOfTheDayTables() {
        UserRiskStats stats = new UserRiskStats(7L, "user")
        stats.onBet(bet("b1", "c1", "r1", "ACCEPTED", "10", "m1", "103"))
        stats.onBet(bet("b2", "c2", "r1", "ACCEPTED", "15", "m2", "103"))
        stats.noteMarket("m1", "WHO_WIN")
        stats.noteMarket("m2", "PAIR")
        stats.onBet(bet("b1", "c1", "r1", "SETTLED", "10", "m1", "103", "0", "LOSE"))
        stats.onBet(bet("b2", "c2", "r1", "SETTLED", "15", "m2", "103", "0", "LOSE"))
        stats.onWinning(winning("r1", Winning.Status.LOST, "-25"))

        RiskSnapshot snap = stats.snapshot()
        assertTrue(snap.day.isEmpty())
        assertTrue(snap.market.isEmpty())
        assertMoney("0", snap.slicePending)
    }

    @Test
    void voidedPairReturnsStakeInTheMarketTotal() {
        UserRiskStats stats = new UserRiskStats(7L, "user")
        stats.onBet(bet("b1", "c1", "r1", "VOIDED", "12", "m1", "103"))
        stats.onBet(bet("b2", "c2", "r1", "VOIDED", "8", "m1", "103"))
        stats.noteMarket("m1", "WHO_WIN")
        stats.onWinning(winning("r1", Winning.Status.WON, "0"))

        RiskSnapshot snap = stats.snapshot()
        assertMoney("20", snap.market["103|WHO_WIN"].bets)
        assertMoney("20", snap.market["103|WHO_WIN"].wins)
    }

    @Test
    void sliceDiffAddsTheRunOnTopOfTheBaseline() {
        Map<String, MoneyPair> baseline = ["6|10": new MoneyPair(bets: new BigDecimal("100"), wins: new BigDecimal("40"))]
        Map<String, MoneyPair> delta = ["6|10": new MoneyPair(bets: new BigDecimal("30"), wins: new BigDecimal("10"))]
        Map<String, MoneyPair> actual = ["6|10": new MoneyPair(bets: new BigDecimal("130"), wins: new BigDecimal("50"))]
        assertTrue(UserRiskTracker.diffMoneyMaps(baseline, delta, actual).isEmpty())

        actual["6|10"].bets = new BigDecimal("100")
        assertEquals(1, UserRiskTracker.diffMoneyMaps(baseline, delta, actual).size())
    }

    @Test
    void windowIsActiveOnlyUntilEndTime() {
        UserCurrentConsolidatorRow row = new UserCurrentConsolidatorRow(
                endTime: Instant.now().plusSeconds(60)
        )
        assertTrue(row.windowActive())
        row.endTime = Instant.now().minusSeconds(5)
        assertFalse(row.windowActive())
    }

    private static Bet bet(String id, String clientBetId, String roundId, String status, String amount,
                            String marketId = null, String gameId = null, String winAmount = null, String outcome = null) {
        return new Bet(
                id: id,
                clientBetId: clientBetId,
                roundId: roundId,
                status: status,
                amount: new BigDecimal(amount),
                marketId: marketId,
                gameId: gameId,
                winAmount: winAmount == null ? null : new BigDecimal(winAmount),
                outcome: outcome
        )
    }

    private static String dayKey() {
        LocalDate today = LocalDate.now(UserRiskTracker.zone)
        return "${today.dayOfMonth}|${today.monthValue}"
    }

    private static Winning winning(String roundId, Winning.Status status, String outcome) {
        return new Winning(
                roundId: roundId,
                status: status,
                winAmount: new BigDecimal(outcome),
                userId: 7L
        )
    }

    private static void assertMoney(String expected, BigDecimal actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual), "expected ${expected} but was ${actual}")
    }
}
