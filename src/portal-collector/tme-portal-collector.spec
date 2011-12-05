
%define __os_install_post \
    /usr/lib/rpm/brp-compress \
    %{!?__debug_package:/usr/lib/rpm/brp-strip %{__strip}} \
    /usr/lib/rpm/brp-strip-static-archive %{__strip} \
    /usr/lib/rpm/brp-strip-comment-note %{__strip} %{__objdump} \
    /usr/lib/rpm/brp-python-bytecompile \
%{nil}

%define name tme-portal-collector
%define ver #MAJOR_VER#

Summary: TME Portal Collector
Name: %{name}
Version: %{ver}
Release: #RELEASE_VER#
License: Trend Micro Inc.
Group: System Environment/Daemons
Source: %{name}-%{ver}.tar.gz
BuildRoot: %{_tmppath}/%{name}-%{ver}-root
Requires: jdk, tme-common
Requires(post): /sbin/chkconfig, /sbin/service
Requires(preun): /sbin/chkconfig, /sbin/service

%description

TME Portal Collector

%prep

rm -rf $RPM_BUILD_ROOT
mkdir -p $RPM_BUILD_ROOT

%setup -q

%build

%install

cp -rf * $RPM_BUILD_ROOT/
mkdir -p $RPM_BUILD_ROOT/var/lib/tme/portal-collector

%clean

rm -rf $RPM_BUILD_ROOT

%files
/etc/init.d/tme-portal-collector

%dir 
/opt/trend/tme/bin
/opt/trend/tme/lib
/var/lib/tme/portal-collector

%config /opt/trend/tme/conf/portal-collector/portal-collector.properties
%config /opt/trend/tme/conf/portal-collector/exchange.xml
%config /opt/trend/tme/conf/portal-collector/logback.xml

%pre

if [ "$1" = "1" ]; then
    # install
	usleep 1
elif [ "$1" = "2" ]; then
    # upgrade
	usleep 1
fi

%post

if [ "$1" = "1" ]; then
    chown TME:TME /var/lib/tme/portal-collector
    # install
    usleep 1
elif [ "$1" = "2" ]; then
    # upgrade
    usleep 1
fi

%preun

if [ "$1" = "1" ]; then
    # upgrade
    usleep 1
elif [ "$1" = "0" ]; then
    # uninstall
    usleep 1
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
* Tue Nov 29 2011 Scott Wang
- Initial
