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

## Invitations

Team owners can run `/team invite` to open a double-chest menu containing the
heads of all online players, sorted by name. Each page shows up to 45 heads.
Use **Previous Page** and **Next Page** to browse, **Refresh** to update the list,
or **Close** to exit. Opening another page also refreshes the online list.

Click a player's head to invite them. Your own head and players already on a
team are shown with an unavailable status. A pending invitation cannot be sent
again until it is declined or expires. Players who disconnect cannot be invited
from an old menu; the clicked head always identifies the original player.

The recipient receives clickable **Accept** and **Decline** chat buttons, or can
type `/team accept` or `/team decline`. If multiple invitations are pending,
these commands show the individual team buttons so the recipient can choose.
Buttons target a specific invitation using `/endersteams:team accept <invite-id>`
or `/endersteams:team decline <invite-id>`.

Invitations expire after five minutes and clear when the plugin/server restarts.
Accepted memberships save immediately and survive restarts. The
`endersteams.invite` permission defaults to true, but only the team owner can
send invitations. Accepting and declining do not require creation permission.

Invite blocking, roster, ownership transfer, team management, chat, friendly
fire, and team homes are planned separately.

## Build and install

Run `./gradlew build` on Linux/macOS or `.\gradlew.bat build` on Windows, using
Java 21. Copy `build/libs/EndersTeams-1.0-SNAPSHOT.jar` into your Paper server's
`plugins` folder and restart the server. Remove the original template plugin
JAR if it was previously installed.

## Verification

`gradlew test` covers restart persistence, name validation, duplicate names,
multiple memberships, failed writes, and protection of corrupt saved data.
Invitation tests cover owner/recipient checks, duplicates, expiration, decline,
accepting a specific team, retrying failed saves, and membership persistence.
Pagination tests cover empty/full pages, 46 and 91 players, page clamping after
disconnects, and stable head-to-player mappings. These run without Minecraft.

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
- As an owner, run `/team invite` and check player names, head textures, status
  text, refresh, and page arrows with more than 45 online players.
- Click a head, accept/decline using both the chat buttons and commands, and
  check that only the intended recipient can respond. With two teams inviting
  one player, verify that commands offer a choice and buttons target that team.
- Check expired invites, offline targets, rapid clicks, and attempts to remove
  heads with shift-clicks, drags, hotbar swaps, offhand swaps, and drops.
