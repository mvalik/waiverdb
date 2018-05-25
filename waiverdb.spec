
%global upstream_version 0.10.0

%if 0%{?fedora} || 0%{?rhel} > 7
%bcond_without server
%else
# For EPEL7 we build only the CLI, not the server bits,
# because Flask and other dependencies are too old.
%bcond_with server
%endif

Name:           waiverdb
Version:        0.10.0
Release:        1%{?dist}
Summary:        Service for waiving results in ResultsDB
License:        GPLv2+
URL:            https://pagure.io/waiverdb
Source0:        https://files.pythonhosted.org/packages/source/w/%{name}/%{name}-%{upstream_version}.tar.gz

BuildArch:      noarch
%if 0%{?fedora} || 0%{?rhel} > 7
BuildRequires:  python3-devel
BuildRequires:  python3-setuptools
%else # EPEL7 uses Python 2 and python- package naming convention
BuildRequires:  python2-devel
BuildRequires:  python-setuptools
%endif

%if %{with server}
BuildRequires:  python3-sphinx
BuildRequires:  python3-sphinxcontrib-httpdomain
BuildRequires:  python3-sphinxcontrib-issuetracker
BuildRequires:  python3-flask
BuildRequires:  python3-sqlalchemy
BuildRequires:  python3-flask-restful
BuildRequires:  python3-flask-sqlalchemy
BuildRequires:  python3-psycopg2
BuildRequires:  python3-gssapi
BuildRequires:  python3-pytest
BuildRequires:  python3-mock
BuildRequires:  python3-flask-oidc
BuildRequires:  python3-click
BuildRequires:  python3-flask-migrate
BuildRequires:  python3-stomppy
BuildRequires:  python3-fedmsg
BuildRequires:  python-flask
BuildRequires:  python-sqlalchemy
BuildRequires:  python-flask-restful
BuildRequires:  python-flask-sqlalchemy
BuildRequires:  python-psycopg2
BuildRequires:  python-gssapi
BuildRequires:  pytest
BuildRequires:  python-mock
BuildRequires:  python-flask-oidc
BuildRequires:  python-click
BuildRequires:  python-configparser
BuildRequires:  python-flask-migrate
BuildRequires:  stomppy
BuildRequires:  fedmsg
Requires:       python3-flask
Requires:       python3-sqlalchemy
Requires:       python3-flask-restful
Requires:       python3-flask-sqlalchemy
Requires:       python3-psycopg2
Requires:       python3-gssapi
Requires:       python3-mock
Requires:       python3-flask-oidc
Requires:       python3-click
Requires:       python3-flask-migrate
Requires:       python3-stomppy
Requires:       python3-fedmsg
Requires:       waiverdb-common = %{version}-%{release}
%endif

%description
WaiverDB is a companion service to ResultsDB, for recording waivers
against test results.

%package common
Summary: Common resources for WaiverDB subpackages.

%description common
This package is not useful on its own.  It contains common filesystem resources
for other WaiverDB subpackages.

%package cli
Summary: A CLI tool for interacting with waiverdb
%if 0%{?fedora} || 0%{?rhel} > 7
BuildRequires:  python3-click
Requires:       python3-click
%else
BuildRequires:  python-click
Requires:       python-click
Requires:       python-configparser
%endif

Requires:       waiverdb-common = %{version}-%{release}

%description cli
This package contains a CLI tool for interacting with waiverdb.

Primarily, submitting new waiverdbs.

%prep
%setup -q -n %{name}-%{upstream_version}

# Replace any staging urls with prod ones
sed -i 's/\.stg\.fedoraproject\.org/.fedoraproject.org/g' conf/client.conf.example

%build
%if 0%{?fedora} || 0%{?rhel} > 7
%py3_build
make -C docs SPHINXOPTS="-D issuetracker_plaintext_issues=0" html man text
%else
%py2_build
%endif

%install
%if 0%{?fedora} || 0%{?rhel} > 7
%py3_install
%else
PYTHONDONTWRITEBYTECODE=1 %py2_install
%endif

%if ! %{with server}
# Need to properly split out the client one day...
rm %{buildroot}%{_bindir}/waiverdb
ls -d %{buildroot}%{python2_sitelib}/waiverdb/* | grep -E -v '(__init__.py|cli.py)$' | xargs rm -r
%endif

install -d %{buildroot}%{_sysconfdir}/waiverdb/
install -m0644 \
    conf/client.conf.example \
    %{buildroot}%{_sysconfdir}/waiverdb/client.conf

%if 0%{?fedora} || 0%{?rhel} > 7
install -D -m0644 \
    docs/_build/man/waiverdb-cli.1 \
    %{buildroot}%{_mandir}/man1/waiverdb-cli.1

install -D -m0644 \
    docs/_build/man/client.conf.5 \
    %{buildroot}%{_mandir}/man5/waiverdb-client.conf.5

install -D -m0644 \
    docs/_build/man/waiverdb.7 \
    %{buildroot}%{_mandir}/man7/waiverdb.7
%endif

# Tests don't make sense here now that we require postgres to run them.
#%%check
#export PYTHONPATH=%%{buildroot}/%%{python3_sitelib}
#py.test-3 tests/

%if %{with server}
%files
%{python3_sitelib}/%{name}
%{python3_sitelib}/%{name}*.egg-info
%attr(755,root,root) %{_bindir}/waiverdb
%endif

%files common
%license COPYING
%doc README.md conf
%if 0%{?fedora} || 0%{?rhel} > 7
%doc docs/_build/html docs/_build/text
%{python3_sitelib}/%{name}/__init__.py*
%{python3_sitelib}/%{name}*.egg-info
%else
%{python2_sitelib}/%{name}/__init__.py*
%{python2_sitelib}/%{name}*.egg-info
%endif

%files cli
%license COPYING
%if 0%{?fedora} || 0%{?rhel} > 7
%{python3_sitelib}/%{name}/cli.py*
%else
%{python2_sitelib}/%{name}/cli.py*
%endif
%attr(755,root,root) %{_bindir}/waiverdb-cli
%config(noreplace) %{_sysconfdir}/waiverdb/client.conf

%if 0%{?fedora} || 0%{?rhel} > 7
%{_mandir}/man1/waiverdb-cli.1*
%{_mandir}/man5/waiverdb-client.conf.5*
%{_mandir}/man7/waiverdb.7*
%endif

%changelog
