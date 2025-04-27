FROM openjdk:21-jdk

WORKDIR /app

COPY ./mvnw /app/mvnw
COPY .mvn/ /app/.mvn/
COPY pom.xml /app/
COPY mvnw.cmd /app/
RUN ./mvnw dependency:go-offline

COPY src /app/src

RUN ./mvnw package -DskipTests

CMD ["sh", "-c", "java -jar target/*.jar"]