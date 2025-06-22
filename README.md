# 🚀 Novastream - Build & Package Instructions (with `jpackage`)

This document describes how to:

- ✅ Build your Spring Boot + JavaFX app JAR
- ✅ Create a custom JRE with JavaFX modules
- ✅ Package it as a native Windows app using `jpackage`

---

## 📦 **1️⃣ Build the shaded JAR**

Run inside the `<project-root>` directory:

```bash
mvn clean package -DskipTests
```

This generates:

```
<project-root>/target/novastream-backend.jar
```

Then:

```bash
mkdir launcher
copy target/novastream-backend.jar launcher/
copy src/main/resources/static/icon.ico launcher/icon.ico
```

👉 Place your JavaFX jmods in `<project-root>` like:

```bash
<project-root>/javafx-jmods-21/
```

This prepares a clean packaging folder.

---

## 🛠 **2️⃣ Create the custom JRE**

Ensure you have:

- JDK 23
- JavaFX 21 jmods (Unzipped & placed in `<project-root>/launcher` already in the last step)

```bash
jlink --module-path "$env:JAVA_HOME/jmods;javafx-jmods-21" --add-modules java.base,java.logging,java.management,java.desktop,java.naming,java.security.jgss,java.instrument,java.sql,javafx.controls,javafx.graphics,javafx.fxml --output runtime --strip-debug --compress 2 --no-header-files --no-man-pages
```

This creates:

```
<project-root>/runtime
```

---

## 🖥 **3️⃣ Package as native app (jpackage)**

Run:

```bash
jpackage --name Novastream --input launcher --main-jar novastream-backend.jar --main-class com.novastream.GUILauncher --icon launcher\icon.ico --runtime-image runtime --type app-image --dest .
```

✅ jpackage will produce:

```
<project-root>/Novastream/Novastream.exe
<project-root>/Novastream/app/
<project-root>/Novastream/runtime/
```

---

## 📁 **Final structure**

Your distribution folder should look like:

```
<project-root>/
 └── Novastream/
      ├── Novastream.exe
      ├── app/
      └── runtime/

```

---

## 💡 Notes

⚠ _You don’t need to install Java on the target machine — the custom JRE is bundled._  
⚠ _Distribute the `Novastream/` folder — zip it and ship it._  
⚠ _Task Manager will show `Novastream.exe` as process name._
