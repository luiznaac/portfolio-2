package dev.agner.portfolio.persistence.configuration

/**
 * Shared widths for this module's Exposed column definitions.
 *
 * Each value is the schema: it must keep matching the DECIMAL/VARCHAR declaration in the
 * corresponding `V*.sql` migration, which `MigrationSchemaTest` diffs against a live database.
 * Constants are named after the data their columns hold rather than after the number, because the
 * same number is a different width depending on the column (12 is a ticker length, a price
 * precision and an index-value precision).
 */
object ColumnSizes {
    /** Enum names and other short text discriminators stored verbatim in the column. */
    const val ENUM_NAME_LENGTH = 20

    /** Broker rating text from a model-portfolio report. */
    const val RATING_LENGTH = 20

    /** ISO-8601 `DatePeriod` text, e.g. "P1Y2M3D". */
    const val MATURITY_DURATION_LENGTH = 5

    /** Brazilian rate index ids, e.g. "CDI". */
    const val INDEX_ID_LENGTH = 10

    /** Bond rate types, e.g. "FIXED". */
    const val RATE_TYPE_LENGTH = 10

    /** Listed-asset kinds, e.g. "STOCK". */
    const val ASSET_KIND_LENGTH = 10

    /** B3 ticker symbols, e.g. "PETR4". */
    const val TICKER_LENGTH = 12

    /** Bond, checking-account and strategy names. */
    const val NAME_LENGTH = 100

    /** B3's trading name for a listed asset. */
    const val B3_IDENTIFIER_LENGTH = 100

    /** Listed-asset and ticker-catalog display names. */
    const val LISTED_ASSET_NAME_LENGTH = 150

    /** Free-text movement notes. */
    const val NOTE_LENGTH = 255

    /** Allocation weights, stored as a fraction (0.05 is 5%). */
    const val WEIGHT_PRECISION = 7
    const val WEIGHT_SCALE = 4

    /** Bond and checking-account rates, stored as a percentage of an index (120 is 120%). */
    const val RATE_PRECISION = 8
    const val RATE_SCALE = 4

    /** Transactional monetary amounts: bond orders, statements, transfer threshold. */
    const val MONEY_PRECISION = 12
    const val MONEY_SCALE = 2

    /** Accumulated position and capital balances: principal, yield, taxes, external balance. */
    const val BALANCE_PRECISION = 14
    const val BALANCE_SCALE = 2

    /** Share/unit quantities, at the 8 decimals B3's fractional market needs. */
    const val QUANTITY_PRECISION = 18
    const val QUANTITY_SCALE = 8

    /** Corporate-action ratios (shares per share). */
    const val RATIO_PRECISION = 18
    const val RATIO_SCALE = 8

    /** Unit prices and per-share monetary values. */
    const val PRICE_PRECISION = 12
    const val PRICE_SCALE = 4

    /** Accumulated index factors (CDI/SELIC). */
    const val INDEX_VALUE_PRECISION = 12
    const val INDEX_VALUE_SCALE = 8
}
