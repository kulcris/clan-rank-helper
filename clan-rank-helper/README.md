# Clan Rank Helper

A RuneLite plugin that helps clan administrators manage rank changes by highlighting members who need their rank updated.

## Features

- **Fetches rank data from API** - Automatically retrieves pending rank changes from your API
- **Highlights members in Member List** - Members needing rank changes are highlighted with their target rank shown
- **Overlay panel** - Shows a list of all members who need rank changes
- **Configurable filters** - Ignore specific ranks or players
- **Auto-refresh** - Periodically refreshes data from the API

## How to Use

1. Open the Clan Settings and go to the **Member list**
2. Scroll through the list - members needing rank changes will be highlighted
3. The overlay panel shows all confirmed members needing changes
4. Use the "Reset Checked Data" option to clear and re-scan

## Configuration

| Setting | Description |
|---------|-------------|
| API URL | URL to fetch rank data from |
| Refresh Interval | How often to refresh data (minutes) |
| Show Overlay Panel | Toggle the overlay panel visibility |
| Highlight in Clan Chat | Toggle highlighting in member list |
| Highlight Color | Color for highlighting |
| Ignored Current Ranks | Comma-separated ranks to ignore (e.g., `Owner,Deputy Owner,Quester`) |
| Ignored Target Ranks | Comma-separated target ranks to ignore |
| Ignored Players | Comma-separated player RSNs to ignore |
| Reset Checked Data | Toggle ON to clear all checked data |

## API Format

The plugin expects a JSON array from the API:

```json
[
  {"mainRSN": "PlayerName", "osrsName": "TargetRank"},
  {"mainRSN": "AnotherPlayer", "osrsName": "Sergeant"}
]
```

- `mainRSN` - The player's RuneScape name
- `osrsName` - The rank they should have

## Building

```bash
./gradlew build
```

## Installation

1. Build the plugin or download the JAR
2. Place in your RuneLite plugins folder
3. Enable "Clan Rank Helper" in RuneLite

## Requirements

- RuneLite 1.10.0+
- Java 11+
