#!/bin/bash

# SPDX-License-Identifier: GPL-2.0+

set -e

version="$1"
prerelease="$2"

if [ -z "$version" ] ; then
    echo "Usage: $0 <version> [<prerelease>]" >&2
    echo "Example: $0 0.1 rc1" >&2
    exit 1
fi

if git status --porcelain | grep -q '^.M' ; then
    echo "Work tree has modifications, stash or add before tagging" >&2
    exit 1
fi

sed -i -e "/%global upstream_version /c\%global upstream_version ${version}${prerelease}" waiverdb.spec
sed -i -e "/^Version:/c\Version:        $version" waiverdb.spec
if [ -n "$prerelease" ] ; then
    sed -i -e "/^Release:/c\Release:        0.$prerelease%{?dist}" waiverdb.spec
else
    sed -i -e "/^Release:/c\Release:        1%{?dist}" waiverdb.spec
fi
sed -i -e "/^version = /c\version = '$version$prerelease'" setup.py
git add setup.py waiverdb.spec
git commit -m "Automatic commit of release $version$prerelease"
git tag -a "waiverdb-$version$prerelease" -m "Tagging release $version$prerelease"
