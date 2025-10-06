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

suspend fun createFloatingBond(value: String, index: String) =
    postRequest<Map<String, String>> {
        path = "/bonds/floating"
        body = mapOf("name" to "pipipipopopo", "value" to value, "index_id" to index)
    }["id"]!!

suspend fun createBondOrder(bondId: String, type: String, date: String, amount: String) =
    postRequest<Unit> {
        path = "/bonds/orders"
        body = mapOf(
            "bond_id" to bondId,
            "type" to type,
            "date" to date,
            "amount" to amount.toBigDecimal(),
        )
    }

suspend fun consolidateBond(bondId: String) =
    postRequest<Unit> {
        path = "/bonds/$bondId/consolidate"
    }

suspend fun bondPositions(bondId: String) =
    getRequest<List<Map<String, Any>>> {
        path = "/bonds/$bondId/positions"
    }
