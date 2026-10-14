# Reading plans

Every `*.txt` file in this folder is bundled into the app as a reading plan
(rebuild and reinstall after adding or editing one). Plans are sorted by file name.

## Format

```
title: Psalms and Proverbs
description: A psalm and a proverb chapter every day
# Lines starting with # are comments; blank lines are ignored.
Psalm 1; Proverbs 1
Psalm 2; Proverbs 2
```

- `title:` and `description:` are optional (title defaults to the file name).
- `schedule: yearly` (optional) ties the plan to the calendar: line 1 is January 1,
  line 365 is December 31, every year. February 29 repeats February 28. Without it,
  line 1 is the start date you pick in the app.
- `labels:` (optional) names each passage slot, separated by `;`, e.g.
  `labels: Family; Family; Secret; Secret`. A label shows above its passage.
- Every other line is **one day**, in order. Line 1 is day 1.
- Separate multiple passages for the same day with `;`.
- Passage text is shown as-is, so write it however you like (`John 3:16-21`, `Gen 1–3`, …).

The file name (minus `.txt`) is the plan's ID, which is what the app uses to remember
your start date. Renaming a file resets its start date.
