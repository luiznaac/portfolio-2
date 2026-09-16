---
source: luiznaac/portfolio-2#96
branch: research/kinvo-export
date: 2026-09-14
---

# Research: Kinvo export for initial positions

- **Ticket:** [luiznaac/portfolio-2#96](https://github.com/luiznaac/portfolio-2/issues/96) — "Research: Kinvo export for initial positions"
- **Date:** 2026-09-14
- **Branch:** `research/kinvo-export`
- **Question:** Does Kinvo (kinvo.com.br) offer an export (XLSX/CSV) or API of portfolio positions? If yes, which fields and how do they map to an initial position seed; if no, what is the fallback?

## Answer (short)

- **No general-purpose XLSX/CSV export of current positions is documented, and there is no public API.**
- The **only documented Excel export** is the **"Resumo de Posições para Imposto de Renda"** (IR auxiliary report): **Premium plan, Kinvo Web only**, showing **positions as of 31/12 of a closed base year**, for **renda variável + criptoativos only**, valued at **average acquisition cost**, plus received proventos. The article explicitly says the report can be "exportar também para o formato excel".
- Kinvo's plans page also advertises **"Relatórios em PDF e Excel"** as a Premium feature, but **no public article documents what those reports contain** or that they export the current portfolio. This must be confirmed inside the account.
- **Seed implication:** Kinvo is a poor primary seed source. Its export (if usable) only seeds **ações/FIIs as of a past 31/12** (quantity + average price), never renda fixa/fundos/caixa and never "today". The realistic seed path remains **B3 Área do Investidor extract (ações/FIIs) + manual entry (RF/caixa)**; Kinvo can serve as a cross-check/reference.

## Evidence

### 1. Plan feature list (marketing site)

Source: <https://consolidador.kinvo.com.br/planos/> (fetched 2026-09-14).

Premium feature comparison rows include:

- "Resumo de Posições para Imposto de Renda"
- "Relatórios em PDF e Excel"

Free plan rows include "Lista de ativos", "Detalhamento dos ativos", "Extrato", "Conexões" — but **no export/report row**. So any report/export capability is Premium.

### 2. The IR auxiliary report — the only documented Excel export

Source: "Relatório auxiliar de Declaração de I.R no Kinvo: Como solicitar?" — <https://kinvo.freshdesk.com/support/solutions/articles/44002422013> (legacy Kinvo help desk, article updated 2023-08-25; fetched 2026-09-14).

Claims in the article:

- "O processo é feito apenas via **Kinvo Web**".
- "escolhe o ano-base e trazemos o resumo de todas as suas posições [...] na **data de 31/12**" for **renda variável e criptomoedas**.
- Delivers a consolidated list of received proventos too.
- "Você pode **exportar também para o formato excel**" (verbatim) — format not further specified (no XLSX/CSV distinction, no column list).
- For "fundos de investimentos, previdência privada, Tesouro Direto e renda fixa, a própria instituição financeira disponibiliza o informe de rendimentos" — i.e. these classes are **not** in the Kinvo report.

Source: "FAQ: Relatório Auxiliar da Declaração de I.R no Kinvo" — <https://kinvo.freshdesk.com/support/solutions/articles/44002422820> (fetched 2026-09-14).

Additional constraints stated:

- Premium only ("Assinantes Kinvo Premium podem encontrar a funcionalidade de IR no menu esquerdo da lateral do Kinvo Web"; "não está disponível no App").
- "posição dos ativos no dia 31/12 do ano de referência, calculada pelo **custo médio de aquisição**".
- Output is organized "com o padrão do Programa Gerador de Declaração" (copy/paste into the Receita Federal program) — i.e. it is an IR declaration layout, not a generic position table.
- At the time, selectable years were 2017–2022; only **liquidated** subscriptions included; **lent (alugadas) assets not supported**; renda fixa/fundos/previdência excluded.

### 3. Current (v4 / Open Finance) help center has no export article

Source: <https://suporte.kinvo.com.br/solutions> and <https://suporte.kinvo.com.br/open-finance/solutions> (fetched 2026-09-14).

The knowledge base categories (Carteira, Conexões, Conta, Premium, Rentabilidade, Kinvo Trade, Análises, etc.) contain **no article about exporting positions or generating XLSX/CSV files**. Relevant articles describe display-only features:

- "Visualizando e Personalizando Seus Ativos na Carteira" (<https://suporte.kinvo.com.br/open-finance/articles/visualizando-e-personalizando-seus-ativos-na-carteira>): grouping, filters, >20 indicators — no export button mentioned.
- "Detalhes do Ativo" (<https://suporte.kinvo.com.br/open-finance/articles/detalhes-do-ativo>): UI fields per asset are **Quantidade, Última cotação, Preço médio, Rentabilidade sobre o preço médio, Ganho de capital, Rentabilidade total, Saldo bruto**.
- "Resumo da Carteira" (<https://suporte.kinvo.com.br/open-finance/articles/resumo-da-carteira>): patrimônio, distribuição, histórico, proventos — no export.

A full-text scan of the help center's bundled article data (Next.js chunk `/_next/static/chunks/353-7a5e988efb5dbd42.js`) found the string "Exportar em excel" only inside the **discontinued Kinvo2B (B2B) article** (<https://kinvo.freshdesk.com/support/solutions/articles/44002542113>), which is not the consumer product. Searches for `exportar`, `xlsx`, `csv`, `planilha` in the legacy help desk returned only the IR article and the 2B discontinuation.

### 4. No public API

- No developer portal, API docs, or partner API is published on kinvo.com.br / suporte.kinvo.com.br (checked 2026-09-14).
- GitHub search (<https://api.github.com/search/repositories?q=kinvo>, fetched 2026-09-14) shows **no official Kinvo API client**; the official `kinvoapp` org only hosts hiring tests. Community hits are unrelated challenges; `fmilani/kinvo-api` (2021) is a stub whose README is just the title.
- The app is a web SPA backed by private endpoints (app.kinvo.com.br), but nothing is documented or licensed for third-party access.

## Field mapping to an initial position seed

If the user's account still exposes the IR Excel export, the **only seedable classes** are ações/FIIs (and ETFs/BDRs/crypto), and only as of 31/12 of the chosen year:

| Kinvo (IR export / UI) | Seed field | Notes |
|---|---|---|
| Ativo / ticker | asset identifier | Class inferred from ticker suffix (3/4/5/6 = ação, 11 = FII/ETF) |
| Quantidade | position quantity | Exact as of 31/12 |
| Preço médio / custo médio de aquisição | average cost | Currency value per unit |
| (derived) quantidade × preço médio | invested amount | |
| Renda fixa, fundos, previdência, caixa | — | **Not exported by Kinvo**; comes from the institution's informe or manual entry |

**Not verified** (public docs do not state, and the acting model could not read the article screenshots): the exact file format (XLS/XLSX/CSV), the exact column headers, whether CNPJ/institution are included, and whether the current year is selectable. See the checklist below.

## Fallbacks (if no usable export exists)

1. **Manual entry** from the Kinvo app/web Ativos screen — the UI exposes exactly the fields a seed needs (quantidade, preço médio, saldo bruto), one asset at a time.
2. **B3 Área do Investidor extract** for ações/FIIs — structured and current; already covered by sibling research ticket [#94](https://github.com/luiznaac/portfolio-2/issues/94). This is the better structured source for listed assets.
3. **Print/PDF of screens** — possible via browser print, but not a structured export and not documented by Kinvo.
4. **Akeloo integration** (Kinvo partner) — reads Kinvo data for IR/DARF calculation; it is not a file export of positions and flows Kinvo → Akeloo, not out to the user.
5. Renda fixa/caixa: institution informes or manual entry, matching the plan's assumption that ongoing balances stay manual.

## What the user must check in the account (exact checklist)

Because public docs are inconclusive about a general export, verify in the logged-in account:

1. **Kinvo Web → Carteira → Ativos**: open the screen's Configurações/menu and look for an "Exportar" / download action (compare Free vs Premium if applicable).
2. **Imposto de Renda feature (Premium, Kinvo Web)**: check which base years are selectable (is the current year there?), generate the report, click the Excel export, and record: file extension, column headers, date basis, which classes appear, whether quantity + average price + institution/CNPJ are present.
3. **Relatórios area (Premium)**: identify what "Relatórios em PDF e Excel" from the plans page actually is, and whether any of them exports **current** positions (not just 31/12).
4. **App (iOS/Android)**: check for share/export on the Ativos screen.
5. Report back: format, columns, date basis, covered classes — this decides whether Kinvo can seed anything or is reference-only.

## Open uncertainty

- The plans-page "Relatórios em PDF e Excel" row is undocumented; it may or may not include a current-positions report.
- The IR Excel export's exact schema and format were not verifiable from public docs (screenshots only).
- The IR docs date from 2023 and predate the v4/Open Finance help center; the feature may have moved or changed.
- The acting model has no vision, so the article screenshots could not be inspected; the checklist above exists precisely to close this gap in-account.
