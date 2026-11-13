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
* **Ping rules**: match by species, group (legendary, mythical, ultra beast, paradox), spawn rarity and shininess, each with its own sound.
* **Needle and results**: the PDA's needle points at the selected match, and holding it lists every match with direction, distance and height.
* **Addon species**: reads Cobblemon's live species and spawn rarity lists, so datapack and addon Pokémon work too.
* **Server-side only**: vanilla command syntax and vanilla menus, so clients need nothing beyond Cobblemon.

## Installation

### Requirements

- Java 21
- Minecraft 1.21.1 with Fabric Loader and Fabric API
- Cobblemon 1.8 or newer

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

Each rule has its own sound. Rules are checked top to bottom and the first match decides the sound. The menu also
sets the range (32 to 128 blocks), mutes pings, and lists nearby matches; click one to point the needle at it.
Rules are saved on the player, so they survive death and a lost PDA.

Rarity is recorded when Cobblemon spawns a Pokémon, from natural spawning and habitats. Pokémon from fishing, bait
or commands have no rarity, so rules with a rarity never match them.
## How phases work

A habitat's phase count is the number of distinct phase numbers in its spawn list. Its phase is
`(gameTime / 24000) % count + 1`, so it moves on once every 24000 ticks (20 minutes at 20 TPS). It uses total
game time, not time of day, so `/time set`, `/time add` and sleeping don't change it.

The command assumes the habitat's default "simple" phase order. A habitat block switched to a random phase order
shuffles its phases by position, so it won't match.

## Development

- Build: `./gradlew build`
- Clean: `./gradlew clean`

## Contributing

Fork the repository and open a pull request.

## License

MIT. See [LICENSE](LICENSE).

## Author

Chad Humphries | [Website](https://grunclepug.com/) | [GitHub](https://github.com/GrunclePug) | [More projects](https://github.com/GrunclePug?tab=repositories)
