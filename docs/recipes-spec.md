# Recipes: spec (draft, 2026-10-29)

Status: draft for owner approval. Nothing is built. Owner answers from 2026-10-29 are marked **(owner)**; the rest are proposals, and the open questions at the end need an answer before any build.

## What a recipe is

- A dish made from several foods that **makes a number of portions**, e.g. "Chili: 500 g beef mince, 1 can kidney beans, 200 g rice, makes 4 portions". **(owner)**
- It is logged by **portion** (1, 2, 1.5 …) **or by weight** (e.g. 300 g). **(owner)**
- No photos. **(owner)**
- No recipe inside a recipe. **(owner)**
- No cooked/raw handling: the user weighs an ingredient however they like (cooked or raw) and picks the matching food. **(owner)**

## Where it lives

- A **separate Recipes tab** in the bottom bar, next to Meals and Library. **(owner)** Settings placement: open question 1.
- The Recipes tab lists recipes (name, kcal per portion), with a way to create one and a "+" per row to log it, like the Library's rows.
- Recipe page: name, portions, ingredient list with amounts, nutrition per portion (kcal, protein, carbs, fat; "?" where an ingredient has no value, as the day total does), and Edit / Delete.

## Making a recipe

- Name (required), number of portions (required, > 0, decimals allowed).
- **Ingredients typed like the Log bar** (proposal): one line such as `500 g beef mince, 1 can kidney beans, 200 g rice`, parsed and matched with the same rules, personal foods first, the same "which one?" choices and "Not in your foods" resolution. This reuses the existing parser and resolver instead of a new picker.
- Optional **total weight of the finished dish** (proposal, see open question 2), used only for logging by weight.

## Logging a recipe

- **Log bar:** `1 chili`, `1.5 chili`, `2 portions chili`, `300 g chili` work like foods. **(owner)** A recipe's name and aliases are matched like a personal food's, with the same script rule (Latin only matches Latin, Cyrillic only Cyrillic).
- **Recipes tab "+":** the same amount sheet as foods, with a **portion** unit and **g** (g only when a weight is known).
- The confirmation is "Logged!" with Undo, as for foods.
- An entry stores its own snapshot (name, amount, nutrition), so **editing or deleting a recipe later never changes past entries**. **(owner)**

## Nutrition

- Recipe total = sum of its ingredients at their amounts; per portion = total / portions.
- By weight: per gram = total / total weight (open question 2).
- Ingredients follow their food: editing a personal food updates recipes that use it (proposal). If the food is deleted, the recipe keeps that ingredient's last numbers, the same way entries do.

## Data

- New tables for recipes and their ingredients. This **is a schema change** with a normal, non-destructive migration; existing data is untouched. Needs owner approval (open question 4).
- Recipes are included in Export / Import so a backup doesn't lose them. No new export UI.

## New text needing approval (proposal; minimal, no helper text)

- Tab: `Recipes`
- Recipes list: `New recipe`, subtitle `N recipes` (like the Library's `N foods`)
- Form fields: `Name`, `Portions`, `Ingredients`, `Total weight` (only if question 2 is yes)
- Unit in the amount sheet: `portion` / `portions`
- Recipe page actions: `Edit`, `Delete recipe`; confirm dialog `Past entries keep their numbers.` / `Cancel` / `Delete recipe` (mirrors Delete food)

## Out of scope

Photos, nested recipes, cooked/raw conversion, sharing, importing recipes from the web, scaling ingredient lists, saved meals.

## Open questions for the owner

1. **Settings in the bottom bar.** It is already the gear at the right end of the bar. Keep it as the gear (bar = Meals, Recipes, Library + gear), or make it a fourth labelled tab (Meals, Recipes, Library, Settings)? Proposal: keep the gear. And tab order: Meals, Recipes, Library? Proposal: Meals, Library, Recipes (logging-first order unchanged, the new tab last).
2. **Logging by weight needs the finished dish's weight.** The ingredients' sum is wrong for anything that loses or gains water while cooking. Proposal: an optional "Total weight" field you fill in after weighing the pot; if empty, the recipe logs by portion only (no g in the amount sheet).
3. **Ingredient entry.** Typing the ingredients like the Log bar (proposal) vs adding them one by one from a search list. Typing is faster and reuses what exists.
4. **Schema change.** Recipes need new tables (a non-destructive migration). Approve?
5. **The text list above.** Approve, or change any word.
