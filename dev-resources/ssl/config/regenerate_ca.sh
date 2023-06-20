#!/usr/bin/env sh

#create a new CA cert and private key
openssl req -new -newkey rsa:2048 -keyout private_keys/cakey.pem -extensions v3_ca -nodes -config ./openssl.cnf -out ca_req.pem
openssl ca -batch -create_serial -days 1460 -keyfile private_keys/cakey.pem -selfsign -out ./ca.pem -notext -config ./openssl.cnf -extensions v3_ca -infiles ca_req.pem
