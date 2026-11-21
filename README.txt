Mek-hjälpen - Android Studio-projekt

1. Installera Android Studio (https://developer.android.com/studio) och öppna den här mappen (MekHjalpen).
2. Låt Gradle synka klart (första gången laddar den ner det som behövs).
3. Meny: Build > Build Bundle(s) / APK(s) > Build APK(s).
4. APK-filen hamnar i app/build/outputs/apk/debug/app-debug.apk
5. För över den till telefonen och installera (tillåt installation från okända källor).

Appens webbsida ligger i app/src/main/assets/index.html.

Bygga APK utan Android Studio: ladda upp INNEHÅLLET i den här mappen till ett GitHub-repo.
Workflow finns i .github/workflows/build.yml (kopia i build.yml.txt om mappen .github inte följer med).
Fliken Actions > Build APK > Run workflow. APK:n finns sedan under Artifacts.
