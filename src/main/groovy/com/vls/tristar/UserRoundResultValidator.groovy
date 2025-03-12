package com.vls.tristar

import com.vls.tristar.model.Bet
import com.vls.tristar.model.Round
import com.vls.tristar.model.Winning
import groovy.transform.ToString


class UserRoundResultValidator {
    private final UserRoundResultData userRoundResultData

    UserRoundResultValidator(UserRoundResultData userRoundResultData) {
        this.userRoundResultData = userRoundResultData
    }

    boolean isCalulationRight() {
        List<Bet> settledBets = userRoundResultData.placedBets.findAll {
            it.status == 'SETTLED'
        }

        BigDecimal totalSettledBetAmountF = settledBets.sum { it.amountF }

        def winBets = settledBets.findAll { it.outcome == 'WIN' }
        BigDecimal totalWinAmountF = 0

        winBets.each {
            totalWinAmountF += (it.amountF * it.price)
        }

        BigDecimal totalPnlF = totalWinAmountF - totalSettledBetAmountF

        boolean result = userRoundResultData.userBalanceBefore + totalPnlF == userRoundResultData.userBalanceAfter
                && userRoundResultData.userWinning.winAmountF == totalPnlF

        if (!result) {
            println "Smth go wrong"
        }

        return result
    }
}

@ToString
class UserRoundResultData {
    String userId
    String userName
    Round round
    List<Bet> placedBets;
    Winning userWinning;
    BigDecimal userBalanceBefore
    BigDecimal userBalanceAfter
}
