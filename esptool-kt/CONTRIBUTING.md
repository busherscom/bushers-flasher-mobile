# Contributing to esptool-kt

Thanks for helping! This project uses **Trunk-Based Development** via
[`tbdflow`](https://github.com/cpendery/tbdflow) and **Conventional Commits**.

## Principles

- **One trunk (`main`).** It is always releasable and always green.
- **Short-lived branches.** A branch lives hours, not days. Integrate small and often.
- **Conventional Commits.** Commit messages drive the changelog and signal intent.

## Workflow with `tbdflow`

```bash
# One-time, in a fresh clone:
tbdflow init

# Start a small change on a short-lived branch:
tbdflow branch --type feat --name add-elf2image

# Commit as you go (Conventional Commit type + scope + message):
tbdflow commit --type feat --scope cli --message "add elf2image command"

# Land it on main (merges + deletes the branch):
tbdflow complete

# Stay current and check for stale/overlapping branches:
tbdflow sync
tbdflow radar
```

For trivial trunk commits you may commit directly on `main` with `tbdflow commit`.
Use `tbdflow changelog` to regenerate the changelog from commit history, and `tbdflow undo <sha>`
as the panic button to revert a bad trunk commit.

### Conventional Commit types

`feat`, `fix`, `docs`, `refactor`, `test`, `build`, `ci`, `perf`, `chore`. Scope is a module
(`core`, `jvm-serial`, `cli`, `android`) or area (`protocol`, `flasher`, `targets`, `security`,
`tools`). Example:

```
feat(core): support FLASH_DEFL for ESP8266 ROM loader
```

> `tbdflow` enforces a ≤72-char commit subject **and** body line length, and an allow-list of
> scopes — keep both tidy.

## Modules

| Module | What | Tested by |
| --- | --- | --- |
| `core` | platform-agnostic protocol engine | unit tests (≥90% Kover gate) |
| `jvm-serial` | desktop/Linux jSerialComm transport | on-device validation |
| `cli` | the `esptool-kt` command | on-device validation |
| `android` | the `.aar` (USB-serial transport) | on-device + the sample app |
| `sample-app` | reference Android app (USB-OTG flashing) | on-device |

## Building & testing

```bash
# Gradle 8.14 must run on JDK <= 21 (compilation targets JVM 17).
JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ./gradlew build   # compile + test + lint + coverage gate

./gradlew :core:test          # core protocol unit tests (no hardware)
./gradlew spotlessApply       # auto-format (spotlessCheck runs in build, all modules incl. Android)
./gradlew :core:koverHtmlReport   # coverage report (gate: >=90% on core)
./gradlew dokkaGenerate       # API docs -> build/dokka/html
./gradlew :sample-app:installDebug   # build + install the reference app on a connected phone
```

All changes must keep `./gradlew build` green (warnings are errors on every module). New protocol
behavior should come with unit tests (SLIP/framing/image/stub/flasher/efuse/secure logic is all
tested without hardware via a `MockEspChip` — see `core/src/test`).

## Hardware-affecting changes

If you change the wire protocol, reset strategies, or flashing logic, validate against a real
chip and note what you tested (chip, transport, commands) in the PR description. Cross-check
against `esptool.py` where possible.

## Code style

- Kotlin official style (`kotlin.code.style=official`); keep the core free of platform-specific
  (desktop-only) APIs so it stays Android-compatible.
- Match the surrounding code: clear names, KDoc on public types, comments that explain *why*.

## Reporting issues

Include: chip + revision, host OS, transport (USB bridge type), the exact command, and a
`--trace` log if it's a protocol problem.
