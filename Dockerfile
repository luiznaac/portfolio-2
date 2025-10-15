# Builder
FROM gradle:8.14-jdk21 AS builder

COPY ./ /home/gradle/project

WORKDIR /home/gradle/project

RUN gradle -Dorg.gradle.daemon=false clean assemble --stacktrace --info

RUN mkdir -p /home/gradle/project/build/distributions/app/

RUN unzip /home/gradle/project/application/build/distributions/*.zip -d /home/gradle/project/build/distributions/app/

# Application
FROM openjdk:21-slim

COPY --from=builder /home/gradle/project/build/distributions/app/ /opt/app/

WORKDIR /opt/app

RUN rm -rf /var/cache/*

EXPOSE 8080

CMD ["/opt/app/application-1.0/bin/application"]
