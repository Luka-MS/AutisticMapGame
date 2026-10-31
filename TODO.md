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

- [x] **Navy and sea transport:** fleets built in ports, moving over sea provinces and between friendly ports, fleet battles, blockades (an enemy fleet next to a coast without a defending fleet halves its income), and sea transport: armies embark in a port, sail, and land on any allowed coast, also in enemy land. A sunk fleet takes its cargo down. The AI builds fleets, hunts enemy fleets, blockades and ferries armies to islands and coasts it cannot reach by land. The narrow-strait crossings stay as a cheap shortcut; they were not replaced.
- [x] **Alliances and call-to-arms:** propose or break alliances (max. 3 per nation; accepted with a common enemy, a shared hegemon threat or by a weaker partner). Allies cannot be attacked, move and are supplied on each other's land, and are called to arms when a member is attacked (AI allies join most of the time, the player gets a notification). The AI forms alliances against shared enemies and drops alliances with a hegemon.
- [x] **More diplomacy:** non-aggression pacts (10 years, block war), one-sided guarantees of independence (guarantors are called to arms), trade agreements (+6% gold each, max. 3) and player-chosen war goals (conquest or tribute: no land, the loser pays 30% of its gold). All in the *Diplomacy* page of the Nation window; the AI uses pacts, trade, guarantees and tribute wars too.
- [x] **Supply and attrition:** armies away from own land lose men: nothing at home or right behind the border, more with distance from own territory, in deserts/snow/mountains, in big stacks (over 4,000 men) and in winter (cold half-year at latitudes above 35 degrees). Armies below 100 men starve away. The selected army shows its supply state.
- [x] **Army quality (unit types and experience):** every army is infantry, cavalry (fast, strong on open ground, fragile, 1.5x gold) or artillery (slow, +50% attack, vulnerable unless infantry stands with it, 2x gold). Armies gain experience in battle (up to +40% attack and -25% damage taken); new men dilute it and merges average it. AI nations mix unit types.
- [x] **Generals and morale:** armies have a morale value (0-100): it falls with the losses suffered in battle, weakens the army's attack and makes it break and retreat at 20; it recovers while resting (faster at home). Veteran armies get a named general (skill 1-4 from experience: +5% attack per skill and less morale loss). Separate commander traits are not implemented.
- [x] **Reinforcement for army groups:** tick *Reinforce automatically* in the group window and set a target strength; the group refills armies standing in own territory (500 men per round) and raises new armies at home when needed, paying gold and manpower like a normal recruit (50 gold are always kept in reserve).
- [x] **Fog of war:** optional (button / **F** in the game, saved with the game). You see foreign armies only on your own and allied land, next to it, and next to your own armies (two provinces for cavalry); everything else is shaded. Own armies are scouts. The AI is not affected.

## Economy and nation management

- [x] **Buildings and provinces:** markets, barracks, forts and roads (up to 3 / 3 / 3 / 2 levels, 60 days each, built in the Nation window). Occupied provinces without a garrison build up unrest and rebel against the occupier after about 250 days; a garrison keeps the peace. The AI develops its richest provinces and fortifies the front.
- [x] **Technology / research:** three fields (military, economy, administration) with five levels each, researched in the Nation window; bigger nations research faster. The AI researches by itself (military first at war).
- [x] **Events and decisions:** seven random events (bumper harvest, plague, peasant revolt, trade treaty, army reform, natural disaster, coup attempt). The player decides in a dialog (the game stands still meanwhile), the AI chooses by itself. Nations are never split: civil wars would need new nations at runtime and are not implemented, the coup attempt is the closest thing to a revolution.
- [x] **Government and policies:** taxes (low / normal / high) and conscription (light / normal / heavy) trade gold against manpower; policies can be changed every 180 days (Nation window). The AI raises taxes when broke and drafts heavily when short of men at war.

## Gameplay and polish

- [x] **Scenarios and start dates:** *Great Powers 1914* and *Europe 1936* start in their year with the big empires (Austria-Hungary, Russian and Ottoman Empire, Soviet Union, Yugoslavia, ...) and the historical alliances. Since the map data is modern, empires are approximated by merging today's nations; true historical borders would need new map data. Scenarios are plain text files and can be added by mods (see below).
- [x] **Difficulty settings** (easy / normal / hard: AI and player income), an **AI aggressiveness** slider (0-100) in the main menu, and a **nation picker** table (provinces, population, income, manpower, army; sortable, double-click to choose) next to the Start button.
- [ ] **Sound and music**, nicer map rendering (terrain textures, rivers, smooth borders), animated battles.
- [ ] **Multiplayer:** hotseat first, online later.
- [x] **Mod support:** a `mods/` folder next to the game can hold scenarios (`mods/scenarios/*.txt`: new nations, merged nations, owners, alliances), texts (`mods/lang/<language>.properties`, `mods/lang/countries.tsv`) and a replacement map (`mods/data/world.txt`). `mods-example/` is a ready-to-copy template.
- [ ] **Map editor** for custom countries and provinces.

## Technical

- [x] **Automated tests** for the simulation (pathfinding, combat, occupation, peace limits, alliances, supply, reinforcement, front planner, save/load) in `mapgame.tools.SelfTest`, run by a CI build on GitHub Actions (`.github/workflows/ci.yml`) for pushes to main/developer and for pull requests.
- [ ] **Packaging:** a runnable `.jar` or Windows installer (`jpackage`) so the game can be played without a JDK.
- [ ] **Performance:** cache the map image per mode, speed up AI pathfinding for 170-nation worlds.

## Project housekeeping

- [ ] Choose a license for the game code (README currently says none).
- [ ] Add screenshots to the README.
