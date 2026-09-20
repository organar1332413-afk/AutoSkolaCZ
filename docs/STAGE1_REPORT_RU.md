# Autoškola CZ — этап 1

## Архитектура и файлы

Сохранены четыре модуля `app`, `core/domain`, `core/data`, `core/designsystem`. Kotlin, Compose, MVVM, Room, DataStore, Material 3. Android API26+, target35. Дерево: `PROJECT_TREE.txt`. Подробности: `ARCHITECTURE.md`.

## База и импорт

29 таблиц: официальные вопросы и их неизменяемые редакции, ответы, категории и группы прав, медиа, раздельные переводы, знаки, уроки, словарь, сохранённые слова, попытки, экзамены и прогресс. UserSettings — DataStore. Реальная KSP-схема в `core/data/schemas`, проект SQL — `schema.sql`.

Импортёр проверяет внутренний транспортный пакет и атомарно переключает базу. Повторный пакет идемпотентен; изменённый пакет с прежней версией отвергается. Старые результаты сохраняют связь с редакциями. Сетевые обновления спроектированы, но не реализованы. Это не автоматический загрузчик банка с сайта MD.

## Экраны и функции

| Экран | Что есть |
|---|---|
| Главная | Входы в основные разделы |
| Учиться | 19 тем и каркасы уроков |
| Вопросы | Три официальные записи из Room |
| Карточка | CS, выбранный перевод, ответы, учебная проверка, словарь по нажатию |
| Экзамен | Условия и объяснение блокировки до полной базы и реализации |
| Чешский / Мои слова | Четыре карточки, сохранение в Room |
| Профиль | Меню RU/UK, материал CS+RU / CS+UK / CS only, уровни подсказок |
| Знаки / Первая помощь | Структура разделов без непроверенного содержания |
| Ошибки / Статистика | Пустые состояния без выдуманных процентов |

Пять пунктов нижнего меню. Настройки сохраняются локально. Чешский текст визуально главный. Средний уровень открывает перевод кнопкой. Экзаменационный уровень скрывает подсказки. Одновременного показа RU и UK нет. Тема спокойная светлая, основа для тёмной предусмотрена.

## Тестовые данные и границы

RP0604222, RP0606185, RP0605535 — реальные тексты и ключи eTesty, восстановленные из предыдущей проверки. Баллы, применимость к B и дата выпуска остаются неподтверждёнными; экзамен не активирован. Переводы и объяснения — черновики. Словарь: ohrozit, omezit, vozidlo, řidič. Другие слова пока без карточек.

TTS, разбор фразы, полный банк, полноценные уроки и знаки, таймер экзамена, медиа-рендереры, запись статистики и интервальные повторения относятся к последующим этапам. Этап 2 не начат.

## Какие данные ещё нужны

Официальный полный экспорт/фиксированный выпуск, медиа, баллы и группы B, версия публикации, проверенная экзаменационная матрица, изображения знаков, актуальная первая помощь, редакторская проверка переводов и условия распространения.

## Принятые решения

Стабильный ID + редакции сохраняют историю; перевод — отдельная locale-запись; меню независимо от перевода; sample не притворяется полным экзаменом; настройки не дублируются в SQL; отсутствие аккаунта и INTERNET-разрешения сохраняет офлайн-работу. Архитектура не менялась при восстановлении.

## Сборка

`./gradlew :core:domain:test :app:assembleDebug :app:lintDebug`.
Результаты новой проверки: `VALIDATION.md`. Инструкция установки и сборки: `BUILD.md`. Точный сохранённый commit/checkpoint указан в поставке.


# Результаты проверки ЭТАПА 1

Дата UTC: 2026-09-19T06:25:33.741735+00:00

Команда: `gradle :core:domain:test :app:assembleDebug :app:lintDebug --no-daemon --stacktrace --max-workers=2 --console=plain` (Gradle 8.11.1 / JDK 17).

- Android-сборка: **BUILD SUCCESSFUL**, APK получен.
- Прежние JUnit-тесты: **10/10**, 0 failures, 0 errors, 0 skipped. LanguagePolicyTest: 4; PackageValidatorTest: 6.
- Android Lint: **0 ошибок, 5 предупреждений**. Проверки не отключались; baseline не добавлялся.
- Room KSP: **29 таблиц**, версия 1; identityHash `81cbd33873aab63bc39ee4c31a037fe3`. SQL и индексы из экспорта выполняются в SQLite.
- APK: подпись v2 подтверждена; ZIP цел; sample и словарь совпадают с исходниками; INTERNET-разрешения нет.
- Структура: 29 таблиц, внешние ключи, XML/JSON/TOML, 93 ключа RU/UK, 3 sourced questions — PASS.

## Точный список предупреждений Lint

- `AppBundleLocaleChanges` — /workspace/scratch/4f17c26b67c4/AutoSkolaCZ/app/src/main/kotlin/cz/autoskola/app/MainActivity.kt:26: Found dynamic locale changes, but did not find corresponding Play Core library calls for downloading languages and splitting by language is not disabled in the `bundle` configuration
- `DataExtractionRules` — /workspace/scratch/4f17c26b67c4/AutoSkolaCZ/app/src/main/AndroidManifest.xml:2: The attribute `android:allowBackup` is deprecated from Android 12 and higher and may be removed in future versions. Consider adding the attribute `android:dataExtractionRules` specifying an `@xml` resource which configures cloud backups and device transfers on Android 12 and higher.
- `UnusedResources` — /workspace/scratch/4f17c26b67c4/AutoSkolaCZ/app/src/main/res/values/strings.xml:2: The resource `R.string.app_name` appears to be unused
- `UnusedResources` — /workspace/scratch/4f17c26b67c4/AutoSkolaCZ/app/src/main/res/values/strings.xml:43: The resource `R.string.not_started` appears to be unused
- `MissingApplicationIcon` — /workspace/scratch/4f17c26b67c4/AutoSkolaCZ/app/src/main/AndroidManifest.xml:2: Should explicitly set `android:icon`, there is no default

## Предупреждения компилятора и упаковки из финального журнала

- w: file:///workspace/scratch/4f17c26b67c4/AutoSkolaCZ/app/src/main/kotlin/cz/autoskola/app/navigation/AppNavigation.kt:25:118 'val Icons.Filled.List: ImageVector' is deprecated. Use the AutoMirrored version at Icons.AutoMirrored.Filled.List.
- Unable to strip the following libraries, packaging them as they are: libandroidx.graphics.path.so, libdatastore_shared_counter.so.

## Известные ограничения

- APK debug, версия 0.1.0-stage1, Android 8.0+ (minSdk 26, compile/target 35); для проверки ЭТАПА 1, не release для Google Play.
- На устройстве/эмуляторе APK в этой среде не запускался. Room instrumentation-тест сохранён, но не выполнялся. Lint-анализ тестов не является запуском instrumentation.
- В базе три реальных sample-вопроса eTesty и четыре словарных слова. Дата выпуска, баллы и применимость sample к категории B не подтверждены; в данных оставлены null/пустые значения. Полный экзамен заблокирован.
- RU/UK переводы и объяснения — черновики для проверки архитектуры; требуют редакторской проверки.
- Уроки, знаки, первая помощь, ошибки и статистика содержат каркасы/пустые состояния. Нет полного банка, экзамена с таймером, TTS, разбора фразы, медиа-рендереров и интервального повторения.
- Словарная карточка работает только для имеющихся четырёх слов и явно сохранённых форм; морфологический анализ и произвольный перевод ещё не реализованы.
- Импортёр принимает внутренний JSON-пакет; автоматического скачивания eTesty и сетевого обновления нет. Нужны официальный экспорт, медиа, проверка метаданных и условий распространения.
- Темная тема предусмотрена архитектурно; завершён только светлый интерфейс.
- Восстановление выполнено из сохранённого ZIP и истории исправлений. Побайтовое совпадение с первоначальной утраченной временной папкой не заявляется; подробности в docs/RECOVERY.md. Debug-подпись создавалась в восстановленной среде; приватный ключ не включён в исходники.

Полные отчёты: `docs/verification/`. Исходники и локальная Git-история находятся в ZIP; итоговая метка: `stage1-apk`.
