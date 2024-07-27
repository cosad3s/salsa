FROM openjdk:17-jdk-slim
WORKDIR /app
COPY target/salsa-jar-with-dependencies.jar /app/salsa.jar
ENTRYPOINT ["java", "-jar", "salsa.jar"]