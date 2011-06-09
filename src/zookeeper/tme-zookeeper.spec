
%define __os_install_post \
    /usr/lib/rpm/brp-compress \
    %{!?__debug_package:/usr/lib/rpm/brp-strip %{__strip}} \
    /usr/lib/rpm/brp-strip-static-archive %{__strip} \
    /usr/lib/rpm/brp-strip-comment-note %{__strip} %{__objdump} \
    /usr/lib/rpm/brp-python-bytecompile \
%{nil}

%define name tme-zookeeper
%define ver	#MAJOR_VER#

Summary: zookeeper
Name: %{name}
Version: %{ver}
Release: #RELEASE_VER#
License: Trend Micro Inc.
Group: System Environment/Daemons
Source: %{name}-%{ver}.tar.gz
BuildRoot: %{_tmppath}/%{name}-%{ver}-root
Requires: jdk, spn-infra-common
Requires(pre): /bin/tar, /usr/bin/gzip
Requires(post): /sbin/chkconfig, /sbin/service
Requires(preun): /sbin/chkconfig, /sbin/service

%description

Zookeeper package for TME

%prep

%setup -q

%{__mkdir} -p $RPM_BUILD_ROOT/usr/share
%{__mkdir} -p $RPM_BUILD_ROOT/etc/init.d
%{__mkdir} -p $RPM_BUILD_ROOT/usr/share/tme-zookeeper

cd zookeeper-3.3.2/conf; cat zoo_sample.cfg | sed -e 's/dataDir=.*/dataDir=\/var\/tme-zookeeper/' > zoo.cfg; cd -

cp -rf zookeeper-3.3.2/zookeeper-3.3.2.jar $RPM_BUILD_ROOT/usr/share/tme-zookeeper
cp -rf zookeeper-3.3.2/conf $RPM_BUILD_ROOT/usr/share/tme-zookeeper/conf
cp -rf zookeeper-3.3.2/bin $RPM_BUILD_ROOT/usr/share/tme-zookeeper/bin
cp -rf zookeeper-3.3.2/lib $RPM_BUILD_ROOT/usr/share/tme-zookeeper/lib

%build

%install

install -m755 etc/init.d/tme-zookeeperd $RPM_BUILD_ROOT/etc/init.d
install -m755 usr/share/tme-zookeeper/watchdog-zookeeperd $RPM_BUILD_ROOT/usr/share/tme-zookeeper
install -m755 usr/share/tme-zookeeper/install_zookeeperd.sh $RPM_BUILD_ROOT/usr/share/tme-zookeeper
install -m755 usr/share/tme-zookeeper/remove_zookeeperd.sh $RPM_BUILD_ROOT/usr/share/tme-zookeeper
install -m755 usr/share/tme-zookeeper/change_zk_mem.sh $RPM_BUILD_ROOT/usr/share/tme-zookeeper
install -m644 usr/share/tme-zookeeper/etc/tme-zookeeper.cron $RPM_BUILD_ROOT/usr/share/tme-zookeeper
install -m644 usr/share/tme-zookeeper/etc/log4j.properties $RPM_BUILD_ROOT/usr/share/tme-zookeeper/conf
install -m600 usr/share/tme-zookeeper/jmxremote.* $RPM_BUILD_ROOT/usr/share/tme-zookeeper

%clean

rm -rf $RPM_BUILD_ROOT

%files

/etc/init.d/tme-zookeeperd

%dir

/usr/share/tme-zookeeper

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
    installed=`rpm -qa | grep tme-zookeeper | wc -l`
    if [ "$installed" = "1" ]; then
        echo `rpm -qa | grep tme-zookeeper` already installed
        exit 1
    fi
    if [ "`getent passwd TME`" == "" ]; then
        /usr/sbin/useradd -r TME
    fi
elif [ "$1" = "2" ]; then
    # upgrade
    /usr/share/tme-zookeeper/remove_zookeeperd.sh
    cp -f /usr/share/tme-zookeeper/conf/zoo.cfg{,.old}
    echo backup configuration to /usr/share/tme-zookeeper/conf/zoo.cfg.old
fi 

%post

mkdir -p /var/tme-zookeeper
mkdir -p /var/run/tme 

chown -R TME.TME /usr/share/tme-zookeeper
chown -R TME.TME /var/tme-zookeeper
chown -R TME.TME /var/run/tme

/usr/share/tme-zookeeper/change_zk_mem.sh 3072m > /dev/null
if [ "$1" = "1" ]; then
    # install
    /sbin/chkconfig --add tme-zookeeperd
    /sbin/chkconfig --level 35 tme-zookeeperd on
    echo done. please configure and execute install_zookeeperd.sh to install service
elif [ "$1" = "2" ]; then
    # upgrade
    echo service tme-zookeeperd removed. please re-configure and execute install_zookeeperd.sh again
fi

%preun

if [ "$1" = "1" ]; then
    # upgrade
    usleep 1
elif [ "$1" = "0" ]; then
    # uninstall
    /usr/share/tme-zookeeper/remove_zookeeperd.sh
    /sbin/chkconfig --del tme-zookeeperd
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
