# Metric definitions

Let N = packets generated, D = unique packets delivered to the application (in order), A = total data
transmission attempts, T = completion time (s), L = payload bits per packet.

| Metric | Formula | Notes |
|---|---|---|
| Packets generated | N | |
| Unique delivered | D | A packet counts once, however often it was received |
| Delivery ratio | D / N x 100 | 0 if N = 0 |
| Transmission attempts | A | initial sends + retransmissions |
| Retransmissions | A - (number of distinct packets ever sent) | attempts beyond the first per packet |
| ACK count | ACKs emitted by the receiver | lost/corrupted ACK counts reported separately |
| Lost / corrupted packets | data transmissions lost / corrupted by the channel | counted when transmitted |
| Completion time | time of the last ACK (COMPLETED) or the abort time | simulated ms |
| Avg delivery delay | mean over delivered packets of (delivery time - first transmission start) | includes retransmission waiting; NaN ("n/a") if D = 0 |
| Throughput | D x L / T | bit/s; 0 if T = 0 or D = 0 |
| Protocol efficiency | D x L / (A x L) x 100 | payload bits only, ACKs and headers excluded; 0 if A = 0 |
| Channel utilization | A x tx_delay / (T x 1000) x 100, capped at 100 | fraction of time the sender link transmits; includes retransmissions |

*Efficiency* answers "what fraction of what we sent was useful?"; *utilization* answers "how busy was the
link?". A protocol can have 100 % utilization and 20 % efficiency (a link kept busy resending data).

"Delivered" means handed to the application *in order*. For GBN this equals arrival; for SR a buffered
packet is delivered when the gap before it is filled.

Invalid runs (event limit, stall) are flagged and excluded from experiment statistics.
