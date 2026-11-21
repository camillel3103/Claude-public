# Clairière — jeux de Nim à deux

Application Android (Kotlin + Jetpack Compose) réunissant six jeux de Nim pour **deux joueurs sur le même téléphone**.
Pas d'adversaire ordinateur : on se passe le téléphone à chaque coup.

Ambiance nature et apaisante : bâtons de bois, brindilles, glands, feuilles, galets et champignons dessinés en vectoriel,
forêt en plusieurs plans, feuilles qui tombent, lucioles la nuit, sons de forêt en fond.

## Les jeux

| Jeu | Objets | Règle |
|---|---|---|
| Nim classique | bâtons | Retirer autant de bâtons qu'on veut dans un seul tas |
| Marienbad | brindilles | Rangées 1-3-5-7, même règle, misère par défaut |
| Soustraction | glands | Un seul tas, retirer de 1 à 3 (ou 4) glands |
| Wythoff | feuilles | Deux tas : retirer dans un seul tas, ou autant dans les deux |
| Kayles | galets | Retirer un galet ou deux galets voisins |
| Grundy | champignons | Partager un tas en deux tas inégaux |

Chaque jeu se joue en mode **normal** (le dernier coup gagne) ou **misère** (le dernier coup perd).
On peut annuler un coup, choisir qui commence, et les scores de la session sont comptés.

## Installer l'APK

Chaque push lance le workflow GitHub Actions **Android** : il exécute les tests puis compile l'application.
L'APK se télécharge dans l'onglet *Actions* → dernière exécution → artefact **Clairiere-apk**
(`app-release.apk` est la version optimisée, la plus fluide).

L'APK est signé avec une clé de debug : pour installer une nouvelle version par-dessus une ancienne,
il peut être nécessaire de désinstaller l'ancienne d'abord.

## Compiler soi-même

Android Studio (ou JDK 17 + SDK Android 35) :

```
./gradlew assembleRelease        # app/build/outputs/apk/release/app-release.apk
./gradlew testDebugUnitTest      # tests des règles du jeu
```

## Organisation du code

- `game/` — règles des six variantes et état de partie (Kotlin pur, testé).
- `ui/` — écrans Compose, dessin des objets naturels (`Items.kt`), décor animé (`Background.kt`), plateau animé (`Board.kt`).
- `audio/` + `AndroidSound.kt` — effets (SoundPool) et ambiance en boucle (MediaPlayer) avec fondus.
- `tools/make_sounds.py` — génère tous les sons par synthèse (`python3 tools/make_sounds.py app/src/main/res/raw`).
  Les fichiers `.ogg` de `res/raw` peuvent être remplacés par de vrais enregistrements en gardant les mêmes noms.
