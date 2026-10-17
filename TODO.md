# TODO

Ideas and planned work for Autistic Map Game. Tick items off (`[x]`) as they are done.

## Suggested order

1. [x] Save / load and AI balance on the real maps
2. [ ] Navy and sea transport
3. [ ] Alliances and supply
4. [ ] Real capitals and cities, then buildings and technology

## Fix first (small, high value)

- [x] **Save / load:** quick save/load (F5/F9), file dialog, yearly autosave, "Load game" in the main menu. Saves store only the changing state; the map is recreated from map type + seed.
- [x] **AI balance on real maps:** peace deals take at most 35% of the loser's land, nobody loses their last province in a normal war, capitulation instead of instant elimination, affordable start armies. Europe: 41% of nations were wiped out within 5 years before, now none. Follow-ups below.
- [ ] **AI balance follow-ups:** let the AI declare total war occasionally, anti-hegemon coalitions, war goals, occupation income penalty, a few nations are permanently in debt.
- [ ] **Real capitals and cities:** capitals are currently just the central province. Natural Earth populated places could provide real capitals and big cities with higher income.
- [ ] **UI gaps:** minimap, better province tooltips, an event/notification list, a "go to capital" key.

## Core strategy depth

- [ ] **Navy and sea transport (biggest gap):** ships, ports, sea zones, naval invasion and blockades. Replaces the temporary strait crossings.
- [ ] **Alliances and diplomacy:** alliances, guarantees, non-aggression pacts, call-to-arms, trade, war goals instead of "conquer everything".
- [ ] **Supply and attrition:** supply range, terrain and winter attrition so armies cannot march anywhere forever.
- [ ] **Army quality:** unit types (infantry, cavalry, artillery), experience, generals and morale instead of a single strength number.
- [ ] **Reinforcement for army groups:** groups recruit and refill armies automatically from the manpower pool.
- [ ] **Fog of war:** only see armies near your territory; adds scouting.

## Economy and nation management

- [ ] **Buildings and provinces:** farms, factories, fortresses, infrastructure, province development, unrest and rebellions in occupied land.
- [ ] **Technology / research tree:** military and economic upgrades over time.
- [ ] **Events and decisions:** random events, civil wars, revolutions, treaties.
- [ ] **Government and policies:** conscription levels, war economy, taxes.

## Gameplay and polish

- [ ] **Scenarios and start dates:** real 1936 or 1914 starts with historical borders and nations.
- [ ] **Difficulty settings** and a nation picker screen with stats and an AI aggressiveness slider.
- [ ] **Multiple languages:** a language setting in the main menu. Besides English: German, Serbo-Croatian and Russian. Needs all UI texts moved into language files (e.g. `res/mapgame/lang/en.properties`, `de`, `sh`, `ru`), UTF-8 and Cyrillic font support, and translated log messages.
- [ ] **Sound and music**, nicer map rendering (terrain textures, rivers, smooth borders), animated battles.
- [ ] **Multiplayer:** hotseat first, online later.
- [ ] **Mod support:** load custom maps, nations and scenarios from files.
- [ ] **Map editor** for custom countries and provinces.

## Technical

- [ ] **Automated tests** for the simulation (combat, pathfinding, front planner) and a CI build on GitHub Actions.
- [ ] **Packaging:** a runnable `.jar` or Windows installer (`jpackage`) so the game can be played without a JDK.
- [ ] **Performance:** cache the map image per mode, speed up AI pathfinding for 170-nation worlds.

## Project housekeeping

- [ ] Choose a license for the game code (README currently says none).
- [ ] Add screenshots to the README.
