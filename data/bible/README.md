# Bible text

`kjv.tsv` is the King James Version (standard 1769 text, 66 books, 31,102 verses), bundled
into the app so the Bible view works offline. It comes from eBible.org's verse-per-line
edition (`eng-kjv2006_vpl`, courtesy of the CrossWire Bible Society and eBible.org), and was
checked verse by verse against Project Gutenberg's KJV (#10).

The KJV is in the public domain everywhere except the United Kingdom, where printing it is
governed by the Crown's letters patent.

## Format

One verse per line, tab-separated, in canonical order:

```
Genesis	1	1	In the beginning God created the heaven and the earth.
```

- Book names match the app's book list (`Bible.books`). Every chapter must be present, with
  its verses numbered from 1 in order.
- `[brackets]` mark words the translators supplied, printed in italics in the KJV. They can
  nest (1 John 2:23). The app shows them in italics.
- `¶` at the start of a verse marks a new paragraph, as in the 1611 printing (they stop
  after Acts 20).
