
%define __os_install_post \
    /usr/lib/rpm/brp-compress \
    %{!?__debug_package:/usr/lib/rpm/brp-strip %{__strip}} \
    /usr/lib/rpm/brp-strip-static-archive %{__strip} \
    /usr/lib/rpm/brp-strip-comment-note %{__strip} %{__objdump} \
    /usr/lib/rpm/brp-python-bytecompile \
%{nil}

%define name tme-mist
%define ver #MAJOR_VER#

Summary: TME MIST Daemon
Name: %{name}
Version: %{ver}
Release: #RELEASE_VER#
License: Trend Micro Inc.
Group: System Environment/Daemons
Source: %{name}-%{ver}.tar.gz
BuildRoot: %{_tmppath}/%{name}-%{ver}-root
Requires: jdk, tme-common >= 1.0-20111222Z, monit
Requires(post): /sbin/chkconfig, /sbin/service
Requires(preun): /sbin/chkconfig, /sbin/service

%description

TME MIST Daemon

%prep

rm -rf $RPM_BUILD_ROOT
mkdir -p $RPM_BUILD_ROOT

%setup -q

%build

%install

cp -rf * $RPM_BUILD_ROOT/

%clean

rm -rf $RPM_BUILD_ROOT

%files
/etc/init.d/tme-mistd
/usr/bin/mist-session
/usr/bin/mist-sink
/usr/bin/mist-source
/usr/bin/mist-decode
/usr/bin/mist-encode
/usr/bin/mist-line-gen
/usr/bin/tme-console

%dir 
/opt/trend/tme/bin
/opt/trend/tme/lib

%config /opt/trend/tme/conf/mist/mistd.properties
%config /opt/trend/tme/conf/mist/tme-mistd.monit
%config /opt/trend/tme/conf/mist/log4j.properties
%config /opt/trend/tme/conf/mist/mistd.log4j

%pre

if [ "`getent passwd TME`" == "" ]; then
	echo "Error: must create user TME first!"
	exit 1
fi

if [ "$1" = "1" ]; then
    # install
	usleep 1
elif [ "$1" = "2" ]; then
    # upgrade
	usleep 1
fi

%post

if [ "$1" = "1" ]; then
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
