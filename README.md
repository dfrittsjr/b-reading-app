# b-reading-app

A minimal Android app (Kotlin + Jetpack Compose) that opens straight to today's reading
from a Bible reading plan. The plan view shows references (e.g. "John 3"); the switch in
the bottom bar flips to a Bible view with the full King James Version, stored in the app
so it works offline.

## Bible view

The KJV scrolls continuously from Genesis to Revelation. The bottom bar shows the chapter
you're in: tap it to pick a book and chapter (Old/New Testament tabs, then a chapter
grid), or use the arrows to step a chapter at a time. The app remembers where you left
off. The text is in [`data/bible/`](data/bible/README.md).

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
