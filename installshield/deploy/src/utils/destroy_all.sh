#!/bin/sh

if [ $# -lt 1 ]; then
    echo "usage: $0 exchange_name"
    exit 0
fi

EXCHANGE_NAME=$1

for sess in `mist-session -l | grep $EXCHANGE_NAME | sed -e 's/\t.*//g'`
do
    mist-session -d $sess
done

rm -f /tmp/tme-install/t_socket_*
