# Autistic Map Game

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
- **Balanced peace deals:** in a normal war the winner takes at most 35% of the loser's provinces and nobody loses their last province; a nation without any land left capitulates. Only a total war can wipe a nation out. This keeps the AI world dynamic without snowballing or mass extinction.
- **Save / load** with quick save (F5), quick load (F9), a file dialog, a yearly autosave and a *Load game* button in the main menu.
- **Languages:** English, German, Serbo-Croatian in Croatian wording (Latin and Cyrillic script) and Russian, switchable in the main menu at any time. The choice is remembered. Texts live in `res/mapgame/lang/*.properties`; the Cyrillic Serbo-Croatian texts are generated from `sh.properties` (text in `{braces}` stays Latin). Names of the real countries (Europe and World maps) are translated via `res/mapgame/lang/countries.tsv`; invented nations and province names stay as generated.
- **Real capitals and cities:** every real nation starts with its real capital and the big cities of the Natural Earth data are on the map. City provinces yield more gold and manpower (+30%, megacities +60%), capitals +50%. A nation that loses its capital in a peace deal moves it to its richest province.
- **AI nations** that recruit, attack, make peace and declare wars. They fight for limited war goals, occasionally launch a total war on a far weaker neighbour, band together in a coalition against a nation that holds more than 20% of the land, and demobilize when in debt. Conquered provinces yield only half for a year.
- **Alliances:** propose an alliance (the other side agrees against a common enemy or an over-mighty nation, or when you are the stronger partner), up to three allies. Allied armies may cross and are supplied in each other's land, nobody can declare war on an ally, and when a member is attacked its allies are called to arms - AI allies mostly join, you get a notification and decide yourself. Break an alliance at any time.
- **Supply and attrition:** armies outside your own land lose men - nothing right behind the border, then more with the distance from your territory, in deserts, mountains and snow, in huge stacks (over 4,000 men) and in winter. The info panel shows the supply state of the selected army.
- **Sea crossings:** islands are linked to the mainland over narrow straits (e.g. UK-France, Japan-Korea) as long as there is no navy.

## Getting started

Requirements: a JDK 17 or newer (developed on JDK 25) and PowerShell on Windows.

```powershell
.\build.ps1   # compiles the sources to out\classes and copies the map data
.\run.ps1     # starts the game
```

On macOS or Linux, run `./run.sh` instead. It builds the game on first use (and whenever the sources changed) and starts it. Right-click (marching orders) is ctrl-click or a two-finger click on a Mac trackpad.

To compile manually: `javac -d out\classes (Get-ChildItem src -Recurse -Filter *.java).FullName`, copy `res\*` into `out\classes`, then run `java -cp out\classes mapgame.Main`.

### Building a standalone Windows release (.exe)

```powershell
.\package.ps1 -Version 1.0.0
```

This builds the project, packs it into a runnable jar, then uses `jpackage` (bundled with the JDK) to produce a self-contained app image with its own private Java runtime under `out\dist\AutisticMapGame-Version-1.0.0\`, and zips it as `out\dist\AutisticMapGame-Version-1.0.0-win-x64.zip`. Players don't need Java installed to run the resulting `.exe`.

Pushing a tag like `v1.0.0` triggers [`.github/workflows/release.yml`](.github/workflows/release.yml), which runs the same packaging step on GitHub Actions and attaches the zip to the corresponding GitHub Release automatically.

## How to play

1. Choose a world in the main menu. *Random world* shows its preview first (use *Re-roll* for another one) and you play exactly that map.
2. Click any coloured province to select a nation and press **Start**.
3. Press **Space** to unpause and use **1-5** to change the speed.
4. Select an army (click its counter or a province) and **right-click** a province to march there.
5. An army must stay for a few days in an enemy province to occupy it. Occupied provinces are drawn with stripes in the occupier's colour.
6. Use the side panel to recruit armies, declare war and propose peace. A peace treaty hands the winner some of the occupied provinces (at most 35% of the loser's land, the rest is returned). A nation that loses all of its own land capitulates and survives with the rest; only a **total war** eliminates a nation. You lose when your nation is eliminated and win when you are the last one left.
7. Save with **F5** (quick save) or the *Save* button, load with **F9** or the *Load* button. The game also autosaves every year. Saves are stored in the `saves` folder next to the game.

### Controls

| Input | Action |
|---|---|
| Left-click | Select province / army |
| Right-click | Send the selected armies to a province |
| Shift + left-drag | Draw a selection box around your armies |
| Left-drag, right-drag, middle-drag, arrow keys, WASD | Pan the map |
| Mouse wheel | Zoom |
| Space | Pause / resume |
| 1-5 | Game speed |
| M | Toggle political / terrain map |
| F | Toggle fog of war (foreign armies are only visible near your land and your armies) |
| X | Split the selected army in half (the new army gets selected) |
| C | Merge idle friendly armies in the same province into the selected one |
| G | Open the army group window |
| N | Open the nation window (buildings and more) |
| H / Home | Jump to your capital |
| F5 / F9 | Quick save / quick load |
| Esc | Deselect |

The divider between the control menu and the log on the right can be dragged up and down to resize the log. The bottom-left **minimap** shows the whole world with your current view; click or drag on it to jump. Hovering a province shows a tooltip (owner, population, income, city, armies). The **Events** tab next to the log lists what matters to you (wars, peace, lost and conquered provinces, lost armies, eliminated nations); a click on an entry jumps to the place and the tab title counts new events.

### Armies

- Recruiting costs gold and 1,000 manpower and creates an army in an owned province. Pick the **unit type** in the drop-down above the recruit button: *infantry* (40 gold), *cavalry* (60 gold, 1.5x speed, +25% attack on plains and desert, -20% in forest and mountains, takes 20% more damage, 1.3x upkeep) or *artillery* (80 gold, half-speed, +50% attack, takes 30% more damage unless infantry stands in the same province, 1.6x upkeep). Non-infantry counters on the map carry a letter (`C`, `A`).
- Armies gain **experience** while fighting (shown in the info panel): veterans deal up to 40% more damage and take up to 25% less. Reinforcements dilute the experience, merging averages it. Only armies of the same type merge.
- **Morale** (0-100) falls with the losses in battle and weakens the attack; an army whose morale drops to 20 breaks and retreats. Resting armies recover, faster in own territory. Armies with 25+ experience get a named **general** (skill 1-4) who adds 5% attack per skill and reduces morale loss.
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

**Automatic reinforcement:** tick *Reinforce automatically* and set a *target strength*. Whenever the group is below it, armies standing in your own territory are refilled from the manpower pool (500 men per planning round) and, if that is not enough, new armies are raised in the province closest to the front. The usual recruit prices apply (0.04 gold and 1 manpower per man) and 50 gold are always kept in reserve.

### Nation management

Press **N** (or *Nation management*) to open the nation window. It collects everything that is not about armies; more pages are listed below.

- **Buildings:** select one of your provinces and build a *market* (+20% gold per level), *barracks* (+20% manpower), *fort* (defenders take 15% less damage) or *roads* (armies cross 15% faster). Construction takes 60 days and costs more with every level.
- **Unrest:** a province held by an enemy without a garrison gains unrest; at 100 the people rise up and the province returns to its owner. Keep an army in conquered provinces.
- **Policies:** *taxes* (low: -20% gold, +10% manpower; high: +25% gold, -15% manpower) and *conscription* (light: -30% manpower; heavy: +40% manpower, -10% gold). A change is possible every 180 days.
- **Research:** pick one of three fields - *military* (+6% damage dealt, -4% damage taken per level), *economy* (+8% gold) or *administration* (+10% manpower, -4% army upkeep, faster research). Five levels each; the cost grows with every level (12, 24, 36, 48, 60 points) and large nations earn more points per month.

### Random events

Every month each nation has a small chance of an event: *bumper harvest*, *plague*, *peasant revolt*, *trade treaty* (+10% gold for five years), *army reform*, *natural disaster* and *coup attempt* (when bankrupt). When it hits you, a dialog asks for your decision and the game waits; the AI decides by itself. What matters to you also shows up in the Events tab.

### Wars and total war

- *Declare war* starts a normal war. Peace is accepted when the other side is not winning or when the war has dragged on for a long time. The winner takes at most 35% of the loser's provinces, and a nation can never lose its last province in a normal war.
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
    Game, Ai, FrontPlanner, SaveFile
  ui/                             Swing user interface
    GameWindow, MainMenuPanel, MapPanel, MapRenderer, ArmyGroupDialog
  tools/                          headless developer tools (SelfTest, SimCheck, BalanceCheck, SaveCheck)
res/mapgame/data/world.txt        compact country and terrain geometry for the real maps
tools/prepare_data.py             builds world.txt from Natural Earth GeoJSON
build.ps1, run.ps1, package.ps1    build, run, and release-packaging scripts
assets/icon.ico                    exe icon used by package.ps1
.github/workflows/release.yml      CI: builds and attaches the Windows exe on tag push
```

## Development

All tools are headless and run from `out\classes` after `.\build.ps1`.

**Unit tests.** `SelfTest` checks the simulation rules (pathfinding, combat, occupation, peace limits, alliances, supply, reinforcement, front planner, saving). The exit code is 1 when a check fails; GitHub Actions runs it for every pull request together with `SaveCheck`:

```powershell
java -cp out\classes mapgame.tools.SelfTest
```

**AI balance.** `BalanceCheck` lets the AI play a number of years on several seeds and prints survivors, how much land changes hands, the size of the biggest nations and debt:

```powershell
java -cp out\classes mapgame.tools.BalanceCheck [random|europe|world|all] [seeds] [years]
```

**Save/load round trip.** `SaveCheck` saves a game, loads it, lets both run on and verifies that they stay identical (the map is recreated deterministically from map type and seed):

```powershell
java -cp out\classes mapgame.tools.SaveCheck
```

**Simulation and map images.** `SimCheck` generates a world, lets the AI play for some years, prints statistics and writes map images:

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

Navy and sea transport, buildings, technology, provinces with unrest, generals and morale, events and decisions, scenarios, difficulty settings, sound. See [TODO.md](TODO.md) for the full list.

## Credits and license

Country borders, coastlines and terrain regions: [Natural Earth](https://www.naturalearthdata.com/) (public domain).
This project is inspired by *Hearts of Iron* and *Age of History* but contains no assets or code from them and is not affiliated with their makers.
No license has been chosen for the game code yet.
