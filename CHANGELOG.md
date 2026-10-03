# Changelog

## Migration UI — sources courantes / Current sources

- Quarry reste en 1.4.0 et requiert désormais HomeCore 1.14.0 / API 1.9.0.
- Les écrans et aides utilisent le kit client HomeCore ; `QuarryTheme` et
  `QuarryButton` sont supprimés. Les slots et couleurs de l'aperçu 3D restent locaux.
- **EN:** current sources use the shared HomeCore UI kit, without Dashboard;
  artifact versions below describe their historical releases.

## 1.4.0

- Compatibilité JEI et REI : Avec JEI (19.0 ou plus récent) ou REI (16.0 ou plus récent), chaque objet du mod a une page d'information (onglet « i » de JEI, « Information » de REI) qui explique son rôle, en français et en anglais. Les recettes de fabrication s'y affichent comme les autres. Ces deux mods restent facultatifs et côté client.
- Requiert HomeCore 1.13.0 et HomeLink Energy 0.5.0.

## 1.3.1

- Requiert HomeCore 1.12.0 et HomeLink Energy 0.4.1.
- L'événement de casse est émis au nom du propriétaire de la Quarry : les mods de protection
  (claims) jugent ses droits au lieu de ceux d'un joueur générique.
- Tests unitaires : coût énergétique exact par bloc, statuts et traductions FR/EN.

## 1.0.0

Première version, pour HomeCore 1.7.0.

- Recettes : Control Module pour la Quarry II et les Têtes II / III ; Control et Communication
  Modules pour la Quarry III ; Circuit Board pour la Quarry I et la Tête I.

- Quarry I / II / III (zones 8×8, 16×16, 32×32) et Têtes de forage I / II / III
  (1 bloc / 10, 6, 3 s), toutes combinaisons possibles, têtes sans durabilité.
- Marqueur de Quarry (Coin A / Coin B), Arrêt Y, minage couche par couche avec une tête
  visible, fissures et particules.
- Carburant vanilla : autonomie uniquement, jamais la vitesse.
- Tampon de 27 emplacements, liste noire configurable, coffres récupérés avec leur contenu,
  aucune perte ni duplication, pas de chargement de chunks.
- Écran au style HomeLink (état, zone, sortie, aide intégrée FR/EN), renommage.
- Aperçu 3D personnel : zone, couches, avancée, couche et cible actuelles.
- Port ITEM_OUTPUT arrière avec connexion automatique au Storage Deposit, transfert
  transactionnel d'une pile par seconde, comparateur et entonnoirs.
- Appareil HomeCore : métriques, actions, événements sur transition, réseau et permissions.
