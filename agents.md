# FixUtils – IntelliJ Plugin: Agent Instructions

> ⚠️ **CRITICAL**: Do NOT commit any changes to the repository without **explicit user approval**.
> Present all proposed changes and wait for confirmation before committing.

---

## Project Overview

**FixUtils** is an IntelliJ IDEA plugin that parses raw FIX protocol messages and displays them in a
human-readable table, enriched with field descriptions sourced from QuickFIX-style XML dictionaries.

---

## Repository Structure

```
FixUtils/
├── Dictionaries/            # Bundled QuickFIX XML dictionary files
│   ├── FIX40.xml
│   ├── FIX41.xml
│   ├── FIX42.xml
│   ├── FIX43.xml
│   ├── FIX44.xml
│   ├── FIX44.modified.xml
│   ├── FIX50.xml
│   ├── FIX50SP1.xml
│   ├── FIX50SP1.modified.xml
│   ├── FIX50SP2.xml
│   ├── FIX50SP2.modified.xml
│   └── FIXT11.xml
├── src/
│   └── main/
│       ├── java/
│       │   └── com/fixutils/
│       │       ├── actions/
│       │       │   └── OpenFixParserAction.java       # Toolbar/menu action
│       │       ├── ui/
│       │       │   ├── FixParserToolWindow.java       # Main tool window panel
│       │       │   ├── FixParserPanel.java            # UI form (input + table)
│       │       │   └── FixTableModel.java             # TableModel for parsed fields
│       │       ├── parser/
│       │       │   └── FixMessageParser.java          # Splits raw string into tag=value pairs
│       │       └── dictionary/
│       │           ├── FixDictionaryLoader.java       # XML parser for QuickFIX dictionaries
│       │           ├── FixDictionaryService.java      # Registry of loaded dictionaries
│       │           └── FixFieldDescriptor.java        # POJO: tag number → name, type, enum values
│       └── resources/
│           ├── META-INF/
│           │   └── plugin.xml                         # Plugin descriptor
│           └── Dictionaries/                          # Bundled dictionary files (copied from root)
├── build.gradle.kts                                   # Gradle build (IntelliJ Plugin)
├── gradle.properties
├── settings.gradle.kts
├── agents.md                                          # This file
└── README.md
```

---

## XML Dictionary Format

Dictionaries follow the **QuickFIX/J schema**:

```xml
<fix major="4" minor="1">
  <fields>
    <field number="8"  name="BeginString" type="STRING"/>
    <field number="35" name="MsgType"     type="STRING">
      <value enum="D" description="ORDER_SINGLE"/>
    </field>
    ...
  </fields>
</fix>
```

**Key elements to parse:**
- `<field number="N" name="NAME" type="TYPE">` → maps tag number to name and type
- `<value enum="E" description="DESC"/>` → enum value descriptions for fields with coded values
- Custom / vendor tags (numbers > 9000) may appear in `.modified.xml` files

---

## UI Specification

### Tool Window: FIX Message Parser

The plugin exposes a **Tool Window** (docked panel) titled **"FIX Parser"**.

#### Layout (top-to-bottom)

1. **Message Input Area** (`JTextArea`, multi-line, scrollable)
   - Label: *"FIX Message"*
   - Placeholder hint text: `8=FIX.4.1|9=857|35=U4|...`

2. **Separator Selection Row**
   - Radio buttons (mutually exclusive): `| Pipe`, `^ Caret`, `~ Tilde`, `SOH (\x01)`
   - Short text field labelled *"Custom:"* for entering any other single-character delimiter
   - Group: `ButtonGroup` ensures only one option active at a time

3. **Dictionary Selection Row**
   - `JComboBox` (dropdown) populated from `Dictionaries/` folder files → display names like `FIX41`, `FIX44`, `FIX50SP2.modified`, etc.
   - `JButton` **"Browse…"** opens `FileChooserDescriptor` filtered to `*.xml`, loads and adds selected file to the dropdown

4. **Parse Button** (`JButton` labelled **"Parse"**)
   - Triggers parsing on click; also re-parses on `Ctrl+Enter` in the input area

5. **Result Table** (`JTable`, fills remaining space)
   - Columns: **Tag** | **Field Name** | **Value** | **Enum Description** *(optional, shown when enum mapping exists)*
   - Unknown tags (not in dictionary): shown in italic, `Field Name` = `[unknown]`
   - Header row style: bold, slightly shaded background

---

## Core Logic

### FIX Message Parsing (`FixMessageParser`)

```
input  →  split by selected delimiter  →  List<String> rawPairs
rawPairs  →  split each on first '='  →  List<TagValuePair>
```

- Handle SOH (`\x01`) as byte value 1 separator
- Trim whitespace from each token
- Preserve order (FIX messages are order-sensitive)

### Dictionary Loading (`FixDictionaryLoader`)

- Parse XML using JAXP `DocumentBuilder`
- Build `Map<Integer, FixFieldDescriptor>` (tag number → descriptor)
- `FixFieldDescriptor` holds: `number`, `name`, `type`, `Map<String,String> enumValues`
- Cache per file path; reload on user request

### Dictionary Service (`FixDictionaryService`)

- On startup: scan `resources/Dictionaries/` for bundled XMLs, load with `FixDictionaryLoader`
- Expose method `getDictionary(String name)` → `Map<Integer, FixFieldDescriptor>`
- Support adding external dictionaries (user-browsed files) at runtime

---

## Technology Stack

| Concern | Choice |
|---|---|
| Plugin SDK | IntelliJ Platform SDK (Gradle Plugin) |
| Build tool | Gradle with `org.jetbrains.intellij` plugin |
| UI | IntelliJ UI (Swing-based, `JPanel`, `JTable`, `JComboBox`, `JTextArea`) |
| XML parsing | JAXP (`javax.xml.parsers.DocumentBuilder`) – already on classpath |
| Min IDE version | 2023.1 (IC / IU) |
| Java target | 17 |

---

## Agent Rules

1. **No commits without explicit user approval.**
2. Prefer IntelliJ Platform APIs over raw Swing where equivalents exist (e.g., `Messages`, `FileChooser`).
3. All UI components must be created on the **Event Dispatch Thread** (EDT).
4. Parsing should be offloaded to a background thread (`ApplicationManager.getApplication().executeOnPooledThread(...)`) with results applied on EDT.
5. Keep `FixDictionaryLoader` pure/stateless; state lives in `FixDictionaryService`.
6. Follow IntelliJ plugin conventions: `plugin.xml` must list all extensions and actions.
7. When adding a new bundled dictionary, copy the file to `src/main/resources/Dictionaries/` and it will be auto-discovered at startup.
