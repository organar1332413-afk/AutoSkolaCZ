# Сборка APK

1. Открыть корень AutoSkolaCZ в Android Studio.
2. Выбрать JDK 17 для Gradle. Установить SDK Platform 35 и Build Tools 35.0.0.
3. Задать ANDROID_HOME или локальный `local.properties` с `sdk.dir=/путь/к/sdk`.
4. Выполнить:

```sh
./gradlew :core:domain:test :core:data:testDebugUnitTest :app:testDebugUnitTest :app:assembleDebug :app:assembleRelease :app:lintDebug :app:lintRelease
```

Windows: `gradlew.bat :core:domain:test :core:data:testDebugUnitTest :app:testDebugUnitTest :app:assembleDebug :app:assembleRelease :app:lintDebug :app:lintRelease`.
APK: `app/build/outputs/apk/debug/app-debug.apk`.

Первый запуск сборки требует интернета для Gradle и библиотек. Установленное приложение работает с локальными данными.

На подключённом устройстве/эмуляторе:

```sh
./gradlew :core:data:connectedDebugAndroidTest
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

GitHub Actions в `.github/workflows/android.yml` собирает APK, тестирует domain, Room/DataStore, локализацию и lint; отдельное задание запускает Room instrumentation. Workflow не публиковался в удалённом репозитории.

APK сохраняется как CI artifact после тестов и проверки вариантов, до Lint, чтобы последующий сбой Lint не терял уже собранный файл. Само наличие этого artifact **не означает завершение Stage 2**: необходимо проверить весь workflow, включая Lint и отдельное задание instrumentation. Отчёты, XML тестов и журналы сборки выгружаются отдельными artifacts даже при ошибке последующих шагов.

Debug APK — для проверки. Публикация Google Play, signing release/AAB, актуальный targetSdk и полноценный контент — отдельные задачи.


## Stage 2

Debug package: `cz.autoskola.study.debug`, версия `0.2.0-stage2` (2). Устанавливается рядом со Stage 1. Настройки Stage 1 автоматически между разными package ID не переносятся. Для одного package ID старые DataStore-настройки сохраняются: RU/UK, режим материала и уровень не сбрасываются.

В `app/signing/development.keystore` включён обычный ключ **только для разработки**, чтобы следующие debug APK обновлялись без удаления прогресса. Нельзя использовать его для Google Play. Release APK собирается без подписи; production-ключ в проект не включён.

Для воспроизводимой проверки:

```sh
python tools/validate_structure.py
python tools/verify_apks.py
```

Вторая команда требует `ANDROID_HOME`, Build Tools 35.0.0 и обе собранные версии APK. Проверяет содержимое DEX, resources и assets, а не только исходный код.

Она также проверяет выравнивание обеих версий (`zipalign -P 16`) и подпись debug (`apksigner verify`). Release остаётся намеренно неподписанным. JSON-вывод содержит размеры, SHA-256 и результаты проверки границы debug/release.

После актуального запуска Gradle:

```sh
python tools/collect_stage2_verification.py
```

Сборщик читает только текущие `build/test-results` и `app/build/reports/lint-results-*.xml`: минимум 37 domain + 10 data + 5 app тестов, ни ошибок, ни пропусков; оба Lint без ошибок. Предупреждения Lint не скрываются и перечисляются в JSON. Архивные отчёты из `docs/verification-stage2` не используются для подтверждения новой сборки. CI запускает сборщик в свежем checkout; при локальном использовании сначала запускайте Gradle, а не копируйте старые отчёты в build.

Проверка самого сборщика, без Android SDK:

```sh
python -m unittest discover -s tools -p 'test_*.py' -v
```

Эти шесть Python-проверок относятся только к подготовке поставки. Они не заменяют 52 Android/domain-теста и не проверяют UI/TTS на устройстве.

Robolectric 4.14.1 запускает интеграционные тесты Room/DataStore и проверки Android-ресурсов на JVM с Android API 28. При первом запуске потребуется загрузить тестовый Android runtime. Это не заменяет проверку интерфейса и TTS на телефоне.

Не запускать `git clean -fdx` без полной резервной копии. Проект не требует удаления рабочей папки или Gradle cache для обычной сборки.

## Промежуточные резервные копии без Android SDK

После commit, при чистом рабочем дереве:

```sh
python tools/backup_checkpoint.py ../backups/AutoSkolaCZ-stage2-current-backup.zip
```

ZIP содержит все отслеживаемые файлы (включая Gradle wrapper, Room schema, документацию, обычный debug signing key) и полную локальную `.git` с ветками/tags. Android SDK, кеши, `local.properties` и внешние SSH-ключи не включаются. Инструмент не создаёт commits, не делает push и не выполняет очистку. При незакоммиченных/неотслеживаемых изменениях он отказывает, чтобы нельзя было молча потерять новую работу. Существующий ZIP не перезаписывается: для следующего checkpoint используйте другое имя или отдельную папку.

Перед успешным завершением проверяются Git objects, CRC архива и SHA-256 каждого вложенного файла. JSON в stdout содержит HEAD, ветку, tags, размер и SHA-256 ZIP. Восстановление выполняйте в отдельную папку через `unzip` или архиватор с сохранением Unix file modes, затем `git status` и `git fsck --full`. Python `ZipFile.extractall` сам по себе не восстанавливает executable bit: после такого извлечения требуется `chmod +x AutoSkolaCZ/gradlew`.

Четыре дополнительные regression-проверки резервирования вместе с шестью проверками сборщика дают **10 Python-тестов инструментов**. Это не прежние 10 Stage 1 JUnit-тестов и не 52 Stage 2 Android/domain-теста.
