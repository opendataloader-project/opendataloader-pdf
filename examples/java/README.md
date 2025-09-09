# OpenDataLoader PDF - Java Sample Applications

This directory contains sample applications demonstrating how to use the `opendataloader-pdf-core` library. These examples are designed for beginners to get started easily.

## System Requirements

To run these examples, you will need the following software installed on your system:

1.  **Java Development Kit (JDK) - Version 11 or higher:**
    *   The JDK is required to compile and run Java applications.
    *   We recommend installing a free version from [Eclipse Temurin (Adoptium)](https://adoptium.net/).

2.  **Apache Maven (for the Maven example only):**
    *   Maven is a build tool used for the `maven-example`.
    *   You can download it and find installation instructions on the [Maven website](https://maven.apache.org/download.cgi).

**Note for Gradle Examples:** You do **not** need to install Gradle separately. The `gradle-groovy-example` and `gradle-kotlin-example` projects include a "Gradle Wrapper" (`gradlew` or `gradlew.bat`). When you run a command with the wrapper, it will automatically download and use the correct Gradle version for the project.

---

## 1. Maven Example

This sample shows how to use the library in a standard Java project built with Maven. The example is pre-configured to process a sample PDF file included in this repository.

### Instructions

1.  **Navigate to the directory:**
    Open a terminal or command prompt and navigate to the `maven-example` directory.
    ```shell
    cd maven-example
    ```

2.  **Build and Run the Application:**
    Run the following Maven command. This command will download dependencies, compile the code, and execute the application in one step.
    ```shell
    mvn clean install exec:java
    ```

3.  **Verify the Output:**
    After the command finishes, you will see success messages in your console. The output files (JSON, Markdown, and an annotated PDF) will be generated in the `maven-example/target` directory.

    You can inspect the generated files to see the results of the PDF processing.

---

## 2. Gradle (Groovy DSL) Example

This sample uses Gradle with the traditional Groovy DSL for its build script (`build.gradle`). The example is configured to use the `opendataloader-pdf-core` library to process a sample PDF file from the `samples/pdf` directory and save the results.

### Instructions

1.  **Navigate to the directory:**
    Open a terminal or command prompt and navigate to the `gradle-groovy-example` directory:
    ```shell
    cd gradle-groovy-example
    ```

2.  **Build and Run the Application:**
    Run the application using the Gradle Wrapper. This single command will download dependencies, compile the code, and execute the main method.

    **On Linux/macOS:**
    ```shell
    ./gradlew run
    ```
    **On Windows:**
    ```shell
    gradlew.bat run
    ```

3.  **Verify the Output:**
    After the command finishes, you will see success messages in your console. The output files (JSON, Markdown, and an annotated PDF) will be generated in the `gradle-groovy-example/build` directory.

---

## 3. Gradle (Kotlin DSL) Example

This sample uses Gradle with the modern Kotlin DSL for its build script (`build.gradle.kts`). The example is configured to use the `opendataloader-pdf-core` library to process a sample PDF file from the `samples/pdf` directory and save the results.

### Instructions

1.  **Navigate to the directory:**
    Open a terminal or command prompt and navigate to the `gradle-kotlin-example` directory:
    ```shell
    cd gradle-kotlin-example
    ```

2.  **Build and Run the Application:**
    Run the application using the Gradle Wrapper. This single command will download dependencies, compile the code, and execute the main method.

    **On Linux/macOS:**
    ```shell
    ./gradlew run
    ```
    **On Windows:**
    ```shell
    gradlew.bat run
    ```

3.  **Verify the Output:**
    After the command finishes, you will see success messages in your console. The output files (JSON, Markdown, and an annotated PDF) will be generated in the `gradle-kotlin-example/build` directory.
