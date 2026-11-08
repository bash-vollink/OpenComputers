# Port tooling (1.12.2 → 1.21.1)

`apply_renames.py` performs the mechanical part of the port, one layer at a time:

```bash
python3 -I tools/port/apply_renames.py src/main/java/li/cil/oc/api
```

- `rename-classes.tsv`: MCP/Forge class → Mojang/NeoForge class, or `REMOVED`. Every non-removed target was checked against the NeoForge 21.1 jars.
- `rename-members.tsv`: unambiguous MCP member names → Mojang names, plus regex rewrites that run before (`pre`) and after (`post`) the class pass.

The script is idempotent. It only renames: anything it can't map (wildcard and package imports, removed classes, changed signatures) is left for the compiler, and it prints a list of manual follow-ups. Run it on a layer, then add that layer to `portedSources` in `build.gradle` and fix the remaining compile errors by hand.

Don't run it on files that are already ported.
