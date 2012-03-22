#!/bin/sh

if [ $# != 2 ]
then
    echo "Usage: $0 [rrd dir] [max record count]"
    exit 1
fi

cd $1
for json in `ls *.json`
do
    archive=`echo $json | sed -e 's/\.json$/\.archive/g'`
    cat $json >> $archive
    echo "," >> $archive
    tail -n $2 $archive > $archive.tmp
    mv -f $archive.tmp $archive
done

