# DLA corpus label report

- documents scanned: 200
- usable responses: 200
- documents with at least one visual object: 100
- documents with at least one equation: 16

## Visual objects by class

| label | class | count |
|---|---|---|
| 10 | Figure | 69 |
| 18 | Chart | 49 |
| 19 | Image | 46 |

Chart + Image account for 95 of 164 visual objects (58%). Those are the regions that were dropped entirely before the transformer learned labels 18 and 19.

## Class combinations per document

- `Figure`: 32
- `Chart`: 30
- `Image`: 27
- `Figure+Image`: 6
- `Chart+Figure`: 5

## Overlapping visual detections

One graphic reported under two classes, at IoU >= 0.8. De-duplication keeps the more confident detection.

| document / page | detection A | detection B | IoU |
|---|---|---|---|
| 01030000000009 p0 | Image (0.977) | Figure (0.305) | 0.989 |
| 01030000000100 p0 | Chart (0.745) | Figure (0.387) | 0.986 |
| 01030000000107 p0 | Figure (0.814) | Chart (0.450) | 0.964 |

Confidence gap within a duplicate pair: 0.358-0.671. A wide, consistent gap is what makes confidence a usable tie-breaker; the winning *label* varies.

## All labels seen

| label | count |
|---|---|
| 0 | 49 |
| 1 | 105 |
| 2 | 767 |
| 3 | 251 |
| 4 | 46 |
| 6 | 152 |
| 7 | 17 |
| 8 | 15 |
| 9 | 58 |
| 10 | 69 |
| 11 | 67 |
| 12 | 35 |
| 13 | 40 |
| 14 | 75 |
| 15 | 91 |
| 16 | 10 |
| 17 | 98 |
| 18 | 49 |
| 19 | 46 |

