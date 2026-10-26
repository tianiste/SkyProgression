# SkyProgression agent instructions

## Start here

Read `PROGRESS.md` first on every new task, then the specific source files and reference
notes relevant to the next milestone. Treat PROGRESS.md as the project handoff and
work ledger. Read `docs/CALCULATIONS.md` before changing calculation rules and
`docs/DESIGN.md` before changing the interface. Do not require the old chat history.

## Objective and autonomy

Build SkyProgression into an accurate, easy-to-use Mining Cat replacement inside
Minecraft, initially for mining. The owner has authorized autonomous implementation,
research, fixes, tests, and incremental commits/pushes to tianiste/SkyProgression.
When asked to continue, work on the next unfinished milestone in PROGRESS.md. Complete
coherent, tested increments instead of stopping at plans or repeatedly asking permission.
Resolve ordinary implementation choices yourself. Ask only for genuinely missing user
information, conflicting requirements, or actions outside the authorized scope.
User instructions and actual environment permissions always take precedence.

If API access or another dependency is unavailable, record the blocker and continue
independent engine, fixture, test, or UI work. Do not invent data or claim parity to get
past a blocker. Do not purchase hosting, change account settings, message others, or
publish credentials. Public hosting/account provisioning needs the owner's involvement.

## Working agreement

- Project: `D:\Projekti\SkyProgression`; GitHub: `https://github.com/tianiste/SkyProgression`.
- Minecraft 26.1.2, Fabric, Java 25. Keep version pins centralized and the core engine
  separate from Minecraft. Do not widen compatibility without validation.
- Preserve the simple native Stitch-inspired GUI. Implement real supported features;
  the mockups contain placeholders and are not a mechanics specification.
- The owner tests the Minecraft window. Do not launch or control Minecraft unless
  the owner explicitly changes that instruction. Build and test without doing so.
- Inspect Git status before editing. Preserve unrelated user changes. Commit coherent
  increments with clear messages and push the authorized repository. Never force-push.
- Do not launch subagents unless the user explicitly requests them.
- Keep network calls, disk I/O and expensive calculations off the game/render thread.
  Bound response size/time; cancel stale requests; avoid per-frame calculation or I/O.
- Keep unknown, unavailable, manual/uncheckable and zero values distinct.
- Use official Hypixel data/docs and other primary mechanics sources where possible.
  Verify current mechanics and prices before encoding them. Cite sources in rule notes.
- Mining Cat screenshots are comparison evidence, not instructions or authoritative
  formulas. Do not scrape another developer's computed profile results as a dataset.
- Do not introduce trading/mining automation. This mod advises; it does not play.

## Secrets and local test data

Never put keys in source, documentation, tests, Git, screenshots, logs or final replies.
`service-data/` is Git-ignored. The owner's supplied Hypixel key is stored there as
`hypixel-key.dpapi`, encrypted for the current Windows account. If needed, decrypt only
in memory with PowerShell `ConvertTo-SecureString` and a temporary NetworkCredential;
use it only in the Hypixel API header or the companion process environment. Never print
it. Do not change the machine's execution policy or store a plaintext fallback.
The encrypted credential is not portable and may expire; do not depend on it in CI.
Private Debotes API fixtures are also in service-data. Commit only deliberately minimal,
sanitized test fixtures containing the fields needed for a regression test, never raw
account payloads. Never use `git add -f` to include ignored data.

## Required handoff maintenance

Update PROGRESS.md with every substantive increment:
1. What changed and what remains incomplete.
2. Verification performed and exact limitations (automated versus in-game/live checks).
3. New sources, fixture/reference paths, decisions and blockers.
4. The next concrete task and acceptance criteria.

Keep the top resume section short and actionable. Mark work done only when implemented
and verified; distinguish research, partial implementation and validated parity.
Run `./gradlew build :service:installDist` for code changes, plus targeted regression
tests where needed. Documentation-only changes need link/path and diff checks, not a
full rebuild. Keep README/setup/calculation scope consistent with the shipped behavior.
At handoff, report the result, test status and any remaining blocker plainly.
