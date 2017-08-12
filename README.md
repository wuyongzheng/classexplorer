# Java Class File Explorer

This tool dumps a Java class file into a human readable text file, allows some
editing on the dumped file, and rebuilds from the dumped file.
The main use of this tool is to patch class files without source code.
The advantage over a full fledged Java assembler, such as Soot and Jasmin is:

* No class dependency needed. Just a single class file.
* All Java version supported. Guaranteed lossless dump/build for all Java version.
* Very Light weight.

The disadvantage is:

* User have to understand Java class file format. Consider this tool a
  specialized hex-editor.
* When changing instructions, the size of code has to be preserved. For
  example, to delete a three-byte instruction, one can replace it with three
  one-byte nop instructions. There are other restrictions on changing
  instructions.

## How to use?

We use an example to show the steps to patch a class file.
In this example, we want to remove the license check in
[Subsonic](http://www.subsonic.org/pages/download.jsp) server.
[This link](https://gist.github.com/ProjectMoon/1318300) shows how to do it in
source level, but we do it in binary here.

### Step 1: Dump

Unzip subsonic.war to obtain SettingsService.class, then run:

```
java ClassExplorer d SettingsService.class
```

The dumped file will be SettingsService.clsexp.txt

### Step 2: Edit the dumped file

TODO

### Step 3: Rebuild class file

```
java ClassExplorer b SettingsService.clsexp.txt SettingsService.new.class
```

Use javap to verify the rebuild class file.

```
javap -p -c SettingsService.new.class
```

If there isn't any error, use SettingsService.new.class to replace the original
one and repack subsonic.war.
