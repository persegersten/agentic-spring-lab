---
name: grill
description: Interrogate a brief or plan before anything gets built. Surfaces hidden decisions, unscoped work, and the edges nobody described. Use when the user has a brief, plan, ticket or spec file and wants it stress-tested, challenged, poked at, or torn apart before implementation.
argument-hint: <path-to-brief> [ticket|idea]
disable-model-invocation: true
allowed-tools: Read Glob Grep Bash(cat *)
---

# Grill

## The document

```!
cat "$0" 2>/dev/null || echo "NO FILE READ: argument was '$0'"
```

## Mode

Second argument was: `$1`

- `ticket` — this came from someone else. Run **Interrogation**.
- `idea` — the user wrote it themselves. Run **Compression**.
- Empty or anything else — decide from the document itself. A document that describes a
  request from a named person, or that reads like it was handed down, is `ticket`. A document
  the user clearly authored about their own idea is `idea`. Say in one line which you chose
  and why, then proceed.

If the block above says NO FILE READ, stop. Ask for the path and do nothing else.

## Interrogation (ticket mode)

The danger here is **decisions someone else skipped without noticing**. They are yours now.
Three passes:

1. **Ambiguous nouns.** Every noun that could mean two things is a decision wearing a word.
   Quote each one and give the two readings, concretely. Not "'recently' is vague" but
   "'recently' could mean the last 7 days or since the last release — those are different
   screens."
2. **The edges.** What happens with zero items, one item, a thousand, two people at once, bad
   input, and the thing that already exists? Briefs describe the happy path and nothing else.
3. **The sentence hiding the most work.** There is always one and it is usually short.
   "Should be quick." "Pull it from wherever." "Nice if it could also..." Name it, say what it
   actually costs, and say whether it belongs in this build at all.

## Compression (idea mode)

The danger here is the opposite: **no edges at all**. Do not ask for clarification — the user
has nothing to clarify yet. Make them cut. Check the five moves and fail them honestly:

1. **One sentence, no conjunctions.** If the sentence has an "and" joining two capabilities,
   name both and ask which one they would keep.
2. **The one screen.** Can you picture it? If the description would produce three different
   screens in three different people's heads, say so and describe all three.
3. **The user and the moment.** If the answer is effectively "everyone, whenever", the user
   has not been found. Propose the two most likely real ones.
4. **The out-of-scope list.** This is the one that matters. If it is missing, empty, or made
   of things nobody would have built anyway, that is the finding. Propose three real
   exclusions — things genuinely tempting that should be cut.
5. **The first demoable slice.** If it is "set up the project" or "scaffold the app", reject
   it and propose a slice they could actually look at.

Then: **what are they pretending is simple?** Find the part they wrote in half a sentence
that is actually most of the work.

## Output

Be blunt and specific. Vague criticism is useless and comfortable.

1. **Verdict** — one line: ready to plan, or not yet, and the single reason.
2. **Findings** — numbered, worst first. Each one quotes the document, says what breaks, and
   proposes a concrete call the user can accept or reject.
3. **The one thing to fix before planning** — exactly one. If everything is a priority the
   user will do none of it.

Do not rewrite the document. Do not start implementing. Do not soften findings to be
agreeable — a brief that survives a soft grilling fails later, expensively, in code.
