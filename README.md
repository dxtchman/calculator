# Calculator

An all-in-one calculator for Android with a hidden locked folder.

## Calculator
- Standard keypad with live result preview, smart `( )` bracket key, `+/−`, `%` (200 + 10% = 220)
- Tap anywhere in the expression to move the cursor and edit in place
- Scientific keys (toggle in portrait, always shown in landscape): `⇄` second functions, Rad/Deg,
  √ ∛, sin cos tan and their inverses, sinh cosh tanh and their inverses, ln, log, 1/x, eˣ, 2ˣ, x², x³, xʸ, |x|, π, e, x!
- History: tap an expression or a result to reuse it; clear history
- Long-press the display to copy or paste
- Unit converter: area, length, temperature, volume, mass, data, speed, time, plus a tip calculator
- Keypad vibration toggle in the ⋮ menu

## Locked folder
Type `sin666` and press `=`.
- First time: create a PIN or password. Later: enter it to unlock.
- Photos & videos: pick from the gallery inside the app and they're removed from the gallery after they're encrypted.
- Files: any file type. Notes: private text notes.
- Everything is encrypted (AES, key derived from your password with PBKDF2). There is no recovery if you forget it.
- Locks when you leave the app; hidden from the recent-apps screen.
- Restore puts items back in Pictures/Restored, Movies/Restored or Download.

Uninstalling the app erases the locked folder. Build updates install over the top and keep it.

## Build
GitHub Actions builds `Calculator.apk` on every push to `main` and attaches it to a release.
