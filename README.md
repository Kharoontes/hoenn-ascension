# Hoenn Ascension

Panel de comunicaciones client-side para Minecraft 1.21.1 (Fabric), pensado para servidores Cobblemon.
Código y arte originales; solo se reutiliza la **paleta de colores** (azul Kyogre → violeta → rojo Groudon).

## Compilar
Requiere JDK 21.

    gradle wrapper --gradle-version 8.10.2     # una sola vez
    ./gradlew build                            # el .jar queda en build/libs/

(O abre la carpeta en IntelliJ IDEA con el plugin de Minecraft Development / importa como proyecto Gradle.)

## Uso
- `J`  abre el panel (configurable en Controles → Hoenn Ascension)
- `Y` / `N` aceptan / rechazan el popup accionable más reciente (ej. teletransporte)
- `Tab` cambia de pestaña, `AvPág/RePág` o rueda para scroll, botón de rombo = anclar al HUD
- Botón central de la cabecera: cambia entre temas **Kyogre / Groudon / Primal**
- `/hoennascension reload` recarga `config/hoennascension.json`; `/hoennascension clear` borra el historial

## Versatilidad: canales por regex
Cada canal de `config/hoennascension.json` define:

| campo | qué hace |
|---|---|
| `pattern` | regex (sin mayúsculas). Vacío = canal comodín para lo que no coincida con nada |
| `color` | color del canal (#RRGGBB) |
| `popup`, `sound` | muestra popup y sonido (`none`, `orb`, `levelup`) |
| `hideFromVanilla` | oculta el mensaje del chat vanilla |
| `muted` | no cuenta en "Todo", sin popups ni sonido |
| `sendPrefix` | prefijo al escribir desde esa pestaña (ej. `/clan `) |
| `acceptCommand`/`denyCommand` | convierte el popup en accionable (Y / N) |

Los patrones por defecto son una base genérica: cada servidor formatea distinto el chat,
así que ajústalos a los mensajes reales de tu servidor. También hay `highlightWords`,
posición/tamaño/opacidad del HUD, tiempos de popup e historial por canal.