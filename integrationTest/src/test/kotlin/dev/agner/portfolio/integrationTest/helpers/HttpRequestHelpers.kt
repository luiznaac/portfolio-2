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
