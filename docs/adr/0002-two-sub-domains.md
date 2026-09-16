# Fixed Income and Listed Assets are separate sub-domains, composed by a Portfolio layer

Renda Variável (Ações/FIIs) and Renda Fixa have genuinely different calculation models — a trade ledger with quantity and average price versus daily accrual of principal, yield and taxes. Rather than force one Asset/Position abstraction across both, the domain splits into two sub-domains composed by a thin Portfolio planning layer (Capital, targets, deficits, suggested orders); the only shared vocabulary is a small common kernel (Asset Class, Institution, provenance).

**Considered options**: one unified Asset model where RF products also carry quantity/average price (distorts the accrual model); an RV-only model with RF flattened to manual balances (throws the engine away).
