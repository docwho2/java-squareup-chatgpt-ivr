#!/bin/bash

# ensure sub modules are brought in
git submodule update --init --recursive


# Run install at parent POM level so it can build V4 Libraries and install them
mvn install -DskipTests
