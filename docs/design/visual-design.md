# BubbleSpend visual design

The product owner selected these images as the app's visual direction on 2026-10-10:

- [Floating entry and Home reference](../../ChatGPT%20Image%20Sep%2028,%202026,%2012_52_42%20AM.png)
- [Insights, category breakdown, and trends reference](../../ChatGPT%20Image%20Sep%2028,%202026,%2001_00_27%20AM.png)

## Shared visual language

Use a warm white canvas, dark navy text, coral branding and primary actions, and peach/lavender/mint/blue category surfaces. Keep generous spacing, rounded cards and fields, large spending amounts, visible category icons, fine separators, and a clear bottom navigation. Use native Compose components and code-native icons; no remote fonts or image services are needed.

## Working screens

Home presents actual all-time spending, pastel category cards, matching expense rows, and a prominent Add expense action. The all-time label makes the existing scope explicit. Category cards have a fixed decorative shape; exact amounts and counts carry the information. Tapping a category opens its matching rows; tapping the total returns to all active expenses. Deleted records are excluded and different currencies cannot be combined. Financial sums use exact integer arithmetic even beyond the range of a single stored amount.

Expense entry uses coral branding, a large amount field, rounded selection controls, and clearly visible Save/Close actions. Setup, Settings, expense detail, and management screens share the same color, typography, and component treatment. Existing currency confirmation, validation, saving, Retry, editing, deletion, and undo behavior remains applicable.

Large text may reflow category cards to one column. Text and actions can scroll, navigation remains reachable, and charts or decorative colors are never the sole source of a value.

## Remaining screens

Use the references for the future period dashboard, category donut/list, trend/comparison charts, insight cards, and floating quick-entry panel. Those visuals must use the approved specifications and actual stored data. Sample figures, currency, dates, merchants, payment methods, and insight statements in the images are examples rather than new product rules. The visual references do not approve unresolved analytics, overlay, backup, or accessibility behavior.
