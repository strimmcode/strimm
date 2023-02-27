Packaging Protocol:

1   Maven compile
2   Check program works
3   Maven package
4   jar is in Target/mainModule-1.0.SNAPSHOT.jar
5   examine the jar (right click and unpack) to see MANIFEST
6   copy and paste into /jars/ in the package
7   ProjectStructure/Artefacts  add a jar and dependencies - pulls in all of the dependencies
8   Dependencies added to /jars/ in package
9   To run cmd, navigate to the package folder
10  java -cp ./jars/* Main
