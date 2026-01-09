# Clan Rank Helper

A RuneLite plugin that helps clan administrators manage rank changes by highlighting members who need their rank updated.

## Features

- **Fetches rank data from API or Google Sheets** - Automatically retrieves pending rank changes
- **Highlights members in Member List** - Members needing rank changes are highlighted with their target rank shown
- **Overlay panel** - Shows a list of all members who need rank changes
- **Configurable filters** - Ignore specific ranks or players
- **Customizable colors** - Set colors for each rank type
- **Auto-refresh** - Periodically refreshes data from the source

## How to Use

1. Configure your data source (API URL or Google Sheets URL) in the plugin settings
2. Open the Clan Settings and go to the **Member list**
3. Scroll through the list - members needing rank changes will be highlighted
4. The overlay panel shows all confirmed members needing changes
5. Use the "Reset Checked Data" option to clear and re-scan

## Configuration

### API Settings
| Setting | Description |
|---------|-------------|
| API URL | URL to fetch rank data from (JSON format) |
| Google Sheets URL | URL to a public Google Sheet (Column A = RSN, Column B = Rank) |
| Refresh Interval | How often to refresh data (minutes) |

### Display Settings
| Setting | Description |
|---------|-------------|
| Show Overlay Panel | Toggle the overlay panel visibility |
| Highlight in Member List | Toggle highlighting in member list |
| Default Highlight Color | Color for unknown ranks |
| Max Displayed in Overlay | Limit overlay entries (0 for all) |
| Ignored Current Ranks | Comma-separated ranks to ignore |
| Ignored Target Ranks | Comma-separated target ranks to ignore |
| Ignored Players | Comma-separated player RSNs to ignore |
| Reset Checked Data | Toggle ON to clear all checked data |

### Rank Colors
Customize colors for each rank type (Recruit, Corporal, Sergeant, etc.) and define custom colors for clan-specific ranks.

## Data Source Formats

### API Format
The API must return a JSON array:
```json
[
  {"mainRSN": "PlayerName", "osrsName": "TargetRank"},
  {"mainRSN": "AnotherPlayer", "osrsName": "Sergeant"}
]
```
- `mainRSN` - The player's RuneScape name
- `osrsName` - The target rank (must be the in-game rank name)

### Google Sheets Format
1. Make the sheet publicly accessible (Share → Anyone with the link can view)
2. Column A = Player RSN
3. Column B = Target rank (must be the **in-game rank name**, not clan-specific name)
4. First row can be a header (will be auto-skipped if it contains "rsn", "name", or "rank")

**Note:** Google Sheets URL takes priority if both API URL and Google Sheets URL are configured.

## In-Game Rank Names
The rank names must match OSRS in-game rank names:
- Recruit, Corporal, Sergeant, Lieutenant, Captain, General, Admin, Deputy Owner, Owner
- Or any custom ranks your clan has defined in-game

## Privacy Notice
⚠️ This plugin submits your IP address to third-party websites (your configured API or Google Sheets) not controlled or verified by the RuneLite Developers.

## Building

```bash
./gradlew build
```

## Installation

### From Plugin Hub (Recommended)
Search for "Clan Rank Helper" in the RuneLite Plugin Hub.

### Manual Installation
1. Build the plugin with `./gradlew build`
2. The JAR will be in `build/libs/`

## Requirements

- RuneLite 1.10.0+
- Java 11+

## License

BSD 2-Clause License - see [LICENSE](LICENSE) for details.

## Support

If you encounter issues or have feature requests, please open an issue on GitHub.
