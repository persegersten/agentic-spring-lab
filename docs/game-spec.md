# Wreckage – Spel- och GUI-specifikation

Detta dokument beskriver produktflödet och GUI-förväntningarna för Wreckage
v2. De detaljerade spelreglerna finns i `docs/game-rules.md`, som är den
auktoritativa specifikationen vid varje eventuell motsägelse. Detta dokument ska
inte användas för att införa eller tolka ytterligare spelregler.

## 1. Spelkonfiguration

En spelare kan skapa ett nytt spel och blir spelets initiativtagare.

Följande parametrar ska kunna konfigureras:

* `maxPlayers` – maximalt antal spelare, mellan **2 och 10**.
* `joinTimeoutSeconds` – antal sekunder som andra spelare har på sig att ansluta.
* `planningTimeoutSeconds` – tidsgräns för spelarnas samtidiga planering.
* `programSize` – antal kommandon i varje spelares program.
* `roundLimit` – antal rundor i matchen.
* `mapId` – vilken kompatibel spelkarta som används.

GUI:t ska visa serverns konfigurationsvärden och valideringsresultat. Tillåtna
intervall, standardvärden och övriga konfigurationsregler definieras i
`docs/game-rules.md`.

Systemet skapar en unik spellänk som initiativtagaren kan dela med andra.

---

## 2. Lobby och anslutning

En person som öppnar spellänken kan ansluta till spelet genom att ange ett
nickname. Nicknamet ska vara unikt inom spelet.

Efter anslutning kommer spelaren till spelvyn och kan se:

* spelkartan,
* sitt eget fordon,
* övriga anslutna spelare,
* aktuell spelstatus,
* matchens konfiguration,
* återstående tid innan spelet startar.

Nya spelare får ansluta tills:

* `maxPlayers` har anslutit, eller
* `joinTimeoutSeconds` har löpt ut.

Spelet startar därefter enligt lobbyvillkoren i `docs/game-rules.md`, inklusive
kravet på minst två spelare.

---

## 3. Spelprocess

Spelet består av ett konfigurerat antal rundor. Varje runda följer det
utåtriktade flödet:

**PLANNING → RESOLVING → PLAYBACK**

Alla spelare planerar samtidigt. När planeringen är avslutad beräknar servern
hela rundans resultat innan uppspelningen börjar.

Servern är auktoritativ för spelstatus, initiativ, rörelser, interaktioner,
brädeffekter, krascher, poäng och matchresultat. Klienten skickar spelarens
avsikter och ansvarar för användarinteraktion och animation, men beräknar inte
spelresultat.

---

## 4. Samtidig programplanering

Under `PLANNING` bygger varje aktiv spelare ett ordnat program med exakt
`programSize` kommandon. Samma tillgängliga kommandon visas för alla spelare;
den fullständiga kommandouppsättningen och dess beteende definieras i
`docs/game-rules.md`.

GUI:t ska låta spelaren:

* välja kommandon till programmets register,
* se och ändra ordningen innan programmet låses,
* se hur mycket planeringstid som återstår,
* låsa ett komplett program,
* se vilka andra spelare som är klara utan att se deras program.

En spelares program är privat fram till resolution eller playback. Efter att
programmet har låsts kan det inte ändras under den aktuella rundan.

Planeringen avslutas när alla berörda spelare är klara eller när
`planningTimeoutSeconds` har löpt ut. Servern hanterar ofullständiga program vid
timeout enligt `docs/game-rules.md`; klienten ska inte själv fylla i eller
beräkna det slutliga programmet.

---

## 5. Auktoritativ resolution

Under `RESOLVING` bearbetar servern spelarnas program och brädets effekter i den
ordning som anges i `docs/game-rules.md`. Resolutionen är deterministisk och sker
helt på servern.

GUI:t ska visa att rundan beräknas men får inte flytta fordon, tilldela poäng
eller på annat sätt förutsäga resultatet medan resolutionen pågår.

---

## 6. Playback

Servern beräknar först hela rundans resultat och sparar både den resulterande
spelstatusen och en ordnad, auktoritativ eventsekvens. Eventen ska ge klienten
tillräckligt underlag för att visualisera exempelvis kommandon, rörelser,
rotationer, knuffar, blockerade förflyttningar, krascher, brädeffekter och
poängändringar utan att klienten tillämpar spelregler.

Samma auktoritativa sekvens skickas till alla klienter. Klienterna spelar upp
händelserna i serverns ordning som en gemensam animation. Playback får aldrig
påverka spelresultatet.

Efter playback går matchen vidare till nästa runda eller till avslutat läge om
`roundLimit` har uppnåtts. GUI:t ska under matchen visa aktuell runda och
poängställning och efter sista rundan visa serverns slutresultat.

---

## 7. Krascher och fortsatt deltagande

GUI:t ska kunna visa att ett fordon kraschar, tillfälligt tas bort från brädet
och senare kan återkomma enligt de auktoritativa reglerna. En krasch innebär
inte att spelaren permanent slås ut ur matchen. Detaljer om krasch, respawn och
poäng finns enbart i `docs/game-rules.md`.

---

## 8. Övergripande state machine

Det externt synliga flödet är:

```text
WAITING_FOR_PLAYERS
        ↓
     PLANNING
        ↓
    RESOLVING
        ↓
     PLAYBACK
        ↓
PLANNING eller FINISHED
```

Servern ansvarar för alla state transitions. Klienten visar serverns aktuella
status och använder den för att avgöra vilka kontroller som ska vara tillgängliga.

---

## 9. Återanslutning

En klient som tappar anslutningen eller laddar om sidan ska kunna återansluta
och återskapa den aktuella spelvyn från serverns state.

Den återskapade vyn ska omfatta relevant lobby- eller matchstatus, spelarens
privata planeringsdata när de får visas, aktuell runda, fordon, poäng och den
eventsekvens som behövs för korrekt playback. Klienten ska inte behöva återskapa
spelresultat från lokalt tillstånd.

---

## 10. Testability

The game must support deterministic automated testing.

Wreckage v2 core gameplay has no random behaviour. If randomness is introduced
by a future, explicitly specified feature, its source must be injectable or
persisted so tests and playback remain deterministic.

Time-dependent behaviour must be testable without relying on long real-world
waits.

The GUI must expose stable selectors for automated tests.

Server-generated game state and ordered round events are the authoritative
source used when verifying multiplayer behaviour, reconnection and playback.
