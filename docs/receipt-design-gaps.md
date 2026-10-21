# Receipt v6.5 design gaps

This ledger tracks the former v5 gaps against the locked Receipt v6.5 light and dark references. A row is still open only when the drawing cannot be reproduced honestly from the current data or when the owner device check remains outstanding.

| State | Locked reference(s) | Status / concrete gap |
| --- | --- | --- |
| Contribution review | `R6-OFF-Contribute-Review.dc.html`, `R6D-OFF-Contribute-Review.dc.html` | Done in the contribution slice. |
| Open Food Facts sign in | `R6-OFF-SignIn.dc.html`, `R6D-OFF-SignIn.dc.html` | Done in the contribution slice. |
| Open Food Facts submission failure | `R6-OFF-Submit-Failed.dc.html`, `R6D-OFF-Submit-Failed.dc.html` | Done in the contribution slice. |
| Archived library list and menu | `R6-Library-Archived.dc.html`, `R6-Library-Menu.dc.html`, dark twins | Done. |
| Archived food detail and restore | `R6-Food-Archived.dc.html`, `R6D-Food-Archived.dc.html` | Done. RESTORE clears `archived` and keeps diary snapshots. Owner decision: omit archive dates entirely, including for future archives. |
| Open Food Facts loading and error | `R6-Search-OFF-Loading.dc.html`, `R6-Search-OFF-Error.dc.html`, dark twins | Done. |
| Barcode lookup, manual entry, permission denied, no camera | `R6-Scan-Lookup.dc.html`, `R6-Scan-Manual.dc.html`, `R6-Scan-Denied.dc.html`, `R6-Scan-NoCamera.dc.html`, dark twins | Done. Camera ground is fixed and camera marks stay light in both themes. |
| Add serving inline form | `R6-Food-Edit-AddServing.dc.html`, `R6D-Food-Edit-AddServing.dc.html` | Done with named servings, g/ml choices, validation, and the Room 5→6 migration. The final dark build showed the full ADD SERVING control at 1.1 without saving an edit. |
| Scan review details, Library, and queue variants | `R6-Scan-Review-Details.dc.html`, `R6-Scan-Review-Library.dc.html`, `R6-Scan-Review-Queue.dc.html`, dark twins | Done. |
| Resolve re-search | `R6-Resolve-Search.dc.html`, `R6D-Resolve-Search.dc.html` | Done. |
| Offline personal-food creation | `R6-Search-OFF-Offline.dc.html`, `R6D-Search-OFF-Offline.dc.html` | Done. |
| Create food full form | `R6-Create-Food-FullForm.dc.html`, `R6D-Create-Food-FullForm.dc.html` | Done. Shorthand fills the fields and explicit full-form edits win. |
| Large font scale | `R6-Daily-Font130.dc.html`, `R6-Daily-Font200.dc.html`, `R6D` twins | Verified on the Samsung phone at 1.3 and 2.0 in light, and on Daily at both scales in dark. Meal totals, Add Food buttons, two-line macros, abbreviated day, and stacked tray tabs fit after phone-found fixes. The final build also showed intact Food detail units and Contribution nutrition labels at 2.0. Populated meal times and rows were not available on the checked day. |
| Light and dark twins | Matching `R6-*` and `R6D-*` references | Implemented with system tokens and shared receipt components. Phone walk-through covered dark Daily (today and populated prior day), Meal, Entry detail/Quick Edit/Full Edit, Library (root and meal context), Library menu, archived list/detail, food detail/edit and expanded serving, no-local-match and OFF search/results/product review, Create Food, amount sheet, contribution review/sign-in, scanner denied, manual barcode validation, and both resolver variants. The temporary unresolved Lunch item was skipped and Lunch stayed at 0 kcal. |
| Library amount sheet unit row | `R6-Library-AmountSheet.dc.html`, `R6D-Library-AmountSheet.dc.html` | The dynamic serving/unit choices use a horizontal scrolling row. The mock draws fixed equal-width cells; a food can have a different number of units, so exact spacing for every food remains a visual gap. The phone confirmed the Add action stays above system navigation. |
| Device verification limits | OFF loading/error/offline, contribution submit/failure, scanner no-camera/denied | OFF loading is transient; service failure or offline needs a controlled network failure. Contribution submit/failure would write to the public service. This Samsung has a usable camera, and the last permission-revocation attempt reopened its live preview instead of the denied screen. These screens are implemented but their final-build variants have not all been visually verified on this phone. |
| Camera ground | `R6-Scan.dc.html`, `R6D-Scan.dc.html` | Done: `#141218` ground and light registration marks are centralized. |
| Source box contrast | `R6-Daily.dc.html`, `R6D-Daily.dc.html` and matching rows | Done: the v6.5 source box was removed; B/P/O is soft text. |
| Entry detail contribution action | No R6 reference; contribution remains on Library food detail | Resolved by handoff: intentionally not added; contribution stays on Library food detail. |
| Log page amountless text | `M-Log` has no amountless case | Behaviour kept from v6.5: Add on text with no amounts opens the focused Library for the Log's target meal (its typed or chosen meal), not the Daily search. |

See [the v6.5 handoff](codex-handoff-receipt-v65.md) and [the v6.5 gap brief](../design/canvas/BRIEF-v65-gaps.md) for the complete state inventory and binding control details.
