FROM europe-north1-docker.pkg.dev/cgr-nav/pull-through/nav.no/jre:openjdk-21
WORKDIR /app
COPY api/build/libs/*.jar /app/
COPY web-app/dist /app/public

ENV TZ="Europe/Oslo"
ENV JAVA_TOOL_OPTIONS='-XX:MaxRAMPercentage=75'
EXPOSE 8080

CMD ["-jar", "/app/app.jar"]
