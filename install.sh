#!/bin/bash
APP_NAME='jul'
clear &&
echo "=== clean ${APP_NAME} ===" &&
mvn clean $@ &&
clear &&
echo "=== install ${APP_NAME} ===" &&
mvn install \
        -DskipTests=true \
        -Dmaven.test.skip=true \
        -Dlicense.skipAddThirdParty=true \
        -Dlicense.skipUpdateProjectLicense=true \
        -Dlicense.skipDownloadLicenses \
        -Dlicense.skipCheckLicense=true \
        -Dmaven.license.skip=true $@ &&
clear &&
echo "=== ${APP_NAME} is successfully installed ==="
