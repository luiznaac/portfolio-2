# Research: report and extract ingestion in portfolio tools

_Resolved for [wayfinder ticket #349](https://github.com/luiznaac/portfolio-2/issues/349) on 2026-11-02. Feeds the prototype tickets of [map #347](https://github.com/luiznaac/portfolio-2/issues/347)._

## Question

How do portfolio-management tools handle document ingestion — broker extracts, advisor reports, statements — from upload through review to applied data?

## Method

Public product docs, help centres, official feature pages and public source were read; no logged-in UIs were inspected. Verification is labelled per product.

## Per-product ingestion (compact)

| Product | Entry point | Review / staging | Errors and resumability | Re-import semantics |
| --- | --- | --- | --- | --- |
| [Kinvo](https://suporte.kinvo.com.br) | Mostly auto-sync (Open Finance / partner data); no public file pipeline detail | Unverified | Unverified | Unverified |
| [Gorila](https://guia.gorila.com.br/hc/pt-br/articles/30707125679771) | B3 partner connection, not upload | Per-account connection card with a status ladder | Status ladder is the attention surface ("Aguardando Operações / Sincronizando / Calculando Carteira / Atualizado em") | One B3 account can feed only one portfolio ("CPF em uso") |
| [Status Invest](https://lp.statusinvest.com.br/ao/integracao-b3/) | Daily B3 auto-sync across brokers | None needed for listed equities | Partial coverage: listed only, fixed income pending, pre-2019 manual | Auto-feed plus a manual fallback lane |
| [Sharesight](https://help.sharesight.com/import_bulk_trades/) | "Add investment" → upload via spreadsheet or "any file format" | Column-to-field matching, then itemised trade list; nothing commits until final Import | "Rejected Trades" tab with inline fixes; 2000-trade cap; whole-file commit, no mid-file resume | Per-trade identifier dedup only on broker-connected imports; no file hash |
| [Kubera](https://help.kubera.com/article/142-ai-import-files-and-screenshots) | Per-asset "File Import" plus ambient drag-and-drop/paste | AI extraction, then review and edit before adding | Auto-mapping can bind the wrong asset; help article covers correcting it and remembering the choice | Snapshot overwrite/upsert by name/ticker; refuses connected accounts |
| [Ghostfolio](https://github.com/ghostfolio/ghostfolio) | Portfolio → Activities, JSON import API | UI preview exists since v1.221.0 (third-party tool observed) | Whole-batch 400 on a duplicate; no partial acceptance | Duplicate rejection inside the batch; no file-hash identity |
| [Portfolio Performance](https://portfolio-performance.info/en/) | Explicit add-transaction / statement importer | Add-transaction workflow | CSV plus PDF/XML statement support | Ledger append |

## Cross-cutting patterns

- **Two ingestion tracks are normal**: an auto-sync/connected track plus a manual file track, because the feed never covers everything (pre-2019, fixed income, changed brokers).
- **A human review gate before commit is universal** — column matching, an itemised list, extraction review or a preview — but always a single gate, never a resumable multi-stage workflow.
- **Row-level errors with inline fixes beat whole-file rejection** for statement-like files. Commit is still atomic; no peer retains a partial batch.
- **Re-import semantics split by data kind**: state-like data overwrites in place (Kubera values), event-like data appends and relies on identifier dedup (Sharesight trades, Ghostfolio).
- **"Needs attention" lives on the source/connection object**, not per row (Gorila's status ladder) — a durable per-source status view.
- **Coverage limits are stated explicitly** ("does not modify connected accounts", "history won't be on the new broker download"), and AI extraction is framed as possibly wrong and always reviewable.

## Adopt

- **Contextual upload from a Strategy plus one global inbox sharing the same upload record** — the two entry points converge on one staging step, not two systems.
- **Forced field mapping plus a full itemised preview before commit** for B3 trades/positions/income and fixed-income statements; deterministic template plus human verify over blind parse.
- **Row-level errors with inline correction and an "N rejected" count** for B3 files. The current shared error body is file-level only, so per-row containers are needed before this can be shown.
- **Stage-as-snapshot, diff against the previous Edition, preview the diff, then promote to active** for the XP monthly report → Strategy Edition. No studied peer versions a report this way; treat it as ours, modelled on the review-before-import gate.
- **File hash + source label + retained original** on every applied row for provenance and idempotent re-upload; combine duplicate rejection with snapshot update-in-place by data kind.
- **A per-source status surface** for pending/parked imports ("Awaiting… / Calculating… / Needs review") instead of burying them in an inbox.
- **Partial acceptance for large statement files**: commit clean rows and keep rejected rows as a retry batch — a deliberate improvement over the all-or-nothing imports every studied peer ships.

## Avoid / caution

- **AI extraction as the primary path** for structured B3/XP formats: peers hide it behind toggles and warn it is error-prone. Use deterministic parsers, with AI only as a flagged fallback.
- **All-or-nothing import on statement-scale files** without a park/staging state: one bad row should not block or orphan the rest.
- **Auto-mapping that can bind to the wrong object** without preview, correction and a remembered choice.
- **Snapshot-overwrite semantics for event-like data**: keep update-in-place for positions/report state and append+dedup for trades.
- **Expecting B3 feeds to cover fixed income**: they cover listed equities only and reach back roughly to 2019, which is exactly why fixed-income statements need their own ingest track.
- **Inheriting the one-source-one-portfolio lock** (Gorila "CPF em uso"): model source existence separately from where its data is applied.

## Verification limits

- Kinvo's file pipeline and XP's ingestion details could not be verified from public sources and were skipped rather than guessed.
- Ghostfolio's preview was observed through a third-party export tool, not its own UI; its backend duplicate behaviour is from public source.
- Gorila, Status Invest and Sharesight were read from help/marketing pages, not live accounts.

## Sources

- <https://suporte.kinvo.com.br>
- <https://guia.gorila.com.br/hc/pt-br/articles/30707125679771>
- <https://lp.statusinvest.com.br/ao/integracao-b3/>
- <https://www.b3.com.br/pt_br/produtos-e-servicos/central-depositaria/canal-com-investidores/area-do-investidor/>
- <https://help.sharesight.com/import_bulk_trades/>
- <https://help.sharesight.com/ai-importer/>
- <https://help.sharesight.com/common-errors-when-bulk-importing-trades/>
- <https://help.kubera.com/article/142-ai-import-files-and-screenshots>
- <https://help.kubera.com/article/156-ai-import-updating-the-wrong-asset>
- <https://github.com/ghostfolio/ghostfolio>
- <https://github.com/dickwolff/Export-To-Ghostfolio>
- <https://portfolio-performance.info/en/>
