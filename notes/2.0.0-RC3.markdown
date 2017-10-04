This release is brings a lot of improvements with minimum impact on the usage interface. It drops old and unnecessary dependencies, updates the rest, brings some new functionality and improves internals in preparation for the final release.

* #70: Dropped upickle dependency (it was deprecated)
* #73: Dropped better-files dependency
* #77: Local manager functionality:
    + You can run loquat manager locally at your computer, useful for small tasks and testing:
        ```scala
        loquat.launchLocally(user)
        ```
    + You can also run progress monitor (a.k.a terminator) manually:
        ```scala
        loquat.monitorProgress(interval = 3.minutes)
        ```
* #79, #81: Updated all dependencies and cross-compiled with Scala 2.11/2.12
* Other minor improvements:
    + Added initial SQS visibility timeout as a config parameter
    + Improved worker logging
