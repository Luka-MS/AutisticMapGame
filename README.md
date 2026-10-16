# Map Game

A real-time grand-strategy map game written in plain Java (Swing, no external dependencies), inspired by *Hearts of Iron* and *Age of History*.
Pick a nation, raise armies, declare wars and conquer the map - on a randomly generated world, on Europe or on the whole Earth with real borders.

## Features

- **Three worlds** chosen from a main menu: a procedurally generated random world, Europe, and the whole world with all of its real nations.
- **Real maps** built from [Natural Earth](https://www.naturalearthdata.com/) data: real countries, borders and coastlines, terrain derived from real mountain ranges, deserts and tundra, and province population derived from real country populations (scaled for gameplay).
- **Province-based map** with up to ~2,000 provinces, a political and a terrain map mode, pan and zoom.
- **Real-time calendar** starting in 1900 with pause and five game speeds.
- **Economy:** population produces gold and manpower every month; armies cost upkeep.
- **Armies:** pathfinding by terrain travel time, battles with terrain defence bonuses, retreats, occupation of provinces, merging and splitting of armies.
- **Army groups with fronts** (Hearts of Iron style): assign armies to a group, pick an enemy and a stance, and the group spreads its men evenly along the front by itself.
- **Diplomacy:** declare war, propose peace (decided by war score), post-war truces, and **total war** that can never end in peace.
- **AI nations** that recruit, attack, make peace and declare wars.
- **Sea crossings:** islands are linked to the mainland over narrow straits (e.g. UK-France, Japan-Korea) as long as there is no navy.

## Getting started

Requirements: a JDK 17 or newer (developed on JDK 25) and PowerShell on Windows.

```powershell
.\build.ps1   # compiles the sources to out\classes and copies the map data
.\run.ps1     # starts the game
```

To compile manually: `javac -d out\classes (Get-ChildItem src -Recurse -Filter *.java).FullName`, copy `res\*` into `out\classes`, then run `java -cp out\classes mapgame.Main`.

## How to play

1. Choose a world in the main menu. *Random world* shows its preview first (use *Re-roll* for another one) and you play exactly that map.
2. Click any coloured province to select a nation and press **Start**.
3. Press **Space** to unpause and use **1-5** to change the speed.
4. Select an army (click its counter or a province) and **right-click** a province to march there.
5. An army must stay for a few days in an enemy province to occupy it. Occupied provinces are drawn with stripes in the occupier's colour.
6. Use the side panel to recruit armies, declare war and propose peace. A peace treaty hands every occupied province to its occupier. A nation that loses all of its own provinces is eliminated; you lose when your nation is eliminated and win when you are the last one left.

### Controls

| Input | Action |
|---|---|
| Left-click | Select province / army |
| Right-click | Send the selected army to a province |
| Left-drag, arrow keys, WASD | Pan the map |
| Mouse wheel | Zoom |
| Space | Pause / resume |
| 1-5 | Game speed |
| M | Toggle political / terrain map |
| X | Split the selected army in half (the new army gets selected) |
| C | Merge idle friendly armies in the same province into the selected one |
| G | Open the army group window |
| Esc | Deselect |

The divider between the control menu and the log on the right can be dragged up and down to resize the log.

### Armies

- Recruiting costs 40 gold and 1,000 manpower and creates an army in an owned province.
- Armies have upkeep; if gold runs out, they slowly lose men.
- Your own armies never merge automatically, so you can split an army (**X**) and spread the parts over several provinces. Use **C** to merge them again.
- A manual move order takes an army out of its army group.

### Army groups (fronts)

Open the window with **G**:

1. *New group* (the selected army is added automatically), then *Add selected army* or *Add all free armies*.
2. Choose the enemy under *Front against* (you must be at war with it) and the orders:
   - **Attack:** the group pushes into enemy provinces that touch your territory.
   - **Defend:** the group holds your border provinces facing that enemy.
3. From then on the group handles itself: it finds the front, splits armies so that every front province can be covered, spreads the men evenly (taking distance into account), re-balances every three days and keeps advancing as provinces fall. Without a land border, attackers march towards the nearest enemy land.

Army counters of a group carry a `G<number>` tag. The selected group shows its front provinces as circles and the assignment of each army as dashed lines.

### Wars and total war

- *Declare war* starts a normal war. Peace is accepted when the other side is not winning or when the war has dragged on for a long time.
- *Declare TOTAL war* starts a war that can never end in peace - not by you and not by the AI. It only ends when one of the two nations is eliminated. An existing war can be escalated to total war. The game asks for confirmation first.

## The worlds

| World | Description |
|---|---|
| Random world | Voronoi provinces, noise-based continents and terrain, 16 invented nations. A new map every time. |
| Europe | Europe, the Mediterranean, North Africa and the Near East with about 75 real nations. |
| World | The whole Earth with about 177 real nations. |

On the real maps, terrain follows real geography, population follows the real population of each country and islands are connected to the mainland over narrow straits (dotted lines on the map, +4 days travel time).

## Project structure

```
src/mapgame
  Main.java                       entry point
  model/                          world data and map generation
    MapGenerator, RealMapGenerator, GeoData, MapKind, World, Province, Country, Army, ArmyGroup, Terrain
  sim/                            game rules
    Game, Ai, FrontPlanner
  ui/                             Swing user interface
    GameWindow, MainMenuPanel, MapPanel, MapRenderer, ArmyGroupDialog
  tools/SimCheck.java             headless AI-only simulation for testing
res/mapgame/data/world.txt        compact country and terrain geometry for the real maps
tools/prepare_data.py             builds world.txt from Natural Earth GeoJSON
build.ps1, run.ps1                build and run scripts
```

## Development

**Headless simulation.** `SimCheck` generates a world, lets the AI play for some years and prints statistics. It also writes map images, which is handy for checking map generation and game balance:

```powershell
java -cp out\classes mapgame.tools.SimCheck <seed> <years> <outDir> [random|europe|world]
# example
java -cp out\classes mapgame.tools.SimCheck 12345 20 out\check europe
```

**Map data.** `res/mapgame/data/world.txt` is generated from the 50m Natural Earth country and geography-region data. To rebuild it (requires Python 3 and an internet connection):

```powershell
python tools\prepare_data.py
```

## Roadmap

Navy and sea transport, alliances, fog of war, technology, buildings, provinces with unrest, save/load, real capitals and cities, automatic reinforcement for army groups, sound.

## Credits and license

Country borders, coastlines and terrain regions: [Natural Earth](https://www.naturalearthdata.com/) (public domain).
This project is inspired by *Hearts of Iron* and *Age of History* but contains no assets or code from them and is not affiliated with their makers.
No license has been chosen for the game code yet.
