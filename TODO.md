# TODO

Ideas and planned work for Map Game. Tick items off (`[x]`) as they are done.

## Suggested order

1. [x] Save / load and AI balance on the real maps
2. [ ] Navy and sea transport
3. [ ] Alliances and supply
4. [ ] Real capitals and cities, then buildings and technology

## Fix first (small, high value)

- [x] **Save / load:** quick save/load (F5/F9), file dialog, yearly autosave, "Load game" in the main menu. Saves store only the changing state; the map is recreated from map type + seed.
- [x] **AI balance on real maps:** peace deals take at most 35% of the loser's land, nobody loses their last province in a normal war, capitulation instead of instant elimination, affordable start armies. Europe: 41% of nations were wiped out within 5 years before, now none. Follow-ups below.
- [x] **AI balance follow-ups:** the AI occasionally declares total war on much weaker neighbours, neighbours form a coalition against any nation holding over 20% of the land, AI war goals (settles for a limited share of the enemy land), conquered provinces yield only half for a year, and AI nations in debt demobilize troops. Europe, 20 years: biggest nation 34% -> 17%, nations in debt 8 -> 1.
- [x] **Real capitals and cities:** Natural Earth populated places give every country its real capital (where it lies on the map) and about 300 big cities. City provinces are named after the city and yield +30% (+60% for megacities), capitals +50%. Cities and capitals are drawn on the map; a nation that loses its capital in a peace deal moves it.
- [x] **UI gaps:** minimap (click/drag to jump), province tooltips, an Events tab with click-to-jump notifications, and the H / Home key to jump to the capital.

## Core strategy depth

- [ ] **Navy and sea transport (biggest gap):** ships, ports, sea zones, naval invasion and blockades. Replaces the temporary strait crossings.
- [x] **Alliances and call-to-arms:** propose or break alliances (max. 3 per nation; accepted with a common enemy, a shared hegemon threat or by a weaker partner). Allies cannot be attacked, move and are supplied on each other's land, and are called to arms when a member is attacked (AI allies join most of the time, the player gets a notification). The AI forms alliances against shared enemies and drops alliances with a hegemon.
- [ ] **More diplomacy:** guarantees, non-aggression pacts, trade, war goals chosen by the player.
- [x] **Supply and attrition:** armies away from own land lose men: nothing at home or right behind the border, more with distance from own territory, in deserts/snow/mountains, in big stacks (over 4,000 men) and in winter (cold half-year at latitudes above 35 degrees). Armies below 100 men starve away. The selected army shows its supply state.
- [ ] **Army quality:** unit types (infantry, cavalry, artillery), experience, generals and morale instead of a single strength number.
- [x] **Reinforcement for army groups:** tick *Reinforce automatically* in the group window and set a target strength; the group refills armies standing in own territory (500 men per round) and raises new armies at home when needed, paying gold and manpower like a normal recruit (50 gold are always kept in reserve).
- [ ] **Fog of war:** only see armies near your territory; adds scouting.

## Economy and nation management

- [ ] **Buildings and provinces:** farms, factories, fortresses, infrastructure, province development, unrest and rebellions in occupied land.
- [ ] **Technology / research tree:** military and economic upgrades over time.
- [ ] **Events and decisions:** random events, civil wars, revolutions, treaties.
- [ ] **Government and policies:** conscription levels, war economy, taxes.

## Gameplay and polish

- [ ] **Scenarios and start dates:** real 1936 or 1914 starts with historical borders and nations.
- [ ] **Difficulty settings** and a nation picker screen with stats and an AI aggressiveness slider.
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
