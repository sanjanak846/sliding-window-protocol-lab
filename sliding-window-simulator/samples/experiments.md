# Sample experiment scenarios (paste into the Experiments tab)

Leave "Timeout = k x RTT" at 1.5 so the timeout follows the delay you sweep.

| Scenario | Loss % | Corruption % | Windows | Delay ms | Packets | Trials |
|---|---|---|---|---|---|---|
| A. Loss sweep (default) | 0, 5, 10, 20, 30 | 0 | 2, 4, 8, 16 | 50 | 100 | 5 |
| B. Corruption sweep | 0 | 0, 5, 10, 20, 30 | 8 | 50 | 100 | 10 |
| C. Window sweep, lossy link | 10 | 0 | 1, 2, 4, 8, 16, 32 | 50 | 200 | 10 |
| D. Long-delay links | 5 | 0 | 4, 16 | 20, 50, 100, 200 | 100 | 5 |
| E. Transfer size | 10 | 5 | 8 | 50 | 20, 50, 100, 500 | 5 |

Recommendation demo: loss 20 %, window 8, 100 packets, tick *max retransmissions* = 150 -> SR typically
passes and GBN fails; then lower the limit below SR's measured count to see "neither".
