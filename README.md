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

### Version release

```bash
./gradlew assembleRelease
```

La release est réduite et optimisée par R8 (`isMinifyEnabled`, `isShrinkResources`, règles dans `app/proguard-rules.pro`). Pour la signer, copie `keystore.properties.example` en `keystore.properties` à la racine et remplis `storeFile`, `storePassword`, `keyAlias`, `keyPassword`. Ce fichier et les keystores (`*.jks`, `*.keystore`) sont ignorés par git. Sans `keystore.properties`, le build fonctionne quand même et produit un APK **non signé** (`app/build/outputs/apk/release/app-release-unsigned.apk`).

### Tests

```bash
./gradlew testDebugUnitTest
```

Tests JUnit sur la JVM (`app/src/test/`) pour la logique pure de `Logic.kt` : validation de l'URL et détection des adresses locales, cible des actions Télé + LED, seuils de température, lecture des états HA, anti-rebond.

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
├── Ha.kt               Client HTTP de l'API REST HA (délai global, pas de redirection) + configuration
├── HaRepository.kt     Lectures d'état en parallèle, cache du dernier état connu, logique des actions
├── Actions.kt          File unique des actions (un seul thread), anti-rebond, rafraîchissements
├── ActionReceiver.kt   Récepteur non exporté de tous les clics (widgets, tuiles, confirmation PC)
├── Widgets.kt          Redessine tous les widgets et tuiles, construit les PendingIntent
├── Logic.kt            Logique pure testable (URL locale, cibles Télé + LED, seuils, états)
├── Constants.kt        Clés de préférences, couleurs, seuils, budgets de temps, actions
├── HaLog.kt            Logs (détaillés en debug, sans URL ni entity_id en release)
├── TokenStore.kt       Chiffrement du jeton avec une clé de l'Android Keystore
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

Les widgets utilisent des `RemoteViews` classiques (layouts dans `res/layout`, descripteurs dans `res/xml`). Tous les textes visibles sont dans `res/values/strings.xml`. Chaque clic passe par `ActionReceiver`, qui garde le récepteur en vie avec `goAsync()` et confie le travail à la file de `Actions` avec un budget d'environ 8 s. À la fin de chaque action, tous les widgets et tuiles sont redessinés depuis le cache.

## Sécurité : à lire avant d'utiliser sur un réseau non maîtrisé

Ce projet est conçu pour un usage domestique sur un réseau de confiance. Ce qui est en place :

- Le jeton HA est **chiffré** (AES-GCM) avec une clé qui reste dans l'Android Keystore. Un ancien jeton stocké en clair est migré automatiquement.
- La sauvegarde Android (cloud et transfert d'appareil) est désactivée.
- Le HTTP en clair n'est accepté que vers une **adresse locale** (IP privée, `localhost`, `*.local`, `*.lan`, `*.home.arpa`…). Une URL `http://` vers une adresse publique est refusée : utilise `https://`.
- Les redirections HTTP ne sont pas suivies, pour ne jamais envoyer le jeton vers un autre hôte.
- Les clics des widgets, des tuiles et de la confirmation PC passent par un récepteur **non exporté** : les autres apps ne peuvent pas les déclencher.
- En release, les logs ne contiennent ni URL, ni `entity_id`, ni jeton.

Limites connues :

- En `http://` sur le réseau local, le jeton circule en clair : sur un réseau partagé, il peut être lu en transit. Préfère HTTPS si ton HA l'expose.
- Les tuiles de Réglages rapides peuvent être accessibles depuis l'écran verrouillé, selon la configuration du téléphone : quelqu'un qui tient ton téléphone pourrait allumer ou éteindre tes appareils. Le PC n'a volontairement pas de tuile.

N'utilise jamais ton jeton ailleurs que dans l'app, et révoque-le dans HA si tu perds le téléphone.

## Dépannage

- **« Home Assistant injoignable (Wi-Fi ?) »** : le téléphone n'est pas sur le même réseau que HA, ou l'URL est fausse. Hors de chez soi, l'adresse locale ne répond pas.
- **« Token Home Assistant refusé »** : le jeton est invalide ou a été révoqué.
- **« Entité introuvable »** : l'`entity_id` n'existe pas (ou plus) dans HA.
- **Le widget n'a pas changé après une mise à jour de l'app** : retire-le et remets-le.
- **L'icône de l'app est restée l'ancienne** : le lanceur la garde en cache, redémarre-le.
- **« Connexion non chiffrée refusée »** : l'URL est en `http://` vers une adresse publique. Utilise `https://`, ou l'adresse locale de HA.
- **Plus de connexion après une restauration ou un changement de téléphone** : le jeton chiffré ne peut pas être relu sur un autre appareil. Ressaisis-le.
- Les logs de l'app sont filtrables avec `adb logcat -s HueWidget` (détaillés seulement en debug).

## Pistes d'amélioration

- Une URL externe de secours (Nabu Casa, DuckDNS) pour les widgets hors du Wi-Fi.
- Un panneau Edge Samsung (Edge panel) en plus des widgets.
- Un rafraîchissement plus fréquent que toutes les 30 minutes (limite de `updatePeriodMillis`), par exemple via WorkManager ou l'API WebSocket de HA.
