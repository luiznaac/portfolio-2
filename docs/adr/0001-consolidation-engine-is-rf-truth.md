# The consolidation engine is the source of truth for Renda Fixa values

The plan said RF balances would be manual (mirroring the Kinvo workflow), but the backend already computes RF positions day by day — principal, yield, taxes — from orders and BACEN index rates. We decided the engine stays the source of truth for every product registered in it; manually entered balances exist only for products outside the engine (unregistered RF products and anything predating go-live).

**Considered options**: manual balances as truth (simpler, but loses computed yield/IR and throws away a working engine); manual balance overriding the engine where both exist (two sources of truth, permanent reconciliation burden).
