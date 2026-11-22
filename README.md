# RedeemCode

A professional redeem code plugin for **Paper 26.3** with a full in-game GUI editor.

## Features
- `/redeem <code>` – players redeem a code (case-insensitive).
- GUI editor opened by `/redeem create <code>` or `/redeem edit <code>`:
  - **Item rewards** – drag & drop any items (enchantments, names, lore, components are kept exactly).
  - **Command & balance rewards** – any console command, e.g. `eco give %player% 5000`.
    The *Add Balance Reward* button asks only for the amount and uses the configurable
    `settings.balance-command` template.
  - **Max total claims** – e.g. only the first 200 players.
  - **Limit per IP** – stops alt accounts from claiming the same code.
  - **Expiration** – `30m`, `12h`, `7d`, `1d12h` or `never`.
  - **Required permission** – e.g. VIP-only codes.
  - **Enable / disable** toggle and delete button.
- Each player can redeem a code only once.
- Anti brute-force cooldown after entering an invalid code.
- Full-inventory handling (drop leftovers or refuse the claim).
- Claim history (player, IP, time) with `/redeem info`, and `/redeem resetclaims`.
- Async, atomic saving to `codes.yml` – no lag, no corrupted files.
- All messages configurable with MiniMessage.

## Commands
| Command | Description | Permission |
|---|---|---|
| `/redeem <code>` | Redeem a code | `redeemcode.use` (default: everyone) |
| `/redeem create <code>` | Create a code and open the editor | `redeemcode.admin` |
| `/redeem edit <code>` | Open the editor | `redeemcode.admin` |
| `/redeem delete <code>` | Delete a code | `redeemcode.admin` |
| `/redeem info <code>` | Details and latest claims | `redeemcode.admin` |
| `/redeem list` | List all codes | `redeemcode.admin` |
| `/redeem resetclaims <code> [player]` | Reset all or one player's claim | `redeemcode.admin` |
| `/redeem reload` | Reload the configuration | `redeemcode.admin` |

`redeemcode.bypass.cooldown` skips the invalid-attempt cooldown.

Command placeholders: `%player%`, `%uuid%`, `%code%`.

## Building
Requires JDK 25 and Maven.
```
mvn clean package
```
The jar is written to `target/RedeemCode-1.0.0.jar`.
