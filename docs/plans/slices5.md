# Action audio tuning — slice plan

Source: [docs/dev/idea5.md](../dev/idea5.md). Its final scope decisions supersede earlier wording about priority, once-per-event playback, new assets, and layering.

This is one small slice, as explicitly requested. Baseline capture, tuning, and verification are work within that slice rather than separate deliverables.

1. **Louder action cues with the strongest celebration reserved for victory**
   - **What changes:** Laser firing, laser impact, and flag capture sound noticeably louder; the existing victory cue delivers the strongest perceived audio impact in the existing live-game presentation.
   - **How to verify manually:** Before changing gains, play and record a short live-game sequence containing movement, laser firing, laser impact, flag capture, and a winning finish, including overlapping action sounds. Repeat the same sequence after one focused tuning pass using the same device, browser, event timing, and master volume. Listen to both recordings: the three action cues must be noticeably louder, firing must remain distinct from impact, victory must have the strongest perceived impact, and movement/background levels must remain unchanged. Repeat the overlapping portion at maximum master volume and listen for clipping or distortion. Observe another player's browser to confirm existing recipient behavior; toggle mute, unmute, and adjust master volume to confirm settings still work without replaying consumed events. Run existing audio-policy and relevant browser audio checks plus the frontend build; inspect mixed-signal peaks for the verification sequence as supporting evidence. Record actual results and any unavailable listening checks, and update the documented gain table in docs/assets.md.
   - **What it deliberately does not do yet:** No asset edits, layering, new dependencies, playback-policy changes, recipient changes, timing changes, backend changes, or audio-control changes; no further aesthetic tuning passes.

## The riskiest slice

Slice 1 is the only slice and puts the risk first: stronger individual gains can produce excessive combined volume during overlaps, while perceived victory impact depends on the existing recording and listening device. Capture the baseline before editing, use conservative gains, and verify the overlap mix during the single focused tuning pass. Do not solve failures by adding ducking, limiting, interruption, or new suppression policies. Signal measurements support the listening comparison but do not establish perceived quality or absence of audible distortion by themselves.

## What is not in any slice

No accepted requirement is deferred. Earlier suggestions for new assets, layering, literal playback of every event, or new victory priorities are excluded by the final scope decisions. External sound-library searches, architecture changes, additional screens, production deployment, and subjective audio perfection are outside scope.

Implementation is expected to change only the four cue gains and their documentation. Existing cooldowns, voice limits, suppression, HTML5 fallback behavior, event deduplication, mute/volume handling, recipient behavior, and all other cue gains remain unchanged. Exact gains are implementation decisions.
