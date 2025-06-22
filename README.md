# 🚀 Novastream - Build & Package Instructions

This document describes how to:

- ✅ Build your Spring Boot + JavaFX app JAR
- ✅ Create a custom JRE with JavaFX modules
- ✅ Package it as an EXE using Launch4j

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
mkdir Novastream
mkdir Novastream/launcher
mkdir Novastream/exe
copy target/novastream-backend.jar Novastream/launcher/
copy src/main/resources/static/icon.ico Novastream/launcher/icon.ico
cd Novastream
```

This prepares a clean packaging folder.

---

## 🛠 **2️⃣ Create the custom JRE**

Ensure you have:

- JDK 23
- JavaFX 21 jmods (Unzip & copy to `Novastream/launcher` preferably)

Run inside the `Novastream` directory:

```bash
jlink --module-path "$env:JAVA_HOME/jmods;<path-to-javafx-jmods>" --add-modules java.base,java.logging,java.management,java.desktop,java.naming,java.security.jgss,java.instrument,java.sql,javafx.controls,javafx.graphics,javafx.fxml --output exe/novastream-jre --strip-debug --compress 2 --no-header-files --no-man-pages
```

This creates:

```
<project-root>/Novastream/exe/novastream-jre
```

---

## 🖥 **3️⃣ Package as EXE (Launch4j)**

✅ Your Launch4j settings:

- **Output file:** `<project-root>/Novastream/exe/Novastream.exe`
- **Jar:** `<project-root>/Novastream/launcher/novastream-backend.jar`
- **Don't wrap the jar:** Uncheck this option
- **Icon:** `<project-root>/Novastream/launcher/icon.ico`
- **Options:** Stay alive after launching a GUI application
- **Header Type:** GUI
- **JRE path:** `novastream-jre` (Don't prepend anything, just this as the path as it's relative to the `EXE`)
- **Min JRE version:** 23
- **Save Configuration to** `<project-root>/Novastream/launcher/novastream-launch4j-config.xml`
- **Build Wrapper**

➡ Launch4j will produce:

```
<project-root>/Novastream/exe/Novastream.exe
```

✅ Make sure to zip & ship the entire `Novastream/exe` folder as it has the custom dependent JRE `novastream-jre` along with the EXE.

---

## 📁 **Final structure**

Your distribution folder should look like:

```
Novastream/
 ├── exe/
 |    ├── novastream-jre/
 |    └── Novastream.exe
 └── launcher/
     ├── javafx-jmods-21/
     ├── icon.ico
     ├── novastream-backend.jar
     └── novastream-launch4j-config.xml
```

---

## 💡 Notes

⚠ _Sign the EXE to avoid antivirus false positives (recommended)._  
⚠ _You don’t need to install Java on the target machine — the custom JRE is bundled._
