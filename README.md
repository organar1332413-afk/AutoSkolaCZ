# Autoškola CZ — Stage 2

Текущее продолжение проекта описано в [docs/STAGE2_SCOPE.md](docs/STAGE2_SCOPE.md). Инструкции по сборке: [docs/BUILD.md](docs/BUILD.md). Исходный отчёт Stage 1 и история Git сохранены.

# Autoškola CZ — этап 1

Реальный Android-проект: Kotlin, Jetpack Compose, MVVM, Room, DataStore, Material 3. Восстановлен из исходного кода в истории Work-чата без изменения согласованной архитектуры.

Это каркас первого этапа, не законченный учебный продукт. Три официальных вопроса используются как ознакомительная выборка; полноценный экзамен заблокирован. Переводы и словарные карточки — редакторские черновики.

- [Отчёт](docs/STAGE1_REPORT_RU.md)
- [Архитектура](docs/ARCHITECTURE.md)
- [SQL-схема](docs/schema.sql)
- [Импорт](docs/IMPORT.md)
- [Источники](docs/SOURCES.md)
- [Сборка](docs/BUILD.md)
- [Проверки](docs/VALIDATION.md)
- [Восстановление и исправления](docs/RECOVERY.md)

```sh
./gradlew :core:domain:test :app:assembleDebug :app:lintDebug
```

JDK 17, SDK 35, Build Tools 35.0.0. APK: `app/build/outputs/apk/debug/app-debug.apk`. На Windows используйте `gradlew.bat`.

Нет регистрации, внешней аналитики и разрешения INTERNET. Локальные данные не отправляются на сервер; Android backup отключён.
