# Changelog

## 0.1.0

Première version publique.

### Create

- Les réglages de machines (molettes de valeur, filtres, seuils) passent par un moteur de règles
  avant d'atteindre le bloc. Ils échappaient jusqu'ici aux claims : les packets de configuration
  de Create ne déclenchent aucun événement d'interaction, et FTB Chunks ne les voyait pas.
- Les contraptions n'agissent plus dans un claim auquel leur équipe n'a pas droit : casse de
  blocs, deployers, Portable Storage Interface, et vol de blocs à l'assemblage. Une contraption
  est rattachée à l'équipe du claim où elle a été assemblée, et peut emprunter les droits du
  joueur qui la conduit.

### Waystones

- Les waystones « Visible to Team » ne sont plus proposées qu'à la party FTB du propriétaire.
  Waystones s'appuie sur l'équipe de scoreboard vanilla, qu'un autre mod peut remplir pour
  d'autres raisons — une tab list triée par grade rendait chaque waystone d'équipe visible par
  tout le grade.
- Une waystone posée dans le claim d'une autre équipe peut de nouveau être enregistrée. FTB
  Chunks tentait de l'autoriser, mais visait des tags au singulier (`#waystones:waystone`)
  alors que Waystones les fournit au pluriel.

### Administration

- Panel en jeu dans un double coffre vanilla (`/arcadiactp panel`) : chaque protection a sa
  politique (`ALLOW`, `CHECK`, `ALLY_ONLY`, `TEAM_ONLY`, `DENY`), écrite immédiatement dans
  `config/arcadia-ctp.json`. Le texte libre se saisit dans le chat.
- `/arcadiactp status`, `reload` et `probe`, qui évalue les règles sur le bloc visé.
- Create et Waystones sont optionnels. Une protection dont le mod visé est absent, ou a changé de
  structure, se désactive seule avec une ligne de log au lieu de bloquer le démarrage.

### Limites connues

- Le panel et ses invites sont en français uniquement.
- Le presse-papiers de Create et le Schematicannon ne sont pas couverts.
