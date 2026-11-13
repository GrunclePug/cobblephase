# cobblephase

A `/phase` command that shows a Cobblemon habitat's current spawn phase.

## Overview

Cobblemon habitats rotate through spawn phases, and each phase allows a different set of Pokémon. The phase is
worked out from the world's total game time, so it can't be seen in game. `/phase <habitat>` shows the current
phase, when it next changes and which species can spawn now. It's a small server-side Fabric mod: players don't
need to install it.

## Features

* **Phase lookup**: `/phase flowerbed_clearing` shows the current phase out of the habitat's total, the next phase and roughly when it starts.
* **Spawn list**: lists the species that can spawn in the current phase.
* **Autocomplete**: suggests every loaded habitat as you type, including habitats added by datapacks.
* **Full ids**: also accepts the `/locate structure` form, `cobblemon:habitats/flowerbed_clearing`.
* **Server-side only**: vanilla command syntax, so clients need nothing extra.

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
