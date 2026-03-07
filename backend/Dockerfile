FROM gradle:8.8-jdk21 AS build
WORKDIR /home/gradle/project
COPY --chown=gradle:gradle . .
RUN gradle clean bootJar --no-daemon

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /home/gradle/project/build/libs/*.jar /app/app.jar
ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75"
EXPOSE 8080
ENTRYPOINT ["bash","-lc","java $JAVA_OPTS -jar /app/app.jar"]
