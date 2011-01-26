#!/bin/bash

while(true)
do

bytesStr=`ifconfig |sed -e 's/ (.*) /,/g' -e 's/ (.*)//g' -e '/RX bytes/!d' -e 's/ *[RT]X bytes://g' -e 's/,/ /g'`
#echo $bytesStr

i=0
for bytes in $bytesStr
do
if [[ $i == 0 ]]; then
ethRx=$bytes
fi
if [[ $i == 1 ]]; then
ethTx=$bytes
fi
if [[ $i == 2 ]]; then
loRx=$bytes
fi
if [[ $i == 3 ]]; then
loTx=$bytes
fi
i=$((i+1))
done

dethRx=$((ethRx-oethRx))
dethTx=$((ethTx-oethTx))
dloRx=$((loRx-oloRx))
dloTx=$((loTx-oloTx))
echo eth0 recv: `expr $dethRx / 1024` kB/s , send: `expr $dethTx / 1024` kB/s
echo lo recv: `expr $dloRx / 1024` kB/s , send: `expr $dloTx / 1024` kB/s

oethRx=$ethRx
oethTx=$ethTx
oloRx=$loRx
oloTx=$loTx

sleep 1
clear
done
