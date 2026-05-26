# libfdx Agent Instructions

These instructions apply to the whole repository.

## Purpose

`AGENTS.md` is not the architecture source of truth. It exists to prevent documentation drift.

- Use `docs/ARCHITECTURE.md` for module layout, Gradle paths, Maven artifacts, dependency direction, package roots, and ownership.
- Use `docs/COMMON_API.md` for provider-neutral public API contracts and behavior.
- Do not duplicate detailed architecture decisions here. If a decision changes, update the relevant doc instead.

## Sync Rules

When changing code, Gradle tasks, launchers, public command names, system properties, examples, logs, or externally visible behavior:

- Search the whole repository for old names, old commands, old examples, and old behavior descriptions before finishing.
- Update every affected source-of-truth doc, README section, sample, test launcher, benchmark launcher, Gradle task, and report generator in the same change.
- If a generic command remains available but changes meaning or becomes ambiguous, document the explicit replacement commands wherever the generic command is mentioned.
- Treat generated build output and ignored IDE metadata as non-source unless the user explicitly asks to update them.

When editing `docs/ARCHITECTURE.md` or `docs/COMMON_API.md`:

- If a module, artifact, folder, package, or dependency rule changes, check whether the API doc also needs updated examples or type ownership.
- If a public type, interface, method, lifecycle rule, `Fdx` root accessor, or provider boundary changes, check whether the architecture doc also needs updated module tables, package maps, examples, or dependency rules.
- Search for old names, old examples, and old interface shapes before finishing.
- Keep examples conceptually compilable against the interfaces documented in `COMMON_API.md`.
- Prefer one authoritative section for each rule. Use links or short references instead of repeating the same rule in many places.

## Fdx Root Rules

- Use the typed `Fdx` root passed to `ApplicationListener.create(Fdx fdx)` for backend-owned runtime entry points.
- `Fdx` should stay finite and explicit. Add a direct accessor only for a backend-owned root system or manager, such as `app()`, `displays()`, `graphics()`, `files()`, `logger()`, and future runtime systems when they exist.
- Do not expose user-created feature objects from `Fdx`. `AssetManager`, `Batch2D`/`SpriteBatch`, UI roots, physics worlds, scenes, and game systems should be created explicitly from the APIs they need.
- Backend setup details should not appear in common-code examples.
- Advanced/provider-specific access should stay explicit through typed managers/configs, `providerId()`, and `as()`.

## Platform Validation

Any new feature or behavior change must validate the affected platform before finishing.

- If Android backend, Android sample, Android test, Android Gradle, or Android native code changes, run the relevant Android assemble task and the relevant Android run task against a connected device or emulator.
- If desktop backend, desktop sample, desktop test, desktop Gradle, or desktop native/runtime code changes, run the relevant desktop compile/build task and the relevant desktop run task.
- Apply the same rule to any other platform touched by a change.
- If the required device, emulator, OS, SDK, native toolchain, or hardware capability is unavailable, run every validation step that can still execute, report the exact blocker, and do not claim the platform was fully validated.

## Interface Review

Before changing an interface in `docs/COMMON_API.md`, classify it:

- Is it a backend-owned system exposed through the typed `Fdx` root?
- Is it provider/backend-backed and therefore expected to expose provider-specific access?
- Is it a disposable resource with explicit lifetime?
- Is it provider SPI/factory code used by backend setup?
- Is it launcher/backend infrastructure instead of game-facing API?
- Is it only a listener/callback?
- Is it really a descriptor/config/value type rather than an interface?

After classification, update all affected prose, examples, and tables.

## Consistency Checklist

Before finishing any doc change:

1. Search for stale terms and old decisions.
2. Check that `ARCHITECTURE.md` and `COMMON_API.md` use the same names.
3. Check that examples do not use undefined classes or methods.
4. Check that examples use the typed `Fdx` root and explicit user-created objects.
5. Check that provider-specific examples are clearly marked as provider-specific.
6. Check that nullable-return behavior is documented for methods that may not find an object.
7. Run the validation commands below.

## Validation Commands

Run these from the repository root after architecture/API doc changes:

```powershell
rg -n "Optional|java\.util\.Optional" docs
rg -n "ApplicationBackend extends FdxService|AudioProvider extends FdxService|NetworkProvider extends FdxService|Gamepads extends FdxService" docs
rg -n "\bGraphics\s+graphics\b|new SpriteBatch\(" docs
rg -n "^## (?!Index|[0-9])|^### (?![0-9])" --pcre2 docs
git diff --check -- docs/ARCHITECTURE.md docs/COMMON_API.md AGENTS.md
```

Some `rg` commands are expected to return no matches. Treat matches as prompts to review the docs, not automatically as errors.
