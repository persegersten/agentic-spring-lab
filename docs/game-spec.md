# Wreckage – Spel- och GUI-specifikation

Detta dokument beskriver produktflödet och GUI-förväntningarna för Wreckage
v2. De detaljerade spelreglerna finns i `docs/game-rules.md`, som är den
auktoritativa specifikationen vid varje eventuell motsägelse. Detta dokument ska
inte användas för att införa eller tolka ytterligare spelregler.

## 1. Spelstart

Startsidan är en huvudlobby där en spelare kan skapa ett nytt spel och blir
spelets initiativtagare. Standardkonfigurationen används utan att startsidan
visar tekniska konfigurationsfält. Därefter navigeras initiativtagaren till
spelets unika lobby på `/game/{id}/lobby`, anger sitt namn och kan dela dess länk.
Efter anslutning visas ingen spelplan förrän värden startar spelet. Vid start
navigerar alla anslutna spelare till `/game/{id}`. En ansluten spelare som
öppnar lobbylänken efter start navigeras också till spelplanen. Nya spelare
får i stället ett meddelande om att lobbyn är stängd.

Följande parametrar ska kunna konfigureras:

* `maxPlayers` – maximalt antal spelare, mellan **2 och 10**.
* `joinTimeoutSeconds` – antal sekunder som andra spelare har på sig att ansluta.
* `planningTimeoutSeconds` – tidsgräns för spelarnas samtidiga planering.
* `programSize` – fem register i varje spelares program.
* `mapId` – vilken kompatibel spelkarta som används.

När lobbyn stängs bestämmer servern brädstorlek och `roundLimit` från antalet
spelare som faktiskt har anslutit: 2–3 ger 10×10 och 7 rundor, 4–6 ger 12×12
och 6 rundor, samt 7–10 ger 16×16 och 5 rundor. Värdena kan inte väljas av
klienten och ändras inte under matchen.

Tillåtna intervall, standardvärden och övriga konfigurationsregler definieras i
`docs/game-rules.md`.

---

## 2. Lobby och anslutning

En person som öppnar spellänken kan ansluta till spelet genom att ange ett
nickname. Nicknamet ska vara unikt inom spelet.

Efter anslutning stannar spelaren i spel-lobbyn och kan se:

* övriga anslutna spelare,
* aktuell spelstatus,
* varje spelares offentliga val av primärvapen och specialförmåga.

Varje ny spelare har direkt standardvalet `LASER + SHIELD`. Den egna loadouten
kan ändras med två snabba val medan lobbyn är öppen; vapenvalen är Laser,
Repulsor och Rocket och förmågevalen är Turbo, Shield, Side Step och Anchor.
Kontrollerna visar korta regelbeskrivningar men kräver ingen separat bekräftelse.
När värden startar matchen låses samtliga loadouts för resten av matchen.

Initiativtagaren har en startknapp som inte visas för övriga spelare. Spelet kan
startas när minst två spelare har anslutit. Vid starten väljer servern brädstorlek
och rundantal utifrån det faktiska spelarantalet.

Nya spelare får ansluta tills kapaciteten är nådd eller initiativtagaren startar
spelet. Efter start är lobbyn permanent stängd för nya spelare.

En ansluten mänsklig spelare kan lägga till en bot i den aktuella lobbyn. Varje
tryck lägger till en bot med ett unikt namn och samma medlemskap, startposition,
loadout och spelregler som övriga spelare. Kontrollen är inte tillgänglig när
lobbyn är full eller matchen har startat. Botar låser automatiskt sina utdelade
program varje runda och visas uttryckligen som botar för alla anslutna spelare.

---

## 3. Spelprocess

Spelet består av ett konfigurerat antal rundor. Varje runda följer det
utåtriktade flödet:

**PLANNING → RESOLVING → PLAYBACK**

Alla spelare planerar samtidigt. När planeringen är avslutad beräknar servern
hela rundans resultat innan uppspelningen börjar.

Servern är auktoritativ för spelstatus, initiativ, rörelser, actions, vapen,
skada, interaktioner, brädeffekter, krascher, poäng och matchresultat. Klienten
skickar spelarens avsikter och ansvarar för användarinteraktion och animation,
men beräknar inte spelresultat.

---

## 4. Samtidig programplanering

Under `PLANNING` delar servern ut en privat hand med åtta viktat slumpade kort
till varje aktiv spelare. Spelaren väljer exakt fem av de utdelade korten och
ordnar dem i fem register. Dubbletter får bara användas så många gånger som de
förekommer på handen. Den fullständiga kortuppsättningen och dess beteende
definieras i `docs/game-rules.md`.

Spelaren får dessutom välja högst en action för rundan och koppla den till ett
av programmets register, eller uttryckligen välja att inte använda någon action.
GUI:t visar endast actions som är tillåtna av fordonets fasta primärvapen och
specialförmåga samt aktuell ammunition. För `SIDE_STEP` väljer spelaren vänster
eller höger riktning. Loadouten kommer från spelarens val i lobbyn och kan inte
ändras efter matchstart.

GUI:t ska låta spelaren:

* välja kommandon till programmets register,
* se och ändra ordningen innan programmet låses,
* välja, ändra eller ta bort rundans action och dess register innan låsning,
* se sitt fordons fasta vapen, specialförmåga och återstående Rocket-ammunition,
* se hur mycket planeringstid som återstår,
* låsa ett komplett program,
* se vilka andra spelare som är klara utan att se deras program.

En spelares program och action är privata fram till resolution eller playback.
Andra spelare får inte se action-typ eller register under planeringen. Efter att
programmet har låsts kan varken programmet eller action-valet ändras under den
aktuella rundan.

Planeringen avslutas när alla berörda spelare är klara eller när
`planningTimeoutSeconds` har löpt ut. Servern hanterar ofullständiga program vid
timeout enligt `docs/game-rules.md`; klienten ska inte själv fylla i eller
beräkna det slutliga programmet. Timeout skapar inte någon action åt en spelare
som inte har valt en.

---

## 5. Auktoritativ resolution

Under `RESOLVING` bearbetar servern spelarnas actions, program och brädets
effekter i den registerordning som anges i `docs/game-rules.md`. Resolutionen är
deterministisk och sker helt på servern.

GUI:t ska visa att rundan beräknas men får inte flytta fordon, avgöra siktlinje,
beräkna skada eller knuffar, tilldela poäng eller på annat sätt förutsäga
resultatet medan resolutionen pågår.

---

## 6. Playback

Servern beräknar först hela rundans resultat och sparar både den resulterande
spelstatusen och en ordnad, auktoritativ eventsekvens. Eventen ska ge klienten
tillräckligt underlag för att visualisera exempelvis kommandon, actions,
vapeneld, blockerade skott, träffar, applicerad eller förhindrad skada,
förmågeaktiveringar, ammunitionsändringar, rörelser, rotationer, knuffar,
blockerade förflyttningar, krascher, brädeffekter och poängändringar utan att
klienten tillämpar spelregler.

Samma auktoritativa sekvens skickas till alla klienter. Klienterna spelar upp
händelserna i serverns ordning som en gemensam animation. Playback får aldrig
påverka spelresultatet.

Efter playback går matchen vidare till nästa runda eller till avslutat läge om
`roundLimit` har uppnåtts. GUI:t ska under matchen visa aktuell runda och
poängställning och efter sista rundan visa serverns slutresultat. Fordonens
aktuella skada, loadout och relevanta ammunition ska visas från serverns state;
klienten får inte härleda dessa värden från animationen som en alternativ
sanningskälla.

---

## 7. Krascher och fortsatt deltagande

GUI:t ska kunna visa att ett fordon kraschar, tillfälligt tas bort från brädet
och senare kan återkomma enligt de auktoritativa reglerna. En krasch innebär
inte att spelaren permanent slås ut ur matchen. Detaljer om krasch, respawn och
poäng finns enbart i `docs/game-rules.md`. GUI:t ska återge serverns uppdaterade
skadevärde efter en lyckad respawn.

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
privata program och action-val när de får visas, aktuell runda, fordonens
loadout, skada och ammunition, poäng samt den eventsekvens som behövs för
korrekt playback. Klienten ska inte behöva återskapa spelresultat från lokalt
tillstånd.

---

## 10. Testability

The game must support deterministic automated testing.

The server-side programming-card deal uses an injectable random source and the
resulting hands are persisted. Resolution and playback remain deterministic.

Time-dependent behaviour must be testable without relying on long real-world
waits.

The GUI must expose stable selectors for automated tests.

Server-generated game state and ordered round events are the authoritative
source used when verifying multiplayer behaviour, reconnection and playback.
