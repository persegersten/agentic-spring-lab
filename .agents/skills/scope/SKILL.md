---
name: scope
description: Cut an approved brief or plan into slices that can each be built and verified on their own. Use when a plan is agreed and the next question is what order to build it in, or when a task feels too big to hand over in one go.
argument-hint: <path-to-brief-or-plan>
disable-model-invocation: true
allowed-tools: Read Glob Grep Bash(cat *)
---

# Scope

## The document

```!
cat "$0" 2>/dev/null || echo "NO FILE READ: argument was '$0'"
```

If the block above says NO FILE READ, stop and ask for the path.

## What a slice is

A slice is **one thing you can look at and judge.** Not a layer, not a file, not a phase.

Three tests. A slice that fails any of them is not a slice:

1. **Visible.** When it is done, something observable changed — a screen renders, a command
   prints, a request returns. "Set up the data model" fails this. "The table shows three
   hardcoded rows" passes.
2. **Verifiable without the next slice.** You can confirm it works before building anything
   else. If confirming it requires slice 4, it belongs with slice 4.
3. **Revertible.** It is one commit. If it goes wrong, throwing it away loses an hour, not a
   day.

## What to produce

**A numbered list of slices, in build order.** Aim for 3 to 6. More than 8 means the brief is
too big and you should say so instead of slicing it. For each slice:

- **What changes** — in one line, in terms of behaviour, not files.
- **How to verify** — the exact thing a human does with their own hands. A command to run, a
  URL to open, a click. Not "check that it works" and not "the tests pass".
- **What it deliberately does not do yet** — one line. This is what keeps the slice small
  when you are mid-build and tempted.

Then, separately:

**The riskiest slice.** Name the one most likely to go wrong or take twice its estimate, and
say why. If it is not slice 1, say whether it should be — doing the risky part first means
finding out early, while there is still time to change the plan.

**What is not in any slice.** Anything in the brief you could not place. That is either
scope you forgot or scope that should be cut, and either way the user needs to see it now
rather than discover it at the end.

## Rules

- Do not implement anything. This produces a build order, nothing else.
- Do not invent requirements the document does not contain. If a slice needs a decision that
  has not been made, say so and stop at that slice.
- Order for **early feedback**, not for architectural tidiness. Building the data layer first
  is tidy and tells you nothing for an hour.
