# Requirement-based recommendation

## Inputs
**Hard requirements** (each optional; unticked = not checked):
delivery ratio >= x %, average delay <= x ms, throughput >= x kbit/s, efficiency >= x %,
retransmissions <= x. (A maximum acceptable loss rate is the same thing as min delivery ratio = 100 - loss.)
The network conditions (packets, window, loss, corruption, delay, ...) come from the configuration panel.
**Preferences**: weights >= 0 for throughput, low delay, few retransmissions, efficiency, and a tie margin.

## Procedure
1. Validate configuration and requirements (errors abort; nothing is simulated).
2. Run both protocols with the **same** configuration and seed (common random numbers).
3. Compute metrics.
4. Check every specified hard constraint. A run that ended abnormally (event limit / stall) fails the
   implicit "simulation validity" constraint. A delay of NaN (nothing delivered) fails any delay limit.
5. Protocols violating any hard constraint are **eliminated**.
6. One survivor -> recommended.
7. Both survive -> rank by score; if |score_SR - score_GBN| < tie margin -> **TIE** (trade-offs are shown;
   GBN's lower implementation complexity is mentioned as an extra-metric tie-breaker, but no winner is
   declared). Otherwise the higher score wins.
8. No survivor -> NEITHER, listing each violated constraint with measured vs required values.
9. The explanation quotes the measured values.

## Scoring (only decisive when both protocols pass)
For metric values (g, s) of GBN and SR:

* benefit metrics (throughput, efficiency): `score = v / max(g, s)`; both 0 -> 1 each
* cost metrics (delay, retransmissions): `score = min(g, s) / v`; both 0 -> 1 each; v = 0 -> 1;
  an undefined (NaN) delay scores 0 (both NaN -> 0 each)

`Score(P) = (w_thr*s_thr + w_delay*s_delay + w_retx*s_retx + w_eff*s_eff) / (w_thr + w_delay + w_retx + w_eff)`

Scores are in [0, 1]. They are displayed even when a protocol is eliminated, labelled informational.
The score never overrides a violated hard constraint.
