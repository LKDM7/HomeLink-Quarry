# HomeLink Quarry et HomeCore

HomeLink Quarry n'utilise que l'API publique `fr.lkdm.homecore.api` de HomeCore 1.12.0
(API 1.8.0) et ne recrée aucune classe HomeCore.

## Appareil

- Type : `homelink_quarry:quarry`. UUID stable, sauvegardé avec le bloc ; un bloc copié qui
  entrerait en collision avec un appareil vivant reçoit une nouvelle identité.
- Enregistré au chargement du bloc, retiré au déchargement du chunk ou à la destruction
  (HomeCore ne scanne jamais le monde). Détruire le bloc le retire aussi de son réseau.
- Nom : le nom donné dans l'écran (bouton Renommer), sinon « Quarry I/II/III ».
- Réseau : bouton « HomeLink : réseau » de l'écran. Il faut pouvoir configurer la Quarry
  (propriétaire, opérateur ou permission CONFIGURE) **et** avoir MANAGE_NETWORK sur le
  réseau d'arrivée comme sur le précédent.

## État

HomeCore n'exécute une action que sur un appareil `ONLINE`. Pour que Pause, Reprendre et
Arrêter restent utilisables à distance quand la Quarry attend (énergie, sortie pleine,
chunk déchargé), une Quarry chargée reste `ONLINE` et porte son état précis dans le message
et dans la métrique `homelink_quarry:status`. Les situations d'alerte sont signalées par
événements. `ERROR` (arrêt sur erreur inattendue) donne `ERROR`, un bloc déchargé `OFFLINE`.

| Quarry | HomeCore |
|---|---|
| IDLE, READY, MINING, PAUSED, FINISHED | ONLINE (+ message) |
| NO_POWER, NO_HEAD, OUTPUT_FULL, INVALID_AREA, BLOCKED | ONLINE (+ message, métrique et événement) |
| ERROR | ERROR |
| chunk déchargé / bloc retiré | OFFLINE |

## Métriques

`status` (énumération), `quarry_level`, `area_width`, `area_length`, `start_y`, `stop_y`,
`current_layer`, `progress` (%), `blocks_mined` (travail en cours), `lifetime_blocks_mined`
(séparé), `blocks_remaining`, `mining_head_level`, `mining_speed` (secondes par bloc),
`energy_percentage` (%), `runtime_remaining` (durée en ticks), `estimated_blocks_with_energy`,
`output_usage` (%), `output_item_count`, `storage_connected`. Toutes dans l'espace de noms
`homelink_quarry`, mises à jour une fois par seconde et à chaque changement d'état.

Le nombre de blocs estimé vaut `HE stockés / HE par bloc`, arrondi à l’entier inférieur. L’autonomie en ticks vaut `HE stockés × ticks par bloc de la Tête / HE par bloc`. Sans Tête, les deux valeurs sont nulles. Le tampon contient 50 fois le coût configuré par bloc ; le niveau de Quarry ne modifie pas ces règles. Les HE sont consommés progressivement pendant le forage, pour un coût total identique quelle que soit la Tête.
La progression vaut positions traitées / positions totales (les blocs passés — air,
bedrock, blocs protégés — comptent comme traités) : elle ne dépasse jamais 100 %.

## Actions

`start`, `pause`, `resume`, `stop` (boutons, permission CONTROL). HomeCore vérifie
l'appartenance au réseau, la permission, la limite de débit et la disponibilité ; la Quarry
revérifie ensuite son propre état et répond `FAILED` avec une explication quand l'action
n'a pas de sens (par exemple Pause sur une Quarry arrêtée). L'aperçu 3D reste une préférence
locale du joueur et n'est pas une action serveur.

La Quarry implémente aussi `Switchable` et `Renamable` : HomeCore ajoute les actions
standard `homecore:power` (toggle, CONTROL ; éteindre = Pause, allumer = Resume ou Start)
et `homecore:rename` (texte, CONFIGURE ; même nettoyage que l'écran de la Quarry).

## Événements (sur transition uniquement)

`started`, `paused`, `resumed`, `stopped`, `finished`, `energy_low`, `no_power`,
`output_full`, `output_available`, `storage_connected`, `storage_disconnected`, `blocked`.

La première observation après un chargement ne fait qu'enregistrer l'état (pas de
rediffusion). `energy_low` part une fois quand la charge passe sous `quarryEnergyLowThreshold`
(15 %) pendant un travail, puis se réarme quand il remonte d'au moins 10 points.
`output_full` / `output_available` suivent l'entrée et la sortie de l'état `OUTPUT_FULL`.

## Port de sortie

Le port arrière expose la capability HomeCore `ItemApi.BLOCK` de type `OUTPUT`. Le transfert automatique cherche un port `INPUT` ou `BOTH` sur la face voisine, selon le même contrat que FarmBot Station et Storage Deposit. La capability standard NeoForge reste disponible pour l'extraction par entonnoir ; les tags de noms de blocs ne définissent plus le contrat inter-mods.
