# Farther View Distance

## author: `xuancat0208`

### Contributors: [``xuancat0208``, ``EuSouVoce``]

### Special thanks to `DarkChroma` admin of [CatCraft](https://catcraft.net/) for the support.

---

## ~~How-to?~~

1. ~~Open each branch, compile it's corresponding pom.xml (`mvn package`)~~
    > ~~Note: on the main _pom.xml_, on the branch 1.19 and branch 1.20 **you must** run the `mvn paper-nms:init` in order to mojang and paper mappings to work.~~
2. ~~Compile main pom.xml~~
3. ~~run `pack.bat` (needs 7zip installed)~~
4. ~~\<Optional> Rename pack.jar -> FartherViewDistance.jar~~

## Since v10.0.0 **it now**:
*  Uses: Java 21
* Every version will be compiled against a paper version, if you are seeking retro-compatibility, use an older version,
* If the server is running in an incompatible version it will disable the plugin and log this info.

### If you wish to build it yourself:

1. run `mvn paper-nms:init`
2. run `mvn package`
3. Add the resulting jar from `./target` to your plugins folder and be happy.

---

#### For older versions of papermc: 
> Check the version used on `pom.xml` based of **older commit files** that you want to compile against.