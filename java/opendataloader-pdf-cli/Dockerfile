#
# Copyright 2025 Hancom Inc.
#
# This Source Code Form is subject to the terms of the Mozilla Public
# License, v. 2.0. If a copy of the MPL was not distributed with this
# file, You can obtain one at http://mozilla.org/MPL/2.0/.
#

# Use an official OpenJDK runtime as a parent image
FROM eclipse-temurin:11-jre

# Set the working directory in the container
WORKDIR /app

# Copy the fat jar from the target directory to the container
COPY target/opendataloader-pdf-cli-*.jar app.jar

# port 8080 available to the world outside this container
EXPOSE 8080

# Run the jar file
ENTRYPOINT ["java","-jar","app.jar"]
