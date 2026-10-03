# Migration GUI Quarry / Quarry UI migration

HomeLink Quarry 1.4.0 utilise HomeCore 1.14.0, API publique 1.9.0. Les classes locales `QuarryTheme` et `QuarryButton` sont supprimées.

| Ancien code / Previous code | API actuelle / Current API |
| --- | --- |
| palette `QuarryTheme` | `HomeLinkTheme` |
| `window`, `panel`, `slot`, `screw`, `gauge` | mêmes helpers `HomeLinkUi` |
| `divider`, `statusLight` | `HomeLinkUi.separator`, `statusDot` |
| `QuarryTheme.status(...)` | mapping métier local `QuarryStatusColors.color(...)` vers `HomeLinkStatusTone` |
| `QuarryButton.builder(...)` | `HomeLinkButton.builder(...)` |
| `accentWhen(supplier)` | `HomeLinkButton.selectedWhen(supplier)` : bouton gris enfoncé, texte cuivre |
| champ de renommage | `HomeLinkUi.input(...)`, hauteur 18 |

Le header utilise 29 pixels. Les dimensions fonctionnelles de `QuarryScreenLayout` restent 270 × 234, y compris les coordonnées de la Tête, du tampon et de l'inventaire. Prévoir un viewport GUI de 286 × 250 pour conserver les marges de 8 pixels ; sous ces dimensions, réduire l'échelle GUI. Les vues statut, zone, sortie et aide utilisent le même kit sans imposer une surface 520 × 340.

**English:** the kit belongs to `fr.lkdm.homecore.api.client.ui` and is client-only. Quarry keeps status semantics, mining state, help content, network actions, inventory geometry and its copper/red 3D area preview. HomeCore contains only shared visual tokens and controls. Native button narration, keyboard focus, localized labels and full-label tooltips remain available. New HomeLink consumers can use the kit without installing Dashboard or copying a theme.

Declare `fr.lkdm.homecore:homecore:1.14.0` and metadata `[1.14.0,2.0.0)`. Local composites require the matching checkout; using this API neither embeds HomeCore in Quarry nor updates it automatically from GitHub.

HomeCore 1.14.0 has not been published to Maven as part of this migration.
Build with matching adjacent HomeCore and Energy sources and
`-PuseLocalDependencies=true`; pushing Git commits does not publish a Maven artifact.

The optional `-PwithStorage` verification uses Storage 1.4.0 explicitly
(`storage_version=1.4.0`). Its local composite requires a matching Minecraft
1.21.1 / Storage 1.4.0 checkout, normally `../HomeLink Storage`.

La CI clone le consommateur, HomeCore et Energy dans des dossiers voisins et
utilise ces composites, avec des commits de dépendances épinglés dans
`.github/workflows/ci.yml`. **EN:** private dependency repositories require
`HOMELINK_REPOSITORIES_TOKEN` with Contents read access to HomeCore and Energy.
An existing `HOMELINK_PACKAGES_TOKEN` with the same repository access is also
accepted; public repositories can use the `github.token` fallback. Tokens are
not persisted by checkout, and these workflows do not publish Maven artifacts.
