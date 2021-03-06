FROM gradle:5.6-jdk8 as builder
COPY --chown=gradle:gradle . /home/src
RUN ls
WORKDIR /home/src
RUN gradle actionShadowJar


FROM openjdk:8-jre-slim

WORKDIR /
COPY --from=builder /home/src/build/libs/unicorn-action.jar /
ENTRYPOINT ["java", "-jar", "/unicorn-action.jar"]
