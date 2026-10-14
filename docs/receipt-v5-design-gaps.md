# Receipt v5 design gaps

This ledger tracks UI states absent from the locked Receipt v5 screen set. It is a follow-up list for dedicated Claude designs, not permission to invent or restyle them. Update it as implementation uncovers further gaps.

| Screen or state | Current evidence | Decision / implementation status | Needed from Claude |
|---|---|---|---|
| Open Food Facts contribution review | Existing `ContributionReviewDialog`; no Receipt v5 mock | Owner chose to wait for dedicated design. Keep the existing UI and behavior for now. | Review screen showing exactly which product facts will be submitted, create versus correction context, and confirm/cancel actions. |
| Open Food Facts sign-in | Existing `ContributionAuthDialog`; no Receipt v5 mock | Owner chose to wait for dedicated design. Keep the existing UI and behavior for now. | Credential form and return to contribution review. |
| Open Food Facts submission failure | Existing `ContributionFailureDialog`; no Receipt v5 mock | Owner chose to wait for dedicated design. Keep the existing UI and behavior for now. | Failure, retry, and return flow without disturbing local saved food or diary entries. |
| Archived foods view | Current Library has an Archived filter; no Receipt v5 archive screen | Owner chose an **Archived foods** item in the Library top-bar three-dot menu. The destination layout has no dedicated mock. | Archived-food list and return route, showing that past diary entries remain readable while archived foods cannot be newly logged. |
| OFF search loading | Existing search flow; no Receipt v5 loading mock | Owner chose to wait for dedicated design. Keep the existing UI and behavior for now. | Search-in-progress state and placement of the loading affordance. |
| Barcode lookup loading | Existing scanner/lookup flow; no Receipt v5 loading mock | Owner chose to wait for dedicated design. Keep the existing UI and behavior for now. | Lookup-in-progress state over or after the camera preview. |
| Light-theme variants | PRODUCT.md calls for light and dark; Receipt v5 draws dark only | Owner directed implementation of the theme shown in Receipt v5, so the current pass is dark-only. | A complete light counterpart to the locked screen set, if light mode is still wanted later. |

## Locked reference set

See [the implementation handoff](codex-handoff-receipt-v5.md) for the 21 named locked screens. The contribution screens and loading states are explicitly listed there as undesigned; the archive view follows the owner's later menu decision.
