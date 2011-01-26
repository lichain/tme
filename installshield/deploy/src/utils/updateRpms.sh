#!/bin/bash

progs="tme-zookeeper tme-mist tme-broker tme-portal"

ftp_dir=ftp://coretech-backend-dev.tw.trendnet.org/uploads/scott_wang/

rm -f ../rpm/*.rpm
wget -O - --no-remove-listing $ftp_dir

for prog in $progs
do
    url=$ftp_dir`grep $prog .listing | tail -n 1 | sed -e 's/.* //g' | dos2unix`
    wget $url
done

mv *.rpm ../rpm/
rm .listing
