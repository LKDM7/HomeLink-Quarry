# HomeLink Quarry et HomeCore

HomeLink Quarry n'utilise que l'API publique `fr.lkdm.homecore.api` de HomeCore 1.7.0
(API 1.3.0) et ne recrée aucune classe HomeCore.

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
Arrêter restent utilisables à distance quand la Quarry attend (carburant, sortie pleine,
chunk déchargé), une Quarry chargée reste `ONLINE` et porte son état précis dans le message
et dans la métrique `homelink_quarry:status`. Les situations d'alerte sont signalées par
événements. `ERROR` (arrêt sur erreur inattendue) donne `ERROR`, un bloc déchargé `OFFLINE`.

| Quarry | HomeCore |
|---|---|
| IDLE, READY, MINING, PAUSED, FINISHED | ONLINE (+ message) |
| NO_FUEL, NO_HEAD, OUTPUT_FULL, INVALID_AREA, BLOCKED | ONLINE (+ message, métrique et événement) |
| ERROR | ERROR |
| chunk déchargé / bloc retiré | OFFLINE |

## Métriques

`status` (énumération), `quarry_level`, `area_width`, `area_length`, `start_y`, `stop_y`,
`current_layer`, `progress` (%), `blocks_mined` (travail en cours), `lifetime_blocks_mined`
(séparé), `blocks_remaining`, `mining_head_level`, `mining_speed` (secondes par bloc),
`fuel_percentage` (%), `runtime_remaining` (durée en ticks), `estimated_blocks_with_fuel`,
`output_usage` (%), `output_item_count`, `storage_connected`. Toutes dans l'espace de noms
`homelink_quarry`, mises à jour une fois par seconde et à chaque changement d'état.

L'autonomie dépend du carburant disponible (réservoir + emplacement) et jamais du niveau
de Quarry ; l'estimation de blocs divise cette autonomie par le temps de forage de la Tête.
La progression vaut positions traitées / positions totales (les blocs passés — air,
bedrock, blocs protégés — comptent comme traités) : elle ne dépasse jamais 100 %.

## Actions

`start`, `pause`, `resume`, `stop` (boutons, permission CONTROL). HomeCore vérifie
l'appartenance au réseau, la permission, la limite de débit et la disponibilité ; la Quarry
revérifie ensuite son propre état et répond `FAILED` avec une explication quand l'action
n'a pas de sens (par exemple Pause sur une Quarry arrêtée). L'aperçu 3D reste une préférence
locale du joueur et n'est pas une action serveur.

## Événements (sur transition uniquement)

`started`, `paused`, `resumed`, `stopped`, `finished`, `fuel_low`, `fuel_empty`,
`output_full`, `output_available`, `storage_connected`, `storage_disconnected`, `blocked`.

La première observation après un chargement ne fait qu'enregistrer l'état (pas de
rediffusion). `fuel_low` part une fois quand le carburant passe sous `quarryFuelLowThreshold`
(15 %) pendant un travail, puis se réarme quand il remonte d'au moins 10 points.
`output_full` / `output_available` suivent l'entrée et la sortie de l'état `OUTPUT_FULL`.

## Port de sortie

HomeCore 1.7.0 ne possède pas d'API de ports. Aucune n'a été ajoutée : le port ITEM_OUTPUT
utilise la capability standard NeoForge `Capabilities.ItemHandler.BLOCK`, atteinte par la face
que l'entrée présente à la Quarry, et le tag `homelink_quarry:item_inputs` décide quelles
entrées sont compatibles — la même convention que la station FarmBot de HomeLink Farm.
