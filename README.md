# Jonathan HA Widget

Application Android (Kotlin, Jetpack Compose) qui ajoute à l'écran d'accueil des widgets et des tuiles de Réglages rapides pour piloter ses appareils via [Home Assistant](https://www.home-assistant.io/) (HA), en passant par son API REST. Aucun cloud tiers : l'app parle directement à ton instance HA.

Elle a été pensée pour un téléphone Samsung (One UI), mais n'utilise que des API Android standard.

## Fonctionnalités

| Widget | Taille par défaut | Rôle |
|---|---|---|
| **Lumière** | 1×1 | Allume / éteint une lumière (`light.toggle`). Icône jaune quand elle est allumée, grise sinon. |
| **Volet** | 3×1 | ▲ ouvrir, ■ stop, ▼ fermer un volet (`cover.open_cover`, `stop_cover`, `close_cover`). |
| **Télé + LED** | 4×1 | Deux prises : un gros bouton « Tout » (allume ou éteint la télé et les LED ensemble), et un bouton par prise pour les piloter séparément. Le gros bouton suit l'état de la télé. |
| **Thermomètre** | 2×2 | Température (et humidité en option) d'un capteur. Le fond devient bleu sous 18 °C et orangé au-dessus de 26 °C. Un appui actualise. |
| **PC** | 2×1 | Allume ou éteint un PC, **avec une boîte de confirmation** pour éviter les appuis accidentels. |
| **Tableau de bord** | 4×4 | Thermomètre, lumière, PC, volet et télé / LED dans un seul widget, en sections titrées. |

En plus des widgets, deux **tuiles de Réglages rapides** (volet de notifications) : **Lumière** et **Télé + LED**.

Autres points :

- Les widgets affichent le dernier état connu quand HA est injoignable.
- Un court message s'affiche quand une action échoue (HA injoignable, token refusé, entité introuvable…).
- L'écran de réglages propose l'**autocomplétion des `entity_id`** en interrogeant HA, filtrée par type d'entité.
- Quand on ajoute un widget, l'écran de réglages s'ouvre directement sur la page de l'appareil concerné.

## Prérequis

- Android 8.0 ou plus (`minSdk` 26).
- Une instance Home Assistant joignable depuis le téléphone (en pratique : le même réseau local).
- Un **jeton d'accès longue durée** HA : profil utilisateur → Sécurité → Jetons d'accès longue durée.
- Pour compiler : JDK 17 et le SDK Android (API 34).

## Installation

### Compiler

```bash
git clone https://github.com/JonathanOll/jonathan-ha-widget.git
cd jonathan-ha-widget
```

Crée un fichier `local.properties` à la racine (il n'est pas versionné) avec le chemin du SDK Android :

```properties
sdk.dir=C\:\\Users\\<toi>\\AppData\\Local\\Android\\Sdk
```

Puis :

```bash
./gradlew assembleDebug      # Windows : gradlew.bat assembleDebug
```

L'APK est généré dans `app/build/outputs/apk/debug/app-debug.apk`.

### Installer sur le téléphone

Avec le débogage USB ou sans fil activé :

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Tu peux aussi copier l'APK sur le téléphone et l'ouvrir (autoriser l'installation d'apps inconnues).

## Configuration

1. Ouvre l'app. La carte **Home Assistant** demande l'URL (par exemple `http://192.168.1.10:8123`) et le jeton. Un voyant indique si la connexion fonctionne et combien d'entités sont trouvées.
2. Ouvre la carte de chaque appareil et choisis ses entités (l'autocomplétion propose celles de HA) :

| Appareil | Entités attendues |
|---|---|
| Lumière | une `light.*` |
| Volet | une `cover.*` |
| Télé + LED | deux prises distinctes (`switch.*` ou `light.*`) : la télé et les LED |
| Thermomètre | un `sensor.*` de température, et en option un `sensor.*` d'humidité |
| PC | une entité pour **allumer**, une pour **éteindre** (`button`, `switch`, `script`, `scene`, `input_boolean`…), et en option un capteur d'**état** |

   Pour le PC, le capteur d'état sert à savoir si la machine est allumée : un état `unavailable` ou `unknown` (ou une entité absente) veut dire « éteint ». Un capteur de charge CPU qui n'existe que quand le PC tourne convient bien. Sans capteur d'état, la boîte de confirmation propose les deux actions.
3. Ajoute les widgets : appui long sur l'écran d'accueil → Widgets → *Jonathan HA Widget*. Les tuiles s'ajoutent depuis le volet de notifications (crayon d'édition).

### Conseil Samsung / économie d'énergie

Si les widgets mettent du temps à réagir quand l'écran est éteint, désactive l'optimisation de la batterie pour l'app (Réglages → Applications → Jonathan HA Widget → Batterie → Sans restriction) et retire-la des « applis en veille ».

## Structure du code

```
app/src/main/java/com/lashox/huewidget/
├── Ha.kt               Client HTTP de l'API REST HA + configuration (SharedPreferences)
├── ConfigActivity.kt   Écran de réglages Compose (accueil + une page par appareil, autocomplétion)
├── ConfirmActivity.kt  Boîte de confirmation pour allumer / éteindre le PC
├── LightWidget.kt      Widget Lumière
├── CoverWidget.kt      Widget Volet
├── TvWidget.kt         Widget Télé + LED
├── ThermoWidget.kt     Widget Thermomètre
├── PcWidget.kt         Widget PC
├── DashWidget.kt       Widget Tableau de bord
├── Tiles.kt            Tuiles de Réglages rapides (Lumière, Télé + LED)
└── MainActivity.kt     Point d'entrée (lanceur) : réutilise l'écran de réglages
```

Les widgets utilisent des `RemoteViews` classiques (layouts dans `res/layout`, descripteurs dans `res/xml`). Chaque action réseau part dans un thread, avec `goAsync()` pour garder le récepteur en vie, puis le widget est redessiné avec l'état relu depuis HA.

## Sécurité : à lire avant d'utiliser sur un réseau non maîtrisé

Ce projet est conçu pour un usage domestique sur un réseau de confiance. Limites connues :

- Le jeton HA est stocké **en clair** dans les préférences de l'app, et la sauvegarde Android n'est pas désactivée.
- Le trafic en HTTP clair est autorisé (`usesCleartextTraffic`), pour pouvoir viser une adresse locale en `http://`. Sur un réseau partagé, le jeton peut être lu en transit. Préfère HTTPS si ton HA l'expose.
- Les actions des widgets sont reçues par des composants exportés : une autre app installée sur le téléphone peut en théorie les déclencher.
- Les tuiles de Réglages rapides peuvent être accessibles depuis l'écran verrouillé, selon la configuration du téléphone : quelqu'un qui tient ton téléphone pourrait allumer ou éteindre tes appareils. Le PC n'a volontairement pas de tuile.

N'utilise jamais ton jeton ailleurs que dans l'app, et révoque-le dans HA si tu perds le téléphone.

## Dépannage

- **« Home Assistant injoignable (Wi-Fi ?) »** : le téléphone n'est pas sur le même réseau que HA, ou l'URL est fausse. Hors de chez soi, l'adresse locale ne répond pas.
- **« Token Home Assistant refusé »** : le jeton est invalide ou a été révoqué.
- **« Entité introuvable »** : l'`entity_id` n'existe pas (ou plus) dans HA.
- **Le widget n'a pas changé après une mise à jour de l'app** : retire-le et remets-le.
- **L'icône de l'app est restée l'ancienne** : le lanceur la garde en cache, redémarre-le.
- Les logs de l'app sont filtrables avec `adb logcat -s HueWidget`.

## Pistes d'amélioration

- Budget de temps global et lectures en parallèle pour rester sous la limite d'Android pour les widgets.
- Une file unique pour les actions et un anti-rebond contre les doubles appuis.
- Un récepteur non exporté pour les clics, chiffrement du jeton, HTTP en clair limité à l'adresse de HA.
- Mise à jour de tous les widgets après chaque action, et état du PC plus fiable (distinguer « éteint » d'une erreur d'authentification).
- Textes dans `strings.xml`, tests unitaires, configuration de build release.
- Une URL externe de secours (Nabu Casa, DuckDNS) pour les widgets hors du Wi-Fi.
