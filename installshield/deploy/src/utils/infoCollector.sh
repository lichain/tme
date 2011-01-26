#!/bin/bash
stat="/tmp/tme-install/tme_sys_stat"
procs="Daemon TmeSpy Broker TmeFwd QuorumPeerMain"

rm $stat
echo lsof: `lsof|grep java|wc -l` >> $stat
echo socket: `netstat 2>/dev/null|grep tcp|wc -l` >> $stat

for proc in $procs
do
pid=`/usr/java/latest/bin/jps|grep $proc|sed -e 's/ .*//g'`
if [ "" != "$pid" ]; then
echo $proc: `ps uH p $pid|wc -l` threads, >> $stat
mem=`ps -o rss h $pid`
echo mem=`expr $mem / 1024` MB >> $stat
fi
done

cat $stat

