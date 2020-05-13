#!/usr/bin/env bash

if [ ! -e "application.properties" ]
then
    2>&1 echo "No application.properties file found."
    2>&1 echo "Please create from the template and try again."
    exit 1
fi

java \
    -Duser.timezone="America/Chicago" \
    -jar target/query-api-data-extract-*.jar \
    --debug
