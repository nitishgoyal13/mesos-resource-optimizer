FROM docker.phonepe.com:5000/pp-ops-xenial:0.6

EXPOSE 17000
EXPOSE 17001
EXPOSE 5701

VOLUME /var/log/optimizer

ENV CONFIG_PATH optimizer.yml
ENV JAR_FILE optimizer.jar

ADD target/optimizer*.jar ${JAR_FILE}

CMD sh -exc "curl -X GET --header 'Accept: application/x-yaml' http://${CONFIG_SERVICE_HOST_PORT}/v1/phonepe/optimizer/${CONFIG_ENV} > ${CONFIG_PATH} \
    && java -jar -Duser.timezone=IST ${JAVA_OPTS} -Xms${JAVA_PROCESS_MIN_HEAP-512m} -Xmx${JAVA_PROCESS_MAX_HEAP-512m} ${JAR_FILE} server ${CONFIG_PATH}"

