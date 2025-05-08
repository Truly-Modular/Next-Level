# Truly Modular: Next Level

This is a small addon adding tool leveling to **Truly Modular**.  
It introduces a new data-driven system called the **Upgrade**, which enables tools to gain experience and unlock enhancements as they are used.

## 💡 What Are Upgrades?

Upgrades are modular improvements applied to tools that can level up over time.  
They are defined in datapacks and consist of the following components:

### ✨ Upgrade Definition (`upgrade.json`)
Each upgrade is specified with:

- **`tag`**: A string matching module tags the upgrade can apply to.
- **`condition`**: A condition that must be true for the upgrade to apply (e.g., player level, biome, etc.).
- **`properties`**: A map of upgrade levels to their associated stat or behavior changes.
The highest set level below the current one will be selected for the properties
- **`incompatible`**: A list of upgrades that cannot coexist with this one (defaults to empty list).
- **`max`**: The maximum level this upgrade can reach.
- **`cost`**: The XP cost per level (defaults to 1).

### 🧠 XP and Leveling

As tools are used, they accumulate XP. These XP then convert into upgrade points
that can be spend to upgrade

### 📦 Example Datapack Entry

```json
{
    "tag": "blade",
    "condition": {
        "type": "true"
    },
    "max": "5",
    "incompatible": [
        "tm_next_level:tm_arsenal/slashing"
    ],
    "properties": {
        "1": {
            "merge": {
                "armor_pen": [
                    {
                        "operation": "**",
                        "value": "0.3"
                    },
                    {
                        "operation": "+",
                        "value": "[module_upgrade.tm_next_level.tm_arsenal/slashing]"
                    }
                ]
            }
        }
    }
}
```


---

For developers, check the `Upgrade` class under `smartin.tmnextlevel.upgrade` to see codec structure, property resolution, and data integration.
