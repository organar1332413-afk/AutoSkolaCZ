# Autoškola CZ — финальный отчёт Stage 2

Дата финальной CI-проверки: 20.09.2026.

## Проверенный исходный код

- Ветка: `master`
- Проверенный commit: `52d552efb9472fc0852043f4d661c2977ca7d4d0`
- GitHub Actions workflow: `Android stage 2`
- Успешный run: №24, run id `35523975266`
- Stage 3 не начинался.

## Итог

Stage 2 технически собран и прошёл текущую автоматическую проверку. Debug APK создан и проверен. Release-вариант также собран для контроля границы production, но он unsigned и не предназначен для публикации в Google Play.

## Проверки

### Gradle / Android
- `assembleDebug`: успешно.
- `assembleRelease`: успешно.
- Основная сборка: `BUILD SUCCESSFUL`.
- Android SDK 35 / Build Tools 35.0.0.
- Java 17 / Gradle 8.11.1.

### JVM / Android unit tests
Свежий итог из текущих build-каталогов:
- domain: 37 тестов, 0 failures, 0 errors, 0 skipped;
- data: 10 тестов, 0 failures, 0 errors, 0 skipped;
- app: 5 тестов, 0 failures, 0 errors, 0 skipped;
- всего: 52 теста, все успешно.

### Room instrumentation
Отдельный job `room-tests` успешно прошёл на Android-эмуляторе API 29:
- `cz.autoskola.data.ImportTest`: 1 тест;
- 0 failures, 0 errors, 0 skipped.

### Структура данных
- Room schema v1: 29 таблиц;
- identity hash не изменён;
- foreign keys проверены;
- 166 согласованных resource keys для CS/RU/UK;
- sample: 3 официальных вопроса;
- `completeForB=false`.

### Android Lint
- debug: 0 errors, 40 warnings;
- release: 0 errors, 29 warnings.
Предупреждения сохранены в отчётах; критических Lint-ошибок нет.

### APK
Debug APK:
- файл: `app-debug.apk`;
- package: `cz.autoskola.study.debug`;
- versionCode: 2;
- versionName: `0.2.0-stage2`;
- minSdk: 26;
- targetSdk: 35;
- размер: 11 404 191 байт;
- SHA-256: `81bf8c731ff3183fbd0da3a5d63a0a7636d9eabe4e3e28ace51a3fe253d33af6`;
- ZIP/APK структура проверена;
- 16 KiB alignment: проверен;
- подпись: Android Debug, APK Signature Scheme v2;
- sample assets присутствуют только в debug, как предусмотрено архитектурой.

Release APK:
- файл: `app-release-unsigned.apk`;
- package: `cz.autoskola.study`;
- размер: 8 329 660 байт;
- SHA-256: `13ed2c9d5a937acf59a8d4660dde58bcf5fd55c40d22cacbb15e5d9a0a2d4ea7`;
- `debuggable=false`;
- developer markers отсутствуют;
- sample assets отсутствуют;
- 16 KiB alignment проверен;
- production signing пока отсутствует намеренно.

## Ограничения, которые остаются после Stage 2

1. Полного официального банка eTesty пока нет: только 3 проверочных sample-вопроса, `completeForB=false`.
2. Настоящий экзамен на sample заблокирован.
3. Release ещё не предназначен для Google Play: нет полного контента и production signing.
4. Физический телефон, реальный пользовательский UI-проход и реальное звучание установленного чешского TTS-голоса не проверяются CI. Это отдельная проверка после установки debug APK.
5. Полное наполнение eTesty, RU/UK-переводы, официальный контент, знаки и первая помощь относятся к следующим этапам.

## Поставка

Проверенный код для Stage 2 фиксируется тегом `stage2-apk` на commit `52d552efb9472fc0852043f4d661c2977ca7d4d0`.

Файлы поставки:
1. debug APK;
2. source ZIP проверенного commit;
3. CI reports ZIP;
4. Room device test reports ZIP.

Stage 3 до этой фиксации не начинался.
