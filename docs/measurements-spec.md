# Measurements: spec (approved, 2026-10-29)

Status: approved by the owner on 2026-10-29; built, pending device check. Owner answers are marked **(owner)**. The decisions on the open questions are in "Decisions" at the end and override anything above them. The owner may revise details later.

## Today

- A food has one **nutrition basis**: per 100 g or per 100 ml.
- It may have extra **servings** (`ServingConversion`): a fixed unit (piece, tbsp, tsp, g, ml) and how many basis units one is. The food form already has a "Servings" list with an optional name ("1 bottle = 500 ml"), but a **name is display only**: "2 bottles cola" is not understood.
- `NutritionCalculator` picks the first serving with the typed unit; two named servings in g ("bottle", "glass") are not told apart.
- The Log bar turns kg, l, dl, cl into g or ml. The amount sheet never offers them.
- tbsp and tsp on a per-100 g food count as 15 g and 5 g, marked approximate.
- "slice" is read as "piece". Cups, oz, lb, fl oz, and words like can, bottle, scoop, serving are unknown.
- Open Food Facts products come in per 100 g or 100 ml only; their printed serving ("1 can (330 ml)") is ignored.
- A recipe yields portions, a total weight and/or a total volume (three fields).

## What changes

### 1. Units are supported, not forced **(owner)**

- **Weight:** g, kg, oz, lb. **Volume:** ml, cl, dl, l, tsp, tbsp, cup, fl oz. Fixed factors: kg 1000 g, oz 28.35 g, lb 453.6 g; cl 10, dl 100, l 1000, tsp 5, tbsp 15, cup (open question 1), fl oz 29.57 ml.
- Any of these **works when typed** for a food in the same family (weight units on a per-100 g food, volume units on a per-100 ml food, both with a density, see 3).
- The **amount sheet does not list them all**. It offers only what the food itself has: its basis unit (g or ml), the other one when the food has a density, and the food's named servings. A food with nothing extra looks exactly as it does today.

### 2. Named servings per food, like MyFitnessPal **(owner)**

- A food has a list of servings, each **a name and an amount**: "slice = 30 g", "can = 330 ml", "scoop = 32 g", "piece = 118 g".
- The **amount sheet** lists them as units, after the basis unit: `g`, `slice`, `can`.
- The **Log bar** understands a serving name where a unit goes today: "2 slices bread", "1 can coke", "bread 2 slices". Names match like aliases: singular or plural, any case, and the script rule (Latin only matches Latin, Cyrillic only Cyrillic).
- A name the food doesn't have ("2 cans bread") is treated like an unsupported unit today.
- **piece** becomes an ordinary named serving: the built-in foods' "1 medium banana = 118 g" becomes "piece = 118 g". "pc", "pcs", "парче" keep matching it; "slice" still falls back to piece when the food has no slice.
- Entries remember the name they were logged with, so the day reads "2 slices", not "60 g".

### 3. Weight ↔ volume per food **(owner)**

- Optional per food: **"100 ml = X g"**. With it, g and ml both work for that food, and every unit of the other family too ("1 cup ice cream" on a per-100 g ice cream).
- Without it, the other family doesn't work, except as in open question 2.

### 4. Where servings are set **(owner)**

- **New/Edit food form:** the existing "Servings" list; each row "1 [name] = [amount] [g | ml]". The density field sits next to it. One section (`food/ServingsSection.kt`) in New food, Edit food and the details of a product's review before it is stored, so a serving can be added the first time a product is. A row needs a name; a serving still in its form goes with Save, and one that doesn't read stops it. A serving in the other of g and ml needs the density. If the basis changes after a serving was added, the serving goes through the density; with none, it stays listed in the unit it was added in, the density field shows an error, and Save waits for a density, the old basis or its removal.
- **Open Food Facts:** a product's `serving_size` becomes a named serving when it reads clearly: "1 can (330 ml)" → can = 330 ml; "2 biscuits (25 g)" → biscuit = 12.5 g; a bare "30 g" → serving = 30 g. Anything unclear is skipped, never guessed. The g/ml in brackets must match the product's basis (or its density), else skipped.

### 5. Recipes use the same system **(owner)**

- A recipe yields **portions and/or one Total amount**, typed in any weight or volume unit with a g/ml choice. This replaces the separate Total weight and Total volume fields.
- With a total in g, every weight unit logs it; in ml, every volume unit. Portions log as today.

### 6. Past entries **(owner)**

- The owner will clear the diary before real use, so old entries need no special care. Entries still store their own snapshot as now, and migrations stay non-destructive (the never-wipe rule stands).

## Data

Schema change, additive (needs approval, open question 5):

- `foods`: a nullable density column (grams per 100 ml).
- `serving_conversions`: the name becomes the key a serving is matched by; existing rows keep their numbers (piece rows get the name "piece", tbsp/tsp rows "tbsp"/"tsp").
- entries: a nullable column for the serving name or unit label they were logged with.
- recipes: the existing `total_weight` and `total_volume` columns stay; the form fills only one of them.
- Export/Import carries the new fields; older files import without them.

## New text needing approval (proposal; minimal, no helper text)

- Unit labels: `kg`, `oz`, `lb`, `cl`, `dl`, `l`, `cup` / `cups`, `fl oz` (tsp, tbsp, g, ml exist)
- Serving name from Open Food Facts when the product gives none: `serving`
- Density field in the food form: `100 ml =` with `g` after the field
- Recipe form: `Total` (replaces `Total weight` and `Total volume`)

## Out of scope

Servings on recipes ("1 bowl = 350 g"), cooked/raw conversion, editing servings of Open Food Facts products on Open Food Facts itself, unit preferences per user (always metric first).

## Decisions (owner, 2026-10-29)

All proposals below were approved as written: 1. cup = 240 ml; 2. tsp, tbsp and cup keep the rough 1 ml ≈ 1 g fallback on a weight food without density (marked ≈), plain ml/cl/dl/l/fl oz do not; 3. an Open Food Facts product's package becomes a `package` serving; 4. the unit row wraps; 5. the schema change is approved (additive, non-destructive); 6. the text list is approved.

## Open questions (answered above)

1. **Cup size.** Metric cup 250 ml, or US cup 240 ml (what MyFitnessPal and most US labels use)? Proposal: 240 ml, since cup amounts mostly come from US products.
2. **Spoons and cups on a weight food with no density.** Today "1 tbsp peanut butter" counts as 15 g, marked ≈. Keep that rough fallback (1 ml ≈ 1 g, marked ≈) for tsp, tbsp and cup, or make them not work until the food has a density or a tbsp serving? Proposal: keep the fallback, so nothing that works today stops working; plain ml/l on a weight food still needs a density.
3. **Open Food Facts package size.** Also add the product's package as a serving ("package = 500 g" from its `quantity`), so "1 yogurt" logs the whole cup? Proposal: yes, named `package` (new text).
4. **Amount sheet with many servings.** The unit row wraps to a second line when a food has many servings (same chips, no new component). OK, or cap it?
5. **Schema change** as listed under Data: approve?
6. **The text list above**: approve, or change any word.
