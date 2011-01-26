#!/bin/bash

./longtime_watch.sh > /tmp/tme-install/longtime.all
counts=`sed -e 's/ messages ... success//g' -e '/messages/d' -e 's/Case [^,]*//g' -e 's/, //g' -e 's/\[.*] //g' -e 's/ (.*)//g' -e '/[a-zA-Z\.]/d' /tmp/tme-install/longtime.all`

startDate=`sed -e '/#[1]:/!d' /tmp/tme-install/longtime.all |head -n 1|sed -e 's/\].*//g' -e 's/\[//g'`
startTs=`date -d "$startDate" +%s`
endTs=`date +%s`
ts=`expr $endTs - $startTs`

total=0
for cnt in $counts
do
total=$(($total+$cnt))
done
echo total count: $total

echo throughput = `expr $total / $ts` msgs / sec

sed -e '/success/d' /tmp/tme-install/longtime.all
