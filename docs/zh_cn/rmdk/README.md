### RMDK

  RMDK全称Resource Mod Develop Kit，是一个便于开发者快速开发的工具。
  
  基于TemplateDevEnv(https://github.com/CleanroomMC/TemplateDevEnv) ，这个工具自带有本地RML。
  
  这个工具略微粗糙，但是足够使用。另外，它也是一个完成的普通Java Mod环境。
  
  在`dev\`下进行开发，下面文本引用自其`README.md`。
  
> ## First to use
>Before the using, you need to install jdk8.
>(Use `JDKSwitcher_forWindows.exe` to set `JAVA_HOME`).
>
>Use `setupEnvironment.bat` for environment setup.
>
>## Commands
>
>
>---
>
>
>Use `runClient.bat` for client test.
>
>
>---
>
>
>Use `runServer.bat` for client test.
>
>
>---
>
>
>Use `pack.bat` for output.
>Named `<archiveName>-<version>.zip` at `releases/`.
>
>
>The archiveName and version could be configed at `gradle.properties`.
>
>```text
># Mod Information
> -> mod_version = 1.0
> maven_group = com.cleanroommc
> -> archives_base_name = modid
>```
>
>mod_version is version and archives_base_name is archiveName.
>
>## For updates
>
>just use a new `rml` jar file to replace the old at `run\mods` and `runServer\mods`.
>
>Usually named `ResourceModLoader-<version>.jar`.
