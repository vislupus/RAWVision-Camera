# RAWVision Camera

Android приложение на **Kotlin + Jetpack Compose** за снимане в RAW (DNG) с пълен ръчен
контрол и недеструктивно редактиране. Всичко, което приложението създава, живее в
неговата собствена папка (app sandbox), а цялата информация се пази в SQLite база данни.

## Какво има вътре

| Област | Реализация |
| --- | --- |
| Заснемане | Camera2 API директно (без CameraX), `RAW_SENSOR` → DNG чрез `DngCreator`, JPEG компаньон, миниатюра, XMP sidecar |
| Ръчни настройки | ISO, скорост на затвора, фокус (AF/MF, tap-to-focus), баланс на бялото (пресети + Kelvin/оттенък), EV корекция, AE-lock, светкавица/фенер, зуум, таймер |
| Авто режим | 3A на камерата (AE/AF/AWB) + AF заключване и AE pre-capture преди всяка снимка; ISO и затворът могат да са авто *поотделно* (приложението измерва сцената само през малък YUV поток) |
| Метаданни | GPS (LocationManager), дата/час, устройство, обектив, всички параметри на снимката, автор и авторски права – в базата, в DNG/JPEG EXIF и в XMP |
| Редактор | Недеструктивна „рецепта“ (JSON в SQLite + XMP): експозиция, контраст, светли/сенки, бели/черни, температура/оттенък, наситеност/живост, острота, яснота, dehaze, винетка, завъртане, обръщане, изправяне, изрязване. Undo/redo, история на версиите, връщане към оригинала, сравнение с натискане |
| Галерия | Мрежа с филтри (всички / любими / редактирани / RAW), детайли, оценка, бележки, споделяне на JPEG експорт или на DNG файла |
| Настройки | Тема (системна/светла/тъмна + чисто черно), основен цвят (10 акцента или Material You), размер на шрифта, автор/copyright, формат (RAW+JPEG/RAW/JPEG), качество, GPS, мрежа, нивелир, хистограма, звук, бутони за звука като затвор |
| UI | Material 3, шрифт **Roboto** (вграден, OFL), икони **Lucide** (ISC), унифицирани компоненти |

### Структура на папката на приложението (`Context.filesDir`)

```
files/
  db/rawvision.db      SQLite: photos, edits, edit_history, exports, settings, camera_presets
  photos/RV_*.dng      RAW снимки
  photos/RV_*.jpg      JPEG компаньон от камерата
  photos/RV_*.xmp      sidecar: автор, GPS, EXIF, рецепта за редакция
  thumbs/RV_*.jpg      миниатюри за галерията
  exports/RV_*_edit_*.jpg  рендерирани редакции, готови за споделяне
```

Нищо не се записва в общата галерия на телефона. Споделянето става през системния
share sheet (FileProvider), т.е. само когато ти решиш.

## Структура на кода

```
app/src/main/java/com/rawvision/camera/
  RawVisionApp.kt, MainActivity.kt
  data/        AppContainer (ръчен DI), модели, SQLite (AppDatabase + DAO), репозитории, AppStorage
  camera/      CameraController (Camera2), CameraInfo, CameraParams, ExposureMath, FrameAnalyzer,
               CaptureSaver (DNG/JPEG/thumb/XMP), Sensors (GPS, нивелир, ориентация),
               CameraViewModel, CameraScreen/Controls/Overlays/Preview
  editor/      ImagePipeline (CPU пайплайн), Exporter, EditorViewModel, EditorScreen
  gallery/     GalleryViewModel, GalleryScreen, PhotoDetailScreen
  settings/    SettingsScreen
  ui/          theme (Roboto, цветови схеми от seed, Lucide икони), components, nav
  util/        ImageUtils, Formatters, ThumbnailCache
```

## Как се билдва и стартира

Изисквания: **Android Studio** (Narwhal 2025.1.1 или по-нов), JDK 17+ (Studio си носи),
телефон с Android 9+ (API 28). RAW изисква камерата да декларира `RAW` capability
(повечето Pixel/Samsung/Xiaomi флагмани и среден клас го правят; иначе приложението
работи само с JPEG и го показва).

### Вариант 1 – само Android Studio (препоръчително)

1. `File → Open…` и избери папката на проекта. Gradle sync сваля всички зависимости.
2. Включи USB debugging на телефона, свържи го, избери го от списъка с устройства и натисни **Run ▶**.
   Това компилира, инсталира и стартира приложението с едно действие.
3. **Hot reload:** Compose има *Live Edit*. Включва се от
   `Settings → Editor → Live Edit → Enable Live Edit` (режим *Automatic*). След това всяка промяна в
   `@Composable` функция се прилага на телефона за секунди без рестарт. За промени извън
   Compose (ViewModel, Camera2 и т.н.) използвай **Apply Changes ⚡** (`Ctrl+F10`), което
   пренарежда кода без преинсталация; ако и това не стига, Studio сам предлага пълен Run.
4. За бърз преглед на UI без телефон – `@Preview` функции се рендерират в панела *Design*.

### Вариант 2 – от терминала

```bash
./gradlew installDebug          # компилира и инсталира на свързания телефон
adb shell am start -n com.rawvision.camera.debug/com.rawvision.camera.MainActivity
adb logcat -s CameraController  # логове на камерата
```

`gradlew` (wrapper) сам сваля Gradle 8.13. Нужен е само `ANDROID_HOME`/`local.properties`,
който Android Studio създава автоматично при първото отваряне.

### Версии (gradle/libs.versions.toml)

AGP 8.11.1 · Kotlin 2.2.21 · Compose BOM 2025.06.01 · Material 3 · Navigation 2.9 ·
Lifecycle 2.9 · Coroutines 1.10.2 · ExifInterface 1.4.1. Няма KSP/kapt и няма външни
библиотеки за база данни или изображения – всичко е в самия проект.

## Бележки за имплементацията

* **Полуавтоматична експозиция.** Camera2 позволява само „всичко авто“ или „всичко ръчно“.
  Когато например ISO е ръчно, а затворът авто, приложението изключва AE и само изчислява
  затвора от последната измерена яркост на сцената (`exposure × ISO` = константа, коригирана
  с обратна връзка от средната яркост на малък YUV поток). Ако устройството не позволи
  четвърти поток, приложението автоматично минава на три и хистограмата се скрива.
* **Ръчен баланс на бялото** използва `COLOR_CORRECTION_MODE_TRANSFORM_MATRIX` с гейнове,
  изчислени от температурата в Kelvin. RAW данните не се променят; DNG пази `AsShotNeutral`.
* **Редакторът** декодира DNG чрез вградения в Android декодер (BitmapFactory, demosaic +
  цветен профил на производителя), работи върху копие ≤1600 px за преглед и до 6000 px при
  експорт. Оригиналните файлове никога не се променят.
* **Историята** пази до 50 версии на рецептата за снимка (`edit_history`), плюс undo/redo
  в рамките на сесията.

## Лицензи на трети страни

* Lucide icons – ISC (`licenses/LUCIDE_ISC.txt`)
* Roboto – SIL Open Font License 1.1 (`licenses/ROBOTO_OFL.txt`)
