# cobblephase

Server-side Cobblemon helpers: habitat spawn phases and the Trainer PDA.

## Overview

Cobblemon habitats rotate through spawn phases, and each phase allows a different set of Pokémon. The phase is
worked out from the world's total game time, so it can't be seen in game. `/phase <habitat>` shows the current
phase, when it next changes and which species can spawn now. The Trainer PDA is a craftable gadget that pings you
when Pokémon you're looking for are nearby and points its needle at them. It's a small server-side Fabric mod:
players don't need to install it.

## Features

* **Phase lookup**: `/phase flowerbed_clearing` shows the current phase out of the habitat's total, the next phase and roughly when it starts.
* **Spawn list**: lists the species that can spawn in the current phase.
* **Autocomplete**: suggests every loaded habitat as you type, including habitats added by datapacks.
* **Full ids**: also accepts the `/locate structure` form, `cobblemon:habitats/flowerbed_clearing`.
* **Trainer PDA**: a craftable gadget that pings you when wild Pokémon matching your rules are nearby.
* **Ping rules**: match by species, group (legendary, mythical, ultra beast, paradox), spawn rarity, shininess and "not caught yet", each with its own sound.
* **Rule filters**: narrow a rule by type, form, nature, level range, perfect IVs and hidden ability.
* **Pokémon tracker**: see every way a Pokémon spawns (biomes, habitats, time, weather, sky) and teleport to the nearest suitable spot.
* **What spawns here?**: everything that can spawn in your current biome, with filters and sorting.
* **Habitat planner**: pick a Pokémon to see which habitats spawn it and when, and get reminded when its phase starts.
* **Wishboard**: post the Pokémon you're looking for; whoever catches one is told who wants it.
* **Proximity beeps**: the selected match's sound repeats faster as you get closer.
* **Needle and results**: the PDA's needle points at the selected match, and holding it lists every match with direction, distance and height.
* **Addon species**: reads Cobblemon's live species and spawn rarity lists, so datapack and addon Pokémon work too.
* **Outbreaks**: every hour or two, one species spawns 15 times as often for 20 minutes.
* **Condition alerts**: get pinged once when it next thunders, rains, clears, turns day, night, dawn or dusk, or a full or new moon rises.
* **Shiny guard**: your own hits and arrows can't hurt wild shinies (switchable).
* **Server Pokédex**: what nobody on the server has caught yet, and who caught each species first.
* **Evolution and EV helpers**: what your party needs to evolve (with a ping when it's ready), and your EVs with what to battle for each stat.
* **Habitat observer**: read a real habitat block's phase, including random phase orders.
* **News**: a login summary of what you missed, and a news page in the PDA.
* **Trainer visits**: with Radical Cobblemon Trainers installed, your next trainer turns up within 10 blocks every 20–40 minutes.
* **Catch news**: tells everyone online when someone catches a shiny or the server's first of a species.
* **Server-side only**: vanilla command syntax and vanilla menus, so clients need nothing beyond Cobblemon.

## Installation

### Requirements

- Java 21
- Minecraft 1.21.1 with Fabric Loader and Fabric API
- Cobblemon 1.8 or newer
- Optional: Radical Cobblemon Trainers 0.19 (for trainer visits)

### Build

```sh
git clone https://github.com/GrunclePug/cobblephase.git
cd cobblephase
./gradlew build
```

Copy `build/libs/cobblephase-<version>.jar` into the server's `mods` folder and restart the server.

## Usage

```
/phase flowerbed_clearing
/phase cobblemon:habitats/flowerbed_clearing
```

```
Flowerbed Clearing: phase 5/9, next 6 in ~12m 20s
Spawning now: Bulbasaur, Ivysaur, Venusaur, ...
```

Type `/phase ` and scroll the suggestions, or type part of a name and press Tab. Any player can use it.

## Trainer PDA

Craft it from a compass, four redstone and any basic Poké Ball:

```
    Redstone
Redstone  Compass  Redstone
    Poké Ball
```

| Key | Action |
| --- | --- |
| Right-click | Open the PDA menu |
| Sneak + right-click | Point the needle at the next result |
| Hold in either hand | Show results above the hotbar |

Pings work while a PDA is anywhere in your inventory. When a matching wild Pokémon is within range you hear the
rule's sound, from the Pokémon's direction, and get a chat line:

```
[PDA] Shiny Eevee · Rare · 42m NE (120, 64, -340)
```

The sound then repeats for the selected result (the one the needle points at), faster the closer you get: about
every 5 seconds at the edge of your range, down to every 0.4 seconds when it's next to you. The menu's **Repeat**
button switches between always, only while the PDA is held, and off (a single ping when found).

While you hold the PDA, the bar above the hotbar lists matches as `▶ ↗ Eevee 42m · ← Pikachu 80m ▼12`: the arrow
is relative to where you're facing and `▲`/`▼` shows the height difference. Only the closest of each species is
listed, with shinies listed separately.

### Rules

Open the menu and pick **New rule**. A Pokémon matches a rule when it matches everything you've set:

- **Pokémon or groups**: any chosen species, or any Pokémon in a chosen group. Use **Search** to type names in
  chat; exact names, comma-separated, are added directly.
- **Rarity**: the spawn bucket it spawned from (common, uncommon, rare, ultra-rare, boss, plus any the server
  adds). None chosen means any rarity.
- **Shiny only**.
- **Not caught yet**: only species missing from your own Pokédex. On its own, it pings for every species you
  still need.
- **More filters**: types, forms (Alolan, Galarian, Mega and any others the server's species have), natures, a
  minimum and maximum level, a minimum number of perfect (31) IVs, and hidden ability. Any filter alone is
  enough for a rule, so "any Water type" works by itself.

Each rule has its own sound. Rules are checked top to bottom and the first match decides the sound. The menu also
lists nearby matches (click one to point the needle at it), and its **Settings** page sets the range (32 to 128
blocks), repeat, mute, catch news, shiny guard and trainer visits.
Rules are saved on the player, so they survive death and a lost PDA.

Rarity is recorded when Cobblemon spawns a Pokémon, from natural spawning and habitats. Pokémon from fishing, bait
or commands have no rarity, so rules with a rarity never match them.
## How phases work

A habitat's phase count is the number of distinct phase numbers in its spawn list. Its phase is
`(gameTime / 24000) % count + 1`, so it moves on once every 24000 ticks (20 minutes at 20 TPS). It uses total
game time, not time of day, so `/time set`, `/time add` and sleeping don't change it.

The command assumes the habitat's default "simple" phase order. A habitat block switched to a random phase order
shuffles its phases by position, so it won't match.

## Pokémon tracker

Open **Pokémon tracker** in the PDA menu and pick a Pokémon. Each way it spawns gets its own card: natural spawn
entries (biomes, time of day, weather, sky, height, rarity, level, and anything that makes it more likely, like
thunderstorms for Zapdos) and habitats that can spawn it.

- **Left-click a spawn card** to find the nearest spot in one of its biomes and teleport there. Right-click it to
  pick one specific biome instead.
- **Left-click a habitat card** to find the nearest habitat of that kind and teleport there. Right-click sets a
  phase reminder.
- **Ping me for it** adds a PDA rule for the Pokémon.

Teleports are free, have no cooldown, stay in your current dimension and reach up to 10,000 blocks. They land you on
safe ground and work with Essential Commands' `/back`. Searching runs in the background, so it never stalls the
server; it can take a few seconds for rare biomes. Finding a spot finds the right terrain; it doesn't guarantee the
Pokémon is there.

After each teleport, chat asks whether it was a good spot. **[Good]** saves it, and later searches for the same
biome or habitat go there first; **[Bad]** makes everyone's later searches skip that area (a tiny biome patch, say).
Clicking neither changes nothing.

## What spawns here?

**What spawns here?** in the PDA menu lists every Pokémon that can spawn naturally in the biome you're standing in.
Filter to what can spawn right now (time, weather, sky), by rarity, by type, or to species you haven't caught; sort
by rarity, how common, name or Pokédex number. Click one to open it in the tracker.

## Habitat planner

Open **Habitat planner** in the PDA menu and pick a Pokémon (only ones some habitat can spawn are listed). Each
habitat that can spawn it shows the phases it spawns in, the current phase, and when its next window starts.
Click a habitat to be reminded: you get a chat ping once, the next time that habitat's phase allows the spawn,
or when you log in during such a phase. **Your reminders** lists and removes them. Like `/phase`, it assumes a habitat's default phase
order.

## Wishboard

**Wishboard** in the PDA menu lists the Pokémon everyone is looking for. **Post a wish** picks a species, with an
optional shiny-only flag and a short note typed in chat. When someone else catches a match, they're told who
wished for it, and the wisher is told who caught it, so you can sort out a trade. Each player can post 9 wishes;
wishes expire after 14 days, and you can remove your own at any time.

## Outbreaks

Every 60 to 120 minutes while someone is online, one species (common, uncommon or rare, never a legendary,
mythical, ultra beast or paradox) has an outbreak for 20 minutes: whenever its rarity comes up, it's 15 times as
likely to be the one that spawns. Players with catch news on get a chat line when it starts and ends, and the PDA
home screen shows the current outbreak; click it to open the species in the tracker.

## Condition alerts and shiny guard

**Condition alerts** in the PDA menu pings you once when a condition next starts in the overworld: thunderstorm,
rain, clear weather, dawn, day, dusk, night, full moon or new moon. Times and moon phases use Cobblemon's own
ranges, so they match spawn conditions.

**Shiny guard** (on by default, toggle on the PDA home screen) cancels damage from your own attacks and projectiles
to wild shinies, so a stray swing can't kill one. Battles aren't affected.

## Party helpers, Pokédex, news

- **Evolution helper**: pick a party Pokémon to see each of its evolutions, how it's triggered (level up, item,
  trade, block) and every requirement with ✔ or ✘. **Pin it** to be told once when all of one evolution's
  requirements are met, which helps with time-, place- or friendship-based evolutions.
- **EV helper**: a party Pokémon's EVs per stat (out of 252, total out of 510); pick a stat to list the Pokémon that
  give the most of it, and open any of them in the tracker.
- **Server Pokédex**: how many species the server has caught, which ones nobody has caught yet, and who caught each
  first (species caught before the mod was installed show as caught before tracking began).
- **News**: recent server firsts, shiny catches, wishes and outbreaks, plus notes just for you (someone caught your
  wish). When you log in, chat summarizes what happened since you left.

## Habitat observer

Right-click a habitat block with the PDA, or use **Nearest habitat block** in the habitat planner, to read that
habitat's real state: its pool, phase order (simple, fixed random or full random), current phase, when it next
changes and what it can spawn now. Unlike `/phase`, this is exact for habitats set to a random phase order.

## Trainer visits

With [Radical Cobblemon Trainers](https://modrinth.com/mod/rctmod) installed, every 20 to 40 minutes of play the
next trainer in your current RCT series (the one its trainer card shows, e.g. Gym Leader Gardenia) turns up 5 to 10
blocks from you to battle. If your series has several next trainers, one is picked at random. The trainer is spawned
by RCT itself, so its rules still apply: unique trainers like gym leaders only appear once at a time, and RCT's
per-player trainer limit counts it. The spot must be dry, solid ground. RCT's server-wide dimension and biome lists
apply, and so does each trainer's biome blacklist (Gardenia won't visit you in the Nether), but not trainers' biome
whitelists, since the point is that they come to you. Visits wait while you're in a battle or somewhere they can't
go, and try again a minute later. The countdown only runs while you're online and carries over between sessions.

The home screen shows your next trainer and roughly when the next visit is; operators can click it to call one now.
Turn visits off under **Settings**. Without RCT, the feature stays off.

## Catch news

When someone catches a shiny, or a species nobody on the server has caught before, everyone online gets a chat
line; hover the Pokémon's name for its level, nature and ability:

```
★ Oddie caught a Shiny Gible!
[Catch] moomoocat28 caught the server's first Dratini!
```

On first start the mod reads every known player's Pokédex, so species caught before it was installed don't count
as firsts. New wishes are announced the same way. Each player can turn the news off with **Catch news** in the PDA menu.

## Development

- Build: `./gradlew build`
- Clean: `./gradlew clean`

## Contributing

Fork the repository and open a pull request.

## License

MIT. See [LICENSE](LICENSE).

## Author

Chad Humphries | [Website](https://grunclepug.com/) | [GitHub](https://github.com/GrunclePug) | [More projects](https://github.com/GrunclePug?tab=repositories)
