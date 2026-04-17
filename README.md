# FixUtils — FIX Message Parser Plugin for IntelliJ IDEA

[![IntelliJ Platform](https://img.shields.io/badge/IntelliJ%20Platform-2023.1%2B-blue)](https://plugins.jetbrains.com/)
[![Java](https://img.shields.io/badge/Java-17-orange)](https://adoptium.net/)
[![License](https://img.shields.io/badge/License-MIT-green)](LICENSE)

A developer tool that parses raw **FIX protocol messages** and presents them as a richly annotated
table — with field names and enum descriptions sourced from bundled or custom QuickFIX XML
dictionaries.

---

## Features

- 📋 **Paste any FIX message** into the multi-line input area
- 🔀 **Choose delimiter** — Pipe `|`, Caret `^`, Tilde `~`, SOH (`\x01`), or any custom character
- 📖 **Select from bundled dictionaries** — FIX 4.0 through FIX 5.0 SP2 (including `.modified` variants)
- 📂 **Load a custom dictionary** XML file via the file browser
- 📊 **Parsed result table** — Tag number · Field name · Value · Enum description
- ⚡ Re-parse instantly with `Ctrl+Enter`

---

## Bundled Dictionaries

| File | Protocol Version |
|---|---|
| `FIX40.xml` | FIX 4.0 |
| `FIX41.xml` | FIX 4.1 |
| `FIX42.xml` | FIX 4.2 |
| `FIX43.xml` | FIX 4.3 |
| `FIX44.xml` | FIX 4.4 |
| `FIX44.modified.xml` | FIX 4.4 (custom extensions) |
| `FIX50.xml` | FIX 5.0 |
| `FIX50SP1.xml` | FIX 5.0 SP1 |
| `FIX50SP1.modified.xml` | FIX 5.0 SP1 (custom extensions) |
| `FIX50SP2.xml` | FIX 5.0 SP2 |
| `FIX50SP2.modified.xml` | FIX 5.0 SP2 (custom extensions) |
| `FIXT11.xml` | FIXT 1.1 (transport layer) |

---

## Installation

### From source (development)

**Prerequisites:** JDK 17+, IntelliJ IDEA 2023.1+

```bash
git clone https://github.com/your-org/FixUtils.git
cd FixUtils

# Build and run in a sandboxed IDE instance
./gradlew runIde
```

### Install built plugin

```bash
./gradlew buildPlugin
# Output: build/distributions/FixUtils-*.zip
```

In IntelliJ IDEA:  
**Settings → Plugins → ⚙️ → Install Plugin from Disk…** → select the `.zip`

---

## Usage

1. Open the **FIX Parser** tool window (`View → Tool Windows → FIX Parser`)
2. Paste your raw FIX message into the input area:
   ```
   8=FIX.4.1|9=857|35=U4|34=386|49=EMX|52=20260320-08:58:58|56=ZIN70|10=009|
   ```
3. Select the separator character (default: `|`)
4. Choose the appropriate dictionary from the dropdown
5. Click **Parse** (or press `Ctrl+Enter`)
6. The table populates with:

   | Tag | Field Name | Value | Enum Description |
   |-----|-----------|-------|-----------------|
   | 8 | BeginString | FIX.4.1 | |
   | 9 | BodyLength | 857 | |
   | 35 | MsgType | U4 | |
   | 34 | MsgSeqNum | 386 | |
   | 49 | SenderCompID | EMX | |
   | 10 | CheckSum | 009 | |

---

## Using a Custom Dictionary

Click **Browse…** next to the dictionary dropdown to open a file chooser.  
Select any QuickFIX-compatible XML dictionary (`*.xml`).  
The file is added to the dropdown for the current session; the bundled dictionaries remain unchanged.

### Dictionary XML Format

The plugin reads the standard **QuickFIX/J** dictionary schema:

```xml
<fix major="4" minor="1">
  <fields>
    <field number="35" name="MsgType" type="STRING">
      <value enum="D" description="ORDER_SINGLE"/>
      <value enum="8" description="EXECUTION_REPORT"/>
    </field>
    <field number="49" name="SenderCompID" type="STRING"/>
    <!-- custom/vendor tags -->
    <field number="9426" name="ErrorCode" type="STRING"/>
  </fields>
</fix>
```

---

## Project Structure

```
FixUtils/
├── Dictionaries/          # Source dictionary XML files
├── src/
│   └── main/
│       ├── java/com/fixutils/
│       │   ├── actions/   # IntelliJ action to open the tool window
│       │   ├── ui/        # Swing UI components
│       │   ├── parser/    # FIX string tokenizer
│       │   └── dictionary/# XML loader & descriptor service
│       └── resources/
│           ├── META-INF/plugin.xml
│           └── Dictionaries/   # Bundled copies of dictionary XMLs
├── build.gradle.kts
├── agents.md
└── README.md
```

---

## Development

### Key classes

| Class | Responsibility |
|---|---|
| `FixParserPanel` | Main UI panel — input, controls, result table |
| `FixMessageParser` | Splits raw FIX string into `List<TagValuePair>` |
| `FixDictionaryLoader` | Parses QuickFIX XML → `Map<Integer, FixFieldDescriptor>` |
| `FixDictionaryService` | Manages bundled + user-loaded dictionaries |
| `FixTableModel` | `AbstractTableModel` driving the result `JTable` |
| `OpenFixParserAction` | `AnAction` that opens/focuses the tool window |

### Running tests

```bash
./gradlew test
```

### Gradle tasks

| Task | Description |
|---|---|
| `./gradlew runIde` | Launch sandboxed IDE with plugin loaded |
| `./gradlew buildPlugin` | Build distributable `.zip` |
| `./gradlew verifyPlugin` | Run IntelliJ Plugin Verifier |
| `./gradlew publishPlugin` | Publish to JetBrains Marketplace (requires token) |

---

## Contributing

1. Fork the repository
2. Create a feature branch: `git checkout -b feature/my-feature`
3. Make your changes — **do not commit without review**
4. Open a Pull Request with a clear description

---

## License

MIT © FixUtils Contributors
