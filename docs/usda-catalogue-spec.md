# USDA as the app's food database

Owner's request, 2026-10-31: generic foods the built-in list and Open Food Facts handle badly, such as home-made breaded chicken. Evidence: `fndds_coding_agent_review_reply.md` from the researcher. Decided with the owner in chat.

## Decisions
- USDA FNDDS 2021-2023, shipped in the app, is the one bundled food database. `FoodCatalog.kt` and its ~40 foods are deleted, and everything that reads them moves over.
- Foods already stored on a phone stay as that person's foods, and log entries keep their own values. A new install starts with no foods of its own. Every stored food can be deleted; entries keep their own values.
- No change to the diary schema, unless building this shows one is needed: ask the owner first. No new dependency.
- No second dataset until a real gap shows up. CAPNUTRA is out: no licence to redistribute.
- Open Food Facts stays behind the Find matches button, for packaged products.

## The file (built; commit 7ff3f61)
- Built offline by `tools/build_usda_catalogue.py` from the official archive, checked against its SHA-256: zip `dfb06ae7ddc397ccd570b91c14b75438ab2ba39f64f22d321f61d4a52a77f3eb`. The archive is not committed. The output is reproducible.
- Foods: FDC ID, FNDDS code, description, additional descriptions (`SurveyFoods[].foodAttributes[]` where `foodAttributeType.id == 1001`, deduplicated per food), kcal (1008), protein (1003), fat (1004), carbs (1005). A food with no energy is dropped and reported. A missing macro stays unknown, never 0.
- Portions kept: a clear label with grams above 0. Dropped, with counts reported: modifier `90000` or "Quantity not specified", any label containing "guideline amount", "yield" or "surface", and grams missing or not above 0.
- Index: FTS4, `unicode61`, external content in the same file, built offline. Alias table in the script; the first rule is breaded to coated.
- Gzip-compressed in assets, unpacked once per catalogue version to app-private storage through a temporary file and a rename. Opened read-only. If it can't be opened, its results are absent and the rest of the app works.

### To add to the file
- **Ranking bump:** one small number per food, above 0 for common foods ("Egg, whole, raw"), 0 for the rest. The list is short and lives in the script. It orders results and can never make a food green.
- **Local dishes:** a small map in the importer of exact food descriptions to extra search words ("Stuffed cabbage rolls with beef and rice" to "sarma"), indexed like the additional descriptions and never shown. The build fails if a listed description doesn't exist.
- **Macedonian to English:** a table of Macedonian words and their English search words ("јајце" to "egg", "пилешко" to "chicken"). The owner checks the starter list.

## Search
- All words must match, whole word; the last word also as a prefix once it has three letters, and also in its singular. A breaded word also tries coated, and the typed word stays in the query. Whole words in the name rank first, then prefixes in the name, then words only in additional descriptions, then the bump, then the shorter name. At most 10 results.
- Macedonian: Cyrillic words are translated by the table, hidden from the user, and only to search this database. This is the one exception to the script rule. It doesn't reach the user's own foods or Open Food Facts, which keep the rule. A Cyrillic word the table lacks finds nothing, and a token mixing scripts finds nothing. Results show their English names.
- User text is never passed as raw FTS syntax. It runs off the main thread, and a result for an older query is dropped.

## In the Log
- While typing: the user's own foods as chips, with the green pick as today; matching USDA foods as plain rows under them. Long names don't fit chips. USDA rows need no network and no button to press. Rows arriving never change the selection. Add never logs a USDA row that wasn't tapped.
- No "USDA" on the rows. The source shows in the food's details and in Licences.
- A part nothing local fully matches keeps Find matches for Open Food Facts, as today. Its results come after the USDA rows.

The same rows show in Library search, under a no-match part and in recipe ingredient search.

## Picking one
- A tap opens the existing product review with the typed amount, or 100 g with none. Portions show as named servings, marked approximate, with grams visible. Nothing is written before Add.
- Add stores it with id `fndds_<fdcId>`, its source fields and the original description as the name; the user can rename it. Picked twice, the stored food is reused with any edits. The catalogue never writes to a stored food.
- A USDA food with no cup, tablespoon or teaspoon portion answers "1 cup" with the existing "No value for cup", not the generic 240 ml.

## Learning
- A tap on a food counts as a pick: a Log chip or a USDA row gets +1, through `reinforce`, while "Learn from my picks" is on. Today only the feedback strip adds it.
- A green food logged with no tap adds nothing, so an unnoticed wrong pick can't reinforce itself.
- A saved food goes green when its name fully matches. With two saved foods matching "egg", the one with the higher score must win the green pick: test it, and fix it if not.

## Wording (owner: as USDA asks)
- Source in a food's details: "USDA FoodData Central".
- Settings, Licences, added: USDA's suggested citation word for word, "U.S. Department of Agriculture, Agricultural Research Service. FoodData Central, 2019. fdc.nal.usda.gov.", then on its own line "FNDDS 2021-2023. CC0 1.0." USDA doesn't endorse the app, and nothing may imply it.

## Checks
- Importer: 5,432 foods and 22,194 portions in, 9,647 additional descriptions, drops reported. FDC 2705975: 219 kcal, 24.6 g protein, 9.99 g fat, 6.01 g carbs; 165 g gives 361.35 kcal.
- "chicken breast coated" and "breaded chicken breast" both answer with 2705975. "egg" doesn't put eggplant first. "јајце" finds egg foods; a Cyrillic word outside the table finds nothing.
- A tap adds +1; a green log with no tap adds nothing.
- The app with no built-in list: a new install works, onboarding examples still draw, existing diaries and exports are untouched.
- A comma in a portion label doesn't split a part in the Log.
- On a device: time a typed query on the owner's phone, measure installed size, and check the oldest Android available. Android 8 is not yet tested.
