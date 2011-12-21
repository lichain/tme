
%define __os_install_post \
    /usr/lib/rpm/brp-compress \
    %{!?__debug_package:/usr/lib/rpm/brp-strip %{__strip}} \
    /usr/lib/rpm/brp-strip-static-archive %{__strip} \
    /usr/lib/rpm/brp-strip-comment-note %{__strip} %{__objdump} \
    /usr/lib/rpm/brp-python-bytecompile \
%{nil}

%define name tme-mist
%define ver #MAJOR_VER#

Summary: MIST - Messaging Integration STandard
Name: %{name}
Version: %{ver}
Release: #RELEASE_VER#
License: Trend Micro Inc.
Group: System Environment/Daemons
Source: %{name}-%{ver}.tar.gz
BuildRoot: %{_tmppath}/%{name}-%{ver}-root
Requires: jdk, spn-infra-common
Requires(post): /sbin/chkconfig, /sbin/service
Requires(preun): /sbin/chkconfig, /sbin/service

%description

Messaging Integration STandard (or just MIST) is the result of our effort to 
standardize and abstract message delivery so that applications can instead 
focus on message processing. 

%prep

%setup -q

%{__mkdir} -p $RPM_BUILD_ROOT/
%{__mkdir} -p $RPM_BUILD_ROOT/etc/init.d
%{__mkdir} -p $RPM_BUILD_ROOT/usr/bin
%{__mkdir} -p $RPM_BUILD_ROOT/usr/share/mist/bin
%{__mkdir} -p $RPM_BUILD_ROOT/usr/share/mist/etc
%{__mkdir} -p $RPM_BUILD_ROOT/usr/share/mist/lib

%build

%install

install -m755 etc/init.d/mistd $RPM_BUILD_ROOT/etc/init.d
install -m755 usr/bin/mist-session $RPM_BUILD_ROOT/usr/bin
install -m755 usr/bin/mist-sink $RPM_BUILD_ROOT/usr/bin
install -m755 usr/bin/mist-source $RPM_BUILD_ROOT/usr/bin
install -m755 usr/bin/mist-encode $RPM_BUILD_ROOT/usr/bin
install -m755 usr/bin/mist-decode $RPM_BUILD_ROOT/usr/bin
install -m755 usr/bin/mist-forwarder $RPM_BUILD_ROOT/usr/bin
install -m755 usr/bin/mist-line-gen $RPM_BUILD_ROOT/usr/bin
install -m755 usr/bin/mist-broker $RPM_BUILD_ROOT/usr/bin
install -m755 usr/share/mist/bin/test_mistd $RPM_BUILD_ROOT/usr/share/mist/bin
install -m755 usr/share/mist/bin/mon_mistd $RPM_BUILD_ROOT/usr/share/mist/bin
install -m755 usr/share/mist/bin/watchdog-mistd $RPM_BUILD_ROOT/usr/share/mist/bin
install -m755 usr/share/mist/bin/install_mistd.sh $RPM_BUILD_ROOT/usr/share/mist/bin
install -m755 usr/share/mist/bin/remove_mistd.sh $RPM_BUILD_ROOT/usr/share/mist/bin
install -m755 usr/share/mist/bin/alert-spyd $RPM_BUILD_ROOT/usr/share/mist/bin
install -m644 usr/share/mist/lib/*.jar $RPM_BUILD_ROOT/usr/share/mist/lib
install -m644 usr/share/mist/etc/* $RPM_BUILD_ROOT/usr/share/mist/etc
install -m755 etc/init.d/tme-bridge $RPM_BUILD_ROOT/etc/init.d
install -m755 usr/bin/bridge-console $RPM_BUILD_ROOT/usr/bin
install -m755 usr/bin/tme-console $RPM_BUILD_ROOT/usr/bin
install -m755 usr/share/mist/bin/install_bridge.sh $RPM_BUILD_ROOT/usr/share/mist/bin
install -m755 usr/share/mist/bin/remove_bridge.sh $RPM_BUILD_ROOT/usr/share/mist/bin
install -m755 usr/share/mist/bin/watchdog-bridge $RPM_BUILD_ROOT/usr/share/mist/bin

%clean

rm -rf $RPM_BUILD_ROOT

%files

/etc/init.d/mistd
/usr/bin/mist-session
/usr/bin/mist-sink 
/usr/bin/mist-source 
/usr/bin/mist-encode 
/usr/bin/mist-decode 
/usr/bin/mist-forwarder 
/usr/bin/mist-line-gen 
/usr/bin/mist-broker
/usr/bin/tme-console
/usr/share/mist/bin/test_mistd
/usr/share/mist/bin/mon_mistd
/usr/share/mist/bin/alert-spyd
/usr/share/mist/bin/watchdog-mistd
/usr/share/mist/bin/install_mistd.sh
/usr/share/mist/bin/remove_mistd.sh
/etc/init.d/tme-bridge
/usr/bin/bridge-console
/usr/share/mist/bin/install_bridge.sh
/usr/share/mist/bin/remove_bridge.sh
/usr/share/mist/bin/watchdog-bridge
/usr/share/mist/etc/tme-bridge.cron

%dir /usr/share/mist
%dir /usr/share/mist/bin
%dir /usr/share/mist/etc
/usr/share/mist/etc/*
%config /usr/share/mist/etc/mistd.properties
%config /usr/share/mist/etc/mistd.log4j
%dir /usr/share/mist/lib
/usr/share/mist/lib/*

%pre

check_pid() {
    my_pid=$1
    if [ -e $my_pid ]; then
        ps -p `cat $my_pid` > /dev/null
        return $?
    else
        return 1
    fi
}

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
    installed=`rpm -qa | grep tme-mist | wc -l`
    if [ "$installed" = "1" ]; then
        echo `rpm -qa | grep tme-mist` already installed
        exit 1
    fi
    if [ "`getent passwd TME`" == "" ]; then
        /usr/sbin/useradd -r TME
    fi
elif [ "$1" = "2" ]; then
    # upgrade
    check_pid /var/run/tme/tme-spyd.pid
    if [ $? -eq 0 ]; then
        echo tme-spyd is running, please execute remove_spyd.sh to shutdown it first
        exit 1
    fi
    /usr/share/mist/bin/remove_bridge.sh
    /usr/share/mist/bin/remove_mistd.sh
    cp -f /usr/share/mist/etc/mistd.properties{,.old}
    echo backup configuration to /usr/share/mist/etc/mistd.properties.old
fi

%post

mkdir -p /var/run/tme 

chown -R TME.TME /usr/share/mist
chown -R TME.TME /var/run/tme

if [ "$1" = "1" ]; then
    # install
    /sbin/chkconfig --add mistd
    /sbin/chkconfig --level 35 mistd on
    /sbin/chkconfig --add tme-bridge
    /sbin/chkconfig --level 35 tme-bridge on
    echo done. please configure and execute install_mistd.sh to install service
    usleep 1
elif [ "$1" = "2" ]; then
    # upgrade
    echo service mistd removed. please re-configure and execute install_mistd.sh again
fi

%preun

if [ "$1" = "1" ]; then
    # upgrade
    usleep 1
elif [ "$1" = "0" ]; then
    # uninstall
    /usr/share/mist/bin/remove_bridge.sh
    /usr/share/mist/bin/remove_mistd.sh
    /sbin/chkconfig --del tme-bridge
    /sbin/chkconfig --del mistd
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

* Fri Feb 25 2011 Chris Huang 20110225 
- Let user to customize mail sender of alert mail of exchange queue
- Provide client information for each exchange
* Fri Feb 18 2011 Chris Huang 20110218
- bug fix TME2.0 Adoption-  00073: if a vertex has no sub-cycle, must remove itself or the traversal will cause infinite loop
- fix shutdown to clear exchange on ZK
- ignore the broker lock node in BrokerFarm
- fix mount when ZNode:exchange's broker host not exist problem
* Wed Feb 16 2011 Chris Huang 20110216
- Fix TME2.0 Adoption- 00067: Prevent config corruption ; Add error handling ; Show clearer status ; Log console output
- tme-bridge: use JGraphT to detect forwarding loops
* Mon Feb 14 2011 Chris Huang 20110214
- change the TextMessage to ByteMessage encoding from UTF-16 to UTF-8 to meet the standard
- Use fixed port of JMX to broker
