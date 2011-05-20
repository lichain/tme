
%define __os_install_post \
    /usr/lib/rpm/brp-compress \
    %{!?__debug_package:/usr/lib/rpm/brp-strip %{__strip}} \
    /usr/lib/rpm/brp-strip-static-archive %{__strip} \
    /usr/lib/rpm/brp-strip-comment-note %{__strip} %{__objdump} \
    /usr/lib/rpm/brp-python-bytecompile \
%{nil}

%define name tme-broker
%define ver	#MAJOR_VER#
%define debug_package %{nil}

Summary: TME Broker Package
Name: %{name}
Version: %{ver}
Release: #RELEASE_VER#
License: Trend Micro Inc.
Group: System Environment/Daemons
Source: %{name}-%{ver}.tar.gz
BuildRoot: %{_tmppath}/%{name}-%{ver}-root
Requires: jdk, tme-mist
Requires(pre): /usr/bin/unzip
Requires(post): /sbin/chkconfig, /sbin/service
Requires(preun): /sbin/chkconfig, /sbin/service

%description

TME broker package.

%prep

%setup -q

%{__mkdir} -p $RPM_BUILD_ROOT/usr/share/mist/bin
%{__mkdir} -p $RPM_BUILD_ROOT/etc/init.d
%{__mkdir} -p $RPM_BUILD_ROOT/usr/share/tme-broker
%{__mkdir} -p $RPM_BUILD_ROOT/usr/share/tme-broker/etc

cp -rf mq $RPM_BUILD_ROOT/usr/share/tme-broker
%{__mkdir} -p $RPM_BUILD_ROOT/usr/share/tme-broker/mq/var/instances/imqbroker/props
cp -f config.properties $RPM_BUILD_ROOT/usr/share/tme-broker/mq/var/instances/imqbroker/props/

%build

%install

install -m755 etc/init.d/tme-brokerd $RPM_BUILD_ROOT/etc/init.d
install -m755 etc/init.d/tme-spyd $RPM_BUILD_ROOT/etc/init.d
install -m755 usr/share/mist/bin/watchdog-spyd $RPM_BUILD_ROOT/usr/share/mist/bin
install -m755 usr/share/mist/bin/install_spyd.sh $RPM_BUILD_ROOT/usr/share/mist/bin
install -m755 usr/share/mist/bin/remove_spyd.sh $RPM_BUILD_ROOT/usr/share/mist/bin
install -m755 usr/share/tme-broker/install_brokerd.sh $RPM_BUILD_ROOT/usr/share/tme-broker
install -m755 usr/share/tme-broker/remove_brokerd.sh $RPM_BUILD_ROOT/usr/share/tme-broker
install -m755 usr/share/tme-broker/change_broker_mem.sh $RPM_BUILD_ROOT/usr/share/tme-broker
install -m600 usr/share/tme-broker/jmxremote.* $RPM_BUILD_ROOT/usr/share/tme-broker/etc
install -m644 usr/share/tme-broker/tme-spyd.cron $RPM_BUILD_ROOT/usr/share/tme-broker/etc

%clean

rm -rf $RPM_BUILD_ROOT
rm -rf /tmp/tme-broker

%files

/etc/init.d/tme-brokerd
/etc/init.d/tme-spyd
/usr/share/mist/bin/watchdog-spyd
/usr/share/mist/bin/install_spyd.sh
/usr/share/mist/bin/remove_spyd.sh
%config /usr/share/tme-broker/mq/var/instances/imqbroker/props/config.properties

%dir

/usr/share/tme-broker

%pre

myip=`hostname -i`
if [ "$myip" = "" ]; then
    echo "Unable to determine local IP, please make sure \"hostname -i\" works normally"
    exit 1
elif [ "$myip" = "127.0.0.1" ]; then
    echo "Local IP: $myip is not acceptable, please configure a physical IP"
    exit 1
fi

if [ "$1" = "1" ]; then
    # install
    installed=`rpm -qa | grep tme-broker | wc -l`
    if [ "$installed" = "1" ]; then
        echo `rpm -qa | grep tme-broker` already installed
        exit 1
    fi
elif [ "$1" = "2" ]; then
    # upgrade
    /usr/share/tme-broker/remove_brokerd.sh
    cp -f /usr/share/tme-broker/mq/var/instances/imqbroker/props/config.properties{,.old}
    echo backup configuration to /usr/share/tme-broker/mq/var/instances/imqbroker/props/config.properties.old
fi

%post

chown -R TME.TME /usr/share/tme-broker

if [ "$1" = "1" ]; then
    # install
    echo done. please configure and execute install_brokerd.sh to install service
elif [ "$1" = "2" ]; then
    # upgrade
    echo service tme-brokerd removed. please re-configure and execute install_brokerd.sh again
fi

%preun

if [ "$1" = "1" ]; then
    # upgrade
    usleep 1
elif [ "$1" = "0" ]; then
    # uninstall
    /usr/share/tme-broker/remove_brokerd.sh
fi

%postun

if [ "$1" = "1" ]; then
    # upgrade
    usleep 1
elif [ "$1" = "0" ]; then
    # uninstall
    usleep 1
fi

%changelog

* Mon Feb 14 2011 Chris Huang 20110214
- Use fixed port 5567 of JMX to broker (http://wiki.spn.tw.trendnet.org/mediawiki/index.php/TME20_Installation_Guide#Firewall_Requirements)
 