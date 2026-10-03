# ⛏️ HomeLink Quarry

🇬🇧 **English** · 🇫🇷 **[Français plus bas](#-français)**

---

## 🇬🇧 English

**A real quarry that digs layer by layer.**

HomeLink Quarry adds a physical quarry: a visible mining head moves across the area and mines **one block at a time**, layer by layer, from the Start Y down to the Stop Y. Every drop goes into a 27-slot buffer that can empty itself into your storage.

> ⚠️ **Requires [HomeCore](https://www.curseforge.com/minecraft/mc-mods/homecore) and [HomeLink Energy](https://www.curseforge.com/minecraft/mc-mods/homelink-energy).**

### ✨ Features

#### 📐 Area and speed, your choice
- **3 quarry tiers** set the **area**, **3 mining heads** set the **speed**. All 9 combinations work.

| Quarry | Max area | | Mining Head | Speed |
| --- | --- | --- | --- | --- |
| Quarry I | 8×8 | | Head I | 1 block / 10 s |
| Quarry II | 16×16 | | Head II | 1 block / 6 s |
| Quarry III | 32×32 | | Head III | 1 block / 3 s |

#### ⚡ Powered by HomeLink Energy
- Energy sets how long the quarry runs, never how fast it goes: **100 HE per block** by default.
- **No fuel**, and mining heads **never wear out**.

#### 🎯 Easy setup
- Select two corners with the **Quarry Marker**, then pick a Stop Y.
- A **3D preview** shows the volume in the world before you start, then follows the progress live.
- **Pause**, **Resume** exactly where it stopped, or **Stop**.

#### 📤 Automatic output
- Place a compatible input (for example a HomeLink Storage Deposit) behind the quarry: the buffer empties into it by itself.
- A comparator reads how full the buffer is.

#### 🛡️ Safe by design
- Never mines bedrock, portals, command blocks, barriers, unbreakable blocks or fluids. The blacklist is configurable.
- Every block is broken in the owner's name, so **claim and protection mods** apply their rules.
- Never force-loads chunks, and never drops or deletes anything: container contents go into the buffer.

#### 📖 Built-in manual
- The **?** button opens a full guide in game, in English and French.
- With **JEI** or **REI** (optional), every item has an information page.

### 🧱 Blocks and items

| Block / Item | Role |
| --- | --- |
| **Quarry** (I to III) | The machine: sets the maximum area |
| **Mining Head** (I to III) | Sets the mining speed |
| **Quarry Marker** | Selects the two corners of the area |

The recipes use HomeCore's electronic components (Circuit Board, Control Module, Communication Module), assembled at the **HomeLink Electronics Workbench**.

### 🚀 Quick start

1. Place a **Quarry**. Its screen faces you and its output port is on the back.
2. With a **Quarry Marker**: right-click a block for corner A, sneak + right-click for corner B, then right-click the quarry.
3. Connect a HomeLink Energy supply to any side of the quarry.
4. Set the Stop Y, check the **Preview**, then press **Start**.

### 🔌 Compatible mods

- **HomeLink Dashboard**: metrics, actions (start, pause, resume, stop, rename, power) and events
- **HomeLink Energy**: powers the quarry
- **HomeLink Storage**: the Storage Deposit receives the mined blocks automatically

### 📦 Installation

| Component | Version |
| --- | --- |
| Minecraft | 1.21.1 |
| NeoForge | 21.1.251+ |
| HomeCore | 1.14.0+ (required) |
| HomeLink Energy | 0.5.0+ (required) |
| HomeLink Storage | optional |
| JEI / REI | optional |

Install the mod on **both client and server**.

---

## 🇫🇷 Français

**Une vraie carrière qui creuse couche par couche.**

HomeLink Quarry ajoute une carrière physique : une tête de forage visible se déplace au-dessus de la zone et mine **un bloc à la fois**, couche par couche, du Départ Y jusqu'à l'Arrêt Y. Chaque drop passe dans un tampon de 27 emplacements qui peut se vider tout seul dans votre stockage.

> ⚠️ **Ce mod nécessite [HomeCore](https://www.curseforge.com/minecraft/mc-mods/homecore) et [HomeLink Energy](https://www.curseforge.com/minecraft/mc-mods/homelink-energy).**

### ✨ Fonctionnalités

#### 📐 Zone et vitesse au choix
- **3 niveaux de Quarry** fixent la **zone**, **3 Têtes de forage** fixent la **vitesse**. Les 9 combinaisons fonctionnent.

| Quarry | Zone maximale | | Tête de forage | Vitesse |
| --- | --- | --- | --- | --- |
| Quarry I | 8×8 | | Tête I | 1 bloc / 10 s |
| Quarry II | 16×16 | | Tête II | 1 bloc / 6 s |
| Quarry III | 32×32 | | Tête III | 1 bloc / 3 s |

#### ⚡ Alimentée par HomeLink Energy
- L'énergie fixe l'autonomie, jamais la vitesse : **100 HE par bloc** par défaut.
- **Pas de combustible**, et les Têtes de forage **ne s'usent jamais**.

#### 🎯 Mise en place facile
- Sélectionnez deux coins avec le **Marqueur de Quarry**, puis choisissez l'Arrêt Y.
- Un **aperçu 3D** montre le volume dans le monde avant de démarrer, puis suit l'avancée en direct.
- **Pause**, **Reprise** exactement là où elle s'était arrêtée, ou **Arrêt**.

#### 📤 Sortie automatique
- Placez une entrée compatible (par exemple un Dépôt de stockage de HomeLink Storage) derrière la Quarry : le tampon s'y vide tout seul.
- Un comparateur lit le remplissage du tampon.

#### 🛡️ Conçue pour être sûre
- Ne mine jamais le bedrock, les portails, les blocs de commande, les barrières, les blocs incassables ni les fluides. La liste noire est configurable.
- Chaque bloc est cassé au nom du propriétaire : les **mods de protection** (claims) appliquent leurs règles.
- Ne charge jamais de chunk de force, et ne jette ni ne supprime rien : le contenu des coffres rejoint le tampon.

#### 📖 Manuel intégré
- Le bouton **?** ouvre un guide complet en jeu, en français et en anglais.
- Avec **JEI** ou **REI** (facultatifs), chaque objet a une page d'information.

### 🧱 Blocs et objets

| Bloc / Objet | Rôle |
| --- | --- |
| **Quarry** (I à III) | La machine : fixe la zone maximale |
| **Tête de forage** (I à III) | Fixe la vitesse de minage |
| **Marqueur de Quarry** | Sélectionne les deux coins de la zone |

Les recettes utilisent les composants électroniques de HomeCore (circuit imprimé, module de contrôle, module de communication), assemblés dans l'**Établi électronique HomeLink**.

### 🚀 Démarrage rapide

1. Posez une **Quarry**. Son écran est face à vous et son port de sortie à l'arrière.
2. Avec un **Marqueur de Quarry** : clic droit sur un bloc pour le coin A, accroupi + clic droit pour le coin B, puis clic droit sur la Quarry.
3. Raccordez une alimentation HomeLink Energy sur n'importe quelle face de la Quarry.
4. Réglez l'Arrêt Y, vérifiez l'**Aperçu**, puis appuyez sur **Démarrer**.

### 🔌 Mods compatibles

- **HomeLink Dashboard** : mesures, actions (démarrer, pause, reprendre, arrêter, renommer, allumer/éteindre) et événements
- **HomeLink Energy** : alimente la Quarry
- **HomeLink Storage** : le Dépôt de stockage reçoit automatiquement les blocs minés

### 📦 Installation

| Composant | Version |
| --- | --- |
| Minecraft | 1.21.1 |
| NeoForge | 21.1.251+ |
| HomeCore | 1.14.0+ (obligatoire) |
| HomeLink Energy | 0.5.0+ (obligatoire) |
| HomeLink Storage | facultatif |
| JEI / REI | facultatifs |

Installez le mod sur le **client et le serveur**.

---

*Apache 2.0 License · Licence Apache 2.0 — by / par LKDM*
