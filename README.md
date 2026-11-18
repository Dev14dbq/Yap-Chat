# KKS Chat

Custom chat for Minecraft with player avatars, settings, and spam protection.

---

## Main Features

### Beautiful Interface

Chat displays in the center of the screen with player avatars next to messages. Each message is in its own block with adjustable background transparency.

![Hi!](https://cdn.modrinth.com/data/cached_images/ef6d3c683b708fc7bf22735af42a59555f5ee30d.png)

### Positioning

The positioning screen lets you place the chat anywhere on the screen. Alignment modes:

- **Auto** — automatically selects side
- **Left** — messages aligned to the left edge
- **Center** — messages centered
- **Right** — messages aligned to the right edge

### Anti-Spam System

Automatically merges repeated messages and shows a counter (e.g., "x3"). Can be enabled or disabled in settings.

![antispam](https://cdn.modrinth.com/data/cached_images/5082d492b593e0bc009444203af10d41b273d1ca.png)

### Whisper

Private message support. Displayed with special formatting showing sender/receiver.

![whisper](https://cdn.modrinth.com/data/cached_images/641eb308fb5e29f57348cd5046408aeffec1fa23.png)

### Server Message Formats

KKS Chat recognises message types from what the game actually reports (chat type, translation keys), so it works the same in every language. For plugin formats it ships patterns for EssentialsX/CMI (`[Steve -> me] hi`), Hypixel-style (`From [VIP] Steve: hi`) and common chat layouts (`[Rank] Steve » hi`).

If your server uses its own format, add it to `config/kks-chat.json`:

```json
"serverPatterns": [
  { "id": "my-server-pm", "regex": "^✉ (?<from>\\w+) » (?<to>\\w+): (?<body>.*)$", "type": "WHISPER" }
]
```

Named groups: `from` / `to` (private messages; `me`, `я` or your nick mean you), `name` (chat author, must be online), `body` (the message).

---

## Settings

### Background Transparency

Adjust message background transparency from 0% to 100%.

### Font Size

Change text size from 50% to 200%.

### Display Time

Set message display time from 1 to 30 seconds.

### Maximum Message Count

Limit chat history from 10 to 500 messages.

### Additional Options

- **Modify Text** — enable/disable message formatting
- **Reset Settings** — restore defaults

![Settings](https://cdn.modrinth.com/data/cached_images/fc4a43884eac1640b44cbb64a03dd5e205365f69.png)

---

## Supported Languages

The mod supports **12 languages**:

- 🇬🇧 **English** (en_us)
- 🇷🇺 **Русский** (ru_ru)
- 🇩🇪 **Deutsch** (de_de)
- 🇪🇸 **Español** (es_es)
- 🇵🇹 **Português (Brasil)** (pt_br)
- 🇵🇹 **Português (Portugal)** (pt_pt)
- 🇮🇹 **Italiano** (it_it)
- 🇵🇱 **Polski** (pl_pl)
- 🇹🇷 **Türkçe** (tr_tr)
- 🇰🇷 **한국어** (ko_kr)
- 🇯🇵 **日本語** (ja_jp)
- 🇸🇦 **العربية** (ar_sa)

All interface texts, buttons, and messages are automatically translated based on the game language.

---

## Message History

When opening chat (key `T`), the full message history is shown with scrolling. Repeated messages can be expanded to see all repetitions.

![Chat history](https://cdn.modrinth.com/data/cached_images/04a5669ca2b8ca7c44d1062b1aae2d9a59abb4b0.png)

---

## Support

If you have questions or suggestions, message on Discord: **ddev14**

---

## License

This project is licensed under the [GNU General Public License v3.0](LICENSE).
