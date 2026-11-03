# Research: object-first IA and multi-strategy views in portfolio tools

_Resolved for [wayfinder ticket #348](https://github.com/luiznaac/portfolio-2/issues/348) on 2026-11-02. Feeds the prototype tickets of [map #347](https://github.com/luiznaac/portfolio-2/issues/347)._

## Question

How do portfolio-management tools organise their information architecture, and how do they surface multiple strategies / model portfolios within one account?

## Method

Public product docs, help centres, official feature pages and public source (Ghostfolio) were read; no logged-in UIs were inspected. Verification is uneven and labelled below.

## Per-product IA (compact)

| Product | Landing | Top-level objects | Multi-portfolio / strategy treatment | Performance, income, tax |
| --- | --- | --- | --- | --- |
| [Sharesight](https://www.sharesight.com/features/) | Portfolio overview: line chart, diversity card, period compare | Portfolios, holdings | Multiple portfolios as countable containers (1/4/10 per plan); custom groups are filter lenses, not objects | Separate Performance, Tax and Dividend reports, each with its own period |
| [Kubera](https://www.kubera.com/) | Balance sheet / net worth | Nested portfolios, assets, ownership | Nested portfolios as independent linked objects, each with its own view and flows | Reporting layered on the balance sheet; period and comparison tools |
| [Ghostfolio](https://github.com/ghostfolio/ghostfolio) | Portfolio with page tabs | Accounts, activities, allocations, FIRE, X-ray | One trackable portfolio per user; accounts are first-class; no per-strategy split | Analysis tab; fixed quick windows (Today/WTD/MTD/YTD/1Y/5Y/Max) |
| [Portfolio Visualizer](https://www.portfoliovisualizer.com/) | Analysis tools | Hypothetical portfolios / models | Named model portfolios are browseable objects, but hypothetical — no continuous tracking | Backtest/analysis only |
| [Kinvo](https://www.kinvo.com.br/) | Market/news hub, then consolidated app | Content verticals, portfolio, watchlists | Recommended portfolios / watchlists, not owned strategies | Consolidation and tracking, advisor-style suggestions |
| [Gorila](https://gorila.com.br/) | Consolidation for advisors | Multi-custody portfolios, reporting | Reporting/API middleware; no consumer IA detail surfaced | Real-time performance and risk reporting |
| [Status Invest](https://statusinvest.com.br/) | Instrument search / screener | Instrument classes, client dashboard | Portfolio viewer second-class; research-first | Dashboard plus instrument screens |
| [Wealthfront](https://www.wealthfront.com/investing) (counterexample) | Managed account | One managed portfolio | Single managed portfolio from a questionnaire; no multi-strategy browse | Automated rebalancing and tax-loss harvesting embedded |

## Cross-cutting patterns

- **Portfolio first, analysis as a destination.** Nearly every tool centres a live portfolio/overview and reaches reports from it; analysis is not the home.
- **Performance, income and tax are usually separate destinations**, with period selection local to each report rather than one shared analysis blob.
- **Multiple portfolios are containers, not lenses.** Where real portfolio models exist, they are countable containers (Sharesight) or nested/linked objects (Kubera) — not slices of one portfolio.
- **Strategies/model portfolios are rare as first-class tracked objects.** They appear as advisory products (Kinvo/Gorila), hypothetical backtests (Portfolio Visualizer), or hidden inside a managed account (Wealthfront). None of the studied consumer tools lets you browse several owned strategies with their own reports and attribution.
- **Planning is either automated or a report goal**, never a choreographed monthly workflow. Robo/advisory tools rebalance in the background; tracking tools offer a "rebalance to target" report.
- **"What needs attention" is algorithmic in monitored products** (variance, harvest signals, connection status). Tracking-first tools default to steady-state reporting instead.
- **Tabbed object pages recur.** Ghostfolio's portfolio page tabs (Analysis / Activities / Allocations / FIRE / X-ray) are a close precedent for an object-first shell.

## Adopt

- **Portfolio as the landing, with a persistent attention strip** for derived states (drift, data quality, pending imports, plan in progress) rather than market-noise alerts.
- **Strategies as first-class objects with their own tabbed detail** (holdings, editions/report, attribution, performance), and portfolio-wide lenses to slice by strategy/class. This is close to Kubera's independent linked-object hierarchy minus the multi-user access matrix.
- **A fixed period rail plus custom range** (Ghostfolio's Today/WTD/MTD/YTD/1Y/5Y/Max is a good starting set) shared by the result views; it matches a monthly cadence without making the month the spine.
- **Separate result destinations** for performance, income and taxes, with a shared period control, rather than one undifferentiated report.
- **Planning as a graduated tool**: a plan-in-progress state that the portfolio/landing can surface and resume, not the app's organising idea.

## Avoid / caution

- **Advisory watchlists as the IA centre** (Kinvo/Gorila): they make the product a consumption surface for other people's picks, not an owned object-first workspace.
- **Tool-soup analysis navigation** (Portfolio Visualizer): a lab/backtest section is not the primary nav; at most adopt the browseable-model idea.
- **Silently merging strategies into one managed portfolio** (Wealthfront): it hides strategy identity, the inverse of object-first.
- **Copying the multi-user access/ownership matrix** from Kubera: the useful part for a single power user is the independent-object hierarchy, not sharing permissions.
- **Assuming a monthly planning ribbon has a predecessor to imitate.** None of the studied tools exposes one; the design is owned territory.

## Verification limits

- Ghostfolio IA was read from public source/README, not a running instance; Sharesight from product and help pages, not a logged-in account.
- Kinvo, Gorila and Status Invest were read from public marketing/help pages; their logged-in consolidated IA could not be verified.
- Wealthfront, Kubera and Portfolio Visualizer were read from official feature/help pages, not live dashboard screenshots.

## Sources

- <https://www.sharesight.com/features/>
- <https://www.sharesight.com/investment-portfolio-performance/>
- <https://www.sharesight.com/investment-portfolio-tax/>
- <https://help.sharesight.com/>
- <https://www.portfoliovisualizer.com/>
- <https://github.com/ghostfolio/ghostfolio>
- <https://raw.githubusercontent.com/ghostfolio/ghostfolio/main/apps/client/src/app/pages/portfolio/portfolio-page.component.ts>
- <https://www.kubera.com/>
- <https://help.kubera.com/article/122-nested-portfolios>
- <https://www.kinvo.com.br/>
- <https://gorila.com.br/>
- <https://statusinvest.com.br/>
- <https://www.wealthfront.com/investing>
