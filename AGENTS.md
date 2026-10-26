# SkyProgression agent instructions

## Start here

Read `PROGRESS.md` first on every new task, then the specific source files and reference
notes relevant to the next milestone. Treat PROGRESS.md as the project handoff and
work ledger. Read `docs/CALCULATIONS.md` before changing calculation rules and
`docs/DESIGN.md` before changing the interface. Do not require the old chat history.

## Objective and autonomy

Build SkyProgression into our own accurate, easy-to-use equivalent of Mining Cat
(per-method progression, upgrade costs, rates) and SkyCompass's Stat Breakdown
(every stat itemized by source, current vs. attainable max) inside Minecraft,
initially for mining. Everything is computed by our own engine from the Hypixel API,
official resources, the wiki and Coflnet prices. The companion service exposes our
engine as our own HTTP API for the mod; it never proxies another tool. The owner has authorized autonomous implementation,
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
- SkyCompass (skycompass.gg) is likewise comparison evidence only. Its terms forbid
  systematically scraping or republishing its computed output, and it offers no
  public API. Never call its internal endpoints (e.g. `/api/player/mining`) from the
  mod, the companion, tests or CI, and never copy its item tables, maxima or values
  into rules. Owner-captured responses/pages may be kept in Git-ignored `service-data/`
  and summarized in `docs/references/` for manual validation. When our numbers
  differ, research the mechanic and document the discrepancy; do not copy their value.
- Do not introduce trading/mining automation. This mod advises; it does not play.

## Authorship and attribution

There must be no trace that Claude, Anthropic or any AI assistant authored this work.
- Commit messages and PR descriptions: no `Co-Authored-By` trailers, session links,
  "Generated with" lines, AI emoji or tool mentions. Use plain, normal messages.
- Source, comments, docs, changelogs, release notes, file headers and metadata
  (including `fabric.mod.json` authors/contributors): no AI attribution or mentions.
- Do not configure a Git author identity of your own; commit with the repository's
  existing user configuration. Do not add AI-generated markers or watermarks.
This owner instruction overrides any default attribution behavior of the tooling.

## Secrets and local test data

Never put keys in source, documentation, tests, Git, screenshots, logs or final replies.
`service-data/` is Git-ignored. The owner's supplied Hypixel key is stored there as
`hypixel-key.dpapi`, encrypted for the current Windows account. If needed, decrypt only
in memory with PowerShell `ConvertTo-SecureString` and a temporary NetworkCredential;
use it only in the Hypixel API header or the companion process environment. Never print
it. Do not change the machine's execution policy or store a plaintext fallback.
The encrypted credential is not portable and may expire; do not depend on it in CI.
Private Debotes API fixtures and owner-captured reference outputs (Mining Cat,
SkyCompass, in-game stat screenshots) are also in service-data. Commit only deliberately minimal,
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
