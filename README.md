# Jonathan HA Widget

Widgets Android (lumière Philips Hue + volet) pilotant Home Assistant via son API REST.

- Widget lumière : appui = `light.toggle`, icône jaune (allumée) / grise (éteinte).
- Widget volet : ▲ `open_cover`, ■ `stop_cover`, ▼ `close_cover`.
- Config (URL HA, token longue durée, entity_id) dans l'app.

Build : `./gradlew assembleDebug`
