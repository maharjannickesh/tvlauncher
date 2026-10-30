# v1.1 City picker device walk (2026-10-30)

Build under test: HEAD 331a6e2 (installed 2026-10-30 14:33). Device: Chromecast with Google TV 10.0.0.21. Goal: the City field starts EMPTY with the current city as a dim placeholder, so a second edit does not append.

| # | Step | Result | Shot | Shows |
|---|------|--------|------|-------|
| 1 | Settings > Clock 2 > OK | PASS | v11-fix-picker-empty | City row shows "Abu Dhabi" as dim placeholder, Time zone Asia/Dubai |
| 2 | OK on City, type Paris, Enter, pick first (France) | PASS | v11-typed1, v11-results1, v11-fix-picker-paris | Field showed only "Paris"; first result Paris, Ile-de-France, France; row reads `Paris · Europe/Paris` |
| 3 | Reopen Clock 2, type Abu Dhabi, Enter, pick first (UAE) | PASS | v11-fix-picker-empty-again, v11-typed2, v11-results2, v11-fix-picker-twice | Placeholder "Paris"; field showed only "Abu Dhabi" (no "ParisAbu Dhabi"); row reads `Abu Dhabi · Asia/Dubai` |
| 4 | Save, Home | PASS | v11-fix-home-restored | Clock 2 card Abu Dhabi 22:44, 86 deg F (weather present within 20 s) |
| 5 | homekey-status | PASS | n/a | accessibility service enabled, bound: yes |

Notes: the placeholder is rendered as plain dim text on the unfocused row (no caret while not editing); in edit mode the typed text replaces it. DPAD key presses sent faster than ~1 s apart were dropped by the TV, so the walk used 1-1.2 s spacing. Final state equals the starting state (Clock 2 Abu Dhabi, no other setting changed).
