# Vérifications

## GameTests serveur (`./gradlew.bat runGameTestServer`)

La copie de validation du 29 septembre 2026 a réussi **47 GameTests** sur un vrai serveur de test :

- **Fondations** : limites 8×8 / 16×16 / 32×32 (exemples valides et invalides), pose et
  orientation, recettes progressives avec les composants HomeCore, loot.
- **Têtes** : 200 / 120 / 60 ticks exacts, emplacement réservé aux Têtes, 9 combinaisons,
  sauvegarde, recettes.
- **Zone** : ordre X → Z → couche suivante, Arrêt Y borné au monde, zone trop éloignée,
  verrouillage pendant un travail, Stop / Pause, sauvegarde, Marqueur.
- **Énergie HE** : ports HomeCore, consommation pendant le forage, réserve et autonomie,
  suspension sans énergie et reprise du travail après alimentation.
- **Minage (ticks réels)** : bloc encore présent 3 ticks avant la fin du forage et cassé
  3 ticks après ; couche par couche jusqu'à l'Arrêt Y ; bedrock jamais minée ; coffre et
  contenu récupérés une seule fois, rien au sol ; tampon plein puis reprise ; énergie épuisée
  puis reprise sur le même bloc ; pause qui fige forage et consommation ; redémarrage.
- **Port** : entrée compatible derrière, coffre ou entrée devant ignorés, une pile par seconde,
  transfert partiel sans perte ni duplication, déconnexion / reconnexion, Sortie pleine levée
  par l'entrée mais pause joueur conservée, comparateur 0 / partiel / 15, entonnoir dessous.
- **HomeCore** : enregistrement, UUID stable, schéma complet, métriques, statut ONLINE,
  actions via la passerelle sécurisée (DENIED hors réseau ou pour un non-membre), événements
  sur transition, sortie pleine / disponible et liaison avec contrôle des permissions.

HomeLink Storage n'est pas chargé dans ces tests natifs : le mod de validation attribue au dropper
vanilla un port HomeCore `ItemApi.BLOCK` de type `INPUT`. Aucun tag de bloc n'autorise désormais
le transfert. Cette fixture ne figure pas dans le JAR distribué.

Le harnais `HomeCore/integration-tests` a également réussi cinq GameTests avec les vrais mods
HomeCore, Energy, Farm, Storage et Quarry. Le cas Quarry → Storage utilise un vrai Deposit
presque plein : quatre objets entrent et les soixante restants restent dans le tampon Quarry.
Les autres cas couvrent Energy → Farm, FarmBot Station → Storage, les faces fermées et les
permissions HomeNetwork. Ces transferts ne remplacent pas les tests natifs de forage.

## En jeu (`./gradlew.bat -PwithStorage runSmoke`)

Cette vérification client n'a pas été réexécutée pendant la validation serveur ci-dessus.
Les scénarios et captures dans `build/validation/client/screenshots/` couvrent les modèles et
orientations, le Marqueur et ses messages, Quarry III au travail (tête visible, fissures, drops, coffre),
écran (quatre vues, clics réels, renommage), sauvegarde / quitter / recharger le monde,
aperçu (valide, invalide, couches, avancée, cible, distance > 64), port avec le **vrai
Storage Deposit** de HomeLink Storage, rattachement à un réseau HomeCore et pause à distance.
