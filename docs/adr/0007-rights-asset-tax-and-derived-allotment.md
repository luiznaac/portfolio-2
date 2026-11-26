# Rights assets are taxed at 15% and a missing receipt is a replaceable derived allotment

A subscription right is a separate negotiable asset, not the underlying cota and not a quota: the RBRP12 right from RBRP11's 5th issuance is the case that forced the model. We decided a `RIGHT` asset kind and a `RIGHTS` realized kind: a rights sale is taxed at 15% under DARF 6015, joins the monthly bolsa aggregate, and stays outside the R$20k stock meter and the FII 20% bucket. The cost basis is zero when the right was received in the issuance and the purchase price when it was bought. A `RIGHTS_ALLOTMENT` corporate action creates the zero-cost rights position; a rights sale with no receipt derives a `DERIVED` allotment of the sold quantity, which a real allotment replaces. Rights stay in the ledger and tax, and out of the allocation tree.

**Considered options**: report the sale without tax until the IN 1585 wording is text-verified (hides a real 15% gain); treat it as a FII at 20% (the 20% applies to quotas, not rights); book a cash movement on the underlying (contradicts the separate asset); let the sale open a negative position (a silent ledger error).

**Consequences**: the tax report gains a fourth realized row; a rights asset has no Asset Class and never appears in allocation; a derived allotment must be visibly marked and replaced rather than added to, so the position cannot double.

Spec: [issue #448](https://github.com/luiznaac/portfolio-2/issues/448).
