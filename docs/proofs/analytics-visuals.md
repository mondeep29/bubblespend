# Analytics visualization proof

**Status:** Partial physical-device proof; visible accessibility and interaction checks remain pending.

## Device and artifact

- Physical device: Google Pixel 7 (`panther`), Android 17 / API 37.
- Package: `dev.bubblespend.bt003proof`, version `0.1-proof`, target SDK 35.
- The package was already installed. It was not installed or replaced.
- Local artifact: `spikes/BT-003/app/build/outputs/apk/debug/app-debug.apk`.
- SHA-256 of the installed APK pulled read-only from the device and of the local APK: `D41826ABB57E69DDFCED5538EEA359C8A077F8472C4685770F97F09904F78664` (match).
- Fixtures were synthetic only. Temporary screen captures were inspected locally and not retained as evidence because the status bar was visible.

## Observed

- The analytics screen displayed period `2026-01-01` inclusive to `2026-02-01` exclusive.
- Category share showed Food 1750 (37.6%), Travel 800 (17.2%), and Home 2100 (45.2%) synthetic minor units. The displayed shares sum to 100.0%; amounts sum to 4650.
- The trend view explicitly stated that it has a zero baseline and blank buckets are zero. It showed Mon 1250, Tue 800, Wed 0, Thu 500, Fri 2100, Sat 0, Sun 0. Zero-height buckets were visible for Wed, Sat, and Sun.
- The equivalent ordered list showed matching synthetic fixture values and IDs: `synthetic-001` Mon/Food/1250, `synthetic-002` Tue/Travel/800, `synthetic-003` Thu/Food/500, and `synthetic-004` Fri/Home/2100.
- Comparison text stated a zero baseline and showed Food 1750, Travel 800, Home 2100; bars were visible. No comparison bar was selected.
- Touching the Food category and scrolling to the drill-down displayed two matching entries: `synthetic-001` Mon/Food/1250 and `synthetic-003` Thu/Food/500. This matches the Food list fixtures.
- Android initially reported `accessibility_enabled=0`. The product owner later removed screen-reader support from MVP requirements (D-08, 2026-10-07); no further TalkBack testing is required.

## Accessibility follow-up

- Android Accessibility Settings visibly offered TalkBack (initially Off), Display size and text (font size, display size, bold text, outline text), and Colour and motion (dark theme, colour correction, remove animations). Display-size, contrast, and motion options have not been exercised.
- TalkBack was enabled and returned to Off through visible Android Settings controls before the product owner's D-08 decision. The setting returned to its previously reported disabled state; the TalkBack shortcut remained Off. No screen-reader behavior is claimed and no further TalkBack test is needed under the revised requirements.
- The empty-state control's UI node was located at bounds `[85,300][378,353]` on the 1080x2400 display. From the top of the analytics screen, tapping its center `(231,326)` changed the control to `Show populated fixture` and the visible hierarchy showed `No synthetic entries for this period`. **Pass** for displaying the empty-state message. Earlier taps missed because the screen had not been scrolled to the top and the coordinates were wrong.

## Not verified / limitations

- Screen-reader behavior: **out of scope** by product-owner decision D-08; the prior device state was restored to TalkBack Off.
- Empty state: **pass** for the no-entry message on the fixed fixture's empty-state toggle.
- Trend-bucket-to-ID drill-down, comparison-bar drill-down, and bubble touch selection were **untested**. The successful touch proof covers the Food category row only.
- Large/bold text, high contrast/colour correction, and reduced motion were **untested**.
- This is a disposable Compose spike with fixed values, not the production analytics data pipeline. It does not establish filtering correctness, currency formatting, real transaction behavior, or library suitability.
- No chart library is selected by this proof.

## Recommendation

**No-go for approving chart behavior yet.** The observed category values, trend zeros, equivalent list, empty-state message, and category drill-down are consistent for these fixed fixtures. Complete large/bold text, contrast, reduced-motion, trend drill-down, comparison-bar drill-down, and remaining touch-selection checks before relying on the spike as full proof. Screen-reader checks are outside MVP scope by D-08.
