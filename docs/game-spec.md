# Wreckage – Spel- och GUI-specifikation

## 1. Spelkonfiguration

En spelare kan skapa ett nytt spel och blir spelets initiativtagare.

Följande parametrar ska kunna konfigureras:

* `maxPlayers` – maximalt antal spelare, initialt **12**.
* `joinTimeoutSeconds` – antal sekunder som andra spelare har på sig att ansluta.
* `cardsPerRound` – antal kommandokort per spelare och runda, **3–10**.
* `planningTimeoutSeconds` – tidsgräns för spelarnas planering.

Systemet skapar en unik spellänk som initiativtagaren kan dela med andra.

---

## 2. Lobby och anslutning

En person som öppnar spellänken kan ansluta till spelet genom att ange ett nickname.

Nicknamet ska vara unikt inom spelet.

Efter anslutning kommer spelaren direkt till spelvyn och kan se:

* spelkartan,
* sitt eget fordon,
* övriga anslutna spelare,
* gameboard,
* aktuell spelstatus,
* återstående tid innan spelet startar.

Nya spelare får ansluta tills:

* `maxPlayers` har anslutit, eller
* `joinTimeoutSeconds` har löpt ut.

Spelet startar därefter automatiskt om minst två spelare har anslutit.

---

# 3. Spelprocess

Spelet består av rundor.

Varje runda följer processen:

**PLANNING → MOVEMENT/ACTIONS → BOARD EFFECTS → PLAYBACK**

Servern är auktoritativ. Alla spelregler och resultat beräknas på servern. Klienten ansvarar huvudsakligen för användarinteraktion och animation.

---

## 4. Planning

I början av varje runda får varje aktiv spelare `cardsPerRound` slumpmässigt valda kommandokort.

Antalet kan konfigureras mellan **3 och 10**.

Exempel på kort:

* `FORWARD`
* `REVERSE`
* `TURN_LEFT`
* `TURN_RIGHT`

Spelaren ser endast sina egna kort och placerar dem i den ordning de ska utföras.

Exempel:

`FORWARD → TURN_LEFT → FORWARD → REVERSE`

När spelaren är klar bekräftas valet.

Övriga spelare kan se att spelaren är klar men inte vilka kort eller vilken ordning spelaren valt.

Planning avslutas när samtliga spelare är klara eller `planningTimeoutSeconds` har löpt ut.

---

## 5. Movement / Actions

Servern utför spelarnas programmerade kommandon ett kortsteg i taget.

Med tre spelare och tre kort:

`A1 → B1 → C1 → A2 → B2 → C2 → A3 → B3 → C3`

Samma princip gäller för valfritt `cardsPerRound`.

Ordningen mellan spelarna ska vara deterministisk.

### Ramning

Om ett fordon kör in i ett annat kan det knuffa framförvarande fordon.

Knuffar kan fortplantas genom flera fordon:

`A → B → C → tom ruta`

kan bli:

`tom ruta → A → B → C`

Spelare kan därmed ramma andra in i väggar, miljöfaror eller ut från spelområdet.

### Automatisk kanon

Varje fordon har en fast kanon riktad i fordonets färdriktning.

Kanonen avfyras automatiskt efter att de programmerade rörelserna utförts.

Den första spelaren i skottlinjen träffas. Väggar och andra blockerande objekt stoppar skottet.

Träffar kan orsaka skada och malfunction.

---

# 6. Board Effects

Efter Movement/Actions aktiveras spelplanens miljöeffekter.

Spelplanen kan exempelvis innehålla:

* hål och stup,
* minor,
* transportband,
* roterande plattformar,
* fasta kanoner,
* eld,
* pressar och andra miljöfaror.

Effekterna behandlas i en definierad och deterministisk ordning.

Miljön är därmed en del av stridssystemet. En spelare kan exempelvis ramma en motståndare framför en kanon, ner i ett hål eller till en annan farlig position.

---

# 7. Skada och Malfunction

Skada ska kunna påverka spelarens kontroll över sitt fordon.

En skadad spelare kan få `MALFUNCTION`-kort.

Exempel:

* `MALFUNCTION: REVERSE`
* `MALFUNCTION: TURN_LEFT`
* `MALFUNCTION: FORWARD`
* `WEAPON_FAILURE`
* `STUCK`

Vissa malfunction-kort kan vara obligatoriska att använda under nästa Planning-fas och ersätter därmed ett normalt val.

Mer skada kan därför innebära mindre kontroll över fordonet snarare än enbart minskade hit points.

Exakta regler för skada, reparation och malfunction specificeras separat.

---

# 8. Playback

Servern beräknar först hela rundans resultat.

Alla relevanta händelser sparas som en ordnad sekvens av events, exempelvis:

`Player A moves forward`

`Player A rams Player B`

`Player B moves onto mine`

`Player C turns left`

`Player C fires cannon`

`Player A is hit`

`Conveyor moves Player B`

`Player B falls into pit`

Eventsekvensen skickas till samtliga klienter.

Klienterna spelar sedan upp händelserna som en gemensam animation där spelarna kan se kort, rörelser, ramningar, skott, träffar och miljöeffekter.

Klienten beräknar inte spelresultatet utan visualiserar serverns resultat.

När Playback är färdig startar nästa runda.

---

# 9. Övergripande state machine

Spelet följer:

`WAITING_FOR_PLAYERS`

↓

`PLANNING`

↓

`MOVEMENT_ACTIONS`

↓

`BOARD_EFFECTS`

↓

`PLAYBACK`

↓

`PLANNING`

↓

`...`

Servern ansvarar för alla state transitions.

En klient som tappar anslutningen eller laddar om sidan ska kunna återansluta och återskapa aktuell spelvy från serverns state.

## Testability

The game must support deterministic automated testing.

Random game behaviour must be based on an injectable or seedable
random source.

Time-dependent behaviour must be testable without relying on long
real-world waits.

The GUI must expose stable selectors for automated tests.

Server-generated game state and round events are the authoritative
source used when verifying multiplayer behaviour.
