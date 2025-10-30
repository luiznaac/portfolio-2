package dev.agner.portfolio.integrationTest.helpers

import dev.agner.portfolio.integrationTest.config.getRequest
import dev.agner.portfolio.integrationTest.config.postRequest

suspend fun getIndexes() =
    getRequest<List<Map<String, String>>> {
        path = "/indexes"
    }

suspend fun hydrateIndexValues(index: String) =
    postRequest<Map<String, String>> {
        path = "/indexes/$index/values/hydrate"
    }

suspend fun getIndexValues(index: String) =
    getRequest<List<Map<String, String>>> {
        path = "/indexes/$index/values"
    }

suspend fun createFloatingBond(value: String, index: String, maturityDate: String = "2025-10-20") =
    postRequest<Map<String, String>> {
        path = "/bonds/floating"
        body = mapOf(
            "name" to "pipipipopopo",
            "value" to value,
            "index_id" to index,
            "maturity_date" to maturityDate,
        )
    }["id"]!!

suspend fun createBondOrder(bondId: String, type: String, date: String, amount: String? = null) =
    postRequest<Unit> {
        path = "/bonds/orders"
        body = mapOf(
            "bond_id" to bondId,
            "type" to type,
            "date" to date,
            "amount" to amount?.toBigDecimal(),
        )
    }

suspend fun consolidateBond(bondId: String) =
    postRequest<Unit> {
        path = "/bonds/$bondId/consolidate"
    }

suspend fun scheduleConsolidations() =
    postRequest<Map<String, List<Int>>> {
        path = "/consolidations/schedule"
    }.also {
        it.onEach { (type, ids) ->
            ids.forEach { id ->
                consolidateProduct(type, id)
            }
        }
    }

suspend fun consolidateProduct(productType: String, productId: Int) {
    postRequest<Unit> {
        path = "/consolidations/$productType/$productId"
    }
}

suspend fun bondPositions(bondId: String) =
    getRequest<List<Map<String, Any>>> {
        path = "/bonds/$bondId/positions"
    }

suspend fun createCheckingAccount(value: String, index: String, maturityDuration: String) =
    postRequest<Map<String, String>> {
        path = "/checking-accounts"
        body = mapOf(
            "name" to "pipipipopopo",
            "value" to value,
            "index_id" to index,
            "maturity_duration" to maturityDuration,
        )
    }["id"]!!

suspend fun createDeposit(checkingAccountId: String, date: String, amount: String) =
    postRequest<Unit> {
        path = "/checking-accounts/$checkingAccountId/deposit"
        body = mapOf(
            "date" to date,
            "amount" to amount.toBigDecimal(),
        )
    }

suspend fun createWithdrawal(checkingAccountId: String, date: String, amount: String) =
    postRequest<Unit> {
        path = "/checking-accounts/$checkingAccountId/withdraw"
        body = mapOf(
            "date" to date,
            "amount" to amount.toBigDecimal(),
        )
    }

suspend fun createFullWithdrawal(checkingAccountId: String, date: String) =
    postRequest<Unit> {
        path = "/checking-accounts/$checkingAccountId/full-withdraw"
        body = mapOf(
            "date" to date,
        )
    }

suspend fun consolidateCheckingAccount(checkingAccountId: String) =
    postRequest<Unit> {
        path = "/checking-accounts/$checkingAccountId/consolidate"
    }

suspend fun checkingAccountPositions(checkingAccountId: String) =
    getRequest<List<Map<String, Any>>> {
        path = "/checking-accounts/$checkingAccountId/positions"
    }
