
 ## Compression of Triangulated Solids’ Surfaces by Decimating Reconstructable Triangles
 This program performs a lossless triangular mesh decimation.

 ### Compilation
Execute command in the root folder:
 `mvn clean install` **(JDK11 or higher required)**

 ### Running
 Once compilation is complete, extract the contents of the `ToCoS-1.0-SNAPSHOT-release.zip` archive to a suitable directory.

 Using command prompt (ob bash), execute the command: `java -jar ToCoS-1.0-SNAPSHOT.jar`

 This will print out usage:

 ```
$ java -jar ToCoS-1.0-SNAPSHOT.jar
Compress: c input.[obj|stl|...any assimp supported format].
Decompress: d input.[obj|stl|...any assimp supported format].CoToS
```

*3D object reading and writing is done using ASSIMP (https://github.com/kotlin-graphics/assimp)*

### Examples
To compress the object run: `java -jar ToCoS-1.0-SNAPSHOT.jar c input.obj` **(object must be a watertight triangular mesh)**

To decompress the compressed object run: `java -jar ToCoS-1.0-SNAPSHOT.jar d input.obj.CoToS`

