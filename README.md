# Enders Teams

A Paper 1.21.11 plugin for Java 21.

## Team creation

Use `/team create` (or `/endersteams:team create` if another plugin claims `/team`).

1. Choose one of 18 block icons: coal block, block of copper, block of iron,
   block of gold, block of redstone, block of lapis lazuli, block of emerald,
   block of diamond, block of quartz, ancient debris, block of amethyst,
   prismarine, crying obsidian, glowstone, deepslate, sculk, gilded blackstone,
   or sea lantern.
2. Type a team name in the anvil's text field and click its result. No XP or items
   are required. Names must be 3–24 characters, start with a letter or number,
   and contain only English letters, numbers, spaces, underscores, or hyphens.
3. Review the name and icon, then click **Create Team**. You can change either
   choice or cancel. Closing any screen before confirming cancels creation.

Names are unique without regard to capitalization. Players can belong to one
team; the creator becomes its owner. Icons can be shared by multiple teams.
Teams save immediately to `plugins/EndersTeams/teams.yml` and load after restarts.
The `endersteams.create` permission is enabled for everyone by default.
Previously saved teams keep retired icons (obsidian or magma block),
but these icons are no longer offered during creation.

Only team creation is implemented so far. Invitations, roster, ownership
transfer, chat, friendly fire, and team homes are planned separately.

## Build and install

Run `./gradlew build` on Linux/macOS or `.\gradlew.bat build` on Windows, using
Java 21. Copy `build/libs/EndersTeams-1.0-SNAPSHOT.jar` into your Paper server's
`plugins` folder and restart the server. Remove the original template plugin
JAR if it was previously installed.

## Verification

`gradlew test` covers restart persistence, name validation, duplicate names,
multiple memberships, failed writes, and protection of corrupt saved data.

In-game checks for Paper 1.21.11:

- In survival with zero XP, choose each icon and enter a name; ensure the result
  opens the matching confirmation preview without spending XP.
- Change the icon and name from confirmation; ensure the name is retained.
- Try empty, short, long, invalid, and already-used names.
- Close or disconnect on every screen; no team or menu items should be granted.
- Try shift-clicks, double-clicks, number keys, offhand swaps, dropping, and
  dragging items across both inventories; no menu items should be obtainable.
- Confirm rapidly; only one team should be created. Repeat `/team create` to
  verify that existing members are blocked.
- Have two players preview the same name, then confirm both. Only one should
  succeed; the other should be prompted to choose another name.
- Restart the server and verify the team remains saved and creation is blocked
  for its owner. Test `/endersteams:team create` if another command takes priority.
