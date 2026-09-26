# Vérifications

## GameTests serveur (`./gradlew.bat runGameTestServer`)

41 tests, tous au vert, sur un vrai serveur de test :

- **Fondations** : limites 8×8 / 16×16 / 32×32 (exemples valides et invalides), pose et
  orientation, recettes progressives avec les composants HomeCore, loot.
- **Têtes** : 200 / 120 / 60 ticks exacts, emplacement réservé aux Têtes, 9 combinaisons,
  sauvegarde, recettes.
- **Zone** : ordre X → Z → couche suivante, Arrêt Y borné au monde, zone trop éloignée,
  verrouillage pendant un travail, Stop / Pause, sauvegarde, Marqueur.
- **Carburant** : valeurs vanilla (charbon 1600, bloc 16000, lave 20000), seau rendu,
  réservoir jamais dépassé, entonnoir, autonomie sans effet sur la vitesse.
- **Minage (ticks réels)** : bloc encore présent 3 ticks avant la fin du forage et cassé
  3 ticks après ; couche par couche jusqu'à l'Arrêt Y ; bedrock jamais minée ; coffre et
  contenu récupérés une seule fois, rien au sol ; tampon plein puis reprise ; carburant épuisé
  puis reprise sur le même bloc ; pause qui fige forage et carburant ; redémarrage.
- **Port** : entrée compatible derrière, coffre ou entrée devant ignorés, une pile par seconde,
  transfert partiel sans perte ni duplication, déconnexion / reconnexion, Sortie pleine levée
  par l'entrée mais pause joueur conservée, comparateur 0 / partiel / 15, entonnoir dessous.
- **HomeCore** : enregistrement, UUID stable, schéma complet, métriques, statut ONLINE,
  actions via la passerelle sécurisée (DENIED hors réseau ou pour un non-membre), événements
  sur transition (exactement deux `fuel_low` pour deux franchissements), sortie pleine / disponible.

HomeLink Storage n'est pas chargé dans ces tests : le pack de validation ajoute le distributeur
vanilla au tag `homelink_quarry:item_inputs` pour jouer le rôle du Storage Deposit.

## En jeu (`./gradlew.bat -PwithStorage runSmoke`)

Client réel, monde plat neuf, 139 étapes scriptées, captures dans
`build/validation/client/screenshots/` : modèles et orientations, Marqueur et messages,
entonnoir de carburant, Quarry III au travail (tête visible, fissures, drops, coffre),
écran (quatre vues, clics réels, renommage), sauvegarde / quitter / recharger le monde,
aperçu (valide, invalide, couches, avancée, cible, distance > 64), port avec le **vrai
Storage Deposit** de HomeLink Storage, rattachement à un réseau HomeCore et pause à distance.
