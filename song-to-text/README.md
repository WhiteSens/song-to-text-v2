# Song to text (Fabric 1.21.11, client-only)
Збірка: `gradle build` (Java 21) → `build/libs/song-to-text-1.0.0.jar` у `mods/`. Потрібен Fabric API.
Клавіші: U — меню, Y — вмикає/вимикає текст. Змінюються в Керуванні → Song to text.
Режим показу тексту: `display` у `config/songtotext.json` (CHUNKS | FULL_LINES).
Фони панелі: assets/songtotext/textures/gui/panel_day.png, panel_night.png (чисті фони з референсу, tools/clean_refs.py). Інтерфейс меню малює Java2D (MenuRenderer), Minecraft лише показує картинку.
