# USDA food catalogue

Owner's request, 2026-10-31: generic foods the built-in list and Open Food Facts handle badly, such as home-made breaded chicken. Agreed with the owner and the researcher (`fndds_coding_agent_review_reply.md`, which holds the evidence).

## Decisions
- The built-in foods stay. USDA FNDDS 2021-2023 is added as a separate read-only file, offline, and its foods never enter the matcher's in-memory list until picked.
- No schema change to the diary. No new dependency. A picked food is stored with the `FoodRecord` fields that exist (`sourceId`, `sourceName`, `sourceRelease`, `preparation`, `sourceDescription`).
- No second dataset until a real gap shows up. CAPNUTRA is out: no licence to redistribute.

## The file
- Built offline by a script in `tools/` from the official archive, checked against its SHA-256: zip `dfb06ae7ddc397ccd570b91c14b75438ab2ba39f64f22d321f61d4a52a77f3eb`. The archive itself is not committed; the script downloads it. The output is reproducible.
- Foods: FDC ID, FNDDS code, description, additional descriptions (`SurveyFoods[].foodAttributes[]` where `foodAttributeType.id == 1001`, deduplicated per food), kcal (1008), protein (1003), fat (1004), carbs (1005). A food with no energy is dropped and the script reports it. A missing macro stays unknown, never 0.
- Portions kept: a clear label with grams above 0. Dropped, with counts reported by the script: modifier `90000` or "Quantity not specified", any label containing "guideline amount", "yield" or "surface", and grams missing or not above 0.
- Index: FTS4, `unicode61`, external content in the same file, built offline. Plus a small reviewed alias table in the script; the first rule is breaded to coated.
- Shipped gzip-compressed in assets, extracted once per catalogue version to app-private storage through a temporary file and a rename, so a failed extract leaves nothing half done. Opened with `OPEN_READONLY`. Never extracted per query. If it cannot be opened, USDA results are simply absent and the rest of the app works.

## Search
- All words must match, whole word, the last word also as a prefix once it has three letters. A breaded word also tries coated; the typed word stays in the query. Whole words in the name rank first, then prefixes in the name, then words only in additional descriptions, then the shorter name. At most 10 results, because "breaded chicken breast" has nine close answers and the shortest aren't always the one meant.
- The script rule holds: USDA text is Latin, so Cyrillic or mixed input gets no results. User text is never passed as raw FTS syntax.
- Off the main thread; a result for an older query is dropped.

## Where results show
- A part with no full local match ("breaded chicken breast", "cooked chicken" with only raw chicken) goes to Find matches as today. Its results read: your own foods, then USDA, then Open Food Facts. USDA needs no connection and no Add press of its own beyond that flow's. Open Food Facts behaves as now.
- A part that goes green keeps its green pick. An end chip "Find matches" after the other chips opens the same results for that part (owner to approve).
- The Log's green pick is untouched: a partial match already cannot go green (`resolutionOf`, `needsChoice`), and a USDA food can't be a pick until it is one of the user's foods. Results arriving never change the selection.
- The first run in tests: "chicken" stays with the local food; "cooked chicken" with raw chicken alone does not go green.

## Picking one
- A tap opens the existing product review with the typed amount, or 100 g with none. Its portions show as named servings, marked approximate, with grams visible. Nothing is written before Add.
- Add stores it with id `fndds_<fdcId>`, the source fields above, and the original description as name. Picked twice, the stored food is reused, along with any edits to it.
- The catalogue is never written back to a stored food. Log entries hold their own snapshots, so past logs don't move.

## Wording (owner to approve)
- USDA row, under the name: "USDA, 100 g", as an Open Food Facts row reads "brand, 100 g".
- Source in a food's details: "USDA FoodData Central".
- Settings, Licences, added: "U.S. Department of Agriculture, Agricultural Research Service. FoodData Central, FNDDS 2021-2023. fdc.nal.usda.gov. CC0 1.0." USDA doesn't endorse the app, and nothing may imply it.
- Button: "Find matches", as now; as the end chip it is the same words.

## Checks
- Importer: 5,432 foods and 22,194 portions in, 9,647 additional descriptions, drops reported. FDC 2705975: 219 kcal, 24.6 g protein, 9.99 g fat, 6.01 g carbs; 165 g gives 361.35 kcal.
- "chicken breast coated" finds 2705975, and so does "breaded chicken breast". "egg" doesn't find eggplant in first place; "млеко" finds nothing.
- On a device: the exact file opens and answers under the oldest API available, the second start does not extract again, a typed query's time and the installed size are measured.
- Existing diaries and exports are untouched.
