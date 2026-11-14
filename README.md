# Citizen Craft (`citizencraft`): NeoForge 21.1.215 / MC 1.21.1 / Java 21

## Compiler
```
gradlew build
```
Le JAR se trouve dans `build/libs/citizencraft-1.0.0.jar`. Il faut le mettre **sur le serveur ET chez les joueurs** (via Citizen Updater / le dépôt GitHub).
Si Gradle ne trouve pas le plugin `net.neoforged.moddev` 2.0.78, reprends la version du `build.gradle` de citizeneconomy.

## Utilisation
- `/craft` (ou `/recettes`) ouvre le *Livre des Recettes du Royaume* (accessible à tous les joueurs).
- Barre de recherche : nom, identifiant ou mod. `@nomdumod` filtre par mod (ex. `@hibou`). Clic droit dessus pour la vider.
- Catégories : Tout, Pêche, Artefacts, Chasse & Boucherie, Animaux, Forge & Armures, Artisanat, Nourriture & Bière, Courrier, Affiches, Menuiserie, Économie, Minecraft, Autres mods.
- Clic sur un ingrédient : affiche sa propre recette (bouton « ← Retour »). Molette sur le détail : recette suivante.
- Repère rouge sur une case : à faire à la forge. Repère orange : à faire à la taverne ou au restaurant.
- Le bandeau du bas rappelle les règles RP (forge, taverne/restaurant, pêche réservée aux pêcheurs).
- Onglet **Animaux** : poids min./max. de chaque espèce et nombre de morceaux, de poulet ou de filets de poisson obtenus. Survole une ligne pour voir le détail selon le poids.

## Configuration serveur
`config/citizencraft-common.toml` (modifiable par FTP) : `messageActif`, `delaiSecondes` (message de connexion).

## Citizen Réalisme
Si `citizenrealisme` est installé, chaque recette de table de craft affiche : craft instantané ou non, outil d'artisan requis
(marteau, scie, aiguille), taux de réussite et durée. Les outils et sacs ont leur fiche dans l'onglet Artisanat,
avec le rituel d'enchantement et les règles du craft réaliste.
Calcul repris de `CraftRules.java` avec les **réglages par défaut** : si `realisme.json` change sur le serveur,
mettre à jour les constantes de `client/RealismeRules.java`.

## À ajuster dans le code
- `client/RecipeIndex.java` : rangement des objets par catégorie (identifiants des mods, mots-clés).
- Affiches : identifiant `citizenaffiches` ; la recette de copie est rappelée dans la fiche de chaque affiche.
- `client/AnimalTable.java` : poids par espèce et règle de découpe. **À aligner sur les vraies valeurs de citizenchasse.**
