=============
Release Notes
=============

WaiverDB 1.0.0
==============

* Replace fedmsg with fedora-messaging
* Don't validate the CA of the server when downloading a custom CA when the
  container starts

WaiverDB 0.14.1
===============

* Treat "brew-build" subject type as an alias for "koji_build": Greenwave expects
  only values of "koji_build" since that's what the policies apply to.

WaiverDB 0.14.0
===============

* Fix incorrect splitting of Python files into subpackages: Since the -common
  subpackage installs files in %{python3_sitelib}/%{name}, it should also own
  that directory instead of the main package.
  Also, exclude files that are in subpackages from the main package. Finally,
  byte-compiled files were not specified correctly for Python 3. This was
  hidden in the main package glob, instead of being in the subpackages.

* Improve authentication error response: when using Kerberos authentication it
  is necessary to configure "dns_canonicalize_hostname" inside the Kerberos
  configuration file otherwise the user will get an error response that is a bit
  ambiguous. There's no way to detect if that was the problem or it is just a
  generic authentication error. But we can provide an hint to the user, and 
  also advise to check the doc.

* Introduce a /metrics endpoint to the API for monitoring reasons.

WaiverDB 0.13.0
===============

Release 11 January 2019.

* Stop validating subject types against a hard-coded list. Since Greenwave
  now supports arbitrary subject types, this list of valid subject types
  no longer needs to be maintained.

WaiverDB 0.12.0
===============

Released 8 November 2018.

* Invalid ``subject`` values are handled during database migration.

* The :program:`waiverdb-cli` utility accepts new options
  :option:`--subject-identifier` and :option:`--subject-type` which deprecate
  :option:`--subject` option.

* python-requests-gssapi is now a ``requires`` and ``buildrequires``
  dependency.

* Locked DB scenario checked in :http:get:`/healthcheck` API endpoint.

WaiverDB 0.11.0
===============

Released 3 July 2018.

* Waivers now have two new attributes, ``subject_type`` and
  ``subject_identifier``, to identify the subject of the waiver (that is, the
  particular software artifact that the waiver is about). These new attributes
  replace the ``subject`` attribute which is now deprecated.

  The ``subject`` attribute previously accepted any arbitrary key-values, but
  in practice the ``subject`` had to conform to one of several recognized
  structures in order to be usable with Greenwave. This has now been made
  explicit with the ``subject_type`` attribute.
  See :ref:`greenwave:subject-types` in the Greenwave documentation for a list
  of possible subject types and the meaning of their corresponding identifiers.
  See `Greenwave issue 126 <https://pagure.io/greenwave/issue/126>`_ for more
  background about this change.

  For backwards compatibility the ``subject`` attribute is still included when
  fetching waivers, and accepted when creating waivers. However if you create
  a new waiver using the deprecated ``subject`` attribute, its structure must
  match one of the recognized subject types, otherwise the request will fail
  with 400 status code. In this release we have implemented support for all
  known subject types in the wild.

* New endpoint :http:post:`/api/v1.0/waivers/+filtered` deprecates
  :http:post:`/api/v1.0/waivers/+by-subjects-and-testcases`. This allows
  posting an arbitrary set of filter criteria, instead of the using a
  complicated and limited API.

* The :program:`waiverdb-cli` utility will now guess a suitable default value
  for the :option:`--product-version` option in many common cases, in order to
  make it easier to submit waivers (#111). Automated scripts should prefer to
  explicitly pass :option:`--product-version` in case the guessing logic does
  not work in all cases.

* Previously, when you requested a list of waivers, WaiverDB would consider
  waivers from unrelated users and product versions to obsolete each other
  (#137). The API now correctly returns the most recent waiver from each user,
  and for each product version.

* The documentation now includes a section describing how end users can submit
  waivers using the command-line interface (see :ref:`user-guide`, #149).

* New man page available for ``waiverdb-client.conf(5)`` (see :ref:`client-conf`).

WaiverDB 0.10.0
===============

Released 10 May 2018.

* Comment is now explicitly required when creating waivers (both in API and
  CLI).

* Multiple waivers can now be created with single POST request (#98). To create
  multiple waivers, POST list to "waivers/" instead of single waiver.

* When creating a waiver by referring to a result ID, WaiverDB now accepts
  results with ``'type': 'brew-build'`` as an alias for ``'koji_build'``.

* Messaging can be disabled is settings with ``MESSAGE_PUBLISHER = None``.

* The ``KERBEROS_HTTP_HOST`` setting in the server configuration is now
  ignored. This setting is no longer needed because GSSAPI will automatically
  find a key in the Kerberos keytab matching the service principal in the
  client request.

* New man pages are available for ``waiverdb-cli(1)`` and ``waiverdb(7)`` (REST
  API).

* Changed error message for bad ``since`` value. E.g.
  ``api/v1.0/waivers/?since=123`` results in HTTP 400 with message
  ``{"message": {"since": "time data '123' does not match format
  '%Y-%m-%dT%H:%M:%S.%f'"}}``.

* CORS headers are now supported for every request (#160).

* Wrong ``subject`` filter produces more user-friendly error (#162).

* Setting a keytab file is no longer required: if one is not explicitly set,
  ``/etc/krb5.keytab`` will be used (#55).

* Unused option ``resultsdb_api_url`` was removed from client.conf.

* Containers on Quay (`<https://quay.io/repository/factory2/waiverdb>`__).

WaiverDB 0.9.0
==============

Released 1 Mar 2018.

*  The usage of ``JSONB`` has been replaced with the older ``JSON`` column
   type, in order to maintain compatibility with PostgreSQL 9.2 on RHEL7
   (#134).

WaiverDB 0.8.0
==============

Released 16 Feb 2018.

* Removed support to SQLite in favor of PostgreSQL.

* Fixed database migration to use the correct column type for the
  ``waiver.subject`` column (#129).

* Added information on the README file on how to configure the db.

WaiverDB 0.7.0
==============

Released 16 Feb 2018.

* Fixed the database migration strategy for Openshift deployment (#121).
  The migration step is now run in a pre-deployment hook. Previously it ran in
  a post-start pod hook which did not work correctly in some situations.

WaiverDB 0.6.0
==============

Released 13 Feb 2018.

* Dummy authentication for CLI for developing and debugging reasons.

* Added logo in the README page.

* You can now waive the absence of a result. Now it is possible to
  submit waivers using a subject/testcase.

* Backward compatibility for submitting a waiver using the result_id.
  This feature will be removed in the near future.

WaiverDB 0.5.0
==============

Released 17 Jan 2018.

* Database migrations have been introduced, and will be a part of future
  releases.  Users upgrading to 0.5 will need to run these commands::

  $ waiverdb db stamp 0a27a8ad723a
  $ waiverdb db upgrade

* Error messages are now returned by the API in JSON format.

* A new authentication method: ssl auth.  See the docs for more on
  configuration.

* The API now supports a proxyuser argument.  A limited set of superusers,
  configured server-side, are able to submit waivers on behalf of other users.

WaiverDB 0.4.0
==============

Released 08 Nov 2017.

A number of issues have been resolved in this release:

* New WaiverDB CLI for creating waivers (#82).

* New `/about` API endpoint to expose the current running version and the method
  used for authentication of the server.

* Improved the process of building docs by using sphinxcontrib.issuetracker
  extension.

WaiverDB 0.3.0
==============

Released 26 Sep 2017.

A number of issues have been resolved in this release:

* Fixed some type errors in the API docs examples (#73).

* Updated README to recommend installing package dependencies using dnf builddep (#74).

* Fixed the health check API to return a proper error if the application is not
  able to serve requests (#75).

Other updates:

* Supports a new HTTP API `/api/v1.0/waivers/+by-result-ids`.
* Package dependencies are switched to python2-* packages in Fedora.

WaiverDB 0.2.0
==============

Released 16 June 2017.

* Supports containerized deployment in OpenShift. ``DATABASE_PASSWORD`` and
  ``FLASK_SECRET_KEY`` can now be passed in as environment variables instead of
  being defined in the configuration file.

* Supports publishing messages over AMQP, in addition to Fedmsg.
  The ``ZEROMQ_PUBLISH`` configuration option has been renamed to
  ``MESSAGE_BUS_PUBLISH``.

* The :file:`/etc/waiverdb/settings.py` configuration file is no longer
  installed by default. For new installations, you can copy the example
  configuration from :file:`/usr/share/doc/waiverdb/conf/settings.py.example`.

* Numerous improvements to the test and build process for WaiverDB.

WaiverDB 0.1.0
==============

Initial release, 12 April 2017.
