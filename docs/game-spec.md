# Wreckage — Product and UI specification

`docs/game-rules.md` is authoritative for gameplay.

The home page creates a game and routes the host to `/game/{id}/lobby`. Players join with a unique nickname and see the connected players and lobby status. There is no weapon or loadout selection. The authenticated host may start once at least two players have joined; late joins are rejected.

During planning, the player sees all eight dealt cards, five ordered register slots, the remaining planning time, other players' readiness, and a checkbox labelled “Activate shield for this round.” Shield status must clearly show Available, Selected, Active, or Consumed and explain that it is a one-time resource. The shield is not a card and does not consume a register.

During resolution and playback, the client displays the current round and register, authoritative robot movement, Laser fire and displacement, ramming and pushes, shield activation and blocked displacement, crashes, board effects, and checkpoint captures. The client must not calculate game results.

The game view shows checkpoint progress and the current result. It does not show weapon controls, loadouts, ammunition, damage, hit points, or combat scores. At CP4 or after the maximum-round fallback, it shows the server-provided winner or draw.

Player credentials remain in per-game local storage and are sent for authenticated reads and mutations. Reloading restores the private hand and program during planning, the player's shield state, and any persisted playback.
