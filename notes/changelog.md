The version `2.0.0-RC1-10-g9abe18a` was in use for quite some time, so this is just a release to fix it as the next release candidate. Mostly it's about some minor improvements and bugfixes to the 2.0.0-RC1 version:

* Updated better-files
* #75: Fixed the problem with empty file check
* #74: Logging-related improvements:
    + Fixed worker fatal failure notification subject
    + Improved worker non-fatal error message
    + Made transfer manager down/uploads silent again
    + Removed logging on each log upload
