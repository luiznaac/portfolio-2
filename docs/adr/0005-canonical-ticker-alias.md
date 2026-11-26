# The listed-asset book resolves through a canonical ticker alias

B3 renames and class conversions let one asset appear under more than one code. The existing `TICKER_CHANGE` only remapped the catalog, so a buy under the old code and a sell under the new code became two books. We decided a separate ticker alias table stores each old→canonical edge with its effective date, source and optional conversion ratio, and every listed-asset read — position, trade ledger, tax, performance, allocation, orders, gaps, income, quotes and corporate actions — resolves through the alias chain to the canonical ticker. A rename that carries a ratio is one atomic `TICKER_CHANGE`; a rename and a split on the same date apply rename first, then the quantity event.

**Considered options**: rewrite the ledger rows to the new ticker (destructive, loses provenance); resolve as-of the effective date (faithful, but every read becomes date-aware and the book still splits); keep the catalog-only remap (the current behaviour, which leaves the two books).

**Consequences**: the stored trade keeps its original code while every read shows the canonical one; the old and new quote series must be stitched under the canonical ticker; the alias chain must reject cycles; a class conversion is trusted from brapi's `renamed` result and merged into one book.

Spec: [issue #448](https://github.com/luiznaac/portfolio-2/issues/448).
