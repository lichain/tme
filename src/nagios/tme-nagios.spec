
%define name tme-nagios
%define ver #MAJOR_VER#

Summary: TME Nagios Plugin
Name: %{name}
Version: %{ver}
Release: #RELEASE_VER#
License: Trend Micro Inc.
Group: System Environment/Daemons
Source: %{name}-%{ver}.tar.gz
BuildRoot: %{_tmppath}/%{name}-%{ver}-root
Requires: jdk

%description

TME Nagios Plugin

%prep

%setup -q

%{__mkdir} -p $RPM_BUILD_ROOT/opt/trendmicro/%{name}

%build

%install

install -m755 opt/trendmicro/%{name}/check-* $RPM_BUILD_ROOT/opt/trendmicro/%{name}
install -m644 opt/trendmicro/%{name}/tme-nagios-plugin.jar $RPM_BUILD_ROOT/opt/trendmicro/%{name}

%clean

rm -rf $RPM_BUILD_ROOT

%files

%dir /opt/trendmicro/%{name}
/opt/trendmicro/%{name}/check-*
/opt/trendmicro/%{name}/tme-nagios-plugin.jar

%pre

%post

%preun

%postun

