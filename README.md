# Requirements

Java 8 and maven 2.

# Usage

## Compilation

    mvn package
    mvn dependency:copy-dependencies

## Calculations

First, the \`hash' files must be produced for each WARC:

    LD_LIBRARY_PATH=target/lib java -jar target/structuralfingerprinttest-1.0-SNAPSHOT.jar -hash x.warc.gz > x.warc.gz.hash

A hash file contains metadata about resources and the links between them. They
can be catenated.

Then, the different calculations can be performed, for instance:

    LD_LIBRARY_PATH=target/lib java -jar target/structuralfingerprinttest-1.0-SNAPSHOT.jar -secDiverBc x.warc.gz some-domain.org
