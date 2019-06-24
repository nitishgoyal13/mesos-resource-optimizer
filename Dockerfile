FROM ubuntu:14.04
MAINTAINER Nitish Goyal <nitishgoyal13 [at] gmail.com>

EXPOSE 17000
EXPOSE 17001
EXPOSE 5701

VOLUME /var/log/optimizer

ENV CONFIG_PATH optimizer.yml
ENV JAR_FILE optimizer.jar

ADD target/optimizer*.jar ${JAR_FILE}

CMD sh -exc "java -jar -Duser.timezone=IST ${JAVA_OPTS} -Xms${JAVA_PROCESS_MIN_HEAP-512m} -Xmx${JAVA_PROCESS_MAX_HEAP-512m} ${JAR_FILE} server config/local.yml"

