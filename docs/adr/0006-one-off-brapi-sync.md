# brapi is a one-off, persisted sync, not a live dependency

The owner will pay for one brapi Pro month to fetch 2018-onward history, so the app cannot depend on brapi being reachable later. We decided an explicit sync action in Importações · Ações corporativas fetches renames, stock corporate actions and cash income for the ledger's tickers from the earliest trade through today, then persists the parsed events, the raw payload and a sync audit. Natural-key dedup keeps one event per identity; B3 is authoritative when both sources carry it, brapi fills gaps and can enrich a parked event. A missing ex-date parks the event; an incomplete bonificação parks it; brapi's `subscriptions` array is untyped and is not used.

**Considered options**: automatic fetch during every trade import (burns quota unpredictably, couples importing to a paid service); scheduled live calls (contradicts the one-month plan); B3-only (leaves the 2018–2021 proventos and split history incomplete).

**Consequences**: after the paid month the app works entirely from stored data; a stale sync needs a manual rerun; the existing optional brapi token remains the only credential, and a missing token or 403 fails the sync loudly rather than silently skipping it.

Spec: [issue #448](https://github.com/luiznaac/portfolio-2/issues/448).
