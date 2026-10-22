# Contributing

Thanks for helping. Issues and pull requests are welcome.

- **Bugs**: say which device and Fire OS version (Settings > My Fire TV > About), what you pressed and what you
  saw. A screenshot helps: `adb exec-out screencap -p > screen.png`.
- **Code**: Kotlin with plain Android Views, no AndroidX, minSdk 22 (Fire OS 5 and 6 must keep working). Keep
  drawing cheap: Fire TV GPUs are slow, so blur once and cache, and check `adb shell dumpsys gfxinfo` before and
  after a visual change.
- **Never commit keys**: the TMDB key goes in `local.properties` or the `TMDB_API_KEY` environment variable.

## Release signing

Release builds read their signing config from `~/.glass-release/keystore.properties` (or the file named by the
`GLASS_KEYSTORE_PROPERTIES` environment variable), outside the repository:

```properties
storeFile=/path/to/release.jks
storePassword=...
keyAlias=...
keyPassword=...
```

Without it, `assembleRelease` builds an unsigned APK.

By contributing you agree that your work is licensed under the GNU GPL v3.
