# Arcadia CTP — Claim & Team Protection

Fait respecter **FTB Teams** et **FTB Chunks** par les autres mods, pour Minecraft 1.21.1
(NeoForge).

FTB Chunks protège ce qui passe par un clic du joueur. Tout ce qui l'évite — un packet de
réglage, une machine qui agit seule, un mod qui a sa propre notion d'« équipe » — échappe aux
claims. CTP ferme ces trous un par un, chacun sous une politique réglable en jeu :

| Mod | Trou | Section |
|---|---|---|
| Create | réglages modifiables par n'importe qui (vitesse, filtres, seuils) | [Le problème](#le-problème) |
| Create | contraptions qui cassent, déploient et vident les coffres d'un claim | [Deuxième faille](#deuxième-faille--les-contraptions) |
| Waystones | waystones « équipe » visibles par tout un grade | [Troisième faille](#troisième-faille--les-waystones--visibles-par-léquipe-) |
| Waystones | waystones injoignables dans le claim d'une autre équipe | [Waystones injoignables](#waystones-injoignables-dans-un-claim) |

> CTP voulait dire *Create Team Protection* ; le mod couvrant désormais d'autres mods que Create,
> le sigle se lit **Claim & Team Protection**. L'identifiant `arcadia_ctp`, la commande
> `/arcadiactp` et `config/arcadia-ctp.json` sont inchangés.

Non affilié à FTB Team, à BlayTheNinth ni à l'équipe de Create. Leurs mods sont requis et ne
sont pas redistribués : le jar ne contient que le code de CTP.

## Le problème

La protection de claim de FTB Chunks s'accroche aux événements d'interaction NeoForge
(`PlayerInteractEvent`). Les réglages Create ne passent pas par là : le client envoie un
`BlockEntityConfigurationPacket` contenant une `BlockPos`, et `handle()` ne vérifie que la
**distance** avant d'appliquer le changement au block entity. FTB Chunks ne voit jamais rien
passer.

`ValueSettingsPacket` (les molettes de valeur) en est la sous-classe la plus visible, mais la
famille compte une centaine de types dans Create 6.0.10, plus ceux des addons.

## L'approche

Un seul mixin, au `HEAD` de `BlockEntityConfigurationPacket.handle(ServerPlayer)` — la classe
mère. Toute la famille est couverte d'un coup, addons compris, sans énumérer les sous-classes.

Le packet est soumis à un moteur de règles avant d'atteindre le block entity ; s'il est rejeté,
l'injection annule l'appel et `applySettings` n'est jamais exécuté.

Le mixin est `@Pseudo` et gardé par un `IMixinConfigPlugin` qui inspecte la classe cible en ASM
(sans la charger) : si Create est absent, si le champ `pos` est renommé ou si la signature de
`handle` change, le patch se désactive proprement au lieu de faire planter le démarrage.

## Configuration

Fichier généré au premier lancement : `config/arcadia-ctp.json`.

```json
{
  "enabled": true,
  "defaultPolicy": "CHECK",
  "notifyOnDeny": true,
  "respectAdminBypass": true,
  "logDenials": false,
  "rules": [
    {
      "name": "Speed controllers réservés à l'équipe",
      "blocks": ["create:rotation_speed_controller"],
      "policy": "TEAM_ONLY"
    }
  ]
}
```

### Politiques

| Politique   | Effet |
|-------------|-------|
| `ALLOW`     | Toujours autorisé, même sans lien avec le claim |
| `CHECK`     | Délègue à FTB Chunks, comme un clic droit normal *(défaut recommandé)* |
| `ALLY_ONLY` | Membres de l'équipe propriétaire + alliés |
| `TEAM_ONLY` | Membres de l'équipe propriétaire uniquement, ignore les réglages alliés/public du claim |
| `DENY`      | Jamais autorisé via ce packet (le bypass admin s'applique quand même) |

Un chunk non claim n'a pas de propriétaire : `ALLY_ONLY` et `TEAM_ONLY` y laissent tout le monde
passer. Elles durcissent la protection d'un claim, elles n'en créent pas une là où il n'y en a pas.

### Correspondance des règles

Les règles sont évaluées **de haut en bas, la première qui correspond gagne** ; sinon
`defaultPolicy` s'applique.

- `packets` — nom de classe du packet, en nom simple (`ValueSettingsPacket`) ou complet.
- `blocks` — id du bloc visé (`create:rotation_speed_controller`) ; une entrée préfixée `#` est un
  tag de blocs.
- Les deux acceptent les jokers `*` et `?` (`create:*`, `*Filter*Packet`).
- Un critère absent ou vide correspond à tout. Une règle doit déclarer au moins un critère —
  sinon elle masquerait toutes les suivantes, et elle est ignorée avec un avertissement.
- Une chaîne seule est acceptée là où une liste est attendue.

### Autres options

- `notifyOnDeny` — message en barre d'action au joueur bloqué (anti-spam : 2 s).
- `respectAdminBypass` — respecte le bypass admin FTB Chunks (`/ftbchunks admin bypass`).
- `logDenials` — journalise chaque rejet (joueur, position, bloc, règle, politique).

## Panel admin

`/arcadiactp panel` — tout se configure en jeu, dans un double coffre vanilla.

Le menu utilise `MenuType.GENERIC_9x6` : il est **entièrement côté serveur**, le client le
dessine avec son propre écran de coffre. Aucun écran custom, aucun packet, aucune ressource — il
fonctionne pour un joueur qui n'a que le modpack.

Chaque changement est écrit dans `arcadia-ctp.json` immédiatement : ce qui est visible en jeu et
ce qui survit à un redémarrage sont la même chose.

| Écran | Contenu |
|---|---|
| Racine | activation, politique par défaut, notification, bypass admin, journalisation |
| Contraptions | les 5 politiques + repli pilote |
| Waystones | activation + audience de la visibilité « équipe » |
| Règles | liste ordonnée, paginée |
| Édition | nom, packets, blocs, politique, suppression |

Interactions : clic gauche bascule ou fait avancer une politique, clic droit la fait reculer (ou
ouvre l'édition d'une règle), shift + clic réordonne une règle — l'ordre est toute la sémantique
du moteur. La suppression demande shift + clic pour confirmer.

Le texte libre (nom de règle, id de bloc, motif de packet) passe par le **chat** : un coffre
vanilla n'a pas de champ de saisie. Le menu se ferme, le message suivant du joueur est capturé au
lieu d'être envoyé, puis le panel se rouvre là où il était. `cancel` annule, la capture expire au
bout de 2 minutes, et seul le joueur qui a ouvert la saisie est concerné.

## Commandes

Niveau de permission 2.

- `/arcadiactp panel` — ouvre le panel admin en jeu.
- `/arcadiactp reload` — recharge `arcadia-ctp.json`. Un fichier invalide conserve la
  configuration précédente.
- `/arcadiactp status` — état courant et liste ordonnée des règles.
- `/arcadiactp probe [<x> <y> <z> [packet]]` — évalue les règles **pour vous** sur le bloc que
  vous regardez (ou à la position donnée), et affiche la règle retenue, la relation au claim et
  le verdict. Sans argument `packet`, `ValueSettingsPacket` est supposé.

## Installation

Sur le serveur **et** les clients, avec :

| Dépendance | |
|---|---|
| FTB Chunks, FTB Teams, FTB Library, Architectury | requis |
| Create | optionnel — sans lui, les protections Create se désactivent |
| Waystones | optionnel — sans lui, les protections Waystones se désactivent |

Chaque protection vérifie au démarrage que le mod visé a encore la forme attendue. Si un mod est
absent ou a changé de structure, **seule** la protection concernée se désactive, avec une ligne
dans le log (`... disabled: ...`) ; le serveur démarre normalement.

## Build

```bash
./gradlew build
```

Le jar sort dans `build/libs/`.

FTB Chunks / Teams / Library ne sont publiés sur aucun dépôt Maven joignable, et leur licence
(*All Rights Reserved*) interdit de les redistribuer : ils ne sont **pas** dans ce dépôt. Déposer
les trois jars dans `libs/` avant le premier build :

```
libs/
├── ftb-chunks-neoforge-2101.1.14.jar
├── ftb-teams-neoforge-2101.1.10.jar
└── ftb-library-neoforge-2101.1.31.jar
```

Pour d'autres versions ou un autre dossier, surcharger `ftb_jar_dir` et `ftb_*_jar` avec
`-P` ou dans `~/.gradle/gradle.properties`.

Create et Waystones sont publiés sur des Maven joignables et arrivent tout seuls en
`compileOnly` ; ils ne sont pas non plus embarqués dans le jar.

### Banc de test

`./gradlew runServer`, `runClient` (`Owner_Dev`) et `runClient2` (`Intruder_Dev`) lancent un
serveur et deux clients hors ligne dans `run/`, de quoi tester à deux sur une seule machine. Les
mods de test se déposent à la main dans `run/*/mods` (FTB, Architectury, et pour la partie
waystones, Waystones **et** Balm).

## Deuxième faille : les contraptions

Une contraption assemblée hors d'un claim et poussée dedans peut casser des blocs, déployer
dessus et **vider les coffres** via une Portable Storage Interface. Rien de tout ça ne passe par
un joueur : c'est du code serveur déclenché par redstone, donc FTB Chunks n'est jamais consulté.

### Attribution

Une contraption n'a pas de joueur derrière elle et continue de tourner son propriétaire déconnecté.
Elle est donc **estampillée** à la première utilisation avec l'équipe propriétaire du claim où elle
a été assemblée, et cette marque est persistée en NBT (elle survit aux redémarrages, et n'est pas
recalculée là où la machine se trouve ensuite).

Assemblée hors claim, elle n'a aucune équipe et ne peut rien toucher dans un claim — sauf pendant
qu'un joueur la **pilote** : elle emprunte alors ses droits (`pilotFallback`). C'est ce qui permet
d'aller miner en terrain libre puis de rentrer chez soi.

### Points d'accroche

| Vecteur | Accroche |
|---|---|
| Casse de blocs (drill, saw, harvester) | `AbstractContraptionEntity` — call-site de `visitNewPosition` |
| Deployer | idem |
| Portable Storage Interface | idem + `findInterface` (un PSI déjà accroché pompe depuis `tick`) |
| Assemblage happant les blocs du voisin | `BlockMovementChecks.registerMovementAllowedCheck` (API publique) |

`visitNewPosition` est le passage obligé de **tous** les acteurs de contraption et n'a que deux
call-sites, tous deux dans `AbstractContraptionEntity` : les envelopper couvre les acteurs des
addons sans en connaître un seul. Tout acteur non reconnu est classé comme destructeur — un nouvel
addon est protégé par défaut plutôt qu'exempté en silence.

### Réglages

```json
"contraptions": {
  "enabled": true,
  "blockBreaking": "TEAM_ONLY",
  "deployer": "TEAM_ONLY",
  "storageInterface": "TEAM_ONLY",
  "assembly": "TEAM_ONLY",
  "pilotFallback": true
}
```

Mêmes politiques que plus haut ; `CHECK` y est lu comme `ALLY_ONLY`, faute de joueur à vérifier.
Un acteur bloqué reste simplement inerte le temps de traverser le claim — la contraption ne cale pas.

## Troisième faille : les waystones « visibles par l'équipe »

Waystones n'utilise pas FTB Teams. Pour une waystone réglée sur *Visible to Team*,
`WaystoneIndexManager.getTeamTargets(ServerPlayer)` lit `player.getTeam()` — l'**équipe de
scoreboard vanilla** — et rend toutes les waystones indexées sous ce nom d'équipe.

Sur Arcadia, la tab list range chaque joueur dans une équipe de scoreboard `as_<poids>_<grade>`
pour trier l'affichage et porter le préfixe. Pour Waystones, « mon équipe » signifie donc « mon
grade » : chaque joueur du groupe par défaut reçoit les waystones d'équipe de tous les autres
joueurs du groupe par défaut. Les alliances FTB n'y jouent aucun rôle.

`MixinWaystoneIndexManager` injecte au `HEAD` de `getTeamTargets` et reconstruit la liste depuis
FTB Teams : la waystone est offerte à son propriétaire et aux membres de sa **party FTB**. Seule
la lecture change — l'index de scoreboard tenu par Waystones n'est pas touché.

Si FTB Teams n'est pas chargé, la réponse est une liste **vide** : jamais de repli sur la
recherche scoreboard, qui est exactement la fuite qu'on ferme ici. Une destination manquante est
un désagrément ; une destination en trop est la base d'un inconnu sur la carte.

### Réglages

`waystones` dans `arcadia-ctp.json`, ou l'écran *Waystones* du panel :

| Politique | Audience de la waystone « équipe » |
|---|---|
| `ALLOW` | comportement Waystones d'origine (scoreboard) |
| `CHECK` | lu comme `TEAM_ONLY`, faute d'interaction à déléguer |
| `TEAM_ONLY` | la party FTB du propriétaire (défaut) |
| `ALLY_ONLY` | la party plus les équipes alliées |
| `DENY` | le propriétaire seul |

Le propriétaire garde sa propre waystone sous toutes les politiques, `DENY` compris.

Waystones est une dépendance **optionnelle** : absent, le plugin de mixin désactive le patch au
démarrage (une ligne de log) et Waystones garde son comportement. La visibilité `TEAM` n'existe pas dans toutes les versions : présente en 21.1.36 et 21.1.37
(celle du pack Arcadia), absente en 21.1.15 — le Maven de Waystones n'a aucune version entre
les deux pour dater son arrivée plus finement. Sur une version qui ne l'a pas, il n'y a rien à
corriger, et le patch se désactive de lui-même.

La dépendance est déclarée en `[21.1,)`, volontairement large : une dépendance **optionnelle**
reste imposée dès que le mod est présent, et une plage plus étroite empêchait le jeu de
démarrer à côté d'un Waystones plus ancien.

## Waystones injoignables dans un claim

Symptôme : un joueur ne peut pas faire clic droit sur une waystone posée dans le claim d'une
autre équipe pour l'enregistrer. FTB Chunks refuse l'interaction comme sur n'importe quel bloc.

Ce n'est pas un oubli de FTB Chunks — il a déjà tout prévu. `Protection.INTERACT_BLOCK` teste le
tag `#ftbchunks:interact_whitelist` **avant** de regarder l'équipe et rend `ALLOW` immédiatement,
et le tag livré par FTB Chunks contient bien des entrées waystone. Elles sont simplement
**mal nommées** :

| FTB Chunks demande | Waystones fournit |
|---|---|
| `#waystones:waystone` | `#waystones:waystones` |
| `#waystones:sharestone` | `#waystones:sharestones` |
| — | `#waystones:portstones` |

Les deux entrées sont marquées `"required": false` : elles ne résolvent rien, sans le moindre
avertissement, et la whitelist reste vide.

Le mod fournit donc sa propre liste, `#arcadia_ctp:claim_interact_whitelist` (les waystones,
sharestones et portstones sous leurs vrais noms), et l'injecte dans le tag de FTB Chunks. Ça
ouvre la porte — mais un tag de datapack n'a pas d'interrupteur, et c'est tout ou rien. La
décision est donc **rendue au mod**, sous une politique réglable dans le panel :

| `claimInteraction` | Qui peut enregistrer une waystone posée chez quelqu'un d'autre |
|---|---|
| `ALLOW` | n'importe quel visiteur (défaut) |
| `ALLY_ONLY` | l'équipe propriétaire et ses alliés |
| `TEAM_ONLY` | l'équipe propriétaire seule |
| `CHECK` | rendu à FTB Chunks, comme si le bloc n'était pas whitelisté |
| `DENY` | personne |

Le refus passe par `PlayerInteractEvent.RightClickBlock`, respecte le bypass admin et prévient le
joueur comme le reste du mod.

Ce n'est que la **discussion** avec le bloc : renommer une waystone, changer sa visibilité ou la
casser reste soumis à `WaystonePermissionManager.mayEditWaystone` côté Waystones et à la
protection de claim côté FTB Chunks.

Pour couvrir d'autres blocs (un coffre public, une machine partagée), il suffit d'étendre
`#arcadia_ctp:claim_interact_whitelist` depuis un datapack : la politique s'y applique aussi.

## Non couvert

- Le presse-papiers Create (copier/coller de réglages entre machines).
- Schematicannon agissant hors du claim de son propriétaire.

Chacun demande son propre point d'accroche ; le moteur de règles est réutilisable tel quel.

## Versions

| | |
|---|---|
| Minecraft | 1.21.1 |
| NeoForge | 21.1.221 |
| Create | 6.0.10 (optionnel) |
| FTB Chunks | 2101.1.14 |
| FTB Teams | 2101.1.10 |
| FTB Library | 2101.1.31 |
| Waystones | 21.1.x (optionnel) — visibilité équipe corrigée à partir de 21.1.36 |

## Licence

[LGPL-3.0](LICENSE). La LGPL étant un ensemble de permissions ajoutées à la GPL-3.0, le texte de
celle-ci est fourni dans [`COPYING`](COPYING). Les deux textes sont aussi embarqués dans le jar.
