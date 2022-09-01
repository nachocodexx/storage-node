FROM alpine:3.7
RUN apk add openjdk8
COPY ./target/scala-2.13/sn.jar /app/src/app.jar
WORKDIR /app/src
ENTRYPOINT ["java", "-cp","app.jar","mx.cinvestav.Main"]
