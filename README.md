# Dev Tools

Dev Tools is a NeoForge 1.21.1 utility mod for testing Minecraft Beyond and third-party mod integrations. It keeps development-only commands and items out of the gameplay mods that they help diagnose.

## Current Status

Internal development tool. It is useful in a controlled test instance, but it is not intended to be part of normal player progression or a public server mod list.

## Project Facts

- Mod id: `devtools`
- Current version: `0.1.0`
- Target: Minecraft 1.21.1, NeoForge 21.1.234, Java 21
- Optional integration: Lootr 1.11.37+
- Config files: none

## Lootr Dev Tool

The Lootr Dev Tool stores a loot-table id on the item stack and lets a creative player or permission-level-2 operator repeatedly exercise Lootr behavior.

- Right-click an empty placement position to create a Lootr chest using the selected loot table.
- Right-click an existing Lootr container to retarget it to the selected table and reset its saved state.
- Left-click an existing Lootr container to reroll it for the current player while keeping the table assignment.
- The item tooltip shows its selected loot table.
- Loot-table command arguments offer suggestions from the server's reloadable loot-table registry.

Give yourself a tool for a table:

```text
/devtools lootr_dev_tool <loot_table>
```

Give one to selected players:

```text
/devtools lootr_dev_tool_to <targets> <loot_table>
```

Lootr is optional. The item reports that the integration is unavailable instead of attempting to operate when Lootr is not loaded.

## Damage Diagnostics

Enable or disable per-player damage reporting with:

```text
/devtools damage_debug
/devtools damage_debug <true|false>
```

After an enabled player damages a living entity, the server reports:

- Final damage and the event's original raw damage.
- The attacker's current attack-damage attribute.
- Main-hand, offhand, and damage-source weapon ids.
- The attack-damage contribution calculated for each reported stack.
- The attacker's current game mode.

The toggle is held in memory for the current server process and is not a persistent player setting.

## Permissions

All `/devtools` commands require permission level 2. The Lootr item can be used by creative players or operators with the same permission level.

## Building

```sh
./gradlew build
```

The built jar is written to `build/libs/`.

## Minecraft Beyond Integration

The modpack workspace tracks Dev Tools as a source-only local mod and copies its development jar into the Prism instance through `./scripts/modpack sync-local-mods`. Keep it disabled or remove it from release-oriented profiles unless its diagnostics are deliberately needed.

## Known Limitations

- Lootr support targets the API used by Lootr 1.11.37 and may need adjustment when that API changes.
- Damage reporting is intentionally verbose and currently focuses on direct player damage events rather than producing a full modifier-by-modifier trace.
- There is no persistent config or permission model beyond creative/operator checks.

## License

MIT.
