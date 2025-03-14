ARG JAR_FILE_DEFAULT="application.jar"

FROM openjdk:21 as build

ARG JAR_FILE_DEFAULT
ENV JAR_FILE=$JAR_FILE_DEFAULT

WORKDIR /build

COPY . /build

RUN microdnf install findutils
RUN  ./gradlew bootJar
RUN find ./build/. -name "*.jar" -exec cp {} /$JAR_FILE \;



FROM openjdk:21

ARG JAR_FILE_DEFAULT="application.jar"
ENV JAR_FILE=$JAR_FILE_DEFAULT
ENV JAVA_OPTS="-XX:+UseStringDeduplication -Xmx1024m"
ENV JMX_PORT=9016
ENV JMX_OPTS="-Dcom.sun.management.jmxremote \
-Dcom.sun.management.jmxremote.port=$JMX_PORT \
-Dcom.sun.management.jmxremote.rmi.port=$JMX_PORT \
-Dcom.sun.management.jmxremote.ssl=false \
-Dcom.sun.management.jmxremote.local.only=false \
-Dcom.sun.management.jmxremote.authenticate=false"

WORKDIR /app

COPY --from=build "/$JAR_FILE" .
EXPOSE 8080

ENTRYPOINT exec java $JAVA_OPTS $JMX_OPTS -Djava.security.egd=file:/dev/./urandom -jar /app/$JAR_FILE

