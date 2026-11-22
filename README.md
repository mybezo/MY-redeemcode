# RedeemCode

Redeem code plugin for Paper 26.3. You create a code, set up the rewards in a chest GUI, and players type `/redeem <code>` to claim it.

Rewards can be items, console commands, or both. Balance rewards are just commands (`eco give %player% 5000` by default), so it works with whatever economy plugin you run.

## Requirements

- Paper 26.3
- Java 25

## Installation

1. Drop `RedeemCode-x.x.x.jar` into your `plugins` folder.
2. Restart the server.
3. Edit `plugins/RedeemCode/config.yml` if you want to change messages or the balance command, then run `/redeem reload`.

## Quick start

Say you want a code called `LAUNCH` that gives 5000 coins and a diamond sword to the first 200 players, one claim per IP, valid for a week.

```
/redeem create LAUNCH
```

The editor opens. From there:

- **Item Rewards**: put the diamond sword in, close the menu.
- **Command & Balance Rewards**: click *Add Balance Reward* and type `5000` in chat.
- **Max Total Claims**: left click, type `200`.
- **Limit Per IP**: left click, type `1`.
- **Expiration**: left click, type `7d`.

That's it. Everything saves as you go. Players can now use `/redeem LAUNCH`.

Anything you type in chat for the editor can be aborted with `cancel`.

## Commands

| Command | What it does |
|---|---|
| `/redeem <code>` | Claim a code |
| `/redeem create <code>` | Create a code and open the editor |
| `/redeem edit <code>` | Open the editor for an existing code |
| `/redeem delete <code>` | Delete a code and its claim data |
| `/redeem info <code>` | Show settings and the last 10 claims |
| `/redeem list` | List all codes (click one to see its info) |
| `/redeem resetclaims <code> [player]` | Reset every claim, or just one player's |
| `/redeem reload` | Reload config and codes |

`/redeemcode` works as an alias.

## Permissions

| Permission | Default | |
|---|---|---|
| `redeemcode.use` | everyone | Claim codes |
| `redeemcode.admin` | op | All management commands |
| `redeemcode.bypass.cooldown` | op | Skip the wrong-code cooldown |

You can also lock a single code behind any permission from the editor, for example `group.vip`.

## Command rewards

Commands run from console, without the leading `/`. These placeholders are replaced when a player claims:

| Placeholder | Value |
|---|---|
| `%player%` | Player name |
| `%uuid%` | Player UUID |
| `%code%` | The code that was redeemed |

Some examples:

```
eco give %player% 5000
lp user %player% parent addtemp vip 7d
crate key give %player% legendary 1
broadcast %player% redeemed %code%!
```

The *Add Balance Reward* button uses `settings.balance-command` from the config. If your economy uses a different command, change it there:

```yaml
settings:
  balance-command: "eco give %player% %amount%"
```

## How claiming works

When a player runs `/redeem <code>`, the plugin checks in this order:

1. The code exists (wrong guesses trigger a short cooldown, 3 seconds by default)
2. It's enabled and not expired
3. It has at least one reward
4. The player has the required permission, if one is set
5. The player hasn't claimed it before
6. The total claim limit isn't reached
7. The IP limit isn't reached

If everything passes, the claim is saved first and then the rewards are given. If the player's inventory is full, the leftover items drop at their feet. Set `full-inventory-action: CANCEL` if you'd rather refuse the claim until they make room.

## Config

The defaults in `config.yml` are fine for most servers. Things you might want to change:

- `balance-command`: see above
- `code-pattern`: allowed characters and length for new codes
- `invalid-attempt-cooldown`: seconds to wait after a wrong code, `0` to turn it off
- `full-inventory-action`: `DROP` or `CANCEL`
- `broadcast-on-redeem`: announce claims to the whole server
- `messages`: every message, in [MiniMessage](https://docs.advntr.dev/minimessage/format.html) format

Codes are stored in `codes.yml`. You normally don't need to touch it. If you edit it by hand, run `/redeem reload` afterwards.

## Notes

- Codes are not case sensitive. `LAUNCH`, `launch` and `Launch` are the same code.
- Players never see code names in tab completion, only admins do.
- If you're behind Velocity or BungeeCord, turn on IP forwarding. Otherwise every player shows up with the proxy's IP and the per-IP limit will block everyone after the first claim.

## Building

```
mvn clean package
```

Needs JDK 25. The jar ends up in `target/`.
