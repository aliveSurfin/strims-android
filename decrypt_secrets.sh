#!/bin/sh
gpg --quiet --batch --yes --decrypt --passphrase=$KEY_STORE_PASSPHRASE \
--output keystore.properties keystore.properties.gpg

gpg --quiet --batch --yes --decrypt --passphrase=$APP_SIGN_KEY_FILE_PASSPHRASE \
--output ./app/app-sign-key.jks ./app/app-sign-key.jks.gpg

