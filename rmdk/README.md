## First to use
Before the using, you need to install jdk8.
(Use `JDKSwitcher_forWindows.exe` to set `JAVA_HOME`).

Use `setupEnvironment.bat` for environment setup.

## Commands


---


Use `runClient.bat` for client test.


---


Use `runServer.bat` for client test.


---


Use `pack.bat` for output.
Named `<archiveName>-<version>.zip` at `releases/`.


The archiveName and version could be configed at `gradle.properties`.

```text
# Mod Information
> mod_version = 1.0
maven_group = com.cleanroommc
> archives_base_name = modid
```
mod_version is version and archives_base_name is archiveName.

## For updates

just use a new `rml` jar file to replace the old at `run\mods` and `runServer\mods`.

Usually named `ResourceModLoader-<version>.jar`.
