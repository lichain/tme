#!/bin/bash

zkHosts=`/tmp/tme-install/utils/getProperty.sh Zookeepers`
zkString=""
for zkHost in $zkHosts
do
    zkString="$zkString,$zkHost:2181"
done
zkString=${zkString:1}

rpm -ivh /tmp/tme-install/rpm/tme-mist*.rpm

jdbport=`/tmp/tme-install/utils/getProperty.sh MistdJdbPort`
if [[ "$jdbport" != "" ]]; then
    sed -e "s/javacmd -cp/javacmd -Xdebug -Xrunjdwp:transport=dt_socket,address=$jdbport,server=y,suspend=n -cp/g" /etc/init.d/mistd > /tmp/tme-install/mistd
    mv /tmp/tme-install/mistd /etc/init.d/mistd
    chmod 755 /etc/init.d/mistd
fi

sed -e '/mistd.zookeeper=/d' /usr/share/mist/etc/mistd.properties >/tmp/tme-install/mistd.properties
echo mistd.zookeeper=$zkString >> /tmp/tme-install/mistd.properties
mv /tmp/tme-install/mistd.properties /usr/share/mist/etc/mistd.properties

sed -e '/zkeditor.port=/d' /usr/share/mist/etc/mistd.properties >/tmp/tme-install/mistd.properties
echo zkeditor.port=`/tmp/tme-install/utils/getProperty.sh ZkeditorPort` >> /tmp/tme-install/mistd.properties
mv /tmp/tme-install/mistd.properties /usr/share/mist/etc/mistd.properties

sed -e '/mistd.port=/d' /usr/share/mist/etc/mistd.properties >/tmp/tme-install/mistd.properties
echo mistd.port=`/tmp/tme-install/utils/getProperty.sh MistdPort` >> /tmp/tme-install/mistd.properties
mv /tmp/tme-install/mistd.properties /usr/share/mist/etc/mistd.properties
