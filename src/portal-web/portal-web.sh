#!/bin/sh

if [[ -e portal.rb && -e portal-web-conf.sh ]];
then
	. portal-web-conf.sh
else
	. /opt/trend/tme/conf/portal-web/portal-web-conf.sh
	cd /opt/trend/tme/lib/portal-web/
fi

if [ "$1" == "daemon" ];
then
	GEM_HOME=lib/ruby/1.9.1/ rrddir=$rrddir lib/ruby/1.9.1/bin/thin start -p$port > /var/log/tme/portal-web.log 2>&1 &
	echo $! > /var/run/tme/tme-portal-web.pid
else
	GEM_HOME=lib/ruby/1.9.1/ rrddir=$rrddir lib/ruby/1.9.1/bin/thin start -p$port > /var/log/tme/portal-web.log 2>&1
fi
