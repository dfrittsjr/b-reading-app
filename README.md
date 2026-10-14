# b-reading-app

A minimal Android app (Kotlin + Jetpack Compose) that opens straight to today's reading
from a Bible reading plan. Only references are shown (e.g. "John 3"); you read them in
your own Bible.

## Reading plans

Plans are plain-text files in [`data/plans/`](data/plans/README.md), bundled into the
app at build time. Each line is one day. Pick a plan from the title bar, and set its
start date with the calendar button. Day 1 is the start date, except for yearly plans
like M'Cheyne, which follow the calendar (day 1 is January 1).

## Build & install

Open the project in Android Studio (it installs the SDK), or from the command line with
an SDK configured:

```sh
./gradlew installDebug   # with the phone connected via USB debugging
./gradlew test           # plan parser unit tests
```
