# HomeLink Quarry 1.4.0

Minecraft 1.21.1 · NeoForge 21.1.251 · Java 21 · HomeCore 1.14.0 · HomeLink Energy 0.5.0.

HomeLink Quarry ajoute une carrière physique : une Tête de forage visible se déplace
au-dessus de la zone et mine **un bloc à la fois**, couche par couche, du Départ Y
jusqu'à l'Arrêt Y. Les drops passent tous par un tampon de 27 emplacements.

| Quarry | Zone maximale |   | Tête de forage | Vitesse exacte |
|---|---|---|---|---|
| Quarry I | 8×8 | | Tête I | 1 bloc / 10 s (200 ticks) |
| Quarry II | 16×16 | | Tête II | 1 bloc / 6 s (120 ticks) |
| Quarry III | 32×32 | | Tête III | 1 bloc / 3 s (60 ticks) |

Le niveau de la Quarry fixe **seulement la zone**, la Tête **seulement la vitesse** :
les 9 combinaisons sont possibles. Les Têtes n'ont aucune durabilité. La Quarry fonctionne exclusivement en **HomeLink Energy (HE)**. L'énergie fixe l'autonomie, jamais la vitesse : 100 HE par bloc par défaut, avec un tampon égal à 50 fois le coût par bloc, soit 5 000 HE par défaut. Une Tête plus rapide demande le même total d'énergie en moins de temps.

Les modèles 3D partagent une finition industrielle cuivre et acier : châssis simple au
niveau I, circuits de refroidissement et renforts au II, épaules blindées au III.
Les Têtes s'élargissent avec leur niveau et gagnent des vérins puis une couronne blindée.
Des repères I / II / III en relief permettent aussi de reconnaître chaque niveau.

## Installation

Installer `homelink_quarry-1.4.0.jar`, `homecore-1.14.0.jar` et `homelink_energy-0.5.0.jar` dans le dossier `mods`
du client et du serveur NeoForge. HomeLink Storage est facultatif.

La Quarry implémente `NetworkMember` (HomeCore 1.10.0) : le Dashboard peut la lister dans sa zone radio et l’ajouter à un réseau. Son rattachement et son nom personnalisé restent les mêmes que dans son propre écran.

Depuis l’écran Actions du Dashboard, la Quarry s’allume et s’éteint (`Switchable`) et se renomme (`Renamable`, HomeCore 1.11.0). Éteindre met le chantier en pause ; rallumer le reprend exactement au même endroit, ou le démarre s’il était arrêté.

## Prise en main

1. Poser une Quarry : son avant (écran) regarde le joueur, son port de sortie est à l'arrière.
2. Avec un **Marqueur de Quarry** : clic droit sur un bloc = Coin A, accroupi + clic droit =
   Coin B, puis clic droit sur la Quarry pour appliquer. Le Départ Y est le coin le plus haut.
3. Ouvrir la Quarry : vue **Sortie** pour installer la Tête. Raccorder une alimentation HomeLink Energy sur une face de la machine : le port HE est accessible sur les six faces. Sans énergie, elle attend (`NO_POWER`) et reprend lorsque l'alimentation revient. Aucun combustible n'est accepté.
4. Vue **Zone** : régler l'Arrêt Y (Maj = pas de 10, « Fond » = bas du monde), puis
   **Aperçu** pour voir le volume dans le monde. Une zone invalide est rouge et ne démarre pas.
5. **Démarrer**. **Pause** fige tout ; **Reprendre** continue exactement au même endroit.
   **Arrêter** termine la session sans rien effacer : Démarrer reprend le même travail.
   Changer la zone ou l'Arrêt Y commence un nouveau travail.

Le bouton `?` de l'écran contient le guide complet (FR/EN).

### Interface commune / Shared UI

Le GUI Quarry et son aide utilisent le kit client public de **HomeCore 1.14.0**, API **1.9.0**. `QuarryTheme` et `QuarryButton` sont supprimés : les tokens, panneaux, slots, voyants, champs et boutons proviennent de `HomeLinkTheme`, `HomeLinkUi` et `HomeLinkButton`. `QuarryStatusColors` conserve le mapping des états métier. Les couleurs cuivre/rouge de l'aperçu 3D et la géométrie des inventaires restent propres à Quarry.

**Developer guidance (EN):** import `fr.lkdm.homecore.api.client.ui` only in client code. New screens use `HomeLinkUi.frame(...)` / `panel(...)` / `input(...)`, `HomeLinkButton.builder(...)`, `HomeLinkTheme.CONTROL_HEIGHT`, and `HomeLinkScreenLayout.fit(...)`. Depend explicitly on HomeCore 1.14.0 with metadata `[1.14.0,2.0.0)`; Dashboard is unnecessary, including for future HomeLink Furnace. Local composite builds use compatible adjacent sources and do not automatically update from GitHub. See [migration details](docs/UI_MIGRATION.md).

## Sortie et HomeLink Storage

Le tampon se vide dans une **entrée compatible** posée contre l'arrière de la Quarry
(capability HomeCore `ItemApi.BLOCK`, de type `INPUT` ou `BOTH`, notamment le Storage Deposit ; aucune dépendance de code à Storage). Pas de clic, pas de
configuration : la connexion est automatique, avec voyant, raccord cuivre, étincelles et
son. Une pile par seconde est transférée ; seul ce que l'entrée accepte réellement est
retiré du tampon. Un simple coffre derrière ne reçoit rien ; un entonnoir **sous** la Quarry
peut extraire le tampon et un comparateur lit son remplissage (0 à 15).

## Aperçu 3D

Personnel et local : chaque joueur choisit ce qu'il voit, rien n'est envoyé aux autres.
Zone (12 arêtes et coins, cuivre ou rouge), Couches (un plan tous les 8 blocs avec leur Y),
Avancée (volume terminé, couche actuelle, cible actuelle). Seules quelques valeurs sont
synchronisées (coins, Arrêt Y, curseur, état) : jamais la liste des blocs. Rien n'est dessiné
au-delà de `quarryPreviewRenderDistance` (64 blocs par défaut).

## Sécurité et chunks

- Jamais minés : bedrock, portails (End, Nether, passerelle), cadres de portail, blocs de
  commande et de structure, jigsaw, barrière, deepslate renforcée, blocs incassables et
  fluides. Liste configurable (`quarryBlacklist`) et extensible par le tag
  `homelink_quarry:quarry_blacklist`.
- Chaque bloc passe par l'événement de casse, au nom du propriétaire de la Quarry : les mods de protection (claims) appliquent ses droits. Une Quarry sans propriétaire connu utilise l'identité générique `[HomeLink Quarry]`.
- Coffres et inventaires : le contenu va dans le tampon avec le bloc ; si tout ne tient pas
  même dans un tampon vide, le bloc est laissé en place. Rien n'est jamais jeté au sol ni supprimé.
- Tampon plein : la Quarry attend (`Sortie pleine`) puis repart seule. Une pause voulue par
  le joueur n'est jamais levée automatiquement.
- **Chunks** : la Quarry ne charge jamais de chunk. Elle ne travaille que si son propre chunk
  est chargé ; si la cible est dans un chunk déchargé, elle attend (`Bloquée`) et reprend
  quand il se recharge. La zone doit rester à moins de `quarryMaxControllerDistance`
  (64 blocs) du contrôleur.

## HomeCore

La Quarry est un appareil HomeCore (`homelink_quarry:quarry`, UUID stable) : 19 métriques,
actions Démarrer / Pause / Reprendre / Arrêter et 12 événements publiés sur transition.
Détails : [docs/HOMECORE.md](docs/HOMECORE.md).

## Recettes

| Objet | Ingrédients |
|---|---|
| Quarry I | Fer ×2, Piston, Cuivre ×2, HomeLink Circuit Board, Redstone ×2, Coffre |
| Quarry II | **Quarry I**, Diamant ×2, HomeLink Control Module, Or ×2, Cuivre ×2, Circuit Board |
| Quarry III | **Quarry II**, Netherite ×2, HomeLink Communication Module, HomeLink Control Module, Diamant ×2, Bloc de redstone ×2 |
| Tête I | Fer ×3, Redstone, Cuivre ×2, Circuit Board |
| Tête II | **Tête I**, Diamant ×2, Control Module, Cuivre ×2, Fer |
| Tête III | **Tête II**, Diamant ×3, Control Module, Netherite ×2 |
| Marqueur | Redstone, Cuivre, Bâton |

Les modules et composants HomeLink (Circuit Board, Microprocessor, Control Module,
Communication Module) se fabriquent à l'établi électronique de HomeCore. Le Control Module
équipe les machines automatisées (Quarry II/III, Têtes II/III) ; le Communication Module
relie la Quarry III au réseau HomeLink.

## Configuration

Serveur (`homelink_quarry-server.toml`, par monde) : `quarryBlacklist`,
`quarryMaxControllerDistance` (64), `quarryPositionsPerTick` (64 positions vides passées
par tick au plus), `quarryTransferInterval` (20 ticks), `quarryEnergyLowThreshold` (15 %), `quarryEnergyPerBlock` (100 HE),
`quarryMineContainers` (true). Client : `quarryPreviewRenderDistance` (64).

## Construire et vérifier

Cloner HomeCore 1.14.0 et HomeLink Energy 0.5.0 à côté de ce projet (`../HomeCore` et `../HomeLinkEnergy`), puis activer les composites locaux :

HomeCore 1.14.0 n'a pas été publié sur Maven dans ce chantier. Les commandes
ci-dessous compilent les sources composites compatibles ; un push Git ne publie
pas cet artefact.

```powershell
./gradlew.bat -PuseLocalDependencies=true build              # compilation + vérification du JAR produit
./gradlew.bat -PuseLocalDependencies=true runGameTestServer  # GameTests serveur
./gradlew.bat runSmoke           # vérification en jeu (client réel, captures d'écran)
./gradlew.bat -PwithStorage runSmoke   # idem avec le vrai Storage Deposit (../HomeLink Storage)
./gradlew.bat releaseBundle      # build/release : JAR, HomeCore requis, README, LICENSE
```

Voir [docs/VALIDATION.md](docs/VALIDATION.md).

Les modèles sont maintenus par `python scripts/generate_models.py` (Python 3.9 ou plus,
sans dépendances). Modifier ce script puis le relancer pour conserver la cohérence des
modèles de blocs et d'inventaire ; les JSON correspondants sont inclus dans les sources.

Licence : tous droits réservés, auteur LKDM. Voir [LICENSE](LICENSE).
