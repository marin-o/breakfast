# Receipt v6.5 design gaps

This ledger maps follow-up states to the locked R6 and R6D references. A row is a gap only when the current slice cannot build the state as drawn; behavior remains unchanged until its owning slice implements the reference.

| State | Locked reference | Slice 1 status / implementation gap |
| --- | --- | --- |
| Contribution review | `R6-OFF-Contribute-Review.dc.html` | Designed; screen implementation remains in the contribution slice. |
| Open Food Facts sign in | `R6-OFF-SignIn.dc.html` | Designed; screen implementation remains in the contribution slice. |
| Open Food Facts submission failure | `R6-OFF-Submit-Failed.dc.html` | Designed; screen implementation remains in the contribution slice. |
| Archived library list, menu, and detail | `R6-Library-Archived.dc.html`, `R6-Library-Menu.dc.html`, `R6-Food-Archived.dc.html` | Designed; archive screen implementation remains in the library slice. |
| Open Food Facts loading and error | `R6-Search-OFF-Loading.dc.html`, `R6-Search-OFF-Error.dc.html` | Designed; search screen implementation remains in the search slice. |
| Barcode lookup, manual entry, permission denied, and no camera | `R6-Scan-Lookup.dc.html`, `R6-Scan-Manual.dc.html`, `R6-Scan-Denied.dc.html`, `R6-Scan-NoCamera.dc.html` | Designed; scanner states remain in the scanner slice. Camera marks use the fixed camera ground and light marks. |
| Add serving | `R6-Food-Edit-AddServing.dc.html` | Designed; add-serving form remains in the Library and food slice. |
| Restore archived food | `R6-Food-Archived.dc.html` | Designed; restore action remains in the library slice. |
| Scan review variants | `R6-Scan-Review-Details.dc.html`, `R6-Scan-Review-Library.dc.html`, `R6-Scan-Review-Queue.dc.html` | Designed; review variants remain in the scanner slice. |
| Resolve search | `R6-Resolve-Search.dc.html` | Designed; resolve search remains in the search, scan, create, and contribute slice. |
| Create food full form | `R6-Create-Food-FullForm.dc.html` | Designed; full form remains in the create-food slice. |
| Large font states | `R6-Daily-Font130.dc.html`, `R6-Daily-Font200.dc.html` | Designed; large-font layout remains in slice 5. Dead scale policy was removed from the foundation. |
| Light and dark twins | Matching R6 and R6D references | Tokens and shared primitives support both themes. Screen phone verification is pending the owner QA gate. |

See [the v6.5 handoff](codex-handoff-receipt-v65.md) and [the v6.5 gap brief](../design/canvas/BRIEF-v65-gaps.md) for the full state inventory and binding control details.
