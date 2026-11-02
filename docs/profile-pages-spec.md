# Profile pages: calories, pace, macro percentages, weight and nutrition

Owner-approved 2026-11-02. Reuse shared components; no duplicated layout or logic. No new dependencies. Copy below is exact; add no other strings.

## Layout
Profile becomes three swipeable pages, with the tappable words Profile, Weight, Nutrition in the top bar (BottomNav's text-tab style). Back with unsaved changes still asks. Save (primary button) shows on Profile only; a draft survives swiping between pages. Charts on a page take no drag or pinch, so a swipe always changes the page.

## Profile page, in order
Name; Height, cm + Age; Current weight, kg + Target weight, kg; "Add to curve"; "Several weights a day"; Sex (Female, Male, Not saying); Activity (Inactive, Low active, Active, Very active); "Tracking only"; "Daily burn" row with kcal; Goal (Lose, Maintain, Gain); Kg a week (0.25, 0.5, 1, Custom, with a "Kg a week" field for Custom; shown only on Lose and Gain); two soft lines, shown only with a goal chosen; Daily kcal; macro rows. These replace the old macro preset chips. No sex selected counts as Not saying. Daily burn, Goal and Kg a week show only when height, age, current weight and activity are valid; until then, unless Tracking only is on, that place has one soft line: "Enter your height, age, weight and activity to see your daily burn."

Soft lines under the pace controls:
- "Daily burn is an estimate, so your real pace may differ."
- "We'll adjust daily kcal a little if your weight isn't keeping to this pace."

Age range 1 to 120, height 50 to 300 cm. Tracking only hides the Weight page, Goal section, kcal and macro targets, and Daily burn; Nutrition stays without its goal line. Targets are kept, not erased, and return when it is turned off.

## Daily burn
Mifflin-St Jeor times an activity factor, kcal/day; A age, H cm, W kg. Written for adults; other ages still calculate.

Resting = 10W + 6.25H - 5A, plus 5 for Male, -161 for Female, -78 (their average) for Not saying.

| Activity | Factor | Line on the option |
| --- | --- | --- |
| Inactive | 1.2 | Mostly sitting, little walking |
| Low active | 1.375 | Some walking most days |
| Active | 1.55 | On your feet or exercising most days |
| Very active | 1.725 | Hard exercise or physical work daily |

Owner's decision, 2026-11-02. The National Academies 2023 equations gave 3031 kcal for 88 kg, 168 cm, age 24, Low active, which the owner knows would gain weight. These are the factors common calculators use, so the numbers match what people see elsewhere. They sit low on purpose: measured sedentary adults are nearer 1.45, and logging corrects a low start. Prior sigma: male 339, female 246, not saying 300.

Learning from logging, recomputed once a day and on save:
- Window: last 28 days. Slope and its standard error SE: linear regression on raw weigh-ins, kg/day.
- Eligible day: logged, intake at least 50% of the window's median logged intake. EI: mean of eligible days. rho = 8840 kcal/kg.
- adaptive = EI - rho x slope. sigmaA^2 = (rho x SE)^2 + 100^2. w = sigma^2 / (sigma^2 + sigmaA^2). Burn = formula + w(adaptive - formula).
- Needs 14 weigh-ins over 21+ days and 21 eligible days, else w = 0.
- adaptive outside 0.75 to 1.3 x formula: w = 0 and show under Daily burn "Your logging and weight don't match. Check that your meals are all logged."

## Goal and daily kcal
- Change per day = kg a week x 8840 / 7, minus for Lose, plus for Gain, zero for Maintain. Choosing a goal or pace fills Daily kcal in the draft. So does an edit of height, age, current weight, sex or activity that moves the burn while a goal is chosen, macros following as for any kcal change and the goal staying; with the body not valid, Daily kcal stays.
- Lowest from a goal: 1200 Female, 1500 Male or Not saying. When it applies: "1500 kcal is the lowest here. That's about 0.4 kg a week." with the real numbers.
- Pace above 1% of current weight a week: "This is faster than 1% of your weight a week."
- Automatic adjustment: with a saved goal, Daily kcal is recalculated once a day and moves at most 100 kcal in any 7 days. Typing Daily kcal by hand clears the goal and stops this; a hand-typed target may go below the lowest. Nothing announces an adjustment beyond the soft line above.

## Warnings: nothing is blocked
With Goal on Lose, below the Goal row, in body text:
- BMI below 18.5, current or target: "Your weight is already below the healthy range, and losing more could harm you. You deserve support with this, and a doctor or dietitian can help. If food or your weight has been on your mind a lot, you don't have to deal with it alone."
- Age under 18: "You're still growing, and your body needs enough food for that. Please talk to a doctor or a parent you trust before trying to lose weight."

## Macros
Each macro has a "Protein, %" and "Protein, g" field side by side, the dot on the % label; carbs and fat alike. % fields show only with a valid Daily kcal. Factors 4/4/9.
- The macro edited, in either field, stays as typed, capped at 100%. The other two share the rest in their current ratio, both zero meaning 50/50. Grams are rounded, the last macro taking the remainder.
- Each macro remembers how it was entered. When Daily kcal changes, a % macro keeps its percent, a gram macro keeps its grams, and the rest follows.
- Targets saved before this feature show as entered; nothing rebalances until an edit.

## Weight page
- Range: ChoiceOption row 1M, 3M, 6M, 1Y, All; default 3M, or All with under 3 months of points.
- Chart: faint raw dots, bold exponential trend (alpha 0.1), soft target line. Trend breaks over gaps of more than 14 days. 1Y and All draw weekly averages. Macro colours stay out.
- Maximise icon top right lifts the chart: scrim behind, a soft drop shadow, full width minus 8dp each side, same aspect ratio, a close icon, pinch working in as far as a week in view (a chart under three weeks long goes in three times, down to a day), drag working once zoomed. Back closes it. This is the one shadow in the app.
- Projection below the chart, then a hairline, then the existing point list with delete.

Projection: slope and SE over the last 28 days of raw weigh-ins, toward the target. tau = 343 days. r = |slope|, D = kg left, rHigh = r + 1.96SE, rLow = r - 1.96SE. Earliest = D / rHigh. Latest = tau x ln(tau x rLow / (tau x rLow - D)), defined only when rLow > 0 and D < tau x rLow.
- No target, or target reached: nothing.
- Fewer than 3 weigh-ins on different days, or no latest date or latest over 2 years away with fewer than 14 weigh-in days: "Not enough data yet. Come back in 9 days." The number is 14 minus the days with a weigh-in, at least 1.
- Moving away, or under 0.05 kg a week, with no date to give: "No date yet. Your weight isn't moving toward your target."
- Moving toward the target with 14+ weigh-in days, but no latest date or latest over 2 years away: "No date yet. At this pace, your weight may level off before your target."
- Otherwise: "At about 0.4 kg a week, you reach 78 kg between February and April." One month: "in March". A year follows the month only when not within the next 12 months. With fewer than 14 weigh-in days the "Not enough data yet" line follows it.

## Nutrition page
Week title ("26 Oct to 1 Nov"), previous and next arrows, no later than the current week; weeks start Monday. A second chart wrapper, `ui/components/MacroBarsChart.kt`; no screen knows Vico. Stacked per-day bars: height is the day's entry kcal; protein, carbs and fat segments in `macroColor`, each its known grams x 4/4/9, a 1dp gap between them, scaled down proportionally only when together they exceed the entry's kcal; the entry's kcal left over is one `soft` segment, so 500 kcal with only 5 g protein draws 20 protein and 480 soft. Unlogged day: an empty outlined slot, left out of averages. Goal line: ink, across each day at that day's target; never red when over. Under it: "Daily average" with kcal; MacroLabel dots with average grams; "6 days logged".

## Storage
- New Profile fields go at the end, in `ProfilePrefs` and export, optional so old exports import: height, age, sex, activity, goal, pace, tracking only, each macro's percent when entered by percent.
- `target_history`: Room version 12, added by a real additive migration, never destructive, with a migration test and exported schema. One row per day a target took effect: day key, kcal, protein, carbs, fat. Written by the one function that saves targets, manual or automatic. A day uses the latest row at or before it; days before the first row use the first row. Included wherever `weight_points` is exported.

## Rules this amends
Update `DESIGN.md`, `PRODUCT.md`, `docs/profile-spec.md`, `docs/architecture.md` to match: stacked bars may use macro colours; the weight chart's ranges and maximise; the one shadow; two Vico wrappers; goals and progress views in scope; the percent fields replacing preset chips.

## Sources
Mifflin et al. 1990, doi 10.1093/ajcn/51.2.241. National Academies, DRI for Energy (2023), nationalacademies.org/read/26818/chapter/7, considered and not used. Hall et al. 2011, pubmed 21872751. Hall and Chow 2013, doi 10.3945/ajcn.111.014399. Sanghvi et al. 2015, PMC4515869. Thresholds and the blend are judgement, not validated in any published app study.
