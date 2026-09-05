# Installation guide

## Requirements

- Java 21
- Git

The repository includes the Maven Wrapper, so Maven does not need to be installed separately. The first run downloads Maven and the project dependencies, so it needs an internet connection.

## Run the game

Clone the repository, move into it, then run one of these commands:

```sh
./mvnw spring-boot:run
```

On Windows:

```powershell
.\mvnw.cmd spring-boot:run
```

When Spring Boot finishes starting, visit [http://localhost:8080](http://localhost:8080).

The browser client is served by Spring Boot. There is no separate frontend install or build command.

## Test the project

Run the complete verification suite with:

```sh
./mvnw verify
```

On Windows:

```powershell
.\mvnw.cmd verify
```

This runs the unit tests and the WebSocket end-to-end tests.

## Package a runnable application

```sh
./mvnw package
java -jar target/tanchi-snake-0.0.1-SNAPSHOT.jar
```

## Configuration

The application name and static-resource cache behaviour are in `src/main/resources/application.properties`. The game currently keeps rooms in memory, so restarting the application clears active rooms.

## Troubleshooting

If `java --version` does not report version 21, install a Java 21 JDK and ensure it is first on your `PATH`.

If port 8080 is already in use, start the app on another port:

```sh
./mvnw spring-boot:run -Dspring-boot.run.arguments=--server.port=8081
```

Then open [http://localhost:8081](http://localhost:8081).
